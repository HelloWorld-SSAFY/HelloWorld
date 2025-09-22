from __future__ import annotations
import os, logging, requests
from typing import Optional, Literal
from requests.adapters import HTTPAdapter, Retry

log = logging.getLogger(__name__)
Slot = Literal["00-12", "00-16"]

def _session() -> requests.Session:
    s = requests.Session()
    s.headers.update({"Accept": "application/json"})
    r = Retry(total=3, backoff_factor=0.3,
              status_forcelist=[429,500,502,503,504],
              allowed_methods=["GET","POST"])
    s.mount("https://", HTTPAdapter(max_retries=r))
    s.mount("http://",  HTTPAdapter(max_retries=r))
    return s

def _get_access_token(sess: requests.Session) -> str:
    fixed = os.getenv("MAIN_ACCESS_TOKEN")
    if fixed:
        return fixed
    # client_credentials (운영 권장)
    token_url = os.getenv("AUTH_TOKEN_URL")
    cid = os.getenv("AUTH_CLIENT_ID")
    csec = os.getenv("AUTH_CLIENT_SECRET")
    if not (token_url and cid and csec):
        raise RuntimeError("MAIN_ACCESS_TOKEN 또는 AUTH_* 환경변수 필요")
    resp = sess.post(
        token_url,
        headers={"Content-Type":"application/x-www-form-urlencoded"},
        data={"grant_type":"client_credentials","client_id":cid,"client_secret":csec},
        timeout=15
    )
    if resp.status_code >= 400:
        raise RuntimeError(f"토큰 발급 실패: {resp.status_code} {resp.text[:200]}")
    return resp.json()["access_token"]

def _pick_avg(rec: dict) -> Optional[float]:
    """
    응답 스키마 방어적 파싱:
    - steps 평균 필드가 'avg_steps'일 수도, 'avg'일 수도, 재사용 스키마로 'avg_heartrate'일 수도 있음.
    """
    for k in ("avg_steps", "avg", "avg_value", "avg_heartrate"):
        v = rec.get(k)
        if v is not None:
            try:
                return float(v)
            except Exception:
                pass
    return None

def get_steps_overall_avg(couple_id: int, slot: Slot) -> Optional[float]:
    """
    메인 서버: GET /health/api/steps/overall-cumulative-avg?coupleId=7
    records: [{hour_range:"00-12", avg_steps:...}, {hour_range:"00-16", ...}]
    """
    base = (os.getenv("MAIN_BASE_URL") or "").rstrip("/")
    if not base:
        log.warning("MAIN_BASE_URL 비어 있음")
        return None

    sess = _session()
    try:
        token = _get_access_token(sess)
    except Exception as e:
        log.warning(f"[steps-baseline] 토큰 획득 실패: {e}")
        return None

    headers = {"Authorization": f"Bearer {token}", "Accept":"application/json"}
    try:
        resp = sess.get(f"{base}/health/api/steps/overall-cumulative-avg",
                        headers=headers, params={"coupleId": couple_id}, timeout=15)
    except Exception as e:
        log.warning(f"[steps-baseline] HTTP 실패: {e}")
        return None

    if resp.status_code == 401:
        log.warning("[steps-baseline] 401 Unauthorized")
        return None
    if resp.status_code >= 400:
        log.warning(f"[steps-baseline] {resp.status_code} {resp.text[:200]}")
        return None

    data = resp.json() or {}
    for rec in data.get("records", []):
        if rec.get("hour_range") == slot:
            return _pick_avg(rec)
    log.info(f"[steps-baseline] slot {slot} 기준치 없음")
    return None
