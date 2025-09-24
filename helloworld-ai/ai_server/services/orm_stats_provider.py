# services/orm_stats_provider.py
from __future__ import annotations
from datetime import date, datetime, timedelta
from typing import Optional, Tuple, Dict, Any
import os, time, logging

from api.models import UserTodStatsDaily  # Django ORM 모델

log = logging.getLogger(__name__)

BUCKET_FIELDS = ("v_0_4", "v_4_8", "v_8_12", "v_12_16", "v_16_20", "v_20_24")

class OrmStatsProvider:
    """
    user_tod_stats_daily 에서 (mean, stddev) 기준선을 읽어오는 Provider.
    (user_ref, as_of, metric, bucket_idx) -> (mean, stddev) 또는 None

    보강 사항:
      - stat alias: 'mean' 없으면 'avg' 도 허용 (기본 on)
      - 날짜 폴백: as_of에 없으면 as_of 이하 가장 최근 스냅샷의 같은 버킷 사용 (기본 on)
      - 30초 경량 캐시 (기본 on)
    """

    def __init__(
        self,
        *,
        allow_avg_alias: bool | None = None,
        fallback_latest: bool | None = None,
        cache_ttl_sec: int | None = None,
    ):
        # ENV로도 제어 가능
        self.allow_avg_alias = (
            allow_avg_alias if allow_avg_alias is not None
            else os.getenv("ORMSP_ALLOW_AVG_ALIAS", "true").lower() in ("1", "true", "yes")
        )
        self.fallback_latest = (
            fallback_latest if fallback_latest is not None
            else os.getenv("ORMSP_FALLBACK_LATEST", "true").lower() in ("1", "true", "yes")
        )
        self.cache_ttl_sec = (
            cache_ttl_sec if cache_ttl_sec is not None
            else int(os.getenv("ORMSP_CACHE_TTL_SEC", "30"))
        )
        self.debug = os.getenv("ORMSP_DEBUG", "false").lower() in ("1", "true", "yes")

        self._cache: Dict[tuple, tuple[float, Optional[Tuple[float, float]]]] = {}

    # ---- 내부 유틸 ---------------------------------------------------------

    def _cache_get(self, key: tuple) -> Optional[Tuple[float, float]]:
        if self.cache_ttl_sec <= 0:
            return None
        item = self._cache.get(key)
        if not item:
            return None
        expiry, val = item
        if time.time() > expiry:
            self._cache.pop(key, None)
            return None
        return val

    def _cache_set(self, key: tuple, val: Optional[Tuple[float, float]]):
        if self.cache_ttl_sec <= 0:
            return
        self._cache[key] = (time.time() + self.cache_ttl_sec, val)

    def _fetch_for_date(
        self, *, user_ref: str, d: date, metric: str, field: str
    ) -> Optional[Tuple[float, float]]:
        """
        해당 날짜 d에서 field(버킷) 기준 mean/stddev을 읽어옴.
        'mean'이 없고 allow_avg_alias=True면 'avg' 대체 허용.
        """
        # 1) mean
        mean_row = UserTodStatsDaily.objects.filter(
            user_ref=user_ref, as_of=d, metric=metric, stat="mean"
        ).only(field).first()

        # 2) stddev
        std_row = UserTodStatsDaily.objects.filter(
            user_ref=user_ref, as_of=d, metric=metric, stat="stddev"
        ).only(field).first()

        # 3) mean이 없고 alias 허용이면 avg 시도
        if (not mean_row) and self.allow_avg_alias:
            mean_row = UserTodStatsDaily.objects.filter(
                user_ref=user_ref, as_of=d, metric=metric, stat="avg"
            ).only(field).first()

        if not mean_row or not std_row:
            return None

        mean = getattr(mean_row, field)
        std = getattr(std_row, field)
        # NULL 방지: 둘 중 하나라도 None이면 사용 불가
        if mean is None or std is None:
            return None

        # float 변환 (Decimal 대비)
        try:
            mean = float(mean)
        except Exception:
            return None
        try:
            std = float(std)
        except Exception:
            return None

        return (mean, std)

    def _fallback_latest_for_field(
        self, *, user_ref: str, as_of: date, metric: str, field: str
    ) -> Optional[Tuple[float, float]]:
        """
        as_of 이하에서 field 값이 NULL이 아닌 가장 최근 날짜를 찾아서 (mean,stddev) 반환.
        탐색 폭은 최근 7일 내로 제한(필요시 ENV로 조정 가능).
        """
        if not self.fallback_latest:
            return None

        try:
            lookback_days = int(os.getenv("ORMSP_FALLBACK_LOOKBACK_DAYS", "7"))
        except Exception:
            lookback_days = 7
        start_date = as_of - timedelta(days=max(0, lookback_days))

        # 우선 mean이 존재하고 해당 field가 not null인 날짜들을 최신순으로 가져와 본다.
        dates = (UserTodStatsDaily.objects
                 .filter(user_ref=user_ref, metric=metric, stat__in=["mean", "avg"], as_of__gte=start_date, as_of__lte=as_of)
                 .exclude(**{f"{field}__isnull": True})
                 .values_list("as_of", flat=True)
                 .distinct()
                 .order_by("-as_of")[:lookback_days+1])

        for d in dates:
            pair = self._fetch_for_date(user_ref=user_ref, d=d, metric=metric, field=field)
            if pair is not None:
                if self.debug:
                    log.info("[OrmStatsProvider] fallback to latest date=%s user=%s metric=%s field=%s",
                             d.isoformat(), user_ref, metric, field)
                return pair
        return None

    # ---- 공개 API -----------------------------------------------------------

    def get_bucket_stats(
        self, user_ref: str, as_of: date, metric: str, bucket_idx: int
    ) -> Optional[Tuple[float, float]]:
        if not (0 <= bucket_idx < 6):
            return None
        field = BUCKET_FIELDS[bucket_idx]
        key = (user_ref, as_of, metric, bucket_idx)

        # 캐시
        cached = self._cache_get(key)
        if cached is not None:
            return cached

        # 1) 오늘(as_of) 먼저 시도
        pair = self._fetch_for_date(user_ref=user_ref, d=as_of, metric=metric, field=field)

        # 2) 실패 시 최신 폴백(옵션)
        if pair is None:
            pair = self._fallback_latest_for_field(user_ref=user_ref, as_of=as_of, metric=metric, field=field)

        # 캐시 저장
        self._cache_set(key, pair)
        return pair
