# api/views.py
import os
import json
import uuid
from math import radians, cos, sin, asin, sqrt
from datetime import datetime, timezone, date, timedelta

from django.http import JsonResponse, HttpRequest
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from django.views import View  # (남겨둠: 내부 재사용용)

# === DRF / Spectacular 추가 ===
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.decorators import api_view
from rest_framework import serializers  # ← 추가
from drf_spectacular.utils import (
    extend_schema, OpenApiResponse, OpenApiTypes, OpenApiExample,
    inline_serializer, OpenApiParameter,                      # ← 추가
)

from services.anomaly import AnomalyDetector, AnomalyConfig, KST
from services.orm_stats_provider import OrmStatsProvider
from services.policy_service import categories_for_trigger  # DB 우선 + 폴백
from services.weather_gateway import get_weather_gateway  # env 없이 동작(기본 stub)

from api.models import (
    Content,
    RecommendationSession,
    ExposureCandidate,
    ItemRec,
    Feedback as FeedbackModel,
    Outcome as OutcomeModel,
    # ↓ steps-check/places에 필요한 모델
    TriggerCategoryPolicy,
    UserStepsTodStatsDaily,
    PlaceInside,
    PlaceOutside,
    # PlaceExposure는 없을 수도 있어 try/except로 사용
)

# 걸음수 check 임계값 고정
STEPS_DIFF_THRESHOLD = 500

# ── 싱글턴 ────────────────────────────────────────────────────────────────────
_config = AnomalyConfig()
_provider = OrmStatsProvider()
_detector = AnomalyDetector(_provider, _config)

# ── 설정/인증 ────────────────────────────────────────────────────────────────
APP_TOKEN = os.getenv("APP_TOKEN", "")

def _auth_ok(request: HttpRequest) -> bool:
    if not APP_TOKEN:
        return True
    return request.headers.get("X-App-Token", "").strip() == APP_TOKEN

# ── Swagger 공통 헤더 파라미터 ───────────────────────────────────────────────
APP_TOKEN_HEADER = OpenApiParameter(
    name="X-App-Token",
    type=OpenApiTypes.STR,
    location=OpenApiParameter.HEADER,
    required=False,
    description="앱 인증 토큰(환경에 따라 생략 가능)"
)

# ── 유틸 ─────────────────────────────────────────────────────────────────────
def _json(request: HttpRequest):
    try:
        return json.loads(request.body.decode("utf-8"))
    except Exception:
        return {}

def _iso_to_utc(ts_str: str):
    # "2025-09-16T10:00:00+09:00" / "...Z" 모두 지원
    return datetime.fromisoformat(ts_str.replace("Z", "+00:00")).astimezone(timezone.utc)

def _to_kst(dt_utc: datetime) -> datetime:
    if dt_utc.tzinfo is None:
        dt_utc = dt_utc.replace(tzinfo=timezone.utc)
    return dt_utc.astimezone(KST)

def _bucket_index_4h(kst_dt: datetime) -> int:
    return kst_dt.hour // 4  # 0..5

def _haversine_km(lat1, lon1, lat2, lon2):
    R = 6371.0
    dlat = radians(lat2-lat1)
    dlon = radians(lon2-lon1)
    a = sin(dlat/2)**2 + cos(radians(lat1))*cos(radians(lat2))*sin(dlon/2)**2
    c = 2*asin(sqrt(a))
    return R*c

def _clamp(v, lo, hi):
    return max(lo, min(hi, v))

def _session_model_fields():
    return {f.name for f in RecommendationSession._meta.get_fields()
            if getattr(f, "concrete", False) and not getattr(f, "auto_created", False)}

