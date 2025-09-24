# services/main_gateway.py
import os, logging, requests
from urllib.parse import urljoin

log = logging.getLogger(__name__)

def _session() -> requests.Session:
    s = requests.Session()
    s.headers.update({"Accept": "application/json"})
    return s

def _get_access_token(sess: requests.Session) -> str:
    fixed = os.getenv("MAIN_ACCESS_TOKEN")
    if fixed:
        return fixed
    # 필요하면 여기서 OAuth2 client_credentials 로직 추가
    raise RuntimeError("MAIN_ACCESS_TOKEN not set")

def _base() -> str:
    base = os.getenv("MAIN_BASE_URL", "").rstrip("/")
    if not base:
        raise RuntimeError("MAIN_BASE_URL not set")
    return base

def fetch_daily_buckets(date_str: str) -> dict:
    """
    GET /health/api/wearable/daily-buckets?date=YYYY-MM-DD
    """
    sess = _session()
    token = _get_access_token(sess)
    url = urljoin(_base() + "/", f"health/api/wearable/daily-buckets?date={date_str}")
    r = sess.get(url, headers={"Authorization": f"Bearer {token}"}, timeout=15)
    r.raise_for_status()
    return r.json()
