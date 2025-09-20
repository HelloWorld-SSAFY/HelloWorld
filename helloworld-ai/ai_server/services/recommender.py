from __future__ import annotations
import random
from dataclasses import dataclass
from typing import List, Optional, Dict, Any, Tuple
from datetime import timedelta, timezone

from django.db import transaction
from django.utils import timezone as dj_tz

from api.models import (
    Content,
    RecommendationSession,
    ExposureCandidate,
    ItemRec,
)

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
    """입력값을 소문자 리스트로 정규화."""
    if x is None:
        return []
    if isinstance(x, (list, tuple, set)):
        return [str(v).lower() for v in x]
    if isinstance(x, str):
        # "a,b,c" 혹은 "a b c" 모두 대응
        if "," in x:
            return [t.strip().lower() for t in x.split(",") if t.strip()]
        return [x.strip().lower()] if x.strip() else []
    return []

def _tags_from_content(c: Content) -> List[str]:
    # 가장 흔한 필드들 보호적으로 조회
    for attr in ("tags", "labels", "topics"):
        if hasattr(c, attr):
            vals = getattr(c, attr)
            tags = _as_list_lower(vals)
            if tags:
                return tags
    # 일단 없으면 빈 리스트
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
    """Content/ExposureCandidate에서 영상 길이 추정(분). 없으면 None."""
    # Content 우선
    for name in ("duration_min", "duration_minutes"):
        if hasattr(c, name):
            try:
                v = getattr(c, name)
                return float(v) if v is not None else None
            except Exception:
                pass
    for name in ("duration_sec", "duration_seconds"):
        if hasattr(c, name):
            try:
                v = getattr(c, name)
                return float(v) / 60.0 if v is not None else None
            except Exception:
                pass
    # ExposureCandidate 보조
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

def _passes_preferences(c: Content, ec: Optional[ExposureCandidate], prefs: Optional[Dict[str, Any]]) -> bool:
    """선호 조건(있으면) 만족 여부. 모르면 통과(=느슨 필터)."""
    if not prefs:
        return True

    # 1) 길이 범위
    dmin = prefs.get("duration_min")
    dmax = prefs.get("duration_max")
    if dmin is not None or dmax is not None:
        dur = _duration_minutes(c, ec)
        if dur is not None:
            if dmin is not None and dur < float(dmin):
                return False
            if dmax is not None and dur > float(dmax):
                return False
        # dur을 모르면 통과(정보 부족)

    # 2) 음악 공급자 선호
    want = (prefs.get("music_provider") or "").lower()
    if want:
        prov = _provider_for(c)
        # 공급자를 모르면 그냥 통과, 알면 일치해야 통과
        if prov is not None and prov != want:
            return False

    # 3) 음성 안내 여부는 태그에 의존하는데, 데이터 편차가 커서 필수 조건으로는 미적용
    # allow_voice_guidance = prefs.get("allow_voice_guidance")
    # 필요하면 이후 태그('voice','narration','instrumental' 등)로 구현

    return True


# ─────────────────────────────────────────────────────────────
# Thompson Sampling (초간단 버전) + 컨텍스트 가중치
# ─────────────────────────────────────────────────────────────
def _ts_score(prior_a: float = 1.0, prior_b: float = 1.0) -> float:
    return random.betavariate(prior_a, prior_b)

def _context_boost(base: float, ctx: Optional[Dict[str, Any]], c: Content) -> float:
    if not ctx:
        return base

    # 시간대
    try:
        ts = ctx.get("ts")  # ISO8601 or None
        if ts:
            hour = int(ts[11:13])
        else:
            hour = dj_tz.now().astimezone(KST).hour
    except Exception:
        hour = dj_tz.now().astimezone(KST).hour

    hr = ctx.get("hr")
    stress = ctx.get("stress")
    week = ctx.get("pregnancy_week", ctx.get("gw"))
    trimester = ctx.get("trimester")

    cat = (c.category or "").upper()
    score = base

    # 밤 시간: 호흡/명상 소폭 가점
    if hour is not None and (hour >= 22 or hour <= 6):
        if cat in ("BREATHING", "MEDITATION"):
            score += 0.05

    # HR/Stress 기반 소폭 가점
    if isinstance(hr, (int, float)) and hr >= 110:
        if cat == "BREATHING":
            score += 0.03
        if cat == "YOGA":
            score += 0.02

    if isinstance(stress, (int, float)) and stress >= 0.7:
        if cat == "MEDITATION":
            score += 0.04
        if cat == "MUSIC":
            score += 0.02

    # 임신 주차/분기: 후기(3분기/28주~)에는 부드러운 카테고리 가점, 요가 소폭 감점
    tri = trimester if isinstance(trimester, int) else (1 if (isinstance(week, int) and week <= 13)
                                                       else 2 if (isinstance(week, int) and week <= 27)
                                                       else 3 if isinstance(week, int) else None)
    if tri == 3:
        if cat in ("BREATHING", "MEDITATION"):
            score += 0.03
        if cat == "YOGA":
            score -= 0.02

    return score