def _create_session_dynamic(*, mode: str, user_ref: str, trigger: str,
                            reasons, as_of_date: date, bucket4h: int):
    """모델 스키마에 맞춰 안전하게 세션 생성."""
    fields = _session_model_fields()
    kwargs = {"mode": mode, "trigger": trigger}

    if "user_ref" in fields:   kwargs["user_ref"] = user_ref
    elif "user_id" in fields:  kwargs["user_id"] = user_ref

    have_reasons = "reasons" in fields
    have_as_of   = "as_of" in fields
    have_bucket  = "bucket_4h" in fields
    have_context = "context" in fields

    context_payload = {
        "reasons": reasons,
        "as_of": as_of_date.isoformat(),
        "bucket_4h": bucket4h,
        "kst_date": as_of_date.isoformat(),  # 재사용 조회용
        "bucket": bucket4h,
    }
    if have_as_of:  kwargs["as_of"] = as_of_date
    if have_bucket: kwargs["bucket_4h"] = bucket4h

    if have_reasons:
        try:
            kwargs["reasons"] = reasons
            return RecommendationSession.objects.create(**kwargs)
        except Exception:
            kwargs["reasons"] = " | ".join(map(str, reasons))
            return RecommendationSession.objects.create(**kwargs)
    else:
        if have_context:
            try:
                kwargs["context"] = context_payload
            except Exception:
                kwargs["context"] = json.dumps(context_payload, ensure_ascii=False)
        return RecommendationSession.objects.create(**kwargs)

def _build_category_reason(trigger_code: str) -> str:
    if trigger_code == "hr_high": return "hr high"
    if trigger_code == "hr_low":  return "hr low"
    if trigger_code == "steps_low": return "steps low"
    if trigger_code == "stress_up": return "stress high"
    return "anomaly"

def _infer_trigger_code(reasons, metrics):
    """
    현재 요청 페이로드를 최우선으로 결정.
    못 정하면 reasons → 그래도 없으면 'unknown'.
    """
    t = " ".join(str(x).lower() for x in (reasons or []))
    has_hr = isinstance(metrics, dict) and "hr" in metrics
    has_stress = isinstance(metrics, dict) and "stress" in metrics

    if has_hr:
        try:
            hr = float(metrics["hr"])
            if hr >= 150: return "hr_high"
            if hr <= 45:  return "hr_low"
        except Exception:
            pass
        if "hr_high" in t or "hr high" in t or "hr>" in t or "hr≥" in t: return "hr_high"
        if "hr_low"  in t or "hr low"  in t or "hr<" in t or "hr≤" in t: return "hr_low"
        return "hr_high"  # hr만 왔는데 임계 미만이면 hr_high 쪽 추천

    if has_stress:
        return "stress_up"

    if "hr_high" in t or "hr high" in t: return "hr_high"
    if "hr_low"  in t or "hr low"  in t: return "hr_low"
    if "steps_low" in t or "activity low" in t or "steps" in t: return "steps_low"
    if "stress_up" in t or "stress" in t or "z(stress)" in t: return "stress_up"

    return "unknown"

# ── /v1/healthz ──────────────────────────────────────────────────────────────
@extend_schema(
    tags=["health"],
    summary="헬스체크",
    description="Liveness/Readiness probe.",
    parameters=[APP_TOKEN_HEADER],
    responses={200: OpenApiResponse(response=OpenApiTypes.OBJECT, description="OK")},
)
@api_view(["GET"])
def healthz(request: HttpRequest):
    return Response({"status": "ok", "model": "rec_v0.1"})

