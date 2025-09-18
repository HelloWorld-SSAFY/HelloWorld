# services/weather_gateway.py
from __future__ import annotations
import logging
from typing import Optional, Literal, Tuple
from requests.adapters import HTTPAdapter, Retry
import requests

log = logging.getLogger(__name__)

WeatherKind = Literal["clear","clouds","rain","snow","thunder","dust","unknown"]

def _build_session() -> requests.Session:
    s = requests.Session()
    retries = Retry(total=2, backoff_factor=0.2,
                    status_forcelist=[429,500,502,503,504],
                    allowed_methods=["GET","POST"])
    s.mount("http://", HTTPAdapter(max_retries=retries))
    s.mount("https://", HTTPAdapter(max_retries=retries))
    return s

# ▶ 기본값을 코드로 고정 (env 사용 안 함)
DEFAULT_WEATHER_MODE = "stub"            # "stub" | "remote" | "auto"
DEFAULT_WEATHER_STUB_KIND: WeatherKind = "clear"

class WeatherGateway:
    """
    날씨 조회 게이트웨이 (env 없이 동작).
    - 기본: stub 모드, kind="clear"
    - remote 연동하려면 이 파일의 DEFAULT_* 상수만 수정하면 됨.
    """
    def __init__(self,
                 mode: str = DEFAULT_WEATHER_MODE,
                 stub_kind: WeatherKind = DEFAULT_WEATHER_STUB_KIND,
                 remote_base: Optional[str] = None,
                 remote_path: str = "/v1/weather/current",
                 remote_token: Optional[str] = None,
                 timeout: float = 2.5):
        self.mode = (mode or "stub").strip().lower()
        self.stub_kind: WeatherKind = stub_kind
        self.base = (remote_base or "").rstrip("/")
        self.path = remote_path
        self.token = remote_token or ""
        self.timeout = timeout
        self._s = _build_session()

    def _headers(self)->dict:
        h = {"Content-Type":"application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    def _outdoor_ok(self, kind: WeatherKind) -> bool:
        # 맑음/구름 → 실외 OK, 그 외 → 실내
        return kind in {"clear","clouds"}

    def _remote_get(self, lat: float, lng: float) -> Optional[Tuple[WeatherKind, bool]]:
        if not self.base:
            return None
        url = f"{self.base}{self.path}"
        try:
            r = self._s.get(url, params={"lat":lat, "lng":lng}, headers=self._headers(), timeout=self.timeout)
        except Exception as e:
            log.warning("weather remote err: %s", e)
            return None
        if r.status_code == 200:
            try:
                j = r.json()
                cond = (j.get("condition") or j.get("weather", [{}])[0].get("main") or "").lower()
                if "clear" in cond: kind: WeatherKind = "clear"
                elif "cloud" in cond: kind = "clouds"
                elif "rain" in cond or "drizzle" in cond: kind = "rain"
                elif "snow" in cond: kind = "snow"
                elif "thunder" in cond or "storm" in cond: kind = "thunder"
                elif "dust" in cond or "smoke" in cond or "sand" in cond: kind = "dust"
                else: kind = "unknown"
                return (kind, self._outdoor_ok(kind))
            except Exception as e:
                log.warning("weather bad json: %s :: %s", e, r.text[:200])
                return None
        log.warning("weather unexpected %s %s", r.status_code, r.text[:160])
        return None

    def _stub_get(self, lat: float, lng: float, override_kind: Optional[WeatherKind]=None) -> Tuple[WeatherKind, bool]:
        kind: WeatherKind = override_kind or self.stub_kind
        return kind, self._outdoor_ok(kind)

    def get_kind_and_gate(self, lat: float, lng: float, override_kind: Optional[WeatherKind]=None) -> Tuple[WeatherKind, bool]:
        # 요청 단위로 override_kind가 들어오면 그 값을 우선 사용 (테스트용)
        if self.mode == "stub":  return self._stub_get(lat, lng, override_kind)
        if self.mode == "remote":
            return self._remote_get(lat, lng) or self._stub_get(lat, lng, override_kind)
        # auto
        return self._remote_get(lat, lng) or self._stub_get(lat, lng, override_kind)

_gateway: WeatherGateway|None = None
def get_weather_gateway() -> WeatherGateway:
    global _gateway
    if _gateway is None:
        _gateway = WeatherGateway()
    return _gateway
