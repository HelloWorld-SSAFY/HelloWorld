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
    # 개인화 통계 (개인화/TS 학습용)
    ContentStat,
    UserContentStat,
)

APP_TOKEN = os.getenv("APP_TOKEN", "").strip()

from services.youtube_ingest import ingest_youtube_to_session
from services.spotify_ingest import ingest_spotify_to_session
from services.recommender import recommend_on_session, RecInput


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

def _now_kst() -> datetime:
    return datetime.now(tz=KST)

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
    thumbnail = serializers.URLField(required=False, allow_blank=True)  # ✓ 썸네일 필드
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
#  - 요구사항대로 user_ref/ts/metrics만 받음
# ──────────────────────────────────────────────────────────────────────────────
class TelemetryIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField(help_text="ISO8601(+오프셋), 예: 2025-09-08T13:45:10+09:00")
    metrics = MetricsSerializer(help_text="둘 중 하나 이상 필요(hr, stress)")

class CooldownSerializer(serializers.Serializer):
    active = serializers.BooleanField()
    ends_at = serializers.DateTimeField()
    secs_left = serializers.IntegerField(min_value=0)

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
    cooldown = CooldownSerializer(required=False)  # optional
    new_session = serializers.BooleanField(required=False)
    cooldown_min = serializers.IntegerField(required=False)    # 하위호환

class TelemetryEmergencyResp(serializers.Serializer):
    ok = serializers.BooleanField(default=True)
    anomaly = serializers.BooleanField(default=True)
    risk_level = serializers.ChoiceField(choices=["critical"])
    mode = serializers.ChoiceField(choices=["emergency"])
    reasons = serializers.ListField(child=serializers.CharField())
    action = ActionSerializer()
    safe_templates = SafeTemplateSerializer(many=True)

# 신규: cooldown 응답 스키마(세션 생성 안 함)
class TelemetryCooldownResp(serializers.Serializer):
    ok = serializers.BooleanField(default=True)
    anomaly = serializers.BooleanField(default=True)
    risk_level = serializers.ChoiceField(choices=["high", "critical"])
    mode = serializers.ChoiceField(choices=["cooldown"])
    source = serializers.ChoiceField(choices=["restrict", "emergency"])
    cooldown = CooldownSerializer()

# ──────────────────────────────────────────────────────────────────────────────
# Steps: 요청/응답 스키마 (응답은 폴리모픽)
# ──────────────────────────────────────────────────────────────────────────────
class StepsCheckIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField(help_text="KST 권장, 12:00/16:00/20:00 호출")
    cum_steps = serializers.IntegerField(min_value=0, help_text="동시간대 누적 걸음수")
    ctx = serializers.JSONField(required=False, help_text="유저 컨텍스트(선택)")

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
    external_id = serializers.CharField(required=False)
    dwell_ms = serializers.IntegerField(required=False)
    watched_pct = serializers.FloatField(required=False)
    content_id = serializers.IntegerField(required=False)  # ✓ 어떤 컨텐츠에 대한 피드백인지 명시 가능

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
    ctx = serializers.JSONField(required=False, help_text="유저 컨텍스트(선택)")

class PlacesOut(serializers.Serializer):
    ok = serializers.BooleanField()
    session_id = serializers.CharField()
    category = serializers.CharField()
    items = PlaceItemSerializer(many=True)
    fallback_used = serializers.BooleanField()

