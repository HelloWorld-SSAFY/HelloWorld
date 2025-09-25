# services/orm_stats_provider.py
from __future__ import annotations
from typing import Optional, Tuple, Dict
import os, time, logging, re
from datetime import date, timedelta

from api.models import UserTodStatsDaily

log = logging.getLogger(__name__)

BUCKET_FIELDS = ("v_0_4", "v_4_8", "v_8_12", "v_12_16", "v_16_20", "v_20_24")

# user_ref 정규화: c10 -> "10"
_RE_USER = re.compile(r"^[cC]?0*(\d+)$")
def normalize_user_ref(v) -> str:
    s = str(v).strip()
    m = _RE_USER.match(s)
    return m.group(1) if m else s


class OrmStatsProvider:
    """
    (user_ref, metric, bucket_idx) -> (mean, stddev)
    - as_of(날짜)는 **무시**하고, 항상 '가장 최근(as_of 최대값)' 자료에서 찾음
    - 못 찾으면 같은 날짜의 **이웃 버킷(±1, ±2 …)** 으로 폴백
    - 그래도 없으면 그보다 예전 날짜들로 동일 로직 반복
    - stddev<=0 이면 무효
    - 경량 캐시 지원
    """

    def __init__(
        self,
        *,
        allow_avg_alias: bool | None = None,
        fallback_neighbor: bool | None = None,
        neighbor_max_dist: int | None = None,
        lookback_days: int | None = None,
        cache_ttl_sec: int | None = None,
    ):
        self.allow_avg_alias = (
            allow_avg_alias
            if allow_avg_alias is not None
            else os.getenv("ORMSP_ALLOW_AVG_ALIAS", "true").lower() in ("1", "true", "yes")
        )
        self.fallback_neighbor = (
            fallback_neighbor
            if fallback_neighbor is not None
            else os.getenv("ORMSP_FALLBACK_NEIGHBOR", "true").lower() in ("1", "true", "yes")
        )
        try:
            self.neighbor_max_dist = (
                neighbor_max_dist if neighbor_max_dist is not None else int(os.getenv("ORMSP_NEIGHBOR_DIST", "2"))
            )
        except Exception:
            self.neighbor_max_dist = 2

        try:
            self.lookback_days = (
                lookback_days if lookback_days is not None else int(os.getenv("ORMSP_LOOKBACK_DAYS", "30"))
            )
        except Exception:
            self.lookback_days = 30

        try:
            self.cache_ttl_sec = (
                cache_ttl_sec if cache_ttl_sec is not None else int(os.getenv("ORMSP_CACHE_TTL_SEC", "30"))
            )
        except Exception:
            self.cache_ttl_sec = 30

        self.debug = os.getenv("ORMSP_DEBUG", "false").lower() in ("1", "true", "yes")
        # cache: key -> (expiry_ts, (mu, sd) | None)
        self._cache: Dict[tuple, tuple[float, Optional[Tuple[float, float]]]] = {}

    # ───────────────────── 캐시 ─────────────────────
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

    # ─────────────── 단일 날짜에서 버킷 값 읽기 ───────────────
    def _fetch_for_date(self, *, ref: str, d: date, metric: str, field: str) -> Optional[Tuple[float, float]]:
        mean_row = (
            UserTodStatsDaily.objects.filter(user_ref=ref, as_of=d, metric=metric, stat="mean")
            .only(field)
            .first()
        )
        std_row = (
            UserTodStatsDaily.objects.filter(user_ref=ref, as_of=d, metric=metric, stat="stddev")
            .only(field)
            .first()
        )
        if (not mean_row) and self.allow_avg_alias:
            mean_row = (
                UserTodStatsDaily.objects.filter(user_ref=ref, as_of=d, metric=metric, stat="avg")
                .only(field)
                .first()
            )
        if not mean_row or not std_row:
            return None

        mean = getattr(mean_row, field)
        std = getattr(std_row, field)
        if mean is None or std is None:
            return None
        try:
            mean = float(mean)
            std = float(std)
        except Exception:
            return None
        if std <= 0.0:
            return None
        return (mean, std)

    # ─────────────── 날짜 리스트(최신→과거) 뽑기 ───────────────
    def _candidate_dates(self, *, ref: str, metric: str, field: str) -> list[date]:
        """
        field 값이 NULL이 아닌 날짜들을 최신순으로 반환.
        lookback_days 로 상한 제한.
        """
        since = date.today() - timedelta(days=max(0, self.lookback_days))
        qs = (
            UserTodStatsDaily.objects.filter(user_ref=ref, metric=metric, stat__in=["mean", "avg"], as_of__gte=since)
            .exclude(**{f"{field}__isnull": True})
            .values_list("as_of", flat=True)
            .distinct()
            .order_by("-as_of")
        )
        return list(qs[: self.lookback_days + 1])

    # ───────────────────── 공개 API ─────────────────────
    def get_bucket_stats(
        self, user_ref: str, as_of: date, metric: str, bucket_idx: int
    ) -> Optional[Tuple[float, float]]:
        """
        as_of는 **무시**. 항상 '가장 최근' 기준으로 버킷 매칭.
        우선순위:
          A) 최신 날짜의 해당 버킷 → B) 최신 날짜의 이웃 버킷 → 
          C) 더 과거 날짜의 해당 버킷 → D) 과거 날짜의 이웃 버킷
        """
        if not (0 <= bucket_idx < len(BUCKET_FIELDS)):
            return None

        ref = normalize_user_ref(user_ref)
        field = BUCKET_FIELDS[bucket_idx]

        # 캐시 키에 'latest' 표시
        cache_key = (ref, "latest", metric, bucket_idx)
        cached = self._cache_get(cache_key)
        if cached is not None:
            return cached

        dates = self._candidate_dates(ref=ref, metric=metric, field=field)
        # A) 최신 날짜의 해당 버킷
        for d in dates[:1]:
            pair = self._fetch_for_date(ref=ref, d=d, metric=metric, field=field)
            if pair is not None:
                if self.debug:
                    log.info("[OrmStatsProvider] hit latest %s user=%s metric=%s date=%s", field, ref, metric, d)
                self._cache_set(cache_key, pair)
                return pair

        # B) 최신 날짜의 이웃 버킷
        if self.fallback_neighbor and self.neighbor_max_dist > 0 and dates:
            d0 = dates[0]
            for dist in range(1, self.neighbor_max_dist + 1):
                for i in (bucket_idx - dist, bucket_idx + dist):
                    if 0 <= i < len(BUCKET_FIELDS):
                        f = BUCKET_FIELDS[i]
                        pair = self._fetch_for_date(ref=ref, d=d0, metric=metric, field=f)
                        if pair is not None:
                            if self.debug:
                                log.info(
                                    "[OrmStatsProvider] neighbor latest %s->%s user=%s metric=%s date=%s",
                                    field, f, ref, metric, d0
                                )
                            self._cache_set(cache_key, pair)
                            return pair

        # C, D) 더 과거 날짜들 반복
        for d in dates[1:]:
            # 같은 버킷
            pair = self._fetch_for_date(ref=ref, d=d, metric=metric, field=field)
            if pair is not None:
                if self.debug:
                    log.info("[OrmStatsProvider] fallback older %s user=%s metric=%s date=%s", field, ref, metric, d)
                self._cache_set(cache_key, pair)
                return pair

            # 이웃 버킷
            if self.fallback_neighbor and self.neighbor_max_dist > 0:
                for dist in range(1, self.neighbor_max_dist + 1):
                    for i in (bucket_idx - dist, bucket_idx + dist):
                        if 0 <= i < len(BUCKET_FIELDS):
                            f = BUCKET_FIELDS[i]
                            pair = self._fetch_for_date(ref=ref, d=d, metric=metric, field=f)
                            if pair is not None:
                                if self.debug:
                                    log.info(
                                        "[OrmStatsProvider] neighbor older %s->%s user=%s metric=%s date=%s",
                                        field, f, ref, metric, d
                                    )
                                self._cache_set(cache_key, pair)
                                return pair

        # 못 찾음
        self._cache_set(cache_key, None)
        return None
