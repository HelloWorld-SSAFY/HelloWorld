# api/urls.py
from django.urls import path
from .views import healthz, telemetry, feedback

urlpatterns = [
    path("healthz", healthz, name="healthz"),
    path("telemetry", telemetry, name="telemetry"),
    path("feedback", feedback, name="feedback"),
]
