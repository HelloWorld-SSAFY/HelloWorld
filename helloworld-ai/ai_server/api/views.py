import os
import json
import uuid
from math import radians, cos, sin, asin, sqrt
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional

import logging
from django.utils import timezone as dj_timezone

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
from services.policy_service import categories_for_trigger
from services.weather_gateway import get_weather_gateway

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
    ContentStat,
    UserContentStat,
    RecommendationDelivery,   # recommend/places 결과 로그 (테이블명: recommend_delivery)
    UserStepsTodStatsDaily,   # ✅ 걸음수 기준선(평균) 비교용
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

log = logging.getLogger(__name__)

# ✅ auto precompute cooldown
AUTO_COOLDOWN = timedelta(minutes=3)

# ✅ 걸음수 격차 임계값(기본 500걸음): avg - cum_steps ≥ THRESHOLD → restrict
STEPS_GAP_THRESHOLD = int(os.getenv("STEPS_GAP_THRESHOLD", "500"))

def _assert_app_token(request: HttpRequest):
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
# 공통 Serializer
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
    thumbnail = serializers.URLField(required=False, allow_blank=True)
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
# 텔레메트리 스키마
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
    cooldown = CooldownSerializer(required=False)
    new_session = serializers.BooleanField(required=False)
    cooldown_min = serializers.IntegerField(required=False)

class TelemetryEmergencyResp(serializers.Serializer):
    ok = serializers.BooleanField(default=True)
    anomaly = serializers.BooleanField(default=True)
    risk_level = serializers.ChoiceField(choices=["critical"])
    mode = serializers.ChoiceField(choices=["emergency"])
    reasons = serializers.ListField(child=serializers.CharField())
    action = ActionSerializer()
    safe_templates = SafeTemplateSerializer(many=True)

class TelemetryCooldownResp(serializers.Serializer):
    ok = serializers.BooleanField(default=True)
    anomaly = serializers.BooleanField(default=True)
    risk_level = serializers.ChoiceField(choices=["high", "critical"])
    mode = serializers.ChoiceField(choices=["cooldown"])
    source = serializers.ChoiceField(choices=["restrict", "emergency"])
    cooldown = CooldownSerializer()

# ──────────────────────────────────────────────────────────────────────────────
# Steps 스키마 (위치 필수, 형식 고정)
# ──────────────────────────────────────────────────────────────────────────────
class StepsCheckIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField(help_text="KST 권장, 12:00/16:00/20:00 호출")
    cum_steps = serializers.IntegerField(min_value=0, help_text="동시간대 누적 걸음수")
    # ↓ 위치는 무조건 top-level 로 필수
    lat = serializers.FloatField(help_text="사용자 현재 위도 (필수)")
    lng = serializers.FloatField(help_text="사용자 현재 경도 (필수)")
    # 추천 반경/개수 옵션(없으면 기본값 사용)
    max_distance_km = serializers.FloatField(required=False, help_text="기본 3.0, 0.5~10.0")
    limit = serializers.IntegerField(required=False, help_text="기본 3, 1~5")
    # 그 외 컨텍스트(선택)
    ctx = serializers.JSONField(required=False, help_text="기타 유저 컨텍스트(선택)")

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
# Feedback 스키마
# ──────────────────────────────────────────────────────────────────────────────
class FeedbackIn(serializers.Serializer):
    user_ref = serializers.CharField()
    session_id = serializers.CharField()
    type = serializers.ChoiceField(choices=["ACCEPT","COMPLETE","EFFECT"])
    external_id = serializers.CharField(required=False)
    dwell_ms = serializers.IntegerField(required=False)
    watched_pct = serializers.FloatField(required=False)
    content_id = serializers.IntegerField(required=False)

class FeedbackOut(serializers.Serializer):
    ok = serializers.BooleanField()

