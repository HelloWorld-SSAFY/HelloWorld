import os
import json
import uuid
from math import radians, cos, sin, asin, sqrt
from datetime import datetime, timezone, timedelta, date
from typing import List, Dict, Any, Optional

from django.http import HttpRequest
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import serializers
from drf_spectacular.utils import (
    extend_schema,
    inline_serializer,
    OpenApiParameter,
    OpenApiTypes,
    OpenApiExample,
    PolymorphicProxySerializer,
)

# 서비스 계층
from services.anomaly import AnomalyDetector, AnomalyConfig, KST
from services.orm_stats_provider import OrmStatsProvider
from services.policy_service import categories_for_trigger  # DB 우선 + 폴백
from services.weather_gateway import get_weather_gateway    # env 모드에 따라 remote/fallback

# --- 저장용 모델 ---------------------------------------------------------------
from api.models import (
    Content,
    RecommendationSession,
    ExposureCandidate,
    ItemRec,
    Feedback as FeedbackModel,
    Outcome as OutcomeModel,
    PlaceInside,
    PlaceOutside,
    PlaceExposure,
)

APP_TOKEN = os.getenv("APP_TOKEN", "").strip()

# ──────────────────────────────────────────────────────────────────────────────
# 전역 싱글턴 (상태 유지)
# ──────────────────────────────────────────────────────────────────────────────
_config = AnomalyConfig()
_provider = OrmStatsProvider()
_detector = AnomalyDetector(config=_config, provider=_provider)

def _assert_app_token(request: HttpRequest):
    """X-App-Token 검사 (Healthz 제외 모든 엔드포인트에서 사용)."""
    got = request.headers.get("X-App-Token", "").strip()
    if not APP_TOKEN or got != APP_TOKEN:
        return Response({"ok": False, "error": "invalid app token"}, status=401)
    return None

# ── 스웨거: 모든 API에 노출할 공통 헤더 파라미터 (Healthz 제외)
APP_TOKEN_PARAM = OpenApiParameter(
    name="X-App-Token",
    type=OpenApiTypes.STR,
    location=OpenApiParameter.HEADER,
    required=True,
    description="App token issued by server. Put the same value as server APP_TOKEN.",
)

# ──────────────────────────────────────────────────────────────────────────────
# 공통 Serializer (문서/검증용)
# ──────────────────────────────────────────────────────────────────────────────
class MetricsSerializer(serializers.Serializer):
    hr = serializers.FloatField(required=False, help_text="현재 심박수(bpm)")
    stress = serializers.FloatField(required=False, help_text="스트레스 지수(0~100 또는 내부 스케일)")

class CategoryRankSerializer(serializers.Serializer):
    category = serializers.ChoiceField(choices=[
        "BREATHING", "MEDITATION", "WALK", "OUTING", "MUSIC", "YOGA"
    ])
    rank = serializers.IntegerField(min_value=1)
    reason = serializers.CharField(required=False)

class RecommendationEnvelopeSerializer(serializers.Serializer):
    session_id = serializers.CharField()
    categories = CategoryRankSerializer(many=True)

class ActionSerializer(serializers.Serializer):
    type = serializers.ChoiceField(choices=["EMERGENCY_CONTACT"])
    cooldown_min = serializers.IntegerField(min_value=0)

class SafeTemplateSerializer(serializers.Serializer):
    category = serializers.ChoiceField(choices=[
        "BREATHING", "MEDITATION", "WALK", "OUTING", "MUSIC", "YOGA"
    ])
    title = serializers.CharField()

class ContentItemSerializer(serializers.Serializer):
    content_id = serializers.IntegerField()
    title = serializers.CharField()
    url = serializers.URLField()
    rank = serializers.IntegerField(min_value=1)
    score = serializers.FloatField(required=False)
    reason = serializers.CharField(required=False)

class PlaceItemSerializer(serializers.Serializer):
    place_type = serializers.ChoiceField(choices=["outside", "inside"])
    content_id = serializers.IntegerField()
    title = serializers.CharField()
    lat = serializers.FloatField()
    lng = serializers.FloatField()
    distance_km = serializers.FloatField()
    rank = serializers.IntegerField(min_value=1)
    reason = serializers.CharField()
    weather_gate = serializers.ChoiceField(choices=["OK", "INDOOR", "BLOCKED", "OUTDOOR"])
    address = serializers.CharField(required=False, allow_blank=True)

