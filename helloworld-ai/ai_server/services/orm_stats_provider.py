# services/orm_stats_provider.py
from __future__ import annotations
from datetime import date
from typing import Optional, Tuple

from api.models import UserTodStatsDaily  # Django ORM 모델

BUCKET_FIELDS = ("v_0_4", "v_4_8", "v_8_12", "v_12_16", "v_16_20", "v_20_24")

class OrmStatsProvider:
    """
    user_tod_stats_daily 에서 (mean, stddev) 기준선을 읽어오는 Provider.
    (user_ref, as_of, metric, bucket_idx) -> (mean, stddev) 또는 None
    """
    def get_bucket_stats(
        self, user_ref: str, as_of: date, metric: str, bucket_idx: int
    ) -> Optional[Tuple[float, float]]:
        if not (0 <= bucket_idx < 6):
            return None
        field = BUCKET_FIELDS[bucket_idx]

        mean_row = UserTodStatsDaily.objects.filter(
            user_ref=user_ref, as_of=as_of, metric=metric, stat="mean"
        ).only(field).first()

        std_row = UserTodStatsDaily.objects.filter(
            user_ref=user_ref, as_of=as_of, metric=metric, stat="stddev"
        ).only(field).first()

        if not mean_row or not std_row:
            return None

        mean = getattr(mean_row, field)
        std = getattr(std_row, field)
        return (mean, std)
