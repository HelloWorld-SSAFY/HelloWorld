# services/steps_check.py
from __future__ import annotations
from dataclasses import dataclass
from datetime import datetime, timezone, timedelta
from typing import List, Optional
from django.db import transaction

from services.anomaly import KST, bucket_index_4h  # 이미 있는 유틸 재사용
from services.policy_service import categories_for_trigger  # DB 우선 + 폴백
from api.models import (
    RecommendationSession,
    UserStepsTodStatsDaily,
)

EPS = 1e-6

@dataclass
class StepsDecision:
    """
    steps-check 판단 전용 결과 객체.
    - anomaly: 저활동 트리거 여부
    - reasons: 판단 근거 메시지 배열
    - session_id: (restrict일 때) 세션 UUID 문자열, 없으면 None
    - categories: 모바일 힌트용 카테고리 배열 [{category, rank, reason}]
    """
    anomaly: bool
    reasons: List[str]
    session_id: Optional[str]
    categories: List[dict]

def to_kst(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(KST)

def _ensure_steps_session(user_ref: str, kst: datetime, bucket: int) -> RecommendationSession:
    """
    같은 날짜/버킷의 steps_low 세션을 재사용. 없으면 생성.
    """
    with transaction.atomic():
        sess = (RecommendationSession.objects
                .filter(user_ref=user_ref, mode="restrict", trigger="steps_low")
                .filter(context__kst_date=str(kst.date()), context__bucket=bucket)
                .order_by("-created_at")
                .first())
        if sess:
            return sess
        return RecommendationSession.objects.create(
            user_ref=user_ref,
            trigger="steps_low",
            mode="restrict",
            context={"kst_date": str(kst.date()), "bucket": bucket, "reason": "steps_low"},
        )

def _build_categories_for_steps_low() -> List[dict]:
    """
    정책 테이블(services.policy_service)을 통해 steps_low 대응 카테고리 정렬.
    폴백까지 고려하여 [{category, rank, reason}] 형태로 반환.
    """
    policies = categories_for_trigger("steps_low") or []
    out: List[dict] = []
    # policies: [{"code": "OUTING", "priority": 1, ...}, ...] 형태 가정
    # 모바일 힌트 스키마에 맞춰 변환
    for i, p in enumerate(sorted(policies, key=lambda x: x.get("priority", 999)), start=1):
        code = p.get("code") or p.get("category") or ""
        if not code:
            continue
        out.append({"category": code, "rank": i, "reason": "steps low vs baseline"})
    # 폴백(정책이 비어있다면 OUTING 한 개라도 제공)
    if not out:
        out = [{"category": "OUTING", "rank": 1, "reason": "steps low vs baseline"}]
    return out

def decide_steps_low(user_ref: str, ts: datetime, cum_steps: int) -> StepsDecision:
    """
    입력된 누적 걸음수(cum_steps)가 '동시간대 p20 미만' 혹은 'Z <= -1.0'이면 저활동으로 판정.
    - 판정만 수행하며, 장소 추천 실행/저장은 상위(뷰) 레이어에서 처리.
    """
    kst = to_kst(ts)
    b = bucket_index_4h(kst)  # 0..5

    # 기준선: 오늘 우선, 없으면 최근일 한 건
    qs = UserStepsTodStatsDaily.objects.filter(user_ref=user_ref, bucket=b)
    base = qs.filter(d=kst.date()).first() or qs.order_by("-d").first()
    if not base:
        # 기준선 없으면 정상 취급
        return StepsDecision(False, ["no_baseline"], None, [])

    # Z와 p20 비교
    z = (cum_steps - float(base.cum_mu or 0.0)) / max(float(base.cum_sigma or 0.0), EPS)
    is_low = (z <= -1.0) or (cum_steps < (base.p20 or 0))

    if not is_low:
        return StepsDecision(False, [], None, [])

    # 세션 확보(재사용 또는 생성)
    sess = _ensure_steps_session(user_ref=user_ref, kst=kst, bucket=b)

    # 카테고리 힌트(정책)
    cats = _build_categories_for_steps_low()

    reasons = [
        f"cum_steps_z<={z:.2f} (bucket={b})",
        f"cum_steps={cum_steps}, p20={base.p20}",
    ]

    return StepsDecision(
        anomaly=True,
        reasons=reasons,
        session_id=str(sess.id),
        categories=cats,
    )
