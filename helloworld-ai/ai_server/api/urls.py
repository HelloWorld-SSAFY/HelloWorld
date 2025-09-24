# api/urls.py
from django.urls import path
from rest_framework.decorators import api_view, permission_classes, authentication_classes
from rest_framework.permissions import AllowAny
from rest_framework import serializers
from rest_framework.response import Response
from drf_spectacular.utils import extend_schema, inline_serializer
from .views_debug import echo_headers
from .views_debug import whoami

from .views import (
    TelemetryView,
    FeedbackView,
    PlacesView,     # ✅ 클래스 기반
    RecommendView,  # ✅ 추가
)



# ✅ StepsCheckView는 새 모듈에서 import
from .views_steps_check import StepsCheckView

# ⬇️ Delivery 전용 뷰(세션 내 N개 반환)
from .views_delivery import (
    MusicDeliveryView,
    MeditationDeliveryView,
    YogaDeliveryView,
    OutingDeliveryView,
)

# ⬇️ 메인 서버 브릿지(메인 API 호출/저장)
from .views_main_bridge import (
    MainEchoView,
    PullStepsBaselineView,

)

# from .views_main_bridge import IngestDailyBucketsView 

# 선택: reverse 네임스페이스용
app_name = "api"


@extend_schema(
    tags=["health"],
    summary="Health check (no auth)",
    description="Liveness/Readiness probe",
    auth=[],  # ✅ 전역 SECURITY 무시 → 토큰 없이 가능
    responses={
        200: inline_serializer(
            name="Healthz",
            fields={
                "status": serializers.CharField(help_text="ok"),
                "version": serializers.CharField(help_text="API version"),
            },
        )
    },
    operation_id="getHealthz",
)
@api_view(["GET"])
@authentication_classes([])       # ✅ 전역 JWT 인증 우회
@permission_classes([AllowAny])   # ✅ 누구나 접근 허용
def healthz(request):
    return Response({"status": "ok", "version": "v0.2.1"})


urlpatterns = [
    # ---- core ----
    path("telemetry",    TelemetryView.as_view(),   name="telemetry"),
    path("feedback",     FeedbackView.as_view(),    name="feedback"),
    path("steps-check",  StepsCheckView.as_view(),  name="steps-check"),
    path("places",       PlacesView.as_view(),      name="places"), 
    path("recommend",    RecommendView.as_view(),   name="recommend"),
    path("healthz",      healthz,                   name="healthz"),

    # ---- delivery ----
    path("delivery/music",       MusicDeliveryView.as_view(),      name="delivery-music"),
    path("delivery/meditation",  MeditationDeliveryView.as_view(), name="delivery-meditation"),
    path("delivery/yoga",        YogaDeliveryView.as_view(),       name="delivery-yoga"),
    path("delivery/outing",      OutingDeliveryView.as_view(),     name="delivery-outing"),

    # ---- main-bridge (메인 서버 호출/저장) ----
    path("_main/echo",           MainEchoView.as_view(),           name="main-echo"),
    path("steps-baseline/pull",  PullStepsBaselineView.as_view(),  name="steps-baseline-pull"),
    # path("main/ingest-daily-buckets", IngestDailyBucketsView.as_view()),
    path("debug/headers", echo_headers),
    path("debug/whoami", whoami),
]