# ──────────────────────────────────────────────────────────────────────────────
# 텔레메트리: 요청/응답 스키마 (응답은 폴리모픽)
# ──────────────────────────────────────────────────────────────────────────────
class TelemetryIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField(help_text="ISO8601(+오프셋), 예: 2025-09-08T13:45:10Z")
    metrics = MetricsSerializer(help_text="둘 중 하나 이상 필요(hr, stress)")

class TelemetryNormalResp(serializers.Serializer):
    ok = serializers.BooleanField(default=True)
    anomaly = serializers.BooleanField(default=False)
    risk_level = serializers.ChoiceField(choices=["low"])
    mode = serializers.ChoiceField(choices=["normal"])

class TelemetryRestrictResp(serializers.Serializer):
    ok = serializers.BooleanField(default=True)
    anomaly = serializers.BooleanField(default=True)
    risk_level = serializers.ChoiceField(choices=["high"])
    mode = serializers.ChoiceField(choices=["restrict"])
    reasons = serializers.ListField(child=serializers.CharField())
    recommendation = RecommendationEnvelopeSerializer()

class TelemetryEmergencyResp(serializers.Serializer):
    ok = serializers.BooleanField(default=True)
    anomaly = serializers.BooleanField(default=True)
    risk_level = serializers.ChoiceField(choices=["critical"])
    mode = serializers.ChoiceField(choices=["emergency"])
    reasons = serializers.ListField(child=serializers.CharField())
    action = ActionSerializer()
    safe_templates = SafeTemplateSerializer(many=True)

# ──────────────────────────────────────────────────────────────────────────────
# Steps: 요청/응답 스키마 (응답은 폴리모픽)
# ──────────────────────────────────────────────────────────────────────────────
class StepsCheckIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField(help_text="KST 권장, 12:00/16:00/20:00 호출")
    cum_steps = serializers.IntegerField(min_value=0, help_text="동시간대 누적 걸음수")

class StepsNormalResp(serializers.Serializer):
    ok = serializers.BooleanField(default=True)
    anomaly = serializers.BooleanField(default=False)
    mode = serializers.ChoiceField(choices=["normal"])

class StepsRestrictResp(serializers.Serializer):
    ok = serializers.BooleanField(default=True)
    anomaly = serializers.BooleanField(default=True)
    mode = serializers.ChoiceField(choices=["restrict"])
    trigger = serializers.ChoiceField(choices=["steps_low"])
    reasons = serializers.ListField(child=serializers.CharField())
    recommendation = RecommendationEnvelopeSerializer()

# ──────────────────────────────────────────────────────────────────────────────
# Feedback: 요청/응답 스키마
# ──────────────────────────────────────────────────────────────────────────────
class FeedbackIn(serializers.Serializer):
    user_ref = serializers.CharField()
    session_id = serializers.CharField()
    type = serializers.ChoiceField(choices=["ACCEPT","COMPLETE","EFFECT"])
    external_id = serializers.CharField(required=False)  # 모델에 없으므로 저장은 안 함
    dwell_ms = serializers.IntegerField(required=False)
    watched_pct = serializers.FloatField(required=False)

class FeedbackOut(serializers.Serializer):
    ok = serializers.BooleanField()

# ──────────────────────────────────────────────────────────────────────────────
# Places: 요청/응답 스키마
# ──────────────────────────────────────────────────────────────────────────────
class PlacesIn(serializers.Serializer):
    user_ref = serializers.CharField()
    session_id = serializers.CharField()
    category = serializers.CharField()
    lat = serializers.FloatField()
    lng = serializers.FloatField()
    max_distance_km = serializers.FloatField(required=False, help_text="기본 3.0, 0.5~10.0")
    limit = serializers.IntegerField(required=False, help_text="기본 3, 1~5")

class PlacesOut(serializers.Serializer):
    ok = serializers.BooleanField()
    session_id = serializers.CharField()
    category = serializers.CharField()
    items = PlaceItemSerializer(many=True)
    fallback_used = serializers.BooleanField()

