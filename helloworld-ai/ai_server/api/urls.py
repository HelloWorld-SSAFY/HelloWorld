# api/urls.py
from django.urls import path
from rest_framework.decorators import api_view
from rest_framework.response import Response
from drf_spectacular.utils import extend_schema, OpenApiResponse

from .views import (
    TelemetryView,
    FeedbackView,
    StepsCheckView,
    PlacesView,   # ✅ 클래스 기반으로 변경
)

@extend_schema(
    tags=["health"],
    description="Liveness/Readiness probe",
    auth=[],  # ✅ 상단 Authorize 없이 호출 가능
    responses={200: OpenApiResponse(response=dict, description="OK")},
)
@api_view(["GET"])
def healthz(request):
    return Response({"status": "ok", "version": "v1"})

urlpatterns = [
    path("telemetry",    TelemetryView.as_view(),   name="telemetry"),
    path("feedback",     FeedbackView.as_view(),    name="feedback"),
    path("steps-check",  StepsCheckView.as_view(),  name="steps-check"),
    path("places",       PlacesView.as_view(),      name="places"),   # ✅ 수정
    path("healthz",      healthz,                   name="healthz"),
]
