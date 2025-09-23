from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

@api_view(["GET"])
@permission_classes([AllowAny])
def echo_headers(request):
    keys = ["Authorization","X-Internal-User-Id","X-Internal-Couple-Id",
            "X-Internal-Role","X-Internal-Ts","X-Internal-Sig"]
    return Response({k: request.headers.get(k) for k in keys})

from rest_framework.decorators import api_view, permission_classes, authentication_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

@api_view(["GET"])
@permission_classes([IsAuthenticated])     # 인증 필수
def whoami(request):
    u = request.user
    return Response({
        "user_id": getattr(u, "id", None),
        "username": getattr(u, "username", None),
        "is_authenticated": bool(getattr(u, "is_authenticated", False)),
        "auth_class": request.successful_authenticator.__class__.__name__ if request.successful_authenticator else None,
    })