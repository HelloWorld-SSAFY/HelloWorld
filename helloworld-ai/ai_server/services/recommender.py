from __future__ import annotations
import random
from dataclasses import dataclass
from typing import List, Optional, Dict, Any, Tuple
from datetime import timedelta, timezone, datetime

from django.db import transaction
from django.utils import timezone as dj_tz

from api.models import (
    Content,
    RecommendationSession,
    ExposureCandidate,
    ItemRec,
    ContentStat,
    UserContentStat,
)

# CTS 스코어링 유틸
from services.scoring import score_with_reason

KST = timezone(timedelta(hours=9))

@dataclass
class RecInput:
    user_ref: str
    category: str                # e.g. "BREATHING","MEDITATION","YOGA","MUSIC","WALK"
    context: Optional[Dict[str, Any]] = None  # optional (hr, stress, week, time_bucket, etc.)

@dataclass
class RecOutput:
    session_id: str
    category: str
    picked: Dict[str, Any]                 # selected content (dict)
    reason: str
    candidates: List[Dict[str, Any]]       # (debug) up to N candidates with scores


# ─────────────────────────────────────────────────────────────
# 작은 유틸들
# ─────────────────────────────────────────────────────────────
def _as_list_lower(x) -> List[str]:
    if x is None:
        return []
    if isinstance(x, (list, tuple, set)):
        return [str(v).lower() for v in x]
    if isinstance(x, str):
        if "," in x:
            return [t.strip().lower() for t in x.split(",") if t.strip()]
        return [x.strip().lower()] if x.strip() else []
    return []

def _get_excluded_tags(ctx: Dict[str, Any]) -> List[str]:
    out = []
    out += _as_list_lower(ctx.get("taboo_tags"))
    out += _as_list_lower(ctx.get("excluded_tags"))
    return list(dict.fromkeys(out))

def _tags_from_content(c: Content) -> List[str]:
    for attr in ("tags", "labels", "topics"):
        if hasattr(c, attr):
            vals = getattr(c, attr)
            tags = _as_list_lower(vals)
            if tags:
                return tags
    return []

def _tags_from_exposure(ec: Optional[ExposureCandidate]) -> List[str]:
    if not ec:
        return []
    try:
        x = ec.x_item_vec or {}
        if isinstance(x, dict):
            for k in ("tags", "labels", "topics"):
                if k in x:
                    return _as_list_lower(x[k])
    except Exception:
        pass
    return []

def _has_taboo(all_tags: List[str], excluded: List[str]) -> bool:
    if not excluded:
        return False
    st = set(all_tags)
    for t in excluded:
        if t.lower() in st:
            return True
    return False

def _duration_minutes(c: Content, ec: Optional[ExposureCandidate]) -> Optional[float]:
    for name in ("length_sec", "duration_sec", "duration_seconds"):
        if hasattr(c, name):
            try:
                v = getattr(c, name)
                return float(v) / 60.0 if v is not None else None
            except Exception:
                pass
    for name in ("duration_min", "duration_minutes"):
        if hasattr(c, name):
            try:
                v = getattr(c, name)
                return float(v) if v is not None else None
            except Exception:
                pass
    try:
        x = ec.x_item_vec if ec is not None else None
        if isinstance(x, dict):
            if "duration_min" in x and x["duration_min"] is not None:
                return float(x["duration_min"])
            if "duration_sec" in x and x["duration_sec"] is not None:
                return float(x["duration_sec"]) / 60.0
            if "duration_ms" in x and x["duration_ms"] is not None:
                return float(x["duration_ms"]) / 60000.0
    except Exception:
        pass
    return None

def _provider_for(c: Content) -> Optional[str]:
    prov = getattr(c, "provider", None)
    if prov:
        return str(prov).lower()
    url = getattr(c, "url", "") or ""
    u = url.lower()
    if "spotify" in u:
        return "spotify"
    if "youtu" in u:
        return "youtube"
    return None

