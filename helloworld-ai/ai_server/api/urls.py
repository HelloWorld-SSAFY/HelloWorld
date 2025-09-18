from django.urls import path
from .views import TelemetryView, FeedbackView, StepsCheckView, places
from django.http import JsonResponse

def healthz(request):
    return JsonResponse({"status": "ok", "version": "v1"})

urlpatterns = [
    path("telemetry", TelemetryView.as_view()),
    path("feedback",  FeedbackView.as_view()),
    path("healthz",   healthz),
    path("steps-check", StepsCheckView.as_view()),
    path("places", places),

]
