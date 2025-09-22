# services/main_client.py
import os
import json
import logging
from typing import Any, Dict, Optional, Tuple
import requests

log = logging.getLogger(__name__)

MAIN_BASE_URL   = os.getenv("MAIN_BASE_URL", "").rstrip("/")   # 예: https://main.example.com
MAIN_TIMEOUT_S  = float(os.getenv("MAIN_TIMEOUT_S", "5"))
MAIN_VERIFY_SSL = (os.getenv("MAIN_VERIFY_SSL", "true").lower() not in ("0","false","no"))

def call_main(
    path: str,
    *,
    method: str = "GET",
    json_body: Optional[Dict[str, Any]] = None,
    couple_id: Optional[str] = None,
    access_token: Optional[str] = None,
    app_token: Optional[str] = None,
    timeout: Optional[float] = None,
) -> Tuple[int, Any]:
    """
    메인 서버(path 기준)로 요청. JSON 응답은 dict/list로 파싱해서 반환.
    """
    if not MAIN_BASE_URL:
        raise RuntimeError("MAIN_BASE_URL is not set")

    url = f"{MAIN_BASE_URL}/{path.lstrip('/')}"
    headers = {
        "Content-Type": "application/json",
    }
    # 서버 토큰(없으면 스킵)
    app_token = app_token or os.getenv("APP_TOKEN")
    if app_token:
        headers["X-App-Token"] = app_token

    # 커플/유저 식별자
    if couple_id:
        headers["X-Couple-Id"] = couple_id

    # 게이트웨이/메인 인증 토큰
    if access_token:
        headers["Access-Token"] = access_token

    t = timeout if timeout is not None else MAIN_TIMEOUT_S

    resp = requests.request(
        method.upper(),
        url,
        headers=headers,
        json=json_body,
        timeout=t,
        verify=MAIN_VERIFY_SSL,
    )

    # JSON이면 파싱, 아니면 텍스트 그대로
    ctype = (resp.headers.get("content-type") or "").lower()
    data = resp.json() if "application/json" in ctype else resp.text
    return resp.status_code, data
