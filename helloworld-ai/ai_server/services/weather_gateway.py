# services/weather_gateway.py
from __future__ import annotations
import logging, os
from typing import Optional, Literal, Tuple
import requests
from requests.adapters import HTTPAdapter, Retry

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

# ─────────────────────────────────────────────────────────────
# 기본값: remote (무료 Open-Meteo 우선) / 실패 시 OpenWeather(무료 현재날씨) / 최종 stub
# ─────────────────────────────────────────────────────────────
DEFAULT_WEATHER_MODE = os.getenv("WEATHER_MODE", "remote").strip().lower()  # remote|auto|stub
DEFAULT_WEATHER_STUB_KIND: WeatherKind = os.getenv("WEATHER_STUB_KIND", "clear").strip().lower() or "clear"
WEATHER_TIMEOUT = float(os.getenv("WEATHER_TIMEOUT", "2.5"))

# 1) Open-Meteo (무료, 키 불필요)
OM_BASE = "https://api.open-meteo.com/v1/forecast"
OM_PARAMS = "current=weather_code,precipitation,cloud_cover,is_day"  # 필요한 필드만

# 2) OpenWeather 현재날씨(무료 티어, 키 필요 / OneCall 아님)
OWM_BASE = "https://api.openweathermap.org/data/2.5/weather"
OWM_KEY = os.getenv("OPENWEATHER_API_KEY", "").strip()
OWM_UNITS = os.getenv("OPENWEATHER_UNITS", "metric").strip()
OWM_LANG  = os.getenv("OPENWEATHER_LANG", "kr").strip()

# (선택) 사내 프록시가 있으면 먼저 사용
REMOTE_BASE = os.getenv("WEATHER_REMOTE_BASE", "").rstrip("/")
REMOTE_PATH = os.getenv("WEATHER_REMOTE_PATH", "/v1/weather/current")
REMOTE_TOKEN = os.getenv("WEATHER_REMOTE_TOKEN", "").strip()

