# api/middleware.py
import os
from django.http import JsonResponse

# ─────────────────────────────────────────────────────────────
# Gateway 프리픽스 보정 (예: "/ai")
# 환경에서 BASE_PATH_PREFIX="" 로 두면 보정 비활성화
# ─────────────────────────────────────────────────────────────
BASE_PREFIX = os.getenv("BASE_PATH_PREFIX", "/ai").rstrip("/")

def _normalize_path(path: str) -> str:
    """게이트웨이 프리픽스(/ai 등)를 제거해 내부 경로로 정규화"""
    p = (path or "/").rstrip("/") or "/"
    if BASE_PREFIX and p.startswith(BASE_PREFIX + "/"):
        p = p[len(BASE_PREFIX):]  # "/ai/..." -> "/..."
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

# 운영에서만 내부 헤더 강제하고 싶으면 1로 설정 (기본은 허용적: 내부 우선 + 외부 폴백)
REQUIRE_INTERNAL_HEADERS = os.getenv("REQUIRE_INTERNAL_HEADERS", "0").strip().lower() in ("1","true","on")


def app_token_mw(get_response):
    """X-App-Token 검사 (env에 APP_TOKEN이 설정된 경우에만 활성)"""
    def middleware(request):
        if _is_open_request(request):
            return get_response(request)

        # X-App-Token 검사 (env에 값이 있을 때만)
        token = os.getenv("APP_TOKEN")
        if token:
            got = request.headers.get("X-App-Token") or request.META.get("HTTP_X_APP_TOKEN")
            if got != token:
                return JsonResponse({"detail": "invalid app token"}, status=401)

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

        h = request.headers  # 대소문자 무시 dict-like

        # 1) 내부 헤더 우선
        couple_id_val = h.get("X-Internal-Couple-Id")
        user_id_val   = h.get("X-Internal-User-Id")
        role_val      = h.get("X-Internal-Role")

        # 2) 외부 헤더 폴백 (로컬/직통 호출용)
        if not couple_id_val and not REQUIRE_INTERNAL_HEADERS:
            couple_id_val = h.get("X-Couple-Id") or request.META.get("HTTP_X_COUPLE_ID")
        if not user_id_val and not REQUIRE_INTERNAL_HEADERS:
            user_id_val = h.get("X-User-Id") or request.META.get("HTTP_X_USER_ID")
        if not role_val and not REQUIRE_INTERNAL_HEADERS:
            role_val = h.get("X-Role") or request.META.get("HTTP_X_ROLE")

        # 3) couple_id 필수 검증
        if not couple_id_val:
            return JsonResponse({"ok": False, "error": "missing couple id header"}, status=401)

        try:
            couple_id_int = int(str(couple_id_val).strip())
        except Exception:
            return JsonResponse({"ok": False, "error": "invalid couple id"}, status=400)

        # 4) request 속성/메타 동기화
        request.couple_id = couple_id_int
        request.user_id = str(user_id_val).strip() if user_id_val else None
        request.role = str(role_val).strip() if role_val else None

        # META 동기화 (일부 코드가 META를 볼 수도 있으므로)
        request.META["HTTP_X_COUPLE_ID"] = str(couple_id_int)
        if request.user_id:
            request.META["HTTP_X_USER_ID"] = request.user_id
        if request.role:
            request.META["HTTP_X_ROLE"] = request.role

        return get_response(request)
    return middleware