# ── /v1/telemetry ────────────────────────────────────────────────────────────
@csrf_exempt
def telemetry(request: HttpRequest):
    """
    v1 계약(명세서): ok/anomaly/risk_level/mode + reasons
    - restrict: recommendation.session_id, recommendation.categories[]
    - emergency: action, safe_templates (세션/카테고리 없음)
    """
    if request.method != "POST":
        return JsonResponse({"error": "method_not_allowed"}, status=405)
    if not _auth_ok(request):
        return JsonResponse({"detail": "invalid app token"}, status=401)

    try:
        body = _json(request)

        user_ref = body.get("user_ref") or body.get("user_id") or "u1"
        ts_str = body.get("ts") or body.get("timestamp")
        if ts_str:
            ts_utc = _iso_to_utc(ts_str)
        else:
            ts_utc = datetime.now(timezone.utc)
            ts_str = ts_utc.isoformat()

        metrics = body.get("metrics") or {}
        if "hr" in body and body["hr"] is not None:
            metrics["hr"] = float(body["hr"])
        if "stress" in body and body["stress"] is not None:
            metrics["stress"] = float(body["stress"])

        gestational_week = body.get("gestational_week")

        # 엔진 판단
        result = _detector.evaluate(user_ref=user_ref, ts_utc=ts_utc, metrics=metrics)

        # 컨텍스트
        kst = ts_utc.astimezone(KST)
        as_of_date = kst.date()
        bucket4h = kst.hour // 4
        reasons = list(getattr(result, "reasons", []) or [])

        # 트리거: 엔진 제공값 우선 → 추론
        trigger_code = getattr(result, "trigger", None) or _infer_trigger_code(reasons, metrics)

        # emergency
        if getattr(result, "mode", None) == "emergency":
            return JsonResponse({
                "ok": True,
                "anomaly": True,
                "risk_level": "critical",
                "mode": "emergency",
                "reasons": reasons,
                "action": {"type": "EMERGENCY_CONTACT", "cooldown_min": 60},
                "safe_templates": [
                    {"category": "BREATHING", "title": "안전 호흡 3분"},
                    {"category": "BREATHING", "title": "안전 호흡 5분"},
                ],
            }, status=200)

        # restrict
        if getattr(result, "mode", None) == "restrict":
            session = _create_session_dynamic(
                mode="restrict", user_ref=user_ref, trigger=trigger_code,
                reasons=reasons, as_of_date=as_of_date, bucket4h=bucket4h
            )
            raw_cats = categories_for_trigger(trigger_code, gestational_week)
            cats = []
            reason_label = _build_category_reason(trigger_code)
            for i, c in enumerate(raw_cats):
                cats.append({
                    "category": c.get("code") or c.get("category"),
                    "rank": c.get("rank", c.get("priority", i + 1)),
                    "reason": reason_label
                })
            return JsonResponse({
                "ok": True,
                "anomaly": True,
                "risk_level": "high",
                "mode": "restrict",
                "reasons": reasons,
                "recommendation": {
                    "session_id": str(session.id),
                    "categories": cats
                }
            }, status=200)

        # normal
        return JsonResponse({
            "ok": True,
            "anomaly": False,
            "risk_level": "low",
            "mode": "normal"
        }, status=200)

    except Exception as e:
        import logging
        logging.getLogger(__name__).exception("telemetry error")
        return JsonResponse({"detail": "internal error", "error": type(e).__name__, "message": str(e)}, status=500)

# ── /v1/steps-check ──────────────────────────────────────────────────────────
@csrf_exempt
def steps_check(request: HttpRequest):
    """
    고정 시각(12/16/20 KST) 누적 걸음수로 저활동 판단 → restrict 세션 발급
    - 기준: 동시간대 평균(mu)보다 'threshold'(기본 500) 걸음 이상 부족하면 저활동
    - 동일 일자·버킷 재호출 시 기존 session 재사용(context.kst_date/bucket)
    """
    if request.method != "POST":
        return JsonResponse({"ok": False, "error": "INVALID_METHOD"}, status=405)
    if not _auth_ok(request):
        return JsonResponse({"detail": "invalid app token"}, status=401)

    try:
        body = _json(request)
        user_ref = body["user_ref"]
        ts_utc = _iso_to_utc(body["ts"]) if body.get("ts") else datetime.now(timezone.utc)
        cum_steps = int(body["cum_steps"])  # 메인서버가 누적 걸음수 전달
    except Exception:
        return JsonResponse({"ok": False, "error": "INVALID_BODY"}, status=400)

    # KST 기준 버킷
    kst = _to_kst(ts_utc)
    b = _bucket_index_4h(kst)

    # 기준선: 오늘 우선 → 없으면 최근일 (동시간대 평균만 사용)
    qs = UserStepsTodStatsDaily.objects.filter(user_ref=user_ref, bucket=b)
    base = qs.filter(d=kst.date()).first() or qs.order_by("-d").first()
    if not base:
        return JsonResponse({"ok": True, "anomaly": False, "mode": "normal", "note": "no_baseline"}, status=200)

    threshold = STEPS_DIFF_THRESHOLD
    mu = float(base.cum_mu if base.cum_mu is not None else 0.0)
    diff = mu - float(cum_steps)           # +면 평균보다 부족, -면 평균보다 많음
    is_low = diff >= threshold             # 평균보다 threshold 이상 부족하면 저활동

    if not is_low:
        return JsonResponse({"ok": True, "anomaly": False, "mode": "normal"}, status=200)

    # 동일 일자·버킷 steps_low 세션 재사용
    sess = (RecommendationSession.objects
            .filter(user_ref=user_ref, mode="restrict", trigger="steps_low")
            .filter(context__kst_date=str(kst.date()), context__bucket=b)
            .order_by("-created_at")
            .first())
    if not sess:
        sess = _create_session_dynamic(
            mode="restrict", user_ref=user_ref, trigger="steps_low",
            reasons=[f"steps_diff={int(diff)}"], as_of_date=kst.date(), bucket4h=b
        )

    # 정책(우선순위 오름차순)
    pols = (TriggerCategoryPolicy.objects
            .filter(trigger="steps_low", is_active=True)
            .order_by("priority"))
    cats = [{
        "category": p.category,
        "rank": p.priority,
        "reason": "steps low vs avg"
    } for p in pols]

    return JsonResponse({
        "ok": True,
        "anomaly": True,
        "mode": "restrict",
        "trigger": "steps_low",
        "reasons": [
            f"steps_diff={int(diff)} (threshold={threshold}, bucket={b})",
            f"cum_steps={int(cum_steps)}, mu={int(mu)}"
        ],
        "recommendation": {
            "session_id": str(sess.id),
            "categories": cats
        }
    }, status=200)

