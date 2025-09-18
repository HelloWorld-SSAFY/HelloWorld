# services/steps_check.py
from __future__ import annotations
from dataclasses import dataclass
from datetime import datetime, timezone, timedelta
from typing import List
from django.db import transaction

from services.anomaly import KST, bucket_index_4h  # 이미 있는 유틸 재사용
from api.models import (
    RecommendationSession,
    TriggerCategoryPolicy,
    UserStepsTodStatsDaily,
)

EPS = 1e-6

@dataclass
class StepsDecision:
    anomaly: bool
    reasons: List[str]
    session_id: str | None
    categories: List[dict]

def to_kst(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(KST)

def decide_steps_low(user_ref: str, ts: datetime, cum_steps: int) -> StepsDecision:
    kst = to_kst(ts)
    b = bucket_index_4h(kst)  # 0..5

    # 기준선: 오늘 → 없으면 최근일
    qs = UserStepsTodStatsDaily.objects.filter(user_ref=user_ref, bucket=b)
    base = qs.filter(d=kst.date()).first() or qs.order_by("-d").first()
    if not base:
        # 기준선 없으면 정상 취급
        return StepsDecision(False, ["no_baseline"], None, [])

    z = (cum_steps - base.cum_mu) / max(base.cum_sigma, EPS)
    is_low = (z <= -1.0) or (cum_steps < base.p20)

    if not is_low:
        return StepsDecision(False, [], None, [])

    # 같은 일자/버킷 steps_low 세션 재사용 (context에 저장)
    with transaction.atomic():
        sess = (RecommendationSession.objects
                .filter(user_ref=user_ref, mode="restrict", trigger="steps_low")
                .filter(context__kst_date=str(kst.date()), context__bucket=b)
                .order_by("-created_at")
                .first())
        if not sess:
            sess = RecommendationSession.objects.create(
                user_ref=user_ref,
                trigger="steps_low",
                mode="restrict",
                context={"kst_date": str(kst.date()), "bucket": b, "reason": "steps_low"}
            )

    # 정책 조회 (priority 오름차순)
    policies = (TriggerCategoryPolicy.objects
                .filter(trigger="steps_low", is_active=True)
                .order_by("priority"))
    cats = []
    for p in policies:
        cats.append({
            "category": p.category,
            "rank": p.priority,
            "reason": "steps low vs baseline"
        })

    return StepsDecision(
        True,
        [f"cum_steps_z<={z:.2f} (bucket={b})", f"cum_steps={cum_steps}, p20={base.p20}"],
        str(sess.id),
        cats
    )
