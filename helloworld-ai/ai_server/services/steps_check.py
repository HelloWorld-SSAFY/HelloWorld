# services/steps_check.py
from __future__ import annotations
import os, logging, requests
from typing import Any, Dict, Tuple
from datetime import timedelta
from django.utils import timezone

log = logging.getLogger(__name__)

# ── ENV ──────────────────────────────────────────────────────────────────────
MAIN_API_BASE    = os.getenv("MAIN_API_BASE", "").rstrip("/")
MAIN_API_TOKEN   = os.getenv("MAIN_API_TOKEN")            # ex) Bearer 토큰
MAIN_API_TIMEOUT = float(os.getenv("MAIN_API_TIMEOUT", "2.0"))
DIFF_TH          = int(os.getenv("STEPS_DIFF_THRESHOLD", "500"))  # ← 500 고정 규칙

KST = timezone.get_fixed_timezone(9 * 60)

def _main_headers() -> Dict[str, str]:
    h = {"Accept": "application/json"}
    if MAIN_API_TOKEN:
        h["Authorization"] = f"Bearer {MAIN_API_TOKEN}"  # 필요 시 헤더명 교체
    return h

# ── 버킷 계산: 12/16/20시 ─────────────────────────────────────────────────────
def pick_bucket_hms(ts_kst) -> str:
    h = ts_kst.hour
    if 12 <= h < 16: return "12:00:00"
    if 16 <= h < 20: return "16:00:00"
    return "20:00:00"

# ── 메인서버 기준선 조회 ─────────────────────────────────────────────────────
def _extract_baseline_from_payload(payload: Dict[str, Any], bucket_hms: str) -> float | None:
    """
    메인 응답의 키 형태가 달라도 최대한 유연하게 꺼내기.
    기대 예시:
      { "ok": true, "avg": {"00_12": 1800, "00_16": 3200, "00_20": 6500}, "until":"2025-09-21" }
      혹은 { "ok": true, "baseline": 3200, "bucket": "16:00:00" }
    """
    # 1) 단일 baseline
    if "baseline" in payload and payload["baseline"] is not None:
        try:
            return float(payload["baseline"])
        except Exception:
            pass

    # 2) avg/averages 딕셔너리에서 버킷 키 찾기
    bucket_map = {
        "12:00:00": ["00_12", "00-12", "12", "12:00:00"],
        "16:00:00": ["00_16", "00-16", "16", "16:00:00"],
        "20:00:00": ["00_20", "00-20", "20", "20:00:00"],
    }
    for key_group_name in ("avg", "averages", "data", "result"):
        sub = payload.get(key_group_name)
        if isinstance(sub, dict):
            for k in bucket_map.get(bucket_hms, []):
                if k in sub and sub[k] is not None:
                    try:
                        return float(sub[k])
                    except Exception:
                        continue
    return None

def fetch_bucket_baseline_from_main(couple_id: int, bucket_hms: str) -> Tuple[float | None, Dict[str, Any]]:
    """
    메인서버의 '동일 couple_id, 동시간대(버킷), 어제까지 누적 평균'을 조회.
    실제 경로/쿼리는 메인서버 스펙에 맞추세요.
    현재 가정: GET {MAIN_API_BASE}/api/steps/overall-cumulative-avg?coupleId=7
    """
    if not MAIN_API_BASE:
        return None, {"error": "MAIN_API_BASE not set"}

    url = f"{MAIN_API_BASE}/api/steps/overall-cumulative-avg"
    params = {"coupleId": couple_id}

    try:
        r = requests.get(url, headers=_main_headers(), params=params, timeout=MAIN_API_TIMEOUT)
        ctype = r.headers.get("content-type", "")
        payload: Dict[str, Any] = r.json() if "application/json" in ctype else {}
        baseline = None
        if r.status_code == 200:
            baseline = _extract_baseline_from_payload(payload, bucket_hms)
        meta = {"status": r.status_code, "payload_keys": list(payload.keys()) if isinstance(payload, dict) else None}
        if "until" in payload:  # 메인서버가 어제 날짜 반환 시
            meta["until"] = payload["until"]
        meta["bucket"] = bucket_hms
        meta["endpoint"] = url
        meta["params"] = params
        return baseline, meta
    except Exception as e:
        log.warning("baseline fetch failed: %s", e)
        return None, {"error": str(e), "bucket": bucket_hms}

# ── 메인 로직: diff ≥ 500 이면 restrict ──────────────────────────────────────
def decide_steps_status(cum_steps: int, baseline: float | None) -> tuple[str, Dict[str, Any]]:
    """
    baseline 이 None 이면 보수적으로 normal.
    baseline 이 있으면 (baseline - cum_steps) >= DIFF_TH 이면 restrict.
    """
    if baseline is None:
        return "normal", {"reason": "no_baseline"}

    diff = float(baseline) - float(cum_steps)
    if diff >= DIFF_TH:
        return "steps_low", {"diff": int(diff), "threshold": DIFF_TH, "rule": "abs_diff>=500"}
    return "normal", {"diff": int(diff), "threshold": DIFF_TH, "rule": "abs_diff>=500"}

# ── 엔트리: 뷰에서 호출 ──────────────────────────────────────────────────────
def check_steps_low(couple_id: int, cum_steps: int, ts_kst) -> Dict[str, Any]:
    """
    return: { status, baseline, bucket, reasons(meta), decision, ts_kst_iso }
    """
    bucket = pick_bucket_hms(ts_kst)
    baseline, main_meta = fetch_bucket_baseline_from_main(couple_id, bucket)
    status, decision = decide_steps_status(cum_steps, baseline)

    return {
        "status": status,
        "baseline": baseline,
        "bucket": bucket,
        "decision": decision,         # diff/threshold/rule or no_baseline
        "main": main_meta,            # 메인 호출 메타
        "ts_kst_iso": ts_kst.isoformat(),
    }