# ── /v1/places ───────────────────────────────────────────────────────────────
@extend_schema(
    tags=["ai", "places"],
    summary="OUTING 장소 추천 (날씨 게이트 + 거리)",
    description=(
        "OUTING 카테고리에서, 날씨/거리 기반으로 실내·실외 장소를 추천합니다.\n"
        "- 입력: user_ref, session_id, category=OUTING, lat, lng, (선택) max_distance_km, limit, weather_kind\n"
        "- 날씨 게이트: clear/clouds → 실외 후보 / 그 외 → 실내 후보\n"
        "- 중복 방지: 최근 14일 동일 후보 노출 제외(PlaceExposure 있을 때)\n"
        "- 테스트용 `weather_kind`로 날씨 강제 가능"
    ),
    parameters=[APP_TOKEN_HEADER],
    request=inline_serializer(
        name="PlacesReq",
        fields={
            "user_ref": serializers.CharField(),
            "session_id": serializers.CharField(help_text="UUID 문자열"),
            "category": serializers.ChoiceField(choices=["OUTING"]),
            "lat": serializers.FloatField(),
            "lng": serializers.FloatField(),
            "max_distance_km": serializers.FloatField(required=False, default=3.0, min_value=0.5, max_value=10.0),
            "limit": serializers.IntegerField(required=False, default=3, min_value=1, max_value=5),
            "weather_kind": serializers.ChoiceField(
                choices=["clear","clouds","rain","snow","thunder","dust"], required=False
            ),
        },
    ),
    responses={
        200: inline_serializer(
            name="PlacesResp",
            fields={
                "ok": serializers.BooleanField(),
                "session_id": serializers.CharField(),
                "category": serializers.ChoiceField(choices=["OUTING"]),
                "items": serializers.ListSerializer(
                    child=inline_serializer(
                        name="PlaceItem",
                        fields={
                            "content_id": serializers.IntegerField(),
                            "title": serializers.CharField(),
                            "lat": serializers.FloatField(),
                            "lng": serializers.FloatField(),
                            "distance_km": serializers.FloatField(),
                            "rank": serializers.IntegerField(),
                            "reason": serializers.CharField(),
                            "weather_gate": serializers.CharField(),
                            "address": serializers.CharField(allow_blank=True),
                            "address_road": serializers.CharField(allow_null=True, required=False),
                            "address_jibun": serializers.CharField(allow_null=True, required=False),
                            "place_category": serializers.CharField(allow_null=True, required=False),
                        }
                    )
                ),
                "fallback_used": serializers.BooleanField(),
                "weather": inline_serializer(
                    name="WeatherMeta",
                    fields={"kind": serializers.CharField(), "outdoor_ok": serializers.BooleanField()}
                ),
                "safe_templates": serializers.ListField(  # fallback일 때만 존재
                    child=inline_serializer(
                        name="SafeTpl",
                        fields={"category": serializers.CharField(), "title": serializers.CharField()}
                    ),
                    required=False
                ),
            }
        ),
        400: OpenApiResponse(description="INVALID_BODY"),
        401: OpenApiResponse(description="invalid app token"),
        403: OpenApiResponse(description="SESSION_OWNERSHIP"),
        404: OpenApiResponse(description="INVALID_SESSION"),
        409: OpenApiResponse(description="SESSION_CLOSED"),
    },
)
@api_view(["POST"])
def places(request: HttpRequest):
    """
    OUTING: 위치/날씨 기반 장소 추천
    - 입력: user_ref, session_id, category=OUTING, lat, lng, max_distance_km(기본 3.0), limit(기본 3)
    - 검증: session(user_ref 소유, mode=restrict, trigger=steps_low), category=OUTING
    - 날씨 게이트: clear/clouds → 실외 PlaceOutside, 그 외 → 실내 PlaceInside
    - 중복 제거: 최근 14일 내 동일 place_type/place_id 노출 제외 (PlaceExposure 없으면 폴백)
    - 테스트용 weather override: body.weather_kind = clear|clouds|rain|snow|thunder|dust
    """
    # DRF 요청객체를 기존 함수 로직이 그대로 사용 가능
    if not _auth_ok(request._request):
        return Response({"detail": "invalid app token"}, status=401)

    body = _json(request._request)
    required = {"user_ref","session_id","category","lat","lng"}
    if not required.issubset(body):
        return Response({"ok": False, "error": "INVALID_BODY"}, status=400)

    user_ref = str(body["user_ref"])
    session_id = str(body["session_id"])
    category = str(body["category"]).upper().strip()
    if category != "OUTING":
        return Response({"ok": False, "error": "CATEGORY_NOT_ALLOWED"}, status=422)

    try:
        lat = float(body["lat"])
        lng = float(body["lng"])
    except Exception:
        return Response({"ok": False, "error": "INVALID_BODY:latlng"}, status=400)

    limit = int(_clamp(int(body.get("limit", 3)), 1, 5))
    max_distance_km = float(_clamp(float(body.get("max_distance_km", 3.0)), 0.5, 10.0))

    # 세션 검증: 소유 + restrict + steps_low
    try:
        sess = RecommendationSession.objects.get(id=uuid.UUID(session_id))
    except Exception:
        return Response({"ok": False, "error": "INVALID_SESSION"}, status=404)
    if getattr(sess, "user_ref", None) != user_ref:
        return Response({"ok": False, "error": "SESSION_OWNERSHIP"}, status=403)
    if sess.mode != "restrict" or getattr(sess, "trigger", "") != "steps_low":
        return Response({"ok": False, "error": "SESSION_CLOSED"}, status=409)

    # 날씨 게이트 (+ 요청 바디 오버라이드)
    override_kind = None
    if "weather_kind" in body:
        k = str(body["weather_kind"]).lower()
        if k in {"clear","clouds","rain","snow","thunder","dust"}:
            override_kind = k  # type: ignore
    kind, outdoor_ok = get_weather_gateway().get_kind_and_gate(lat, lng, override_kind=override_kind)

    # 후보 쿼리: 실외/실내 중 하나 선택
    if outdoor_ok:
        qset = PlaceOutside.objects.filter(is_active=True)
        place_type = "outside"
    else:
        qset = PlaceInside.objects.filter(is_active=True)
        place_type = "inside"

    # 바운딩 박스 프리필터
    lat_delta = max_distance_km / 111.0
    lng_delta = max_distance_km / max(1e-6, (111.0 * cos(radians(lat))))
    qset = qset.filter(
        lat__isnull=False, lon__isnull=False,
        lat__gte=lat - lat_delta, lat__lte=lat + lat_delta,
        lon__gte=lng - lng_delta, lon__lte=lng + lng_delta
    )[:500]

    # 최근 14일 중복 제거 (PlaceExposure 있으면 사용)
    since = datetime.now(timezone.utc) - timedelta(days=14)
    recent_ids = set()
    try:
        from api.models import PlaceExposure
        recent_ids = set(
            PlaceExposure.objects.filter(
                user_ref=user_ref, place_type=place_type, created_at__gte=since
            ).values_list("place_id", flat=True)
        )
    except Exception:
        recent_ids = set()

    # 거리 계산/정렬 및 필터 적용
    items = []
    for r in qset:
        if r.id in recent_ids:
            continue
        d = _haversine_km(lat, lng, float(r.lat), float(r.lon))
        if d <= max_distance_km:
            items.append({"obj": r, "distance_km": d})
    items.sort(key=lambda x: x["distance_km"])

    picked = items[:limit]

    # 노출 로그 저장 (PlaceExposure 있으면 기록)
    try:
        from api.models import PlaceExposure
        for it in picked:
            PlaceExposure.objects.create(user_ref=user_ref, place_type=place_type, place_id=it["obj"].id)
    except Exception:
        pass

    # 응답 변환 (+ 주소/카테고리 추가)
    resp_items = []
    for rank, it in enumerate(picked, start=1):
        p = it["obj"]
        title = getattr(p, "name", "") or getattr(p, "title", "")
        addr_road = (getattr(p, "address_road", "") or "").strip()
        addr_jibun = (getattr(p, "address", "") or "").strip()
        addr = addr_road or addr_jibun
        resp_items.append({
            "content_id": p.id,
            "title": title,
            "lat": float(p.lat),
            "lng": float(p.lon),
            "distance_km": round(it["distance_km"], 2),
            "rank": rank,
            "reason": "distance",
            "weather_gate": "OK" if outdoor_ok else "INDOOR",
            "address": addr,
            "address_road": addr_road or None,
            "address_jibun": addr_jibun or None,
            "place_category": (getattr(p, "category", "") or None),
        })

    # 후보 0건이면 실내 대체 카드 반환 (fallback)
    if not resp_items:
        return Response({
            "ok": True,
            "session_id": str(sess.id),
            "category": "OUTING",
            "items": [],
            "fallback_used": True,
            "safe_templates": [
                {"category":"WALK","title":"실내 워킹 10분"},
                {"category":"STRETCHING","title":"전신 스트레칭 5분"}
            ],
            "weather": {"kind": kind, "outdoor_ok": outdoor_ok}
        }, status=200)

    return Response({
        "ok": True,
        "session_id": str(sess.id),
        "category": "OUTING",
        "items": resp_items,
        "fallback_used": False,
        "weather": {"kind": kind, "outdoor_ok": outdoor_ok}
    }, status=200)

