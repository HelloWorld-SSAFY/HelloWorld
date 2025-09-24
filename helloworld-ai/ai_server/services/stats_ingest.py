# services/stats_ingest.py
from datetime import date
from typing import Dict, Any, Optional
from django.db import transaction
import re

from api.models import UserTodStatsDaily

# 메인서버 daily-buckets(4시간 피벗) → DB 업서트에 사용
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
    숫자만 추출해 문자열로 반환. 형식이 다르면 원문 유지.
    """
    s = str(v).strip()
    if not s:
        return ""
    m = _norm_user_ref_pat.match(s)
    if m:
        return m.group(1)  # 순수 숫자만
    return s  # 예외 케이스는 원문 유지


def upsert_daily_buckets_payload(payload: Dict[str, Any]) -> int:
    """
    메인서버 daily-buckets 응답(JSON)을 user_tod_stats_daily 로 upsert.
    return: 저장/갱신한 row 수
    기대 payload 예시:
    {
      "stats": [
        {
          "user_ref": "c4",
          "as_of": "2025-09-21",
          "metric": "hr" | "stress",
          "stat": "avg" | "mean" | "stddev",
          "v_0_4": 1.2, "v_4_8": 3.4, ...
        }, ...
      ]
    }
    """
    stats = payload.get("stats", [])
    saved = 0

    with transaction.atomic():
        for s in stats:
            # --- 기본 키 파싱/정규화 ---
            try:
                as_of = s["as_of"]
            except KeyError:
                continue

            # "YYYY-MM-DD" → date
            try:
                as_of_d = date.fromisoformat(str(as_of))
            except Exception:
                # 형식 이상하면 스킵
                continue

            metric_raw = str(s.get("metric", "")).strip().lower()
            if metric_raw not in METRIC_ALLOW:
                continue  # 허용 외 metric은 무시

            stat_norm = STAT_MAP.get(str(s.get("stat", "")).strip().lower())
            if stat_norm not in STAT_ALLOW:
                continue  # 허용 외 stat은 무시

            user_ref_raw = s.get("user_ref", "")
            user_ref = _normalize_user_ref(user_ref_raw)
            if not user_ref:
                continue

            # --- 버킷 값 안전 변환 ---
            defaults = {k: _to_float_or_none(s.get(k)) for k in BUCKET_KEYS}

            # --- upsert ---
            UserTodStatsDaily.objects.update_or_create(
                user_ref=user_ref,
                as_of=as_of_d,
                metric=metric_raw,
                stat=stat_norm,
                defaults=defaults,
            )
            saved += 1

    return saved