# ──────────────────────────────────────────────────────────────────────────────
# 헬스 체크 (토큰 요구 O) — 하위호환상 auth=[]로 토큰 없이 호출 허용
# ──────────────────────────────────────────────────────────────────────────────
class HealthzView(APIView):
    @extend_schema(
        auth=[],  # 전역 SECURITY 무시 → 토큰 없이 호출 가능
        responses={200: inline_serializer("Healthz", {"ok": serializers.BooleanField(), "version": serializers.CharField()})},
        tags=["health"],
        summary="Health check (no auth)",
        examples=[OpenApiExample("RESPONSE", value={"ok": True, "version": "v0.2.1"}, response_only=True)],
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
# 세션 보장/컨텍스트 저장 유틸
# ──────────────────────────────────────────────────────────────────────────────
def _ensure_restrict_session(
    *,
    user_ref: str,
    trigger: Optional[str],
    cats: List[Dict[str, Any]],
    detector_result: Dict[str, Any],
    user_ctx: Optional[Dict[str, Any]] = None,
) -> tuple[RecommendationSession, Dict[str, Any], bool]:
    """
    - detector_result에 session_id가 있으면 조회, 없으면 새로 생성
    - context에 session_id / categories / trigger + (user_ctx) 저장
    - recommend 응답용 envelope 반환
    - return: (session, recommendation_envelope, new_session_flag)
    """
    cat_payload = _serialize_categories(cats, reason=_reason_from_trigger(trigger)) if cats else []

    sess_uuid = None
    sid_raw = detector_result.get("session_id")
    if sid_raw:
        try:
            sess_uuid = uuid.UUID(str(sid_raw))
        except Exception:
            sess_uuid = None

    session: Optional[RecommendationSession] = None
    if sess_uuid:
        session = RecommendationSession.objects.filter(id=sess_uuid).first()

    new_session = False
    if session is None:
        session = RecommendationSession.objects.create(
            user_ref=user_ref,
            trigger=trigger or "",
            mode="restrict",
            context={},
        )
        new_session = True

    ctx = {
        "session_id": str(session.id),
        "categories": cat_payload,
        "trigger": trigger,
    }
    if user_ctx:
        ctx["user_ctx"] = user_ctx

    cd_min = detector_result.get("cooldown_min")
    if cd_min is not None:
        try:
            ctx["cooldown_min"] = int(cd_min)
        except Exception:
            pass

    try:
        session.set_context(ctx, save=True)
    except Exception:
        try:
            session.update_context(ctx, save=True)
        except Exception:
            session.context = ctx
            session.save(update_fields=["context"])

    recommendation = {"session_id": str(session.id), "categories": cat_payload}
    return session, recommendation, new_session

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
                    "cooldown": TelemetryCooldownResp,  # 신규 모드 문서화
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
        reasons = result.get("reasons", []) or result.get("reason", [])

        if level == "emergency":
            risk_level = "critical"
        elif level == "restrict":
            risk_level = "high"
        elif level == "cooldown":
            risk_level = "critical" if (result.get("cooldown_source") == "emergency") else "high"
        else:
            risk_level = "low"

        # 공통 베이스
        resp: Dict[str, Any] = {
            "ok": True,
            "anomaly": level != "normal",
            "risk_level": risk_level,
            "mode": level,   # 그대로 노출 (cooldown 포함)
        }

        if level == "restrict":
            # (1) 트리거 기반 카테고리
            cats = categories_for_trigger(trigger) or []
            if not cats:
                met = payload.get("metrics", {}) or {}
                fallback = []
                if "stress" in met:
                    fallback = [{"code": "BREATHING"}, {"code": "MEDITATION"}, {"code": "MUSIC"}]
                elif "hr" in met:
                    fallback = [{"code": "BREATHING"}, {"code": "YOGA"}]
                cats = fallback

            # (2) 세션 보장 (텔레메트리는 유저 컨텍스트 사용 안 함)
            session, recommendation, new_session = _ensure_restrict_session(
                user_ref=payload["user_ref"],
                trigger=trigger,
                cats=cats,
                detector_result=result,
                user_ctx=None,
            )

            # (3) 쿨다운 정보
            cooldown_min = result.get("cooldown_min")
            cooldown_ends_at = result.get("cooldown_until") or result.get("cooldown_ends_at")
            if isinstance(cooldown_ends_at, str):
                try:
                    cooldown_ends_at = datetime.fromisoformat(cooldown_ends_at)
                except Exception:
                    cooldown_ends_at = None
            if not cooldown_ends_at and cooldown_min is not None:
                cooldown_ends_at = (payload["ts"].astimezone(KST) if payload["ts"].tzinfo else payload["ts"]).astimezone(KST) + timedelta(minutes=int(cooldown_min))

            cooldown_obj = None
            if cooldown_ends_at:
                now = _now_kst()
                secs_left = int(max(0, (cooldown_ends_at - now).total_seconds()))
                cooldown_obj = {"active": secs_left > 0, "ends_at": cooldown_ends_at.isoformat(), "secs_left": secs_left}

            # (4) 응답
            resp.update({"reasons": reasons or [], "recommendation": recommendation, "new_session": new_session})
            if cooldown_obj:
                resp["cooldown"] = cooldown_obj
            if result.get("cooldown_min") is not None:
                resp["cooldown_min"] = int(result["cooldown_min"])

        elif level == "cooldown":
            # 세션 생성 금지, 쿨다운 정보만
            ends = result.get("cooldown_until")
            if isinstance(ends, str):
                try:
                    ends = datetime.fromisoformat(ends)
                except Exception:
                    ends = None
            if ends:
                now = _now_kst()
                secs_left = int(max(0, (ends - now).total_seconds()))
                resp.update({
                    "source": result.get("cooldown_source") or "restrict",
                    "cooldown": {"active": secs_left > 0, "ends_at": ends.isoformat(), "secs_left": secs_left},
                })

        elif level == "emergency":
            action = result.get("action") or {"type": "EMERGENCY_CONTACT", "cooldown_min": 60}
            resp.update({"reasons": reasons or ["emergency condition"], "action": action, "safe_templates": result.get("safe_templates", [])})

        # normal이면 resp 그대로
        return Response(resp)

# ──────────────────────────────────────────────────────────────────────────────
# 피드백 기록 (ACCEPT/COMPLETE/EFFECT) + 통계 업데이트(개인화 학습)
# ──────────────────────────────────────────────────────────────────────────────
class FeedbackView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM],
        request=FeedbackIn,
        responses={200: FeedbackOut},
        tags=["feedback"],
        summary="Log feedback for a recommendation session",
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

        # 타게팅: content_id 명시 우선, 없으면 세션 최근 ItemRec
        content = None
        item_rec = None

        cid = d.get("content_id")
        if cid:
            content = Content.objects.filter(id=int(cid)).first()

        if content is None:
            item_rec = ItemRec.objects.filter(session=session).order_by("-created_at").first()
            if item_rec:
                content = item_rec.content

        if item_rec is None and content is not None:
            item_rec = ItemRec.objects.filter(session=session, content=content).order_by("-created_at").first()

        # value: dwell_ms or watched_pct
        value = d.get("dwell_ms") if d.get("dwell_ms") is not None else d.get("watched_pct")

        # 로그 저장
        FeedbackModel.objects.create(
            user_ref=d["user_ref"],
            session=session,
            item_rec=item_rec,
            content=content,
            type=d["type"],
            value=value,
            dwell_ms=d.get("dwell_ms"),
            watched_pct=d.get("watched_pct"),
        )

        # 통계 업데이트(개인화 학습용)
        try:
            if content:
                cs, _ = ContentStat.objects.get_or_create(content=content)
                ucs, _ = UserContentStat.objects.get_or_create(user_ref=d["user_ref"], content=content)

                if d["type"] == "ACCEPT":
                    cs.accepts += 1; ucs.accepts += 1
                elif d["type"] == "COMPLETE":
                    cs.completes += 1; ucs.completes += 1
                elif d["type"] == "EFFECT" and value is not None:
                    cs.effects_sum += float(value or 0); ucs.effects_sum += float(value or 0)

                cs.save(update_fields=["accepts","completes","effects_sum","updated_at"])
                ucs.save(update_fields=["accepts","completes","effects_sum","updated_at"])
        except Exception:
            pass

        # 보조 Outcome 저장(기존 유지)
        if d["type"] == "EFFECT" and value is not None:
            OutcomeModel.objects.create(
                session=session,
                content=content,
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
        session = RecommendationSession.objects.create(
            user_ref=d["user_ref"],
            trigger="steps_low",
            mode="restrict",
            context={},
        )
        ctx = {
            "session_id": str(session.id),
            "categories": _serialize_categories(cats, reason=_reason_from_trigger("steps_low")),
        }
        # 유저 컨텍스트가 오면 같이 저장
        if d.get("ctx"):
            ctx["user_ctx"] = d["ctx"]

        # context 저장
        try:
            session.set_context(ctx, save=True)
        except Exception:
            try:
                session.update_context(ctx, save=True)
            except Exception:
                session.context = ctx
                session.save(update_fields=["context"])

        resp = {
            "ok": True,
            "anomaly": True,
            "mode": "restrict",
            "trigger": "steps_low",
            "reasons": [f"cum_steps<{2000} or z<=-1.0"],
            "recommendation": {
                "session_id": str(session.id),
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

        # 5) 세션 확정
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
                context={},
            )
            sid = str(session.id)

        # context 저장(메타 + 유저 ctx 병합)
        meta_ctx = {
            "session_id": str(session.id),
            "weather_kind": weather_kind,
            "gate": gate,
            "max_distance_km": max_km,
            "limit": limit,
        }
        # 세션에 기존 user_ctx가 있으면 보존
        try:
            existing_user_ctx = (session.context or {}).get("user_ctx")
        except Exception:
            existing_user_ctx = None
        if existing_user_ctx:
            meta_ctx["user_ctx"] = existing_user_ctx
        if d.get("ctx"):
            meta_ctx["user_ctx"] = {**(meta_ctx.get("user_ctx") or {}), **d["ctx"]}

        try:
            session.update_context(meta_ctx, save=True)
        except Exception:
            try:
                session.set_context(meta_ctx, save=True)
            except Exception:
                session.context = meta_ctx
                session.save(update_fields=["context"])

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

# ==== /v1/recommend ====

# 신규: 추천 컨텍스트 정식 스키마(하위호환으로 기존 gw/ctx도 허용)
class RecommendPreferencesSerializer(serializers.Serializer):
    lang = serializers.CharField(required=False)
    duration_min = serializers.IntegerField(required=False, min_value=1, max_value=60)
    duration_max = serializers.IntegerField(required=False, min_value=1, max_value=180)
    music_provider = serializers.ChoiceField(required=False, choices=["spotify", "youtube"])
    allow_voice_guidance = serializers.BooleanField(required=False)

class RecommendContextIn(serializers.Serializer):
    pregnancy_week = serializers.IntegerField(required=False, min_value=0, max_value=45)
    trimester = serializers.IntegerField(required=False, min_value=1, max_value=3)
    risk_flags = serializers.ListField(child=serializers.CharField(), required=False)
    symptoms_today = serializers.ListField(child=serializers.CharField(), required=False)
    preferences = RecommendPreferencesSerializer(required=False)
    taboo_tags = serializers.ListField(child=serializers.CharField(), required=False)
    locale = serializers.CharField(required=False)
    tz = serializers.CharField(required=False)

class RecommendIn(serializers.Serializer):
    user_ref = serializers.CharField()
    session_id = serializers.CharField()
    category = serializers.CharField(help_text="MEDITATION | YOGA | MUSIC")
    top_k = serializers.IntegerField(required=False, min_value=1, max_value=5, help_text="기본 3")
    ts = serializers.DateTimeField(required=False)
    q = serializers.CharField(required=False, help_text="MUSIC일 때 검색 키워드(옵션)")
    # 정식 컨텍스트
    context = RecommendContextIn(required=False)
    # ↓ 하위호환 입력(점진 폐지 예정)
    gw = serializers.IntegerField(required=False, min_value=0, max_value=45, help_text="임신 주차(legacy)")
    ctx = serializers.JSONField(required=False, help_text="임의 컨텍스트(legacy)")

class RecommendItemOut(serializers.Serializer):
    content_id = serializers.IntegerField()
    title = serializers.CharField()
    url = serializers.URLField()
    thumbnail = serializers.URLField(required=False, allow_blank=True)
    rank = serializers.IntegerField(min_value=1)
    score = serializers.FloatField(required=False, allow_null=True)
    reason = serializers.CharField(required=False)

class RecommendOut(serializers.Serializer):
    ok = serializers.BooleanField()
    session_id = serializers.CharField()
    category = serializers.CharField()
    items = RecommendItemOut(many=True)

def _derive_trimester(week: Optional[int]) -> Optional[int]:
    if week is None:
        return None
    if week <= 13: return 1
    if week <= 27: return 2
    return 3

class RecommendView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM],
        request=RecommendIn,
        responses={200: RecommendOut},
        tags=["recommend"],
        summary="Category-based recommendation with ingest (YouTube/Spotify)",
        operation_id="postRecommend",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = RecommendIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        # 세션 확인
        try:
            sess_uuid = uuid.UUID(d["session_id"])
        except Exception:
            return Response({"ok": False, "error": "invalid session_id"}, status=400)
        session = RecommendationSession.objects.filter(id=sess_uuid).first()
        if session is None:
            return Response({"ok": False, "error": "session not found"}, status=404)

        category = (d["category"] or "").upper().strip()
        top_k = max(1, min(5, int(d.get("top_k") or 3)))

        # 1) 카테고리별 인제스트 (Content/ExposureCandidate 적재)
        try:
            if category == "MEDITATION":
                ingest_youtube_to_session(session_id=session.id, category="MEDITATION", max_total=max(30, top_k*8))
            elif category == "YOGA":
                ingest_youtube_to_session(session_id=session.id, category="YOGA", max_total=max(30, top_k*8))
            elif category == "MUSIC":
                q = d.get("q") or "태교 음악 relaxing instrumental"
                ingest_spotify_to_session(session_id=session.id, max_total=max(30, top_k*10), query=q, market="KR")
            else:
                return Response({"ok": False, "error": f"unsupported category '{category}'"}, status=400)
        except Exception:
            # 인제스트 실패해도 기존 후보가 있으면 추천은 시도
            pass

        # 2) 추천(세션 기반) — 세션에 저장된 user_ctx + 요청 context/gw 병합
        try:
            base_ctx = {}
            try:
                base_ctx = (session.context or {}).get("user_ctx") or {}
            except Exception:
                base_ctx = {}

            # 정식 context 우선, 없으면 legacy ctx/gw 사용
            ctx_in = d.get("context") or {}
            legacy_ctx = d.get("ctx") or {}
            if d.get("gw") is not None:
                legacy_ctx = {**legacy_ctx, "gw": int(d["gw"])}

            # trimester 보정
            if "trimester" not in ctx_in and ("pregnancy_week" in ctx_in or "gw" in ctx_in):
                week = ctx_in.get("pregnancy_week", ctx_in.get("gw"))
                ctx_in["trimester"] = _derive_trimester(week)

            merged_ctx = {**base_ctx, **legacy_ctx, **ctx_in}
            if d.get("ts"):
                merged_ctx["ts"] = d["ts"].isoformat()

            # (NOTE) 당분간 금기 필터는 서버에서 적용하지 않음
            # 필요 시 merged_ctx["excluded_tags"] + context.apply_taboo=true 로 다시 켤 것.

            rec_out = recommend_on_session(
                session_id=session.id,
                rec_in=RecInput(user_ref=d["user_ref"], category=category, context=merged_ctx),
            )
        except ValueError:
            # 후보 없으면 200 + 빈 배열
            return Response({"ok": True, "session_id": str(session.id), "category": category, "items": []})
        except Exception:
            return Response({"ok": False, "error": "recommendation failed"}, status=500)

        # 3) 응답 items 구성: pick + 상위 후보들
        items: List[Dict[str, Any]] = []
        picked = rec_out.picked
        items.append({
            "content_id": picked["content_id"],
            "title": picked["title"],
            "url": picked["url"],
            "thumbnail": picked.get("thumbnail") or "",
            "rank": 1,
            "score": None,
            "reason": "ts+context",
        })

        # 후보에서 추가 (세션 내 후보 pre_score 높은 순)
        try:
            exps = ExposureCandidate.objects.filter(
                session=session, content__category__iexact=category
            ).select_related("content")
            exps = sorted(exps, key=lambda e: (getattr(e, "pre_score", 0.0) or 0.0), reverse=True)
            for e in exps:
                if len(items) >= top_k:
                    break
                if e.content_id == picked["content_id"]:
                    continue
                c = e.content
                thumb = getattr(c, "thumbnail_url", None) or ((e.x_item_vec or {}).get("thumb_url")) or ""
                items.append({
                    "content_id": c.id,
                    "title": c.title,
                    "url": c.url,
                    "thumbnail": thumb,
                    "rank": len(items)+1,
                    "score": getattr(e, "pre_score", None),
                    "reason": "candidate",
                })
        except Exception:
            pass

        return Response({"ok": True, "session_id": rec_out.session_id, "category": category, "items": items[:top_k]})
