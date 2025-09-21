# api/urls.py
from django.urls import path
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework import serializers
from rest_framework.response import Response
from drf_spectacular.utils import extend_schema, inline_serializer

from .views import (
    TelemetryView,
    FeedbackView,
    StepsCheckView,
    PlacesView,     # ✅ 클래스 기반
    RecommendView,  # ✅ 추가
)

# ⬇️ Delivery 전용 뷰(세션 내 N개 반환) — 분리했다면 views_delivery에서 임포트
from .views_delivery import (
    MusicDeliveryView,
    MeditationDeliveryView,
    YogaDeliveryView,
    OutingDeliveryView,
)

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
@permission_classes([AllowAny])
def healthz(request):
    return Response({"status": "ok", "version": "v1"})

urlpatterns = [
    path("telemetry",    TelemetryView.as_view(),   name="telemetry"),
    path("feedback",     FeedbackView.as_view(),    name="feedback"),
    path("steps-check",  StepsCheckView.as_view(),  name="steps-check"),
    path("places",       PlacesView.as_view(),      name="places"),
    path("recommend",    RecommendView.as_view(),   name="recommend"),  # ✅ 추가
    path("healthz",      healthz,                   name="healthz"),

    # ✅ Delivery: 카테고리 GET → 최신 세션의 여러 개 반환 (기본 3개)
    path("delivery/music",       MusicDeliveryView.as_view(),      name="delivery-music"),
    path("delivery/meditation",  MeditationDeliveryView.as_view(), name="delivery-meditation"),
    path("delivery/yoga",        YogaDeliveryView.as_view(),       name="delivery-yoga"),
    path("delivery/outing",      OutingDeliveryView.as_view(),     name="delivery-outing"),
]