# ── /v1/feedback ─────────────────────────────────────────────────────────────
@csrf_exempt
def feedback(request: HttpRequest):
    """명세서 준수: { "ok": true } 만 반환
       steps_low 세션은 No-Op (로그/학습 비반영)"""
    if request.method != "POST":
        return JsonResponse({"error": "method_not_allowed"}, status=405)
    if not _auth_ok(request):
        return JsonResponse({"detail": "invalid app token"}, status=401)

    body = _json(request)
    if not {"user_ref", "type"}.issubset(body):
        return JsonResponse({"ok": False, "error": "missing_fields"}, status=400)

    user_ref = body["user_ref"]
    ftype = body.get("type")
    session_obj = None
    content_obj = None
    item_rec_obj = None

    sid = body.get("session_id")
    if sid:
        try:
            session_obj = RecommendationSession.objects.filter(id=uuid.UUID(str(sid))).first()
        except Exception:
            session_obj = None

    # steps_low 세션은 No-Op
    if session_obj and session_obj.trigger == "steps_low":
        return JsonResponse({"ok": True}, status=200)

    cid = body.get("content_id")
    if cid:
        try:
            content_obj = Content.objects.filter(id=int(cid)).first()
        except Exception:
            content_obj = None

    if session_obj and content_obj:
        item_rec_obj = ItemRec.objects.filter(session=session_obj, content=content_obj).order_by("score").last()
        if not item_rec_obj:
            item_rec_obj = ItemRec.objects.filter(session=session_obj).order_by("score").last()

    FeedbackModel.objects.create(
        user_ref=user_ref if hasattr(FeedbackModel, "user_ref") else None,
        session=session_obj,
        item_rec=item_rec_obj,
        content=content_obj,
        type=ftype,
        value=body.get("value"),
        dwell_ms=body.get("dwell_ms"),
        watched_pct=body.get("watched_pct"),
    )

    if ftype == "EFFECT":
        hr_b, hr_a = body.get("hr_before"), body.get("hr_after_30m")
        st_b, st_a = body.get("stress_before"), body.get("stress_after_30m")
        eff = body.get("effect")
        if hr_b is not None and hr_a is not None:
            OutcomeModel.objects.create(
                session=session_obj, item_rec=item_rec_obj, content=content_obj,
                outcome_type="hr_drop", before=hr_b, after=hr_a, delta=(hr_a - hr_b), effect=eff
            )
        if st_b is not None and st_a is not None:
            OutcomeModel.objects.create(
                session=session_obj, item_rec=item_rec_obj, content=content_obj,
                outcome_type="stress_drop", before=st_b, after=st_a, delta=(st_a - st_b), effect=eff
            )

    return JsonResponse({"ok": True}, status=200)

