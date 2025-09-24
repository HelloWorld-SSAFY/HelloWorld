# services/orm_stats_provider.py
from __future__ import annotations
from datetime import date, timedelta
from typing import Optional, Tuple, Dict
import os, time, logging, re

from api.models import UserTodStatsDaily  # Django ORM 모델

log = logging.getLogger(__name__)

BUCKET_FIELDS = ("v_0_4", "v_4_8", "v_8_12", "v_12_16", "v_16_20", "v_20_24")

# ── 여기서 바로 정규화 함수 정의 (c10 -> "10") ─────────────────────
_RE_USER = re.compile(r"^[cC]?0*(\d+)$")
def normalize_user_ref(v) -> str:
    s = str(v).strip()
    m = _RE_USER.match(s)
    return m.group(1) if m else s
# ──────────────────────────────────────────────────────────────────

class OrmStatsProvider:
    """
    user_tod_stats_daily 에서 (mean, stddev) 기준선을 읽어오는 Provider.
    (user_ref, as_of, metric, bucket_idx) -> (mean, stddev) 또는 None

    보강 사항:
      - user_ref 정규화: 'c10' -> '10'
      - stat alias: 'mean' 없으면 'avg' 도 허용 (기본 on)
      - 이웃 버킷 fallback: 같은 날짜에서 좌/우 가까운 버킷 탐색 (기본 on)
      - 최신 날짜 fallback: as_of 이하 가장 최근 날짜로 탐색 (기본 on)
      - stddev > 0 보장: sd<=0 이면 무효
      - 경량 캐시
    """

    def __init__(
        self,
        *,
        allow_avg_alias: bool | None = None,
        fallback_neighbor: bool | None = None,
        neighbor_max_dist: int | None = None,
        fallback_latest: bool | None = None,
        cache_ttl_sec: int | None = None,
    ):
        self.allow_avg_alias = (
            allow_avg_alias
            if allow_avg_alias is not None
            else os.getenv("ORMSP_ALLOW_AVG_ALIAS", "true").lower() in ("1","true","yes")
        )
        self.fallback_neighbor = (
            fallback_neighbor
            if fallback_neighbor is not None
            else os.getenv("ORMSP_FALLBACK_NEIGHBOR", "true").lower() in ("1","true","yes")
        )
        try:
            self.neighbor_max_dist = (
                neighbor_max_dist if neighbor_max_dist is not None else int(os.getenv("ORMSP_NEIGHBOR_DIST","2"))
            )
        except Exception:
            self.neighbor_max_dist = 2

        self.fallback_latest = (
            fallback_latest
            if fallback_latest is not None
            else os.getenv("ORMSP_FALLBACK_LATEST", "true").lower() in ("1","true","yes")
        )
        try:
            self.cache_ttl_sec = cache_ttl_sec if cache_ttl_sec is not None else int(os.getenv("ORMSP_CACHE_TTL_SEC","30"))
        except Exception:
            self.cache_ttl_sec = 30

        self.debug = os.getenv("ORMSP_DEBUG","false").lower() in ("1","true","yes")
        self._cache: Dict[tuple, tuple[float, Optional[Tuple[float,float]]]] = {}

    # ── 캐시 ─────────────────────────────────────────────────────────
    def _cache_get(self, key: tuple) -> Optional[Tuple[float,float]]:
        if self.cache_ttl_sec <= 0: return None
        item = self._cache.get(key)
        if not item: return None
        expiry, val = item
        if time.time() > expiry:
            self._cache.pop(key, None)
            return None
        return val

    def _cache_set(self, key: tuple, val: Optional[Tuple[float,float]]):
        if self.cache_ttl_sec <= 0: return
        self._cache[key] = (time.time() + self.cache_ttl_sec, val)

    # ── 단일 날짜 조회 ──────────────────────────────────────────────
    def _fetch_for_date(self, *, user_ref: str, d: date, metric: str, field: str) -> Optional[Tuple[float,float]]:
        mean_row = (UserTodStatsDaily.objects
                    .filter(user_ref=user_ref, as_of=d, metric=metric, stat="mean")
                    .only(field).first())
        std_row  = (UserTodStatsDaily.objects
                    .filter(user_ref=user_ref, as_of=d, metric=metric, stat="stddev")
                    .only(field).first())
        if (not mean_row) and self.allow_avg_alias:
            mean_row = (UserTodStatsDaily.objects
                        .filter(user_ref=user_ref, as_of=d, metric=metric, stat="avg")
                        .only(field).first())
        if not mean_row or not std_row:
            return None

        mean = getattr(mean_row, field)
        std  = getattr(std_row, field)
        if mean is None or std is None:
            return None
        try:
            mean = float(mean); std = float(std)
        except Exception:
            return None
        if std <= 0.0:
            return None
        return (mean, std)

    # ── 최신 날짜 fallback ──────────────────────────────────────────
    def _fallback_latest_for_field(self, *, user_ref: str, as_of: date, metric: str, field: str) -> Optional[Tuple[float,float]]:
        if not self.fallback_latest: return None
        try:
            lookback_days = int(os.getenv("ORMSP_FALLBACK_LOOKBACK_DAYS","7"))
        except Exception:
            lookback_days = 7
        start_date = as_of - timedelta(days=max(0, lookback_days))

        dates = (UserTodStatsDaily.objects
                 .filter(user_ref=user_ref, metric=metric, stat__in=["mean","avg"],
                         as_of__gte=start_date, as_of__lte=as_of)
                 .exclude(**{f"{field}__isnull": True})
                 .values_list("as_of", flat=True)
                 .distinct().order_by("-as_of")[:lookback_days+1])
        for d in dates:
            pair = self._fetch_for_date(user_ref=user_ref, d=d, metric=metric, field=field)
            if pair is not None:
                if self.debug:
                    log.info("[OrmStatsProvider] fallback(latest) date=%s user=%s metric=%s field=%s",
                             d.isoformat(), user_ref, metric, field)
                return pair
        return None

    # ── 공개 API ────────────────────────────────────────────────────
    def get_bucket_stats(self, user_ref: str, as_of: date, metric: str, bucket_idx: int) -> Optional[Tuple[float,float]]:
        if not (0 <= bucket_idx < len(BUCKET_FIELDS)): return None

        ref = normalize_user_ref(user_ref)     # ← 정규화
        cache_key = (ref, as_of, metric, bucket_idx)
        cached = self._cache_get(cache_key)
        if cached is not None: return cached

        field = BUCKET_FIELDS[bucket_idx]

        # 1) as_of + 해당 버킷
        pair = self._fetch_for_date(user_ref=ref, d=as_of, metric=metric, field=field)

        # 2) as_of + 이웃 버킷
        if pair is None and self.fallback_neighbor and self.neighbor_max_dist > 0:
            for dist in range(1, self.neighbor_max_dist + 1):
                for i in (bucket_idx - dist, bucket_idx + dist):
                    if 0 <= i < len(BUCKET_FIELDS):
                        f = BUCKET_FIELDS[i]
                        pair = self._fetch_for_date(user_ref=ref, d=as_of, metric=metric, field=f)
                        if pair is not None:
                            if self.debug:
                                log.info("[OrmStatsProvider] fallback(neighbor same-day) %s -> %s user=%s metric=%s date=%s",
                                         field, f, ref, metric, as_of.isoformat())
                            break
                if pair is not None: break

        # 3) 최신 날짜(<=as_of) 같은 버킷
        if pair is None:
            pair = self._fallback_latest_for_field(user_ref=ref, as_of=as_of, metric=metric, field=field)

        # 4) 최신 날짜 + 이웃 버킷
        if pair is None and self.fallback_neighbor and self.neighbor_max_dist > 0:
            for dist in range(1, self.neighbor_max_dist + 1):
                for i in (bucket_idx - dist, bucket_idx + dist):
                    if 0 <= i < len(BUCKET_FIELDS):
                        f = BUCKET_FIELDS[i]
                        pair = self._fallback_latest_for_field(user_ref=ref, as_of=as_of, metric=metric, field=f)
                        if pair is not None:
                            if self.debug:
                                log.info("[OrmStatsProvider] fallback(neighbor latest) %s -> %s user=%s metric=%s date<=%s",
                                         field, f, ref, metric, as_of.isoformat())
                            break
                if pair is not None: break

        self._cache_set(cache_key, pair)
        return pair
