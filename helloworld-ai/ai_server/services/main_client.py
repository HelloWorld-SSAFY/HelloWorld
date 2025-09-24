# services/main_client.py
import os
import logging
import requests
from typing import Tuple, Any, Optional, Dict
from requests.adapters import HTTPAdapter, Retry

log = logging.getLogger(__name__)

def _session() -> requests.Session:
    s = requests.Session()
    r = Retry(
        total=3,
        backoff_factor=0.3,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["GET", "POST", "PUT", "DELETE"],
    )
    s.mount("https://", HTTPAdapter(max_retries=r))
    s.mount("http://",  HTTPAdapter(max_retries=r))
    s.headers.update({"Accept": "application/json"})
    return s

def _ensure_base() -> str:
    base = os.getenv("MAIN_BASE_URL", "").rstrip("/")
    if not base:
        raise RuntimeError("MAIN_BASE_URL is not set")
    return base

def _get_access_token(sess: requests.Session, override: Optional[str] = None) -> str:
    # 우선순위: 요청에서 받은 토큰 → 고정 토큰 → OAuth2 Client Credentials
    if override:
        return override
    fixed = os.getenv("MAIN_ACCESS_TOKEN")
    if fixed:
        return fixed

    token_url = os.getenv("AUTH_TOKEN_URL")
    cid = os.getenv("AUTH_CLIENT_ID")
    csec = os.getenv("AUTH_CLIENT_SECRET")
    if not (token_url and cid and csec):
        raise RuntimeError("Access token missing. Set MAIN_ACCESS_TOKEN or AUTH_* env vars.")

    resp = sess.post(
        token_url,
        data={"grant_type": "client_credentials"},
        auth=(cid, csec),
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()["access_token"]

def _parse_resp(resp: requests.Response) -> Any:
    try:
        return resp.json()
    except Exception:
        return resp.text

def call_main(
    path: str,
    method: str = "GET",
    json_body: Optional[Dict[str, Any]] = None,
    couple_id: Optional[str] = None,
    access_token: Optional[str] = None,
    timeout: int = 15,
) -> Tuple[int, Any]:
    """
    메인 서버에 요청 후 (status_code, body) 반환.
    """
    base = _ensure_base()
    if not path.startswith("/"):
        path = "/" + path
    url = f"{base}{path}"

    s = _session()
    token = _get_access_token(s, override=access_token)

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/json",
    }
    if couple_id:
        headers["X-Couple-Id"] = str(couple_id)

    try:
        m = method.upper()
        if m == "GET":
            resp = s.get(url, headers=headers, timeout=timeout)
        elif m == "POST":
            resp = s.post(url, headers=headers, json=json_body, timeout=timeout)
        elif m == "PUT":
            resp = s.put(url, headers=headers, json=json_body, timeout=timeout)
        elif m == "DELETE":
            resp = s.delete(url, headers=headers, json=json_body, timeout=timeout)
        else:
            raise ValueError(f"Unsupported method: {method}")
    except requests.RequestException as e:
        log.exception("call_main: request error")
        return 599, {"error": "REQUEST_FAILED", "detail": str(e)}

    return resp.status_code, _parse_resp(resp)

# (옵션) 특정 엔드포인트용 래퍼 예시
def get_daily_buckets(date_str: str, couple_id: Optional[str] = None, access_token: Optional[str] = None):
    return call_main(f"/health/api/wearable/daily-buckets?date={date_str}",
                     method="GET", couple_id=couple_id, access_token=access_token)
