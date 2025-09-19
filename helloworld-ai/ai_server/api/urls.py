# api/urls.py (URL은 그대로 둬도 됨. 원하면 trailing slash와 name을 붙여도 좋아요.)
from django.urls import path
from .views import TelemetryView, FeedbackView, StepsCheckView, places
from django.http import JsonResponse

from rest_framework.decorators import api_view
from rest_framework.response import Response
from drf_spectacular.utils import extend_schema, OpenApiResponse

@extend_schema(
    tags=["health"],
    description="Liveness/Readiness probe",
    responses={200: OpenApiResponse(response=dict, description="OK")},
)
@api_view(["GET"])
def healthz(request):
    # DRF Response를 쓰면 스키마 추론이 더 잘 됩니다.
    return Response({"status": "ok", "version": "v1"})

urlpatterns = [
    path("telemetry", TelemetryView.as_view(), name="telemetry"),      # 그대로 OK
    path("feedback",  FeedbackView.as_view(),  name="feedback"),       # 그대로 OK
    path("healthz",   healthz,                  name="healthz"),       # 그대로 OK
    path("steps-check", StepsCheckView.as_view(), name="steps-check"), # 그대로 OK
    path("places", places, name="places"),                             # 함수형이면 아래처럼 스키마를 달아주세요
]