class WeatherGateway:
    """
    위/경도(lat,lng)로 현재 날씨 조회.
    우선순위:
      0) override_kind가 오면 그 값 사용(테스트용)
      1) remote_base 프록시(있을 때)
      2) Open-Meteo(무료, no key)
      3) OpenWeather 현재날씨(무료키)
      4) stub(폴백)
    """
    def __init__(self,
                 mode: str = DEFAULT_WEATHER_MODE,
                 stub_kind: WeatherKind = DEFAULT_WEATHER_STUB_KIND,
                 remote_base: Optional[str] = REMOTE_BASE or None,
                 remote_path: str = REMOTE_PATH,
                 remote_token: Optional[str] = REMOTE_TOKEN or None,
                 timeout: float = WEATHER_TIMEOUT):
        self.mode = (mode or "remote").strip().lower()
        self.stub_kind: WeatherKind = stub_kind
        self.base = (remote_base or "").rstrip("/")
        self.path = remote_path
        self.token = remote_token or ""
        self.timeout = timeout
        self._s = _build_session()

    # ── 공통 ────────────────────────────────────────────────────────────────
    def _headers(self) -> dict:
        h = {"Content-Type": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    @staticmethod
    def _outdoor_ok(kind: WeatherKind) -> bool:
        # 맑음/구름만 실외 OK
        return kind in {"clear","clouds"}

    # ── 매핑 ────────────────────────────────────────────────────────────────
    @staticmethod
    def _map_openmeteo(weather_code: int, cloud_cover: Optional[float], precip: Optional[float]) -> WeatherKind:
        # https://open-meteo.com/en/docs (WMO WMO weather interpretation codes)
        wc = int(weather_code)
        if wc == 0:
            return "clear"
        if wc in (1,2,3,45,48):  # partly cloudy, overcast, fog
            return "clouds"
        if 51 <= wc <= 67 or 80 <= wc <= 82:  # drizzle/rain/showers
            return "rain"
        if 71 <= wc <= 77 or 85 <= wc <= 86:  # snow/snow grains/snow showers
            return "snow"
        if wc in (95,96,99):
            return "thunder"
        return "unknown"

    @staticmethod
    def _map_openweather(main: str, desc: str) -> WeatherKind:
        m, d = (main or "").lower(), (desc or "").lower()
        if "clear" in m: return "clear"
        if "cloud" in m: return "clouds"
        if "rain" in m or "drizzle" in m: return "rain"
        if "snow" in m: return "snow"
        if "thunder" in m or "storm" in m: return "thunder"
        if any(x in m for x in ["dust","sand","smoke","ash"]) or any(x in d for x in ["dust","sand","smoke","haze"]):
            return "dust"
        return "unknown"

    # ── 원격 호출 ───────────────────────────────────────────────────────────
    def _remote_proxy_get(self, lat: float, lng: float):
        if not self.base:
            return None
        url = f"{self.base}{self.path}"
        try:
            r = self._s.get(url, params={"lat": lat, "lng": lng}, headers=self._headers(), timeout=self.timeout)
        except Exception as e:
            log.warning("weather proxy err: %s", e); return None
        if r.status_code != 200:
            log.warning("weather proxy unexpected %s %s", r.status_code, r.text[:160]); return None
        try:
            j = r.json()
            main = (j.get("condition") or (j.get("weather", [{}])[0].get("main")) or "")
            desc = (j.get("description") or (j.get("weather", [{}])[0].get("description")) or "")
            kind = self._map_openweather(main, desc)
            return (kind, self._outdoor_ok(kind))
        except Exception as e:
            log.warning("weather proxy bad json: %s :: %s", e, r.text[:200]); return None

    def _remote_openmeteo_get(self, lat: float, lng: float):
        params = {
            "latitude": lat,
            "longitude": lng,
        }
        # current=... 파라미터는 문자열 전체로 전달
        url = f"{OM_BASE}?{OM_PARAMS}"
        try:
            r = self._s.get(url, params=params, timeout=self.timeout)
        except Exception as e:
            log.warning("open-meteo err: %s", e); return None
        if r.status_code != 200:
            log.warning("open-meteo unexpected %s %s", r.status_code, r.text[:160]); return None
        try:
            j = r.json()
            cur = j.get("current", {}) or j.get("current_weather", {})  # 일부 배포에서 키가 다를 수 있음
            wc = cur.get("weather_code") or cur.get("weathercode")  # 키 호환
            cc = cur.get("cloud_cover")
            pr = cur.get("precipitation")
            if wc is None:
                log.warning("open-meteo missing weather_code: %s", cur); return None
            kind = self._map_openmeteo(int(wc), cc, pr)
            return (kind, self._outdoor_ok(kind))
        except Exception as e:
            log.warning("open-meteo bad json: %s :: %s", e, r.text[:200]); return None

    def _remote_openweather_get(self, lat: float, lng: float):
        if not OWM_KEY:
            return None
        try:
            r = self._s.get(
                OWM_BASE,
                params={"lat": lat, "lon": lng, "appid": OWM_KEY, "units": OWM_UNITS, "lang": OWM_LANG},
                timeout=self.timeout,
            )
        except Exception as e:
            log.warning("openweather err: %s", e); return None
        if r.status_code != 200:
            log.warning("openweather unexpected %s %s", r.status_code, r.text[:160]); return None
        try:
            arr = r.json().get("weather", [])
            main = arr[0].get("main","") if arr else ""
            desc = arr[0].get("description","") if arr else ""
            kind = self._map_openweather(main, desc)
            return (kind, self._outdoor_ok(kind))
        except Exception as e:
            log.warning("openweather bad json: %s :: %s", e, r.text[:200]); return None

    # ── stub ────────────────────────────────────────────────────────────────
    def _stub_get(self, lat: float, lng: float, override_kind: Optional[WeatherKind]=None):
        kind: WeatherKind = override_kind or self.stub_kind
        return kind, self._outdoor_ok(kind)

    # ── 퍼블릭 ──────────────────────────────────────────────────────────────
    def get_kind_and_gate(self, lat: float, lng: float, override_kind: Optional[WeatherKind]=None) -> Tuple[WeatherKind, bool]:
        # 테스트 강제 주입
        if override_kind:
            return self._stub_get(lat, lng, override_kind)

        if self.mode in ("remote","auto"):
            return (
                self._remote_proxy_get(lat, lng) or
                self._remote_openmeteo_get(lat, lng) or
                self._remote_openweather_get(lat, lng) or
                self._stub_get(lat, lng, None)
            )
        return self._stub_get(lat, lng, None)

_gateway: WeatherGateway|None = None
def get_weather_gateway() -> WeatherGateway:
    global _gateway
    if _gateway is None:
        _gateway = WeatherGateway()
    return _gateway
