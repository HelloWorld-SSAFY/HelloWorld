import os, logging, requests
from typing import Dict, Any
from requests.adapters import HTTPAdapter, Retry

log = logging.getLogger(__name__)

def _session() -> requests.Session:
    s = requests.Session()
    r = Retry(total=3, backoff_factor=0.3,
              status_forcelist=[429,500,502,503,504],
              allowed_methods=["GET","POST"])
    s.mount("https://", HTTPAdapter(max_retries=r))
    s.mount("http://",  HTTPAdapter(max_retries=r))
    s.headers.update({"Accept": "application/json"})
    return s

def _get_access_token(sess: requests.Session) -> str:
    # 우선순위: 고정 토큰 → OAuth2 Client Credentials
    fixed = os.getenv("MAIN_ACCESS_TOKEN")
    if fixed:
        return fixed

    token_url = os.getenv("AUTH_TOKEN_URL")
    cid = os.getenv("AUTH_CLIENT_ID")
    csec = os.getenv("AUTH_CLIENT_SECRET")
    if not (token_url and cid and csec):
        raise RuntimeError("MAIN_ACCESS_TOKEN 또는 AUTH_* 환경변수가 필요합니다.")

    resp = sess.post(
        token_url,
        data={"grant_type": "client_credentials"},
        auth=(cid, csec),
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()["access_token"]

def get_daily_buckets(date_str: str) -> Dict[str, Any]:
    """
    메인서버 일간 4시간 버킷 통계 조회
    """
    base = os.getenv("MAIN_BASE_URL", "").rstrip("/")
    if not base:
        raise RuntimeError("MAIN_BASE_URL 이 설정되어야 합니다.")
    url = f"{base}/health/api/wearable/daily-buckets"

    s = _session()
    token = _get_access_token(s)
    r = s.get(url, params={"date": date_str},
              headers={"Authorization": f"Bearer {token}"}, timeout=15)
    r.raise_for_status()
    return r.json()
