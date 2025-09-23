from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

@api_view(["GET"])
@permission_classes([AllowAny])
def echo_headers(request):
    keys = ["Authorization","X-Internal-User-Id","X-Internal-Couple-Id",
            "X-Internal-Role","X-Internal-Ts","X-Internal-Sig"]
    return Response({k: request.headers.get(k) for k in keys})