# ── CBV → DRF APIView 래퍼 ───────────────────────────────────────────────────
@method_decorator(csrf_exempt, name="dispatch")
class TelemetryView(APIView):
    @extend_schema(
        tags=["ai"],
        summary="웨어러블 텔레메트리 평가",
        description=(
            "착용 데이터(심박/스트레스 등)를 받아 이상 여부 판단.\n"
            "- mode: normal | restrict | emergency\n"
            "- restrict일 때 추천용 session_id와 categories 반환\n"
            "- emergency일 때 즉시 액션 템플릿 반환(세션/카테고리 없음)\n"
            "- `metrics` 또는 상위 필드 `hr`, `stress` 사용 가능"
        ),
        parameters=[APP_TOKEN_HEADER],
        request=inline_serializer(
            name="TelemetryReq",
            fields={
                "user_ref": serializers.CharField(required=False, help_text="기본 u1"),
                "ts": serializers.DateTimeField(required=False, help_text="ISO8601(+offset). 미전달시 서버 now"),
                "metrics": serializers.DictField(child=serializers.FloatField(), required=False,
                                                 help_text='예: {"hr":142,"stress":0.62}'),
                "hr": serializers.FloatField(required=False, allow_null=True),
                "stress": serializers.FloatField(required=False, allow_null=True),
                "gestational_week": serializers.IntegerField(required=False, min_value=0),
            },
        ),
        responses={
            200: OpenApiResponse(
                response=OpenApiTypes.OBJECT,
                description="normal / restrict / emergency 모두 200으로 응답",
                examples=[
                    OpenApiExample(
                        "normal",
                        value={"ok": True, "anomaly": False, "risk_level": "low", "mode": "normal"},
                    ),
                    OpenApiExample(
                        "restrict",
                        value={
                            "ok": True, "anomaly": True, "risk_level": "high", "mode": "restrict",
                            "reasons": ["z(hr)=3.1"],
                            "recommendation": {
                                "session_id": "2c3ef0f0-1111-2222-3333-abcdefabcdef",
                                "categories": [
                                    {"category": "BREATHING", "rank": 1, "reason": "hr high"},
                                    {"category": "MUSIC", "rank": 2, "reason": "hr high"}
                                ]
                            }
                        },
                    ),
                    OpenApiExample(
                        "emergency",
                        value={
                            "ok": True, "anomaly": True, "risk_level": "critical", "mode": "emergency",
                            "reasons": ["hr>=150 sustained"],
                            "action": {"type": "EMERGENCY_CONTACT", "cooldown_min": 60},
                            "safe_templates": [
                                {"category": "BREATHING", "title": "안전 호흡 3분"},
                                {"category": "BREATHING", "title": "안전 호흡 5분"}
                            ]
                        },
                    ),
                ],
            ),
            401: OpenApiResponse(description="invalid app token"),
            405: OpenApiResponse(description="method not allowed"),
        },
    )
    def post(self, request, *args, **kwargs):
        return telemetry(request._request)  # 기존 로직 재사용

    def get(self, request, *args, **kwargs):
        return Response({"error": "method_not_allowed"}, status=405)


