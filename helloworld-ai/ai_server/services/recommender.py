# services/recommender.py
from __future__ import annotations
import math, random, uuid
from dataclasses import dataclass
from typing import List, Optional, Dict, Any, Tuple
from datetime import datetime, timezone, timedelta

from django.db import transaction
from django.utils import timezone as dj_tz

from api.models import (
    Content,
    RecommendationSession,
    ExposureCandidate,
    ItemRec,
    TriggerCategoryPolicy,
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
# Thompson Sampling (초간단 버전)
#  - 베타 분포 파라미터를 히스토리에서 추정하면 좋지만,
#    초기엔 노출/클릭 로그가 거의 없으므로 +1, +1 스무딩으로 샘플
#  - 향후: Feedback CLICK/COMPLETE/EFFECT를 반영해 content별 a,b 업데이트
# ─────────────────────────────────────────────────────────────
def _ts_score(prior_a: float = 1.0, prior_b: float = 1.0) -> float:
    # 베타에서 샘플 1회
    # random.betavariate(a,b)
    return random.betavariate(prior_a, prior_b)

def _context_boost(base: float, ctx: Optional[Dict[str, Any]], c: Content) -> float:
    """
    매우 얕은 컨텍스트 가중치(초간단):
    - 밤 시간대(22~06): BREATHING/MEDITATION +0.05
    - 심박수 높음(hr>=110): BREATHING +0.03, YOGA +0.02
    - 스트레스 높음(stress>=0.7): MEDITATION +0.04, MUSIC +0.02
    필요 시 강화 가능.
    """
    if not ctx:
        return base

    try:
        ts = ctx.get("ts")  # ISO8601 or None
        hour = None
        if ts:
            # ts는 앱에서 보낸 KST ISO8601 권장
            hour = int(ts[11:13])
        else:
            hour = dj_tz.now().astimezone(KST).hour
    except Exception:
        hour = dj_tz.now().astimezone(KST).hour

    hr = ctx.get("hr")
    stress = ctx.get("stress")

    cat = (c.category or "").upper()
    score = base

    if hour is not None and (hour >= 22 or hour <= 6):
        if cat in ("BREATHING", "MEDITATION"):
            score += 0.05

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

    return score

def _fetch_candidates(category: str, limit: int = 10) -> List[Content]:
    qs = Content.objects.filter(is_active=True, category__iexact=category).order_by("-priority", "-id")
    return list(qs[:limit])

@transaction.atomic
def recommend(rec_in: RecInput) -> RecOutput:
    # 1) 세션 생성
    session = RecommendationSession.objects.create(
        session_id=str(uuid.uuid4()),
        user_ref=rec_in.user_ref,
        category=rec_in.category.upper(),
    )

    # 2) 후보 가져오기 (없으면 정책기반 대체 or 실패)
    candidates = _fetch_candidates(rec_in.category, limit=12)
    if not candidates:
        # 정책 테이블에서 대체 카테고리 1~2순위 찾아서 재시도
        fallbacks = list(
            TriggerCategoryPolicy.objects.filter(
                trigger_key__in=[rec_in.category.lower(), rec_in.category.upper()],
                enabled=True
            ).order_by("rank")[:2]
        )
        for fb in fallbacks:
            candidates = _fetch_candidates(fb.category, limit=12)
            if candidates:
                rec_in = RecInput(user_ref=rec_in.user_ref, category=fb.category, context=rec_in.context)
                break

    if not candidates:
        # 여전히 없으면 204 유사 상황 → 예외로 올려서 뷰에서 처리
        raise ValueError("No candidates for category")

    # 3) 후보 노출 기록(ExposureCandidate)
    for c in candidates:
        ExposureCandidate.objects.create(
            session=session,
            content=c,
            user_ref=rec_in.user_ref,
            reason="candidate",
        )

    # 4) TS + 컨텍스트 보정 점수로 1개 선택
    scored: List[Tuple[Content, float, Dict[str, float]]] = []
    for c in candidates:
        # 초기 파라미터(아직 학습 전): a=b=1
        ts_s = _ts_score(1.0, 1.0)
        s = _context_boost(ts_s, rec_in.context, c)
        scored.append((c, s, {"ts": ts_s}))

    scored.sort(key=lambda x: x[1], reverse=True)
    picked, best_score, extras = scored[0]

    # 5) 선택 기록(ItemRec) + 외부 트래킹 ID 생성
    ext_id = f"sp:trk:{session.session_id}:{picked.id}"
    item_rec = ItemRec.objects.create(
        session=session,
        content=picked,
        user_ref=rec_in.user_ref,
        external_id=ext_id,
        reason="ts+context",
        score=best_score,
    )

    # 6) 응답 구성
    out = RecOutput(
        session_id=session.session_id,
        category=rec_in.category.upper(),
        picked={
            "content_id": picked.id,
            "title": picked.title,
            "category": picked.category,
            "provider": picked.provider,     # e.g., "YOUTUBE" | "SPOTIFY" | "INAPP"
            "url": picked.url,
            "thumbnail": picked.thumbnail if hasattr(picked, "thumbnail") else None,
            "external_id": ext_id,
        },
        reason=f"ts={extras['ts']:.3f}, ctx_applied=True",
        candidates=[
            {
                "content_id": c.id,
                "title": c.title,
                "score": round(s, 4),
            }
            for c, s, _ in scored[:5]  # 상위 5개만 디버그로 반환
        ],
    )
    return out