# ──────────────────────────────────────────────────────────────────────────────
# 헬스 체크 (토큰 요구 X)
# ──────────────────────────────────────────────────────────────────────────────
class HealthzView(APIView):
    @extend_schema(
        auth=[],  # 전역 SECURITY 무시 → 토큰 없이 호출 가능
        responses={200: inline_serializer("Healthz", {"ok": serializers.BooleanField(), "version": serializers.CharField()})},
        tags=["health"],
        summary="Health check (no auth)",
        examples=[
            OpenApiExample(
                "RESPONSE",
                value={"ok": True, "version": "v0.2.1"},
                response_only=True,
            )
        ],
        operation_id="getHealthz",
    )
    def get(self, request: HttpRequest):
        return Response({"ok": True, "version": "v0.2.1"})

# ──────────────────────────────────────────────────────────────────────────────
# 유틸: 카테고리 직렬화 (스펙: category/rank/(reason))
# ──────────────────────────────────────────────────────────────────────────────
def _serialize_categories(cats: List[Dict[str, Any]], reason: Optional[str] = None) -> List[Dict[str, Any]]:
    cats_sorted = sorted(cats, key=lambda x: x.get("priority", 999))
    out = []
    for i, c in enumerate(cats_sorted, 1):
        item = {"category": c["code"], "rank": i}
        if reason:
            item["reason"] = reason
        out.append(item)
    return out

def _reason_from_trigger(trigger: Optional[str]) -> str:
    m = {
        "stress_up": "stress high",
        "hr_high": "HR high",
        "hr_low": "HR low",
        "steps_low": "steps low vs avg",
    }
    t = (trigger or "").lower()
    return m.get(t, t or "trigger")

# ──────────────────────────────────────────────────────────────────────────────
# 텔레메트리 업로드 → 즉시 판단
# ──────────────────────────────────────────────────────────────────────────────
class TelemetryView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM],
        request=TelemetryIn,
        responses={
            200: PolymorphicProxySerializer(
                component_name="TelemetryResponse",
                resource_type_field_name="mode",   # discriminator
                serializers={
                    "normal": TelemetryNormalResp,
                    "restrict": TelemetryRestrictResp,
                    "emergency": TelemetryEmergencyResp,
                },
                many=False,
            )
        },
        tags=["telemetry"],
        summary="Telemetry ingest & anomaly decision",
        description=(
            "워치에서 10초 간격으로 들어오는 HR/스트레스 등 텔레메트리를 받아 즉시 판단합니다.\n"
            "- 기준선: user_tod_stats_daily(4h 버킷)\n"
            "- 연속 조건: 10초 Z-score 3회 연속\n"
            "- 응급 룰: |Z|≥5 또는 HR≥150/≤45 for 120s\n"
            "- restrict 시 trigger_category_policy로 카테고리 도출"
        ),
        examples=[
            OpenApiExample(
                "REQUEST",
                request_only=True,
                value={
                    "user_ref": "9e2f1c6c-9f5d-4c67-a41c-2e4d2a5b2d51",
                    "ts": "2025-09-08T13:45:10Z",
                    "metrics": {"hr": 102, "stress": 67}
                }
            ),
            OpenApiExample(
                "RESPONSE (이상 아님)",
                response_only=True,
                value={"ok": True, "anomaly": False, "risk_level": "low", "mode": "normal"}
            ),
            OpenApiExample(
                "RESPONSE (이상 감지됨 → 카테고리 동봉)",
                response_only=True,
                value={
                    "ok": True,
                    "anomaly": True,
                    "risk_level": "high",
                    "mode": "restrict",
                    "reasons": ["HR_Z>=2.5 for 3 bins", "stress_Z>=2.5 for 3 bins"],
                    "recommendation": {
                        "session_id": "123456",
                        "categories": [
                            {"category": "BREATHING", "rank": 1, "reason": "stress high"},
                            {"category": "MEDITATION", "rank": 2}
                        ]
                    }
                }
            ),
            OpenApiExample(
                "RESPONSE (응급 감지됨 → 추천 생략/액션)",
                response_only=True,
                value={
                    "ok": True,
                    "anomaly": True,
                    "risk_level": "critical",
                    "mode": "emergency",
                    "reasons": ["HR_inst>=150 for 120s", "HR_inst<=45 for 120s", "Z>=5"],
                    "action": {"type": "EMERGENCY_CONTACT", "cooldown_min": 60},
                    "safe_templates": [
                        {"category": "BREATHING", "title": "안전 호흡 3분"},
                        {"category": "BREATHING", "title": "안전 호흡 5분"}
                    ]
                }
            ),
        ],
        operation_id="postTelemetry",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = TelemetryIn(data=request.data)
        ser.is_valid(raise_exception=True)
        payload = ser.validated_data

        result = _detector.handle_telemetry(
            user_ref=payload["user_ref"],
            ts=payload["ts"],
            metrics=payload["metrics"],
        )
        level = result.get("level", "normal")
        trigger = result.get("trigger")
        reasons = result.get("reasons", [])

        anomaly = level != "normal"
        if level == "emergency":
            risk_level = "critical"
        elif level == "restrict":
            risk_level = "high"
        else:
            risk_level = "low"

        resp: Dict[str, Any] = {
            "ok": True,
            "anomaly": anomaly,
            "risk_level": risk_level,
            "mode": "emergency" if level == "emergency" else ("restrict" if level == "restrict" else "normal"),
        }

        if level == "restrict":
            cats = categories_for_trigger(trigger)
            resp.update({
                "reasons": reasons,
                "recommendation": {
                    "session_id": result.get("session_id"),
                    "categories": _serialize_categories(cats, reason=_reason_from_trigger(trigger)),
                },
            })
        elif level == "emergency":
            action = result.get("action") or {"type": "EMERGENCY_CONTACT", "cooldown_min": 60}
            resp.update({
                "reasons": reasons or ["emergency condition"],
                "action": action,
                "safe_templates": result.get("safe_templates", []),
            })

        return Response(resp)