# ──────────────────────────────────────────────────────────────────────────────
# Places 스키마
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
# Healthz
# ──────────────────────────────────────────────────────────────────────────────
class HealthzView(APIView):
    @extend_schema(
        auth=[],
        responses={200: inline_serializer("Healthz", {"ok": serializers.BooleanField(), "version": serializers.CharField()})},
        tags=["health"],
        summary="Health check (no auth)",
        examples=[OpenApiExample("RESPONSE", value={"ok": True, "version": "v0.2.1"}, response_only=True)],
        operation_id="getHealthz",
    )
    def get(self, request: HttpRequest):
        return Response({"ok": True, "version": "v0.2.1"})

# ──────────────────────────────────────────────────────────────────────────────
# 유틸
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

# ---- Delivery 로깅 헬퍼들 (필드명 정정: item_kind, context, thumbnail) ---------
def _log_recommend_delivery(*, session: RecommendationSession, user_ref: str, category: str, items: List[Dict[str, Any]]):
    rows = []
    for it in items:
        rows.append(RecommendationDelivery(
            session=session,
            user_ref=user_ref,
            # source 필드 없음
            category=category,
            item_kind="CONTENT",
            content_id=it["content_id"],
            title=it.get("title", ""),
            url=it.get("url", ""),
            thumbnail=it.get("thumbnail", "") or "",  # ← thumbnail_url 아님
            rank=it.get("rank"),
            score=it.get("score"),
            reason=it.get("reason", ""),
            context={"api": "recommend"},             # ← meta 아님
        ))
    if rows:
        RecommendationDelivery.objects.bulk_create(rows)

def _log_places_delivery(*, session: RecommendationSession, user_ref: str, category: str, items: List[Dict[str, Any]]):
    rows = []
    for it in items:
        rows.append(RecommendationDelivery(
            session=session,
            user_ref=user_ref,
            # source 필드 없음
            category=category or "OUTING",
            item_kind="PLACE",
            place_type=it.get("place_type"),
            place_id=it.get("content_id"),
            title=it.get("title", ""),
            lat=it.get("lat"),
            lng=it.get("lng"),
            rank=it.get("rank"),
            score=None,
            reason=it.get("reason", ""),
            context={
                "weather_gate": it.get("weather_gate"),
                "address": it.get("address", ""),
                "distance_km": it.get("distance_km"),
            },
        ))
    if rows:
        RecommendationDelivery.objects.bulk_create(rows)

# ──────────────────────────────────────────────────────────────────────────────
# 세션 유틸
# ──────────────────────────────────────────────────────────────────────────────
def _ensure_restrict_session(
    *,
    user_ref: str,
    trigger: Optional[str],
    cats: List[Dict[str, Any]],
    detector_result: Dict[str, Any],
    user_ctx: Optional[Dict[str, Any]] = None,
) -> tuple[RecommendationSession, Dict[str, Any], bool]:
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

# ✅ 최근 사전추천 존재 여부(쿨다운 가드) — 현재 세션은 제외
def _recent_auto_exists(user_ref: str, exclude_session_id: Optional[uuid.UUID] = None) -> bool:
    cut = dj_timezone.now() - AUTO_COOLDOWN
    qs = RecommendationSession.objects.filter(
        user_ref=user_ref, mode="restrict", created_at__gte=cut
    )
    if exclude_session_id:
        qs = qs.exclude(id=exclude_session_id)
    return qs.exists()