def _thumb_for(sess: RecommendationSession, c: Content) -> Optional[str]:
    thumb = getattr(c, "thumbnail_url", None)
    if thumb:
        return thumb
    ec = ExposureCandidate.objects.filter(session=sess, content=c).first()
    if ec and isinstance(ec.x_item_vec, dict):
        t = ec.x_item_vec.get("thumb_url") or ec.x_item_vec.get("thumbnail_url")
        if t:
            return t
    return None

def _parse_pregnancy_week(ctx: Dict[str, Any]) -> Optional[int]:
    # 우선순위: pregnancy_week → gw → trimester로 추정
    if ctx is None:
        return None
    if ctx.get("pregnancy_week") is not None:
        try:
            return int(ctx.get("pregnancy_week"))
        except Exception:
            pass
    if ctx.get("gw") is not None:
        try:
            return int(ctx.get("gw"))
        except Exception:
            pass
    tri = ctx.get("trimester")
    try:
        tri = int(tri) if tri is not None else None
    except Exception:
        tri = None
    # 대표 주차로 매핑(대략값)
    if tri == 1:
        return 10
    if tri == 2:
        return 22
    if tri == 3:
        return 34
    return None

def _prefs_from_context(ctx: Dict[str, Any]) -> Dict[str, Any]:
    """services.scoring의 compute_context_boost에서 기대하는 형태로 변환"""
    prefs_in = ctx.get("preferences") if isinstance(ctx.get("preferences"), dict) else {}
    lang = (prefs_in.get("lang") or ctx.get("lang") or "ko")
    # duration_min/max는 '분', scoring은 '초' 범위를 기대
    dmin = prefs_in.get("duration_min")
    dmax = prefs_in.get("duration_max")
    if dmin is None and dmax is None:
        dur_range_sec = (300, 900)  # 기본 5~15분
    else:
        lo = int(dmin) if dmin is not None else 5
        hi = int(dmax) if dmax is not None else 15
        dur_range_sec = (max(60, lo*60), min(60*180, hi*60))
    prefer_guided = bool(prefs_in.get("allow_voice_guidance", True))
    return {
        "lang": str(lang),
        "duration_range_sec": dur_range_sec,
        "prefer_guided": prefer_guided,
    }

# ─────────────────────────────────────────────────────────────
# 후보 소스
# ─────────────────────────────────────────────────────────────
def _fetch_candidates(category: str, limit: int = 30) -> List[Content]:
    return list(
        Content.objects.filter(is_active=True, category__iexact=category)
        .order_by("-id")[:limit]
    )


