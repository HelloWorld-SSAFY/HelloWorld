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
    p = path.rstrip("/") or "/"
    if BASE_PREFIX and p.startswith(BASE_PREFIX + "/"):
        p = p[len(BASE_PREFIX):]  # "/ai/..." -> "/..."
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


def app_token_mw(get_response):
    def middleware(request):
        raw_path = request.path
        path = _normalize_path(raw_path)

        # 문서/정적/헬스체크/프리플라이트는 토큰 검사 생략
        if (
            request.method == "OPTIONS"
            or path in ALLOW_EXACT
            or any(path.startswith(p) for p in ALLOW_PREFIXES)
        ):
            return get_response(request)

        # X-App-Token 검사 (env에 값이 있을 때만)
        token = os.getenv("APP_TOKEN")
        if token:
            got = (
                request.headers.get("X-App-Token")
                or request.META.get("HTTP_X_APP_TOKEN")
            )
            if got != token:
                return JsonResponse({"detail": "invalid app token"}, status=401)

        return get_response(request)

    return middleware


def couple_id_mw(get_response):
    """Gateway가 주입하는 X-Couple-Id를 request.couple_id 로 노출"""
    def middleware(request):
        cid = request.headers.get("X-Couple-Id") or request.META.get("HTTP_X_COUPLE_ID")
        request.couple_id = cid or None
        return get_response(request)

    return middleware
