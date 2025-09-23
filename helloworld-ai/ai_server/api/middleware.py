# api/middleware.py
import os
import re
import logging
from django.http import JsonResponse

log = logging.getLogger(__name__)

# ─────────────────────────────────────────────────────────────
# Gateway 프리픽스 보정 (예: "/ai")
# 환경에서 BASE_PATH_PREFIX="" 로 두면 보정 비활성화
# ─────────────────────────────────────────────────────────────
BASE_PREFIX = os.getenv("BASE_PATH_PREFIX", "/ai").rstrip("/")

def _normalize_path(path: str) -> str:
    """게이트웨이 프리픽스(/ai 등)를 제거해 내부 경로로 정규화"""
    p = (path or "/").rstrip("/") or "/"
    if BASE_PREFIX and p.startswith(BASE_PREFIX + "/"):
        p = p[len(BASE_PREFIX):]
        if not p.startswith("/"):
            p = "/" + p
    return p

# 공개 경로(토큰 미요구)
ALLOW_PREFIXES = (
    "/docs",          # Swagger UI
    "/swagger",       # Swagger UI
    "/schema",        # OpenAPI JSON
    "/static",        # 정적 파일
    "/favicon.ico",
    "/metrics",
)
ALLOW_EXACT = {
    "/healthz",
    "/v1/healthz",    # 내부/직접 호출 대비
}

def _is_open_request(request) -> bool:
    path = _normalize_path(request.path)
    return (
        request.method == "OPTIONS"
        or path in ALLOW_EXACT
        or any(path.startswith(p) for p in ALLOW_PREFIXES)
    )

# 안전한 헤더 읽기 (request.headers / META 둘 다 시도)
def _get_header(request, name: str):
    meta_key = "HTTP_" + name.upper().replace("-", "_")
    # META가 더 하위 레벨이라 우선 읽음
    v = request.META.get(meta_key)
    if v is None:
        # 일부 환경에서 request.headers가 없을 수 있으니 getattr
        headers = getattr(request, "headers", None)
        if headers is not None:
            v = headers.get(name)
    return v

# 운영에서 내부 헤더만 허용하려면 1로
REQUIRE_INTERNAL_HEADERS = os.getenv("REQUIRE_INTERNAL_HEADERS", "0").strip().lower() in ("1","true","on")


def app_token_mw(get_response):
    """X-App-Token 검사 (env에 APP_TOKEN이 설정된 경우에만 활성)"""
    APP_TOKEN = os.getenv("APP_TOKEN")  # 매 요청마다 getenv 가능하지만, 필요시 모듈 전역화
    def middleware(request):
        if _is_open_request(request):
            return get_response(request)

        if APP_TOKEN:
            got = _get_header(request, "X-App-Token")
            if got != APP_TOKEN:
                return JsonResponse({"ok": False, "error": "invalid app token"}, status=401)

        return get_response(request)
    return middleware


def couple_id_mw(get_response):
    """
    게이트웨이가 주입하는 내부 헤더(X-Internal-User-Id / X-Internal-Couple-Id / X-Internal-Role)를
    우선 사용하고, 없으면 외부 헤더(X-Couple-Id / X-User-Id / X-Role)로 폴백.
      - request.couple_id: int
      - request.user_id: str | None
      - request.role: str | None
    또한 META 동기화(HTTP_X_COUPLE_ID 등)도 수행.
    """
    def middleware(request):
        if _is_open_request(request):
            return get_response(request)

        try:
            # 1) 내부 헤더 우선
            couple_id_raw = _get_header(request, "X-Internal-Couple-Id")
            user_id_raw   = _get_header(request, "X-Internal-User-Id")
            role_raw      = _get_header(request, "X-Internal-Role")

            # 2) 외부 헤더 폴백 (로컬/직통 호출용)
            if not couple_id_raw and not REQUIRE_INTERNAL_HEADERS:
                couple_id_raw = _get_header(request, "X-Couple-Id")
            if not user_id_raw and not REQUIRE_INTERNAL_HEADERS:
                user_id_raw = _get_header(request, "X-User-Id")
            if not role_raw and not REQUIRE_INTERNAL_HEADERS:
                role_raw = _get_header(request, "X-Role")

            if not couple_id_raw:
                return JsonResponse({"ok": False, "error": "missing couple id header"}, status=401)

            # 3) 숫자 검증 (공백/따옴표/이상문자 방지)
            #   예: ' "7" ' , '7 ' , '007' 도 허용
            cid_str = str(couple_id_raw).strip().strip('"').strip("'")
            if not re.fullmatch(r"\d+", cid_str):
                return JsonResponse({"ok": False, "error": "invalid couple id"}, status=400)

            couple_id_int = int(cid_str)

            # 4) request 속성/메타 동기화
            request.couple_id = couple_id_int
            request.user_id = str(user_id_raw).strip() if user_id_raw else None
            request.role = str(role_raw).strip() if role_raw else None

            request.META["HTTP_X_COUPLE_ID"] = str(couple_id_int)
            if request.user_id:
                request.META["HTTP_X_USER_ID"] = request.user_id
            if request.role:
                request.META["HTTP_X_ROLE"] = request.role

            return get_response(request)

        except Exception as e:
            # 절대 500 HTML로 떨어지지 않게 JSON으로 캐치
            log.exception("couple_id_mw error")
            return JsonResponse({"ok": False, "error": f"middleware failure: {type(e).__name__}"}, status=500)

    return middleware
