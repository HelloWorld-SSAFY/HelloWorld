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
class TelemetryIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField()  # ISO8601 with offset
    metrics = serializers.DictField(child=serializers.FloatField(), allow_empty=False)

class TelemetryOut(serializers.Serializer):
    ok = serializers.BooleanField()
    anomaly = serializers.BooleanField()
    risk_level = serializers.ChoiceField(choices=["low","medium","high","critical"])
    mode = serializers.ChoiceField(choices=["normal","restrict","emergency"])
    reasons = serializers.ListField(child=serializers.CharField(), required=False)
    recommendation = serializers.DictField(required=False)
    action = serializers.DictField(required=False)
    safe_templates = serializers.ListField(child=serializers.DictField(), required=False)

class FeedbackIn(serializers.Serializer):
    user_ref = serializers.CharField()
    session_id = serializers.CharField()
    type = serializers.ChoiceField(choices=["ACCEPT","COMPLETE","EFFECT"])
    external_id = serializers.CharField(required=False)  # 모델에 없으므로 저장은 안 함
    dwell_ms = serializers.IntegerField(required=False)
    watched_pct = serializers.FloatField(required=False)

class FeedbackOut(serializers.Serializer):
    ok = serializers.BooleanField()

class StepsCheckIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField()
    cum_steps = serializers.IntegerField(min_value=0)  # 스펙: "cum_steps"

class StepsCheckOut(serializers.Serializer):
    ok = serializers.BooleanField()
    anomaly = serializers.BooleanField(required=False)
    mode = serializers.ChoiceField(choices=["normal","restrict"], required=False)
    trigger = serializers.CharField(required=False)
    reasons = serializers.ListField(child=serializers.CharField(), required=False)
    recommendation = serializers.DictField(required=False)

class PlacesIn(serializers.Serializer):
    user_ref = serializers.CharField()
    session_id = serializers.CharField()
    category = serializers.CharField()
    lat = serializers.FloatField()
    lng = serializers.FloatField()
    max_distance_km = serializers.FloatField(required=False)
    limit = serializers.IntegerField(required=False)

class PlacesOut(serializers.Serializer):
    ok = serializers.BooleanField()
    session_id = serializers.CharField()
    category = serializers.CharField()
    items = serializers.ListField(child=serializers.DictField())
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
        responses={200: TelemetryOut},
        tags=["telemetry"],
        summary="Telemetry ingest & anomaly decision",
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
        responses={200: StepsCheckOut},
        tags=["steps"],
        summary="Check cumulative steps at 12/16/20(KST) and issue steps_low session",
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

        # 2) 게이트별 테이블 선택 (OUTDOOR → Outside / INDOOR → Inside / else 둘 다)
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
