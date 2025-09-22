# api/middleware.py
import os
from django.http import JsonResponse

ALLOW_PREFIXES = (
    "/docs",          # Swagger UI
    "/static",        # openapi.yaml, 정적 파일
    "/favicon.ico",
    "/swagger",
    "/schema",
    "/metrics",
)
ALLOW_EXACT = {"/v1/healthz"}  # 헬스체크는 토큰 없이 허용


def app_token_mw(get_response):
    def middleware(request):
        path = request.path

        # 문서/정적/헬스체크는 토큰 검사 건너뛰기
        if path in ALLOW_EXACT or any(path.startswith(p) for p in ALLOW_PREFIXES):
            return get_response(request)

        # 그 외(/v1/*)는 토큰 검사
        token = os.getenv("APP_TOKEN")
        if token:  # 토큰이 설정돼 있으면 검사
            got = request.headers.get("X-App-Token")
            if got != token:
                return JsonResponse({"detail": "invalid app token"}, status=401)

        return get_response(request)
    return middleware


def couple_id_mw(get_response):
    """Gateway가 주입하는 X-Couple-Id를 request.couple_id 로 노출"""
    def middleware(request):
        cid = request.headers.get("X-Couple-Id") or request.META.get("HTTP_X_COUPLE_ID")
        request.couple_id = (cid or None)
        return get_response(request)
    return middleware