# ──────────────────────────────────────────────────────────────────────────────
# 내부 실행 유틸: 카테고리별 콘텐츠 추천 실행 → Delivery 저장 (응답 미포함)
# ──────────────────────────────────────────────────────────────────────────────
def _ingest_for_category(session_id: uuid.UUID, category: str, top_k: int):
    try:
        if category == "MEDITATION":
            ingest_youtube_to_session(session_id=session_id, category="MEDITATION", max_total=max(30, top_k * 8))
        elif category == "YOGA":
            ingest_youtube_to_session(session_id=session_id, category="YOGA", max_total=max(30, top_k * 8))
        elif category == "MUSIC":
            # 기본 검색어
            ingest_spotify_to_session(
                session_id=session_id,
                max_total=max(30, top_k * 10),
                query="태교 음악 relaxing instrumental",
                market="KR"
            )
        elif category == "BREATHING":
            # youtube_ingest가 막아둘 수 있음 → 시도했다가 허용 안 되면 조용히 스킵
            ingest_youtube_to_session(session_id=session_id, category="BREATHING", max_total=max(24, top_k * 6))
        # 그 외 카테고리는 DB 사전적재 가정 → 인제스트 스킵
    except ValueError as e:
        # 유입 모듈이 명시적으로 금지하는 카테고리면 noisy 스택트레이스 없이 스킵
        if str(e) == "CATEGORY_NOT_ALLOWED":
            log.info("ingest skipped (CATEGORY_NOT_ALLOWED): category=%s session=%s", category, session_id)
        else:
            log.exception("ingest failed for category=%s session=%s", category, session_id)
    except Exception:
        log.exception("ingest failed for category=%s session=%s", category, session_id)

def _run_content_delivery_for_category(*, session: RecommendationSession, user_ref: str, category: str, top_k: int = 3):
    # 인제스트
    _ingest_for_category(session.id, category, top_k)

    # 세션 컨텍스트 합성
    try:
        base_ctx = (session.context or {}).get("user_ctx") or {}
    except Exception:
        base_ctx = {}
    merged_ctx = dict(base_ctx)

    # 추천 실행
    try:
        rec_out = recommend_on_session(
            session_id=session.id,
            rec_in=RecInput(user_ref=user_ref, category=category, context=merged_ctx),
        )
    except ValueError:
        # 🔹 후보 없음은 정상 흐름으로 취급: 조용히 스킵(스택트레이스 X)
        log.info("skip auto content delivery: no candidates (session=%s, category=%s)", session.id, category)
        return
    except Exception:
        log.exception("recommend_on_session failed (session=%s, category=%s)", session.id, category)
        return

    # 아이템 구성 (picked + 후보 상위)
    items: List[Dict[str, Any]] = []
    picked = rec_out.picked
    if picked:
        items.append({
            "content_id": picked["content_id"],
            "title": picked["title"],
            "url": picked["url"],
            "thumbnail": picked.get("thumbnail") or "",
            "rank": 1,
            "score": None,
            "reason": "ts+context",
        })

    try:
        exps = ExposureCandidate.objects.filter(
            session=session, content__category__iexact=category
        ).select_related("content")
        exps = sorted(exps, key=lambda e: (getattr(e, "pre_score", 0.0) or 0.0), reverse=True)
        for e in exps:
            if len(items) >= top_k:
                break
            if picked and e.content_id == picked.get("content_id"):
                continue
            c = e.content
            thumb = getattr(c, "thumbnail_url", None) or ((e.x_item_vec or {}).get("thumb_url")) or ""
            items.append({
                "content_id": c.id,
                "title": c.title,
                "url": c.url,
                "thumbnail": thumb,
                "rank": len(items) + 1,
                "score": getattr(e, "pre_score", None),
                "reason": "candidate",
            })
    except Exception:
        pass

    if items:
        _log_recommend_delivery(session=session, user_ref=user_ref, category=category, items=items[:top_k])

# ──────────────────────────────────────────────────────────────────────────────
# 내부 실행 유틸: OUTING 장소 추천 → Delivery 저장 (응답 미포함)
# ──────────────────────────────────────────────────────────────────────────────
def _haversine_km(lat1, lon1, lat2, lon2):
    r = 6371.0
    dlat = radians(lat2 - lat1)
    dlon = radians(lon2 - lon1)
    a = sin(dlat/2)**2 + cos(radians(lat1))*cos(radians(lat2))*sin(dlon/2)**2
    c = 2*asin(min(1, sqrt(a)))
    return r * c

