# api/views.py
import os
import json
import uuid
from math import radians, cos, sin, asin, sqrt
from datetime import datetime, timezone, timedelta, date

from django.http import JsonResponse, HttpRequest
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt

# DRF / Spectacular
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import serializers
from drf_spectacular.utils import (
    extend_schema,
    OpenApiTypes,
    inline_serializer,
)

# 서비스 계층
from services.anomaly import AnomalyDetector, AnomalyConfig, KST
from services.orm_stats_provider import OrmStatsProvider
from services.policy_service import categories_for_trigger  # DB 우선 + 폴백
from services.weather_gateway import get_weather_gateway    # env 모드에 따라 remote/fallback

# --- 저장용 모델 (있을 때만 import; 없는 경우 주석 처리) -----------------------
from api.models import (
    Content,
    RecommendationSession,
    ExposureCandidate,
    ItemRec,
    Feedback as FeedbackModel,
    Outcome as OutcomeModel,
)

# ──────────────────────────────────────────────────────────────────────────────
# 전역 싱글턴 (상태 유지)
# ──────────────────────────────────────────────────────────────────────────────
_config = AnomalyConfig()
_provider = OrmStatsProvider()
# FIX: AnomalyDetector 시그니처에 맞게 provider로 전달
_detector = AnomalyDetector(config=_config, provider=_provider)

APP_TOKEN = os.getenv("APP_TOKEN", "").strip()

def _assert_app_token(request: HttpRequest):
    """X-App-Token 검사 (Healthz 제외 모든 엔드포인트에서 사용)."""
    got = request.headers.get("X-App-Token", "").strip()
    if not APP_TOKEN or got != APP_TOKEN:
        return Response({"ok": False, "error": "invalid app token"}, status=401)
    return None


# ──────────────────────────────────────────────────────────────────────────────
# 공통 Serializer (문서/검증용)
# ──────────────────────────────────────────────────────────────────────────────
class TelemetryIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField()  # ISO8601 with offset
    metrics = serializers.DictField(child=serializers.FloatField(), allow_empty=False)

class TelemetryOut(serializers.Serializer):
    ok = serializers.BooleanField()
    session_id = serializers.CharField(required=False)
    level = serializers.ChoiceField(choices=["normal","restrict","emergency"])
    trigger = serializers.CharField(required=False)
    categories = serializers.ListField(child=serializers.CharField(), required=False)
    message = serializers.CharField(required=False)

class FeedbackIn(serializers.Serializer):
    session_id = serializers.CharField()
    external_id = serializers.CharField()  # e.g. "sp:trk:xxx" or "yt:vid:xxx"
    action = serializers.ChoiceField(choices=["CLICK","COMPLETE","EFFECT"])
    value = serializers.FloatField(required=False)

class FeedbackOut(serializers.Serializer):
    ok = serializers.BooleanField()

class StepsCheckIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField()
    steps_cum = serializers.IntegerField(min_value=0)

class StepsCheckOut(serializers.Serializer):
    ok = serializers.BooleanField()
    session_id = serializers.CharField(required=False)
    category = serializers.CharField(required=False)
    items = serializers.ListField(child=serializers.DictField(), required=False)

class PlacesIn(serializers.Serializer):
    user_ref = serializers.CharField()
    lat = serializers.FloatField()
    lng = serializers.FloatField()

class PlacesOut(serializers.Serializer):
    ok = serializers.BooleanField()
    session_id = serializers.CharField()
    category = serializers.CharField()
    items = serializers.ListField(child=serializers.DictField())


# ──────────────────────────────────────────────────────────────────────────────
# 헬스 체크 (토큰 요구 X)  — Swagger 상단 Authorize에 안 걸리도록 auth=[]
# ──────────────────────────────────────────────────────────────────────────────
class HealthzView(APIView):
    @extend_schema(
        auth=[],  # 전역 SECURITY 무시 → 토큰 없이 호출 가능
        responses={200: inline_serializer("Healthz", {"ok": serializers.BooleanField(), "version": serializers.CharField()})},
        tags=["health"],
        summary="Health check (no auth)",
    )
    def get(self, request: HttpRequest):
        return Response({"ok": True, "version": "v0.2.1"})