def _fetch_candidates(category: str, limit: int = 30) -> List[Content]:
    return list(
        Content.objects.filter(is_active=True, category__iexact=category)
        .order_by("-id")[:limit]
    )

def _thumb_for(sess: RecommendationSession, c: Content) -> Optional[str]:
    # 1) Content.thumbnail_url 우선
    thumb = getattr(c, "thumbnail_url", None)
    if thumb:
        return thumb
    # 2) 후보에 저장된 x_item_vec.thumb_url 시도
    ec = ExposureCandidate.objects.filter(session=sess, content=c).first()
    if ec and isinstance(ec.x_item_vec, dict):
        t = ec.x_item_vec.get("thumb_url")
        if t:
            return t
    return None


@transaction.atomic
def recommend_on_session(session_id: str, rec_in: RecInput) -> RecOutput:
    """
    새 세션 만들지 않고, 주어진 session_id의 후보(ExposureCandidate)를 기반으로 추천.
    후보가 없으면 Content 테이블에서 category로 보강.
    - 컨텍스트:
      * excluded_tags: 금기 태그(포함되면 제외)
      * preferences.duration_min/max: 길이 조건
      * preferences.music_provider: MUSIC 공급자 선호
    """
    try:
        sess = RecommendationSession.objects.select_for_update().get(id=session_id)
    except RecommendationSession.DoesNotExist:
        raise ValueError("INVALID_SESSION")

    ctx = rec_in.context or {}
    excluded = _as_list_lower(ctx.get("excluded_tags"))
    prefs = ctx.get("preferences") if isinstance(ctx.get("preferences"), dict) else None

    # 1) 세션에 쌓인 후보 우선
    ecs = list(
        ExposureCandidate.objects
        .filter(session=sess, content__category__iexact=rec_in.category)
        .select_related("content")
    )

    candidates: List[Content] = []
    if ecs:
        for ec in ecs:
            c = ec.content
            if not c or not getattr(c, "is_active", True):
                continue
            # 금기 태그 체크(컨텐츠 태그 + 후보 태그 합집합)
            tags = _tags_from_content(c) + _tags_from_exposure(ec)
            if _has_taboo(tags, excluded):
                continue
            # 선호 필터
            if not _passes_preferences(c, ec, prefs):
                continue
            candidates.append(c)
    else:
        # Content에서 보강 후 필터
        raw = _fetch_candidates(rec_in.category, limit=50)
        for c in raw:
            if not c or not getattr(c, "is_active", True):
                continue
            if _has_taboo(_tags_from_content(c), excluded):
                continue
            if not _passes_preferences(c, None, prefs):
                continue
            candidates.append(c)

    if not candidates:
        raise ValueError("No candidates for category")

    # 2) 스코어링(기존 TS + 컨텍스트)
    scored: List[Tuple[Content, float]] = []
    for c in candidates:
        ts_s = _ts_score(1.0, 1.0)
        s = _context_boost(ts_s, ctx, c)
        scored.append((c, s))
    scored.sort(key=lambda x: x[1], reverse=True)
    picked, best_score = scored[0]

    # 3) 선택 기록(ItemRec) (unique_together 보호)
    if not ItemRec.objects.filter(session=sess, content=picked).exists():
        ItemRec.objects.create(session=sess, content=picked, rank=1, score=best_score, reason="ts+context")

    # 4) 응답
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
        },
        reason="ts+context",
        candidates=[
            {"content_id": c.id, "title": c.title, "score": round(s, 4), "thumbnail": _thumb_for(sess, c)}
            for c, s in scored[:5]
        ],
    )