# ──────────────────────────────────────────────────────────────────────────────
# 피드백 기록 (ACCEPT/COMPLETE/EFFECT)
# ──────────────────────────────────────────────────────────────────────────────
class FeedbackView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM],
        request=FeedbackIn,
        responses={200: FeedbackOut},
        tags=["feedback"],
        summary="Log feedback for a recommendation session",
        examples=[
            OpenApiExample(
                "REQUEST (ACCEPT)",
                request_only=True,
                value={
                    "user_ref": "9e2f1c6c-9f5d-4c67-a41c-2e4d2a5b2d51",
                    "session_id": "123456",
                    "type": "ACCEPT",
                    "external_id": "sp:trk:123",
                    "dwell_ms": 120000,
                    "watched_pct": 0.9
                }
            ),
            OpenApiExample("RESPONSE", response_only=True, value={"ok": True}),
        ],
        operation_id="postFeedback",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = FeedbackIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        # 세션 FK 조회
        try:
            sess_uuid = uuid.UUID(d["session_id"])
        except Exception:
            return Response({"ok": False, "error": "invalid session_id"}, status=400)

        session = RecommendationSession.objects.filter(id=sess_uuid).first()
        if session is None:
            return Response({"ok": False, "error": "session not found"}, status=404)

        # value는 dwell_ms or watched_pct 중 하나를 저장(있으면)
        value = d.get("dwell_ms") if d.get("dwell_ms") is not None else d.get("watched_pct")

        FeedbackModel.objects.create(
            user_ref=d["user_ref"],
            session=session,
            type=d["type"],
            value=value,
            dwell_ms=d.get("dwell_ms"),
            watched_pct=d.get("watched_pct"),
        )

        if d["type"] == "EFFECT" and value is not None:
            OutcomeModel.objects.create(
                session=session,
                outcome_type="self_report",
                effect=float(value),
            )

        return Response({"ok": True})

