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
    access_token: Optional[str] = None,  # ← 반드시 Swagger에서 전달해야 함
    timeout: int = 15,
) -> Tuple[int, Any]:
    """
    메인 서버에 요청 후 (status_code, body) 반환.
    ※ 토큰은 반드시 인자로 전달되어야 하며, ENV 토큰/자동발급을 절대 사용하지 않는다.
    """
    if not access_token:
        # 498: nginx/varnish에서 토큰 없을 때 쓰는 비표준 코드. 우리 쪽만의 신호로 사용.
        return 498, {"error": "ACCESS_TOKEN_REQUIRED", "message": "Provide Authorization: Bearer <token> header."}

    base = _ensure_base()
    if not path.startswith("/"):
        path = "/" + path
    url = f"{base}{path}"

    s = _session()
    headers = {
        "Authorization": f"Bearer {access_token}",
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
            return 400, {"error": "UNSUPPORTED_METHOD", "method": method}
    except requests.RequestException as e:
        log.exception("call_main: request error")
        return 599, {"error": "REQUEST_FAILED", "detail": str(e)}

    return resp.status_code, _parse_resp(resp)