# ──────────────────────────────────────────────────────────────────────────────
# 텔레메트리 업로드 → 즉시 판단
# ──────────────────────────────────────────────────────────────────────────────
class TelemetryView(APIView):
    @extend_schema(
        request=TelemetryIn,
        responses={200: TelemetryOut},
        tags=["telemetry"],
        summary="Telemetry ingest & anomaly decision",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad: return bad

        ser = TelemetryIn(data=request.data)
        ser.is_valid(raise_exception=True)
        payload = ser.validated_data

        result = _detector.handle_telemetry(
            user_ref=payload["user_ref"],
            ts=payload["ts"],
            metrics=payload["metrics"],
        )
        # result 예시: {"level":"normal"|"restrict"|"emergency", "trigger": "stress_up"|"hr_high"|..., "session_id": "..."}
        out = {"ok": True, "level": result["level"]}

        if result["level"] == "restrict":
            # 트리거에 따른 카테고리(정책 DB → 폴백)
            cats = categories_for_trigger(result.get("trigger"))
            out.update({
                "session_id": result.get("session_id"),
                "trigger": result.get("trigger"),
                "categories": cats,
            })
        elif result["level"] == "emergency":
            out.update({
                "session_id": result.get("session_id"),
                "trigger": result.get("trigger"),
                "message": "Emergency condition detected",
            })

        return Response(out)


# ──────────────────────────────────────────────────────────────────────────────
# 피드백 기록 (CLICK/COMPLETE/EFFECT)
# ──────────────────────────────────────────────────────────────────────────────
class FeedbackView(APIView):
    @extend_schema(
        request=FeedbackIn,
        responses={200: FeedbackOut},
        tags=["feedback"],
        summary="Log feedback for a recommendation session",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad: return bad

        ser = FeedbackIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        # 저장 규칙: EFFECT는 Outcome에도 기록
        fb = FeedbackModel.objects.create(
            session_id=d["session_id"],
            external_id=d["external_id"],
            action=d["action"],
            value=d.get("value"),
        )
        if d["action"] == "EFFECT":
            OutcomeModel.objects.create(
                session_id=d["session_id"],
                external_id=d["external_id"],
                effect_value=d.get("value"),
            )
        return Response({"ok": True})


# ──────────────────────────────────────────────────────────────────────────────
# 걸음수 저활동 판단 → steps_low 세션 발급 (v0.2.1 규격)
# ──────────────────────────────────────────────────────────────────────────────
class StepsCheckView(APIView):
    @extend_schema(
        request=StepsCheckIn,
        responses={200: StepsCheckOut},
        tags=["steps"],
        summary="Check cumulative steps at 12/16/20(KST) and issue steps_low session",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad: return bad

        ser = StepsCheckIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        # 실제 기준은 서버 정책/DB 사용. 여기선 최소 동작(예: 2,000 미만이면 저활동)
        steps = d["steps_cum"]
        if steps < 2000:
            sid = str(uuid.uuid4())
            RecommendationSession.objects.create(
                session_id=sid, user_ref=d["user_ref"], category="steps_low"
            )
            return Response({
                "ok": True,
                "session_id": sid,
                "category": "steps_low",
                "items": [{"type": "WALK"}, {"type": "OUTING"}],
            })
        return Response({"ok": True})  # No-op


# ──────────────────────────────────────────────────────────────────────────────
# 나들이 장소 추천 (OUTING) — 날씨/공기질 게이트 + 거리순
# ──────────────────────────────────────────────────────────────────────────────
def _haversine_km(lat1, lon1, lat2, lon2):
    r = 6371.0
    dlat = radians(lat2 - lat1)
    dlon = radians(lon2 - lon1)
    a = sin(dlat/2)**2 + cos(radians(lat1))*cos(radians(lat2))*sin(dlon/2)**2
    c = 2*asin(min(1, sqrt(a)))
    return r * c

class PlacesView(APIView):
    @extend_schema(
        request=PlacesIn,
        responses={200: PlacesOut},
        tags=["places"],
        summary="Recommend outing places guarded by weather/air quality",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad: return bad

        ser = PlacesIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        gw = get_weather_gateway()
        weather_kind, gate = gw.gate(lat=d["lat"], lng=d["lng"])  # ("clear", "OUTDOOR") 등

        # 샘플: Content 테이블에서 place 후보 로드(실제 스키마에 맞게 조정)
        places = list(Content.objects.filter(kind="PLACE").values(
            "id", "title", "lat", "lng", "address", "place_category", "weather_gate"
        ))

        # 게이트 필터 + 거리 계산/정렬
        items = []
        for p in places:
            if gate and p.get("weather_gate") and p["weather_gate"] != gate:
                continue
            dist = _haversine_km(d["lat"], d["lng"], p["lat"], p["lng"])
            items.append({
                "content_id": p["id"],
                "title": p["title"],
                "lat": p["lat"],
                "lng": p["lng"],
                "distance_km": round(dist, 2),
                "rank": 0,  # 아래에서 채움
                "reason": "distance",
                "weather_gate": p.get("weather_gate") or "UNKNOWN",
                "address": p.get("address") or "",
                "place_category": p.get("place_category") or "",
            })
        items.sort(key=lambda x: x["distance_km"])
        for i, it in enumerate(items, start=1):
            it["rank"] = i

        sid = str(uuid.uuid4())
        RecommendationSession.objects.create(
            session_id=sid, user_ref=d["user_ref"], category="OUTING"
        )
        # 노출 기록(옵션)
        ExposureCandidate.objects.bulk_create([
            ExposureCandidate(session_id=sid, content_id=it["content_id"], rank=it["rank"])
            for it in items[:20]
        ])

        return Response({
            "ok": True,
            "session_id": sid,
            "category": "OUTING",
            "items": items[:20],
        })