@method_decorator(csrf_exempt, name="dispatch")
class FeedbackView(APIView):
    @extend_schema(
        tags=["ai"],
        summary="추천 피드백 수집",
        description=(
            "사용자 클릭/완료/효과(EFFECT) 피드백을 수집합니다.\n"
            "- steps_low 세션은 학습/로그 비반영(No-Op)\n"
            "- EFFECT: hr/stress before/after 값을 Outcome으로 기록"
        ),
        parameters=[APP_TOKEN_HEADER],
        request=inline_serializer(
            name="FeedbackReq",
            fields={
                "user_ref": serializers.CharField(),
                "type": serializers.ChoiceField(choices=["CLICK","COMPLETE","EFFECT"]),
                "session_id": serializers.CharField(required=False),
                "content_id": serializers.IntegerField(required=False),
                "value": serializers.CharField(required=False),
                "dwell_ms": serializers.IntegerField(required=False),
                "watched_pct": serializers.FloatField(required=False),
                "hr_before": serializers.FloatField(required=False),
                "hr_after_30m": serializers.FloatField(required=False),
                "stress_before": serializers.FloatField(required=False),
                "stress_after_30m": serializers.FloatField(required=False),
                "effect": serializers.CharField(required=False),
            },
        ),
        responses={
            200: OpenApiResponse(
                response=OpenApiTypes.OBJECT,
                description="ok",
                examples=[OpenApiExample("ok", value={"ok": True})],
            ),
            400: OpenApiResponse(description="missing_fields"),
            401: OpenApiResponse(description="invalid app token"),
        },
    )
    def post(self, request, *args, **kwargs):
        return feedback(request._request)

    def get(self, request, *args, **kwargs):
        return Response({"error": "method_not_allowed"}, status=405)