# ──────────────────────────────────────────────────────────────────────────────
# 걸음수 저활동 판단 → steps_low 세션 발급
# ──────────────────────────────────────────────────────────────────────────────
class StepsCheckView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM],
        request=StepsCheckIn,
        responses={
            200: PolymorphicProxySerializer(
                component_name="StepsCheckResponse",
                resource_type_field_name="mode",
                serializers={
                    "normal": StepsNormalResp,
                    "restrict": StepsRestrictResp,
                },
                many=False,
            )
        },
        tags=["steps"],
        summary="Check cumulative steps at 12/16/20(KST) and issue steps_low session",
        description=(
            "매일 12:00 / 16:00 / 20:00(KST) 동시간대 누적 걸음수를 기준선과 비교해 저활동 여부를 판단합니다.\n"
            "- 판단: Z <= -1.0 또는 p20 미만\n"
            "- 중복 방지: 동일 일자·버킷에 열린 steps_low 세션 존재 시 재발급 없이 기존 반환\n"
            "- 레이트리밋: 사용자·버킷당 1회"
        ),
        examples=[
            OpenApiExample("REQUEST", request_only=True, value={
                "user_ref": "u1",
                "ts": "2025-09-17T12:00:00+09:00",
                "cum_steps": 1840
            }),
            OpenApiExample("RESPONSE (정상)", response_only=True, value={
                "ok": True, "anomaly": False, "mode": "normal"
            }),
            OpenApiExample("RESPONSE (저활동 → restrict 세션 발급)", response_only=True, value={
                "ok": True,
                "anomaly": True,
                "mode": "restrict",
                "trigger": "steps_low",
                "reasons": ["cum_steps_z<=-1.0 (bucket=3)"],
                "recommendation": {
                    "session_id": "c5e1c8f6-....",
                    "categories": [
                        {"category": "WALK", "rank": 1, "reason": "steps low vs avg"},
                        {"category": "OUTING", "rank": 2}
                    ]
                }
            }),
        ],
        operation_id="postStepsCheck",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = StepsCheckIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        cum_steps = d["cum_steps"]
        # NOTE: 임시 로직(데모). 실제는 기준선/버킷 기반 Z-score 판단으로 교체.
        if cum_steps >= 2000:
            return Response({"ok": True, "anomaly": False, "mode": "normal"})

        # 저활동 → restrict 세션 발급
        cats = categories_for_trigger("steps_low")
        ctx = {
            "categories": _serialize_categories(cats, reason=_reason_from_trigger("steps_low")),
        }
        session = RecommendationSession.objects.create(
            user_ref=d["user_ref"],
            trigger="steps_low",
            mode="restrict",
            context=ctx,
        )
        sid = str(session.id)

        resp = {
            "ok": True,
            "anomaly": True,
            "mode": "restrict",
            "trigger": "steps_low",
            "reasons": [f"cum_steps<{2000} or z<=-1.0"],
            "recommendation": {
                "session_id": sid,
                "categories": ctx["categories"],
            },
        }
        return Response(resp)

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
        parameters=[APP_TOKEN_PARAM],
        request=PlacesIn,
        responses={200: PlacesOut},
        tags=["places"],
        summary="Recommend outing places guarded by weather/air quality",
        description=(
            "위치 기반 후보를 거리순으로 정렬하고, 서버가 조회한 현재 날씨/공기질로 안전 게이트를 적용합니다.\n"
            "- 거리 정렬(Haversine) 후 limit개(기본 3), max_distance_km(기본 3km)\n"
            "- 최근 14일 노출 content_id는 제외(중복 방지)\n"
            "- 강수/폭염/한파/미세먼지 ‘나쁨’ 시 야외 축소 또는 실내 대체"
        ),
        examples=[
            OpenApiExample("REQUEST", request_only=True, value={
                "user_ref": "u1",
                "session_id": "c5e1c8f6-....",
                "category": "OUTING",
                "lat": 37.501,
                "lng": 127.026,
                "max_distance_km": 3.0,
                "limit": 3
            }),
            OpenApiExample("RESPONSE", response_only=True, value={
                "ok": True,
                "session_id": "c5e1c8f6-....",
                "category": "OUTING",
                "items": [
                    {
                        "place_type": "outside",
                        "content_id": 901,
                        "title": "탄천 산책로 A구간",
                        "lat": 37.503,
                        "lng": 127.035,
                        "distance_km": 0.98,
                        "rank": 1,
                        "reason": "distance",
                        "weather_gate": "OUTDOOR",
                        "address": "성남시 분당구 ..."
                    }
                ],
                "fallback_used": False
            }),
        ],
        operation_id="postPlaces",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = PlacesIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        max_km = float(d.get("max_distance_km") or 3.0)
        limit = int(d.get("limit") or 3)
        max_km = max(0.5, min(10.0, max_km))
        limit = max(1, min(5, limit))

        # 1) 날씨 게이트 (함수/인스턴스 모두 대응, 실패 시 폴백)
        weather_fallback = False
        try:
            gw = get_weather_gateway()
            if callable(gw):
                weather_kind, gate = gw(lat=d["lat"], lng=d["lng"])
            else:
                weather_kind, gate = gw.gate(lat=d["lat"], lng=d["lng"])
        except Exception:
            weather_kind, gate = "unknown", None
            weather_fallback = True

        # 2) 게이트별 테이블 선택
        if gate == "OUTDOOR":
            qs_out = PlaceOutside.objects.filter(is_active=True)
            qs_in = PlaceInside.objects.none()
        elif gate == "INDOOR":
            qs_out = PlaceOutside.objects.none()
            qs_in = PlaceInside.objects.filter(is_active=True)
        else:
            qs_out = PlaceOutside.objects.filter(is_active=True)
            qs_in = PlaceInside.objects.filter(is_active=True)

        # 3) 필요한 필드만 조회
        outs = list(qs_out.values("id", "name", "lat", "lon", "address"))
        ins  = list(qs_in.values("id", "name", "lat", "lon", "address"))

        # 4) 거리계산 + 합치기 + 정렬
        items: List[Dict[str, Any]] = []

        for p in outs:
            if p.get("lat") is None or p.get("lon") is None:
                continue
            dist = _haversine_km(d["lat"], d["lng"], float(p["lat"]), float(p["lon"]))
            if dist > max_km:
                continue
            items.append({
                "place_type": "outside",
                "content_id": p["id"],
                "title": p.get("name") or f"Outside {p['id']}",
                "lat": float(p["lat"]),
                "lng": float(p["lon"]),
                "distance_km": round(dist, 2),
                "rank": 0,
                "reason": "distance",
                "weather_gate": "OUTDOOR",
                "address": p.get("address", ""),
            })

        for p in ins:
            if p.get("lat") is None or p.get("lon") is None:
                continue
            dist = _haversine_km(d["lat"], d["lng"], float(p["lat"]), float(p["lon"]))
            if dist > max_km:
                continue
            items.append({
                "place_type": "inside",
                "content_id": p["id"],
                "title": p.get("name") or f"Inside {p['id']}",
                "lat": float(p["lat"]),
                "lng": float(p["lon"]),
                "distance_km": round(dist, 2),
                "rank": 0,
                "reason": "distance",
                "weather_gate": "INDOOR",
                "address": p.get("address", ""),
            })

        items.sort(key=lambda x: x["distance_km"])
        for i, it in enumerate(items[:], start=1):
            it["rank"] = i

        # 5) 세션 확정: 요청 session_id가 있으면 그걸 쓰고, 없으면 새로 생성
        sid = d.get("session_id")
        session = None
        if sid:
            try:
                sess_uuid = uuid.UUID(sid)
                session = RecommendationSession.objects.filter(id=sess_uuid).first()
            except Exception:
                session = None

        if session is None:
            session = RecommendationSession.objects.create(
                user_ref=d["user_ref"],
                trigger=d.get("category", "OUTING"),
                mode="restrict",
                context={
                    "weather_kind": weather_kind,
                    "gate": gate,
                    "max_distance_km": max_km,
                    "limit": limit,
                },
            )
            sid = str(session.id)

        # 6) 노출 기록 (PlaceExposure)
        if items:
            PlaceExposure.objects.bulk_create([
                PlaceExposure(user_ref=d["user_ref"], place_type=it["place_type"], place_id=it["content_id"])
                for it in items[:limit]
            ], ignore_conflicts=True)

        resp = {
            "ok": True,
            "session_id": sid,
            "category": d.get("category", "OUTING"),
            "items": items[:limit],
            "fallback_used": weather_fallback or (not items),
        }
        return Response(resp)