@transaction.atomic
def recommend_on_session(session_id: str, rec_in: RecInput) -> RecOutput:
    """
    CTS: score = pre × context_boost × θ
      - taboo/excluded 태그는 필터에서 제외
      - 선호 강필터: duration_min/max, music_provider (정보 없는 항목은 통과)
      - 스코어링/탐색: services.scoring.score_with_reason 사용 (전역/개인 Beta 결합)
    """
    try:
        sess = RecommendationSession.objects.select_for_update().get(id=session_id)
    except RecommendationSession.DoesNotExist:
        raise ValueError("INVALID_SESSION")

    ctx = rec_in.context or {}
    excluded = _get_excluded_tags(ctx)
    prefs_map = _prefs_from_context(ctx)
    pregnancy_week = _parse_pregnancy_week(ctx)

    want_provider = None
    prefs_in = ctx.get("preferences") if isinstance(ctx.get("preferences"), dict) else {}
    if prefs_in.get("music_provider"):
        try:
            want_provider = str(prefs_in["music_provider"]).lower()
        except Exception:
            want_provider = None

    # 1) 세션 후보 우선 → 없으면 Content 보강
    ecs = list(
        ExposureCandidate.objects
        .filter(session=sess, content__category__iexact=rec_in.category)
        .select_related("content")
    )

    cand_pairs: List[Tuple[Content, Optional[ExposureCandidate]]] = []
    if ecs:
        for ec in ecs:
            c = ec.content
            if not c or not getattr(c, "is_active", True):
                continue
            tags = _tags_from_content(c) + _tags_from_exposure(ec)
            if _has_taboo(tags, excluded):
                continue

            # 강한 선호 필터(정보 없는 경우 통과)
            dur = _duration_minutes(c, ec)
            dmin = prefs_in.get("duration_min")
            dmax = prefs_in.get("duration_max")
            if dur is not None:
                if dmin is not None and dur < float(dmin):
                    continue
                if dmax is not None and dur > float(dmax):
                    continue
            if want_provider:
                prov = _provider_for(c)
                if prov is not None and prov != want_provider:
                    continue

            cand_pairs.append((c, ec))
    else:
        raw = _fetch_candidates(rec_in.category, limit=50)
        for c in raw:
            if not c or not getattr(c, "is_active", True):
                continue
            if _has_taboo(_tags_from_content(c), excluded):
                continue
            cand_pairs.append((c, None))

    if not cand_pairs:
        raise ValueError("No candidates for category")

    # 2) CTS 스코어링 — Thompson Sampling 포함
    rng = random.Random()  # 필요시 seed 가능
    scored_rows: List[Tuple[Content, Optional[ExposureCandidate], float, float, float, float, str]] = []
    debug_rows: List[Dict[str, Any]] = []

    for c, ec in cand_pairs:
        # 통계 로드(없으면 prior 1,1)
        cs = ContentStat.objects.filter(content=c).first()
        ucs = UserContentStat.objects.filter(user_ref=rec_in.user_ref, content=c).first()
        g_a, g_b = (cs.alpha, cs.beta) if cs else (1.0, 1.0)
        u_a, u_b = (ucs.alpha, ucs.beta) if ucs else (1.0, 1.0)

        xvec = (ec.x_item_vec if ec else {}) or {}

        score, pre, boost, theta, reason = score_with_reason(
            content_obj=c,
            x_item_vec=xvec,
            prefs=prefs_map,
            pregnancy_week=pregnancy_week,
            seen_creators=set(),  # 세션 내 창작자 중복 감점이 필요하면 컨텍스트로 전달 가능
            pre_score_field=(ec.pre_score if ec else None),
            global_alpha=g_a, global_beta=g_b,
            user_alpha=u_a, user_beta=u_b,
            alpha0=1.0, beta0=1.0, lam=0.3,
            rng=rng
        )

        scored_rows.append((c, ec, score, pre, boost, theta, reason))
        debug_rows.append({
            "content_id": c.id,
            "title": getattr(c, "title", "") or "",
            "pre": round(pre, 4),
            "boost": round(boost, 4),
            "theta": round(theta, 4),
            "score": round(score, 4),
            "thumbnail": _thumb_for(sess, c),
        })

    scored_rows.sort(key=lambda x: x[2], reverse=True)
    picked, picked_ec, best_score, pre, boost, theta, picked_reason = (
        scored_rows[0][0], scored_rows[0][1], scored_rows[0][2], scored_rows[0][3], scored_rows[0][4], scored_rows[0][5], scored_rows[0][6]
    )

    # 3) 선택 결과 저장
    rec, created = ItemRec.objects.get_or_create(
        session=sess,
        content=picked,
        defaults={"rank": 1, "score": float(best_score), "reason": picked_reason},
    )
    if not created:
        dirty = False
        if rec.score is None or float(best_score) > float(rec.score):
            rec.score = float(best_score)
            dirty = True
        if not rec.reason:
            rec.reason = picked_reason
            dirty = True
        if dirty:
            rec.save(update_fields=["score", "reason"])

    if picked_ec:
        try:
            ExposureCandidate.objects.filter(id=picked_ec.id).update(chosen_flag=True)
        except Exception:
            pass

    thumb = _thumb_for(sess, picked)

    return RecOutput(
        session_id=str(sess.id),
        category=rec_in.category.upper(),
        picked={
            "content_id": picked.id,
            "title": picked.title,
            "category": picked.category,
            "provider": getattr(picked, "provider", None),
            "url": picked.url,
            "thumbnail": thumb,
            "score": round(float(best_score), 4),
            "reason": picked_reason,
        },
        reason=picked_reason,
        candidates=debug_rows[:5],
    )