@method_decorator(csrf_exempt, name="dispatch")
class StepsCheckView(APIView):
    @extend_schema(
        tags=["ai"],
        summary="누적 걸음수 저활동 판정",
        description=(
            "고정 시각(12/16/20 KST) 누적 걸음수로 저활동 판단 → restrict 세션 발급.\n"
            "- 기준: 동시간대 평균(mu) 대비 부족분이 threshold(기본 500) 이상이면 저활동\n"
            "- 동일 일자·버킷 재호출 시 기존 session 재사용"
        ),
        parameters=[APP_TOKEN_HEADER],
        request=inline_serializer(
            name="StepsCheckReq",
            fields={
                "user_ref": serializers.CharField(),
                "ts": serializers.DateTimeField(required=False),
                "cum_steps": serializers.IntegerField(),
            },
        ),
        responses={
            200: OpenApiResponse(
                response=OpenApiTypes.OBJECT,
                description="normal 또는 no_baseline / restrict도 200으로 응답",
                examples=[
                    OpenApiExample("normal", value={"ok": True, "anomaly": False, "mode": "normal"}),
                    OpenApiExample("no_baseline", value={"ok": True, "anomaly": False, "mode": "normal", "note": "no_baseline"}),
                    OpenApiExample(
                        "restrict",
                        value={
                            "ok": True,
                            "anomaly": True,
                            "mode": "restrict",
                            "trigger": "steps_low",
                            "reasons": [
                                "steps_diff=600 (threshold=500, bucket=3)",
                                "cum_steps=1200, mu=1800"
                            ],
                            "recommendation": {
                                "session_id": "9c5a2c77-aaaa-bbbb-cccc-1234567890ab",
                                "categories": [
                                    {"category": "WALK", "rank": 1, "reason": "steps low vs avg"},
                                    {"category": "OUTING", "rank": 2, "reason": "steps low vs avg"}
                                ]
                            }
                        },
                    ),
                ],
            ),
            400: OpenApiResponse(description="INVALID_BODY"),
            401: OpenApiResponse(description="invalid app token"),
        },
    )
    def post(self, request, *args, **kwargs):
        return steps_check(request._request)

    def get(self, request, *args, **kwargs):
        return Response({"error": "method_not_allowed"}, status=405)
