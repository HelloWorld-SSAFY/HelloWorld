# services/stats_ingest.py
from datetime import date
from typing import Dict, Any, Optional, List
from django.db import transaction
import re

from api.models import UserTodStatsDaily

# 메인서버 daily-buckets(4시간 피벗) → DB 저장에 사용
BUCKET_KEYS = ["v_0_4", "v_4_8", "v_8_12", "v_12_16", "v_16_20", "v_20_24"]

# stat/metric 정규화
STAT_MAP = {"avg": "mean", "mean": "mean", "stddev": "stddev"}
METRIC_ALLOW = {"hr", "stress"}
STAT_ALLOW = {"mean", "stddev"}


def _to_float_or_none(v) -> Optional[float]:
    if v is None:
        return None
    try:
        return float(v)
    except Exception:
        return None


_norm_user_ref_pat = re.compile(r"^[cC]?0*(\d+)$")
def _normalize_user_ref(v: Any) -> str:
    """
    'c10' → '10', 'C007' → '7', '12' → '12'
    숫자만 추출해 문자열로 반환. 형식 다르면 원문 유지.
    """
    s = str(v).strip()
    if not s:
        return ""
    m = _norm_user_ref_pat.match(s)
    if m:
        return m.group(1)
    return s


def _parse_rows(payload: Dict[str, Any]) -> List[UserTodStatsDaily]:
    """payload(stats 배열)를 모델 row 리스트로 파싱."""
    stats = payload.get("stats", [])
    rows: List[UserTodStatsDaily] = []

    for s in stats:
        # as_of
        try:
            as_of = s["as_of"]
            as_of_d = date.fromisoformat(str(as_of))
        except Exception:
            continue

        metric_raw = str(s.get("metric", "")).strip().lower()
        if metric_raw not in METRIC_ALLOW:
            continue

        stat_norm = STAT_MAP.get(str(s.get("stat", "")).strip().lower())
        if stat_norm not in STAT_ALLOW:
            continue

        user_ref = _normalize_user_ref(s.get("user_ref", ""))
        if not user_ref:
            continue

        kwargs = {
            "user_ref": user_ref,
            "as_of": as_of_d,
            "metric": metric_raw,
            "stat": stat_norm,
        }
        for k in BUCKET_KEYS:
            kwargs[k] = _to_float_or_none(s.get(k))

        rows.append(UserTodStatsDaily(**kwargs))

    return rows


def replace_all_daily_buckets(payload: Dict[str, Any]) -> int:
    """
    ⚠️ 전체 테이블을 비운 뒤(payload 기준으로) 다시 채움.
    return: 새로 insert한 row 수
    """
    rows = _parse_rows(payload)
    with transaction.atomic():
        UserTodStatsDaily.objects.all().delete()
        if rows:
            UserTodStatsDaily.objects.bulk_create(rows, batch_size=1000)
    return len(rows)


# 필요 시 계속 사용할 수 있도록 기존 업서트도 유지
def upsert_daily_buckets_payload(payload: Dict[str, Any]) -> int:
    """
    기존 행은 유지하면서 (user_ref, as_of, metric, stat) 기준으로 update_or_create.
    """
    rows = _parse_rows(payload)
    saved = 0
    with transaction.atomic():
        for r in rows:
            _, _created = UserTodStatsDaily.objects.update_or_create(
                user_ref=r.user_ref,
                as_of=r.as_of,
                metric=r.metric,
                stat=r.stat,
                defaults={k: getattr(r, k) for k in BUCKET_KEYS},
            )
            saved += 1
    return saved
