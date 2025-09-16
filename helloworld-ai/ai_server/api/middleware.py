# api/middleware.py
import os
from django.http import JsonResponse

def app_token_mw(get_response):
    APP_TOKEN = os.getenv("APP_TOKEN")  # 없으면 인증을 안걸 수도 있지만, 운영에선 반드시 설정
    def _mw(request):
        if APP_TOKEN:  # 운영/스테이징에서만 강제
            got = request.headers.get("X-App-Token")
            if got != APP_TOKEN:
                return JsonResponse(
                    {"detail": "invalid app token"}, status=401
                )
        return get_response(request)
    return _mw