def _extract_latlng_from_ctx(ctx: Optional[Dict[str, Any]]) -> Optional[tuple[float, float]]:
    if not ctx:
        return None
    # 평평한 키 우선
    lat = ctx.get("lat") or ctx.get("latitude")
    lng = ctx.get("lng") or ctx.get("lon") or ctx.get("longitude")
    if lat is not None and lng is not None:
        try:
            return float(lat), float(lng)
        except Exception:
            return None
    # 중첩 객체(location: {lat, lng})
    loc = ctx.get("location") if isinstance(ctx, dict) else None
    if isinstance(loc, dict):
        lat = loc.get("lat") or loc.get("latitude")
        lng = loc.get("lng") or loc.get("lon") or loc.get("longitude")
        if lat is not None and lng is not None:
            try:
                return float(lat), float(lng)
            except Exception:
                return None
    return None

def _run_places_delivery(*, session: RecommendationSession, user_ref: str, ctx: Optional[Dict[str, Any]], default_max_km: float = 3.0, default_limit: int = 3):
    ll = _extract_latlng_from_ctx(ctx)
    if not ll:
        log.warning("OUTING delivery skipped: missing lat/lng in ctx (session=%s, user=%s)", session.id, user_ref)
        return
    lat0, lng0 = ll
    max_km = float(ctx.get("max_distance_km") or default_max_km) if isinstance(ctx, dict) else default_max_km
    limit = int(ctx.get("limit") or default_limit) if isinstance(ctx, dict) else default_limit
    max_km = max(0.5, min(10.0, max_km))
    limit = max(1, min(5, limit))

    # 1) 날씨 게이트
    weather_fallback = False
    try:
        gw = get_weather_gateway()
        if callable(gw):
            weather_kind, gate = gw(lat=lat0, lng=lng0)
        else:
            weather_kind, gate = gw.gate(lat=lat0, lng=lng0)
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

    outs = list(qs_out.values("id", "name", "lat", "lon", "address"))
    ins  = list(qs_in.values("id", "name", "lat", "lon", "address"))

    items: List[Dict[str, Any]] = []
    for p in outs:
        if p.get("lat") is None or p.get("lon") is None:
            continue
        dist = _haversine_km(lat0, lng0, float(p["lat"]), float(p["lon"]))
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
        dist = _haversine_km(lat0, lng0, float(p["lat"]), float(p["lon"]))
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
    for i, it in enumerate(items, start=1):
        it["rank"] = i

    # 노출 기록 (PlaceExposure)
    if items:
        try:
            PlaceExposure.objects.bulk_create([
                PlaceExposure(user_ref=user_ref, place_type=it["place_type"], place_id=it["content_id"])
                for it in items[:limit]
            ], ignore_conflicts=True)
        except Exception:
            pass

    # Delivery 로깅
    _log_places_delivery(session=session, user_ref=user_ref, category="OUTING", items=items[:limit])

    if weather_fallback or (not items):
        log.info("places delivery fallback or empty (session=%s, user=%s)", session.id, user_ref)

