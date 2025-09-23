from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

@api_view(["GET"])
@permission_classes([AllowAny])
def echo_headers(request):
    keys = ["Authorization","X-Internal-User-Id","X-Internal-Couple-Id",
            "X-Internal-Role","X-Internal-Ts","X-Internal-Sig"]
    return Response({k: request.headers.get(k) for k in keys})

# api/views_debug.py (네가 올린 코드에 한 줄 추가)
from rest_framework.decorators import api_view, permission_classes, authentication_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.authentication import get_authorization_header  # 필요없지만 예시용
from ai_server.settings import GatewayInternalAuth  # settings에 정의한 클래스 임포트

@api_view(["GET"])
@authentication_classes([GatewayInternalAuth])   # 여기가 핵심: 내부 헤더 인증을 강제
@permission_classes([IsAuthenticated])
def whoami(request):
    u = request.user
    return Response({
        "user_id": getattr(u, "id", None),
        "username": getattr(u, "username", None),
        "is_authenticated": bool(getattr(u, "is_authenticated", False)),
        "auth_class": request.successful_authenticator.__class__.__name__ if request.successful_authenticator else None,
    })