# ──────────────────────────────────────────────────────────────────────────────
# Telemetry
# ──────────────────────────────────────────────────────────────────────────────
class TelemetryView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM],
        request=TelemetryIn,
        responses={
            200: PolymorphicProxySerializer(
                component_name="TelemetryResponse",
                resource_type_field_name="mode",
                serializers={
                    "normal": TelemetryNormalResp,
                    "restrict": TelemetryRestrictResp,
                    "emergency": TelemetryEmergencyResp,
                    "cooldown": TelemetryCooldownResp,
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
            "- restrict 시 trigger_category_policy로 카테고리 도출\n"
            "- ⚠ 트리거 발생 시 내부에서 바로 추천 실행하고 recommend_delivery에 저장(응답엔 미포함)"
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
        reasons = result.get("reasons") or result.get("reason") or []
        if not reasons and trigger:
            reasons = [trigger]

        if level == "emergency":
            risk_level = "critical"
        elif level == "restrict":
            risk_level = "high"
        elif level == "cooldown":
            risk_level = "critical" if (result.get("cooldown_source") == "emergency") else "high"
        else:
            risk_level = "low"

        resp: Dict[str, Any] = {
            "ok": True,
            "anomaly": level != "normal",
            "risk_level": risk_level,
            "mode": level,
        }

        if level == "restrict":
            cats = categories_for_trigger(trigger) or []
            if not cats:
                met = payload.get("metrics", {}) or {}
                fallback = []
                if "stress" in met:
                    fallback = [{"code": "BREATHING"}, {"code": "MEDITATION"}, {"code": "MUSIC"}]
                elif "hr" in met:
                    fallback = [{"code": "BREATHING"}, {"code": "YOGA"}]
                cats = fallback

            session, recommendation, new_session = _ensure_restrict_session(
                user_ref=payload["user_ref"],
                trigger=trigger,
                cats=cats,
                detector_result=result,
                user_ctx=None,
            )

            cooldown_min = result.get("cooldown_min")
            cooldown_ends_at = result.get("cooldown_until") or result.get("cooldown_ends_at")
            if isinstance(cooldown_ends_at, str):
                try:
                    cooldown_ends_at = datetime.fromisoformat(cooldown_ends_at)
                except Exception:
                    cooldown_ends_at = None
            if not cooldown_ends_at and cooldown_min is not None:
                dt = payload["ts"]
                try:
                    dt = dt.astimezone(KST)
                except Exception:
                    pass
                cooldown_ends_at = dt + timedelta(minutes=int(cooldown_min))

            cooldown_obj = None
            if cooldown_ends_at:
                now = _now_kst()
                secs_left = int(max(0, (cooldown_ends_at - now).total_seconds()))
                cooldown_obj = {"active": secs_left > 0, "ends_at": cooldown_ends_at.isoformat(), "secs_left": secs_left}

            resp.update({"reasons": reasons or [], "recommendation": recommendation, "new_session": new_session})
            if cooldown_obj:
                resp["cooldown"] = cooldown_obj
            if result.get("cooldown_min") is not None:
                resp["cooldown_min"] = int(result["cooldown_min"])

            # ⚙️ restrict 시: 카테고리별 내부 추천 실행 → recommend_delivery 저장 (응답 미포함)
            try:
                if not _recent_auto_exists(payload["user_ref"], exclude_session_id=session.id):
                    cat_codes = [c.get("code") for c in cats if isinstance(c, dict) and c.get("code")]
                    cat_codes = [c for c in cat_codes if c and c != "OUTING"]
                    for cat in cat_codes:
                        _run_content_delivery_for_category(session=session, user_ref=payload["user_ref"], category=cat, top_k=3)
                else:
                    log.info("skip auto delivery (cooldown) user=%s", payload["user_ref"])
            except Exception:
                log.exception("restrict auto delivery failed (user=%s)", payload.get("user_ref"))

        elif level == "cooldown":
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

        return Response(resp)

# ──────────────────────────────────────────────────────────────────────────────
# Feedback
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
# Baseline(평균) 임포트: 메인서버 응답 형식 그대로 반영
# ──────────────────────────────────────────────────────────────────────────────
class StepsBaselineRecord(serializers.Serializer):
    hour_range = serializers.CharField()                 # "00-12" | "00-16" | "00-24"
    avg_steps = serializers.FloatField(allow_null=True)  # null이면 스킵

class StepsBaselineImportIn(serializers.Serializer):
    user_ref = serializers.CharField()
    date = serializers.DateField(help_text="YYYY-MM-DD (KST 기준)")
    records = StepsBaselineRecord(many=True)

def _bucket_from_hour_range(hr: str) -> Optional[int]:
    try:
        _, end_s = hr.split("-")
        end_h = int(end_s)
        b = end_h // 4
        if end_h >= 24:
            b = 5
        return max(0, min(5, b))
    except Exception:
        return None

def _upsert_steps_baseline_records(*, user_ref: str, d, records: list[dict]) -> list[int]:
    saved = []
    for r in records:
        avg = r.get("avg_steps", None)
        if avg is None:
            continue
        b = _bucket_from_hour_range(str(r.get("hour_range", "")))
        if b is None:
            continue
        obj, _ = UserStepsTodStatsDaily.objects.update_or_create(
            user_ref=user_ref, d=d, bucket=b,
            defaults={
                "cum_mu": float(avg),
                "cum_sigma": 0.0,                          # 표준편차 미제공 → 0
                # p20은 사용하지 않지만 NOT NULL이므로 채워둠(평균-임계값 하한0)
                "p20": max(float(avg) - STEPS_GAP_THRESHOLD, 0.0),
            }
        )
        saved.append(b)
    return saved

class StepsBaselineImportView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM],
        request=StepsBaselineImportIn,
        responses={200: inline_serializer("StepsBaselineImportOut", {
            "ok": serializers.BooleanField(),
            "saved_buckets": serializers.ListField(child=serializers.IntegerField()),
        })},
        tags=["steps"],
        summary="Import daily cumulative steps averages from main server format",
        description='메인서버 응답 {"records":[{"hour_range":"00-12","avg_steps":1234},...]} 를 그대로 넣으면 user_steps_tod_stats_daily에 upsert.',
        operation_id="postStepsBaselineImport",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad:
            return bad
        ser = StepsBaselineImportIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data
        saved = _upsert_steps_baseline_records(user_ref=d["user_ref"], d=d["date"], records=d["records"])
        return Response({"ok": True, "saved_buckets": saved})

# ──────────────────────────────────────────────────────────────────────────────
# Steps Check  ✅ 평균 대비 고정 격차 비교 + (트리거 시) 장소 추천 저장
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
        summary="Compare cum_steps to stored average; restrict if gap ≥ threshold(기본 500)",
        operation_id="postStepsCheck",
        description="입력으로 user_ref/ts/cum_steps/lat/lng를 받고, 저장된 평균(cum_mu)과 비교해 (avg - cum_steps) ≥ THRESHOLD(기본 500) 이면 OUTING을 내부 추천하여 recommend_delivery에 저장합니다(응답엔 미포함).",
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = StepsCheckIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        # 🔹 위치는 top-level 필수 → user_ctx 구성
        user_ctx: Dict[str, Any] = {"lat": float(d["lat"]), "lng": float(d["lng"])}
        if d.get("max_distance_km") is not None:
            user_ctx["max_distance_km"] = float(d["max_distance_km"])
        if d.get("limit") is not None:
            user_ctx["limit"] = int(d["limit"])
        if d.get("ctx"):
            extra = dict(d["ctx"])
            extra.pop("lat", None); extra.pop("lng", None)
            user_ctx.update(extra)
        user_ctx["location_source"] = "steps-check"

        # ts → KST & 버킷(0..5)
        ts = d["ts"]
        try:
            ts_kst = ts.astimezone(KST)
        except Exception:
            ts_kst = ts  # 이미 KST라고 가정
        hour = ts_kst.hour
        bucket = hour // 4  # 0..5

        # 오늘 기준선(평균) 조회
        baseline = UserStepsTodStatsDaily.objects.filter(
            user_ref=d["user_ref"],
            d=ts_kst.date(),
            bucket=bucket,
        ).first()

        cum_steps = int(d["cum_steps"])

        # 기준선 없으면 '정상'
        if not baseline or baseline.cum_mu is None:
            return Response({"ok": True, "anomaly": False, "mode": "normal"})

        avg = float(baseline.cum_mu or 0.0)
        gap = max(0.0, avg - float(cum_steps))

        # 격차 임계 비교
        if gap >= float(STEPS_GAP_THRESHOLD):
            cats = categories_for_trigger("steps_low") or []
            session = RecommendationSession.objects.create(
                user_ref=d["user_ref"],
                trigger="steps_low",
                mode="restrict",
                context={},
            )
            ctx = {
                "session_id": str(session.id),
                "categories": _serialize_categories(cats, reason=_reason_from_trigger("steps_low")),
                "user_ctx": user_ctx,
                "ts": ts_kst.isoformat(),
            }

            # context 저장
            try:
                session.set_context(ctx, save=True)
            except Exception:
                try:
                    session.update_context(ctx, save=True)
                except Exception:
                    session.context = ctx
                    session.save(update_fields=["context"])

            # ⚙️ OUTING 장소 추천 실행 → recommend_delivery 저장 (lat/lng는 user_ctx에서 추출)
            try:
                _run_places_delivery(session=session, user_ref=d["user_ref"], ctx=user_ctx or {})
            except Exception:
                log.exception("steps_low places delivery failed (user=%s)", d["user_ref"])

            reasons = [f"avg({avg:.0f}) - cum_steps({cum_steps}) = {int(gap)} ≥ {STEPS_GAP_THRESHOLD} @bucket{bucket}"]
            resp = {
                "ok": True,
                "anomaly": True,
                "mode": "restrict",
                "trigger": "steps_low",
                "reasons": reasons,
                "recommendation": {
                    "session_id": str(session.id),
                    "categories": ctx["categories"],
                },
            }
            return Response(resp)

        # 기준선 이상(또는 격차 미만) → normal
        return Response({"ok": True, "anomaly": False, "mode": "normal"})

# ──────────────────────────────────────────────────────────────────────────────
# Places (외부 호출용은 유지하되 Delivery 필드명 정정)
# ──────────────────────────────────────────────────────────────────────────────
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

        # 1) 날씨 게이트
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
            try:
                PlaceExposure.objects.bulk_create([
                    PlaceExposure(user_ref=d["user_ref"], place_type=it["place_type"], place_id=it["content_id"])
                    for it in items[:limit]
                ], ignore_conflicts=True)
            except Exception:
                pass

        # ✅ RecommendationDelivery 로깅 (필드명 정정: item_kind/context/thumbnail)
        _log_places_delivery(session=session, user_ref=d["user_ref"], category=d.get("category", "OUTING"), items=items[:limit])

        resp = {
            "ok": True,
            "session_id": sid,
            "category": d.get("category", "OUTING"),
            "items": items[:limit],
            "fallback_used": weather_fallback or (not items),
        }
        return Response(resp)

# ==== /v1/recommend ====
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

        # 1) 인제스트
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
        except ValueError as e:
            if str(e) == "CATEGORY_NOT_ALLOWED":
                log.info("ingest skipped in /v1/recommend (CATEGORY_NOT_ALLOWED): category=%s session=%s", category, session.id)
            else:
                pass
        except Exception:
            pass

        # 2) 추천
        try:
            base_ctx = {}
            try:
                base_ctx = (session.context or {}).get("user_ctx") or {}
            except Exception:
                base_ctx = {}

            ctx_in = d.get("context") or {}
            legacy_ctx = d.get("ctx") or {}
            if d.get("gw") is not None:
                legacy_ctx = {**legacy_ctx, "gw": int(d["gw"])}

            if "trimester" not in ctx_in and ("pregnancy_week" in ctx_in or "gw" in ctx_in):
                week = ctx_in.get("pregnancy_week", ctx_in.get("gw"))
                ctx_in["trimester"] = _derive_trimester(week)

            merged_ctx = {**base_ctx, **legacy_ctx, **ctx_in}
            if d.get("ts"):
                merged_ctx["ts"] = d["ts"].isoformat()

            rec_out = recommend_on_session(
                session_id=session.id,
                rec_in=RecInput(user_ref=d["user_ref"], category=category, context=merged_ctx),
            )
        except ValueError:
            return Response({"ok": True, "session_id": str(session.id), "category": category, "items": []})
        except Exception:
            return Response({"ok": False, "error": "recommendation failed"}, status=500)

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

        # ✅ RecommendationDelivery 로깅 (필드명 정정)
        _log_recommend_delivery(session=session, user_ref=d["user_ref"], category=category, items=items[:top_k])

        return Response({"ok": True, "session_id": str(session.id), "category": category, "items": items[:top_k]})
