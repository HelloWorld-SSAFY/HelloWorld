# api/views.py
import os
import json
import uuid
from datetime import datetime, timezone, date

from django.http import JsonResponse, HttpRequest
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from django.views import View

from services.anomaly import AnomalyDetector, AnomalyConfig, KST
from services.orm_stats_provider import OrmStatsProvider
from services.policy_service import categories_for_trigger  # DB 우선 + 폴백

from api.models import (
    Content,
    RecommendationSession,
    ExposureCandidate,
    ItemRec,
    Feedback as FeedbackModel,
    Outcome as OutcomeModel,
)

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

# ── 유틸 ─────────────────────────────────────────────────────────────────────
def _json(request: HttpRequest):
    try:
        return json.loads(request.body.decode("utf-8"))
    except Exception:
        return {}

def _iso_to_utc(ts_str: str):
    # "2025-09-16T10:00:00+09:00" / "...Z" 모두 지원
    return datetime.fromisoformat(ts_str.replace("Z", "+00:00")).astimezone(timezone.utc)

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
    # 카테고리 사유 간단 라벨
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
def healthz(request: HttpRequest):
    return JsonResponse({"status": "ok", "model": "rec_v0.1"})

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

        # 트리거: 엔진 제공값 우선 → 추론(엔진이 trigger 지원)
        trigger_code = getattr(result, "trigger", None) or _infer_trigger_code(reasons, metrics)

        # emergency
        if getattr(result, "mode", None) == "emergency":
            # v1 명세: emergency는 세션/카테고리 없이 액션/템플릿만
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

# ── /v1/feedback ─────────────────────────────────────────────────────────────
@csrf_exempt
def feedback(request: HttpRequest):
    """명세서 준수: { "ok": true } 만 반환"""
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

# ── CBV 래퍼 ─────────────────────────────────────────────────────────────────
@method_decorator(csrf_exempt, name="dispatch")
class TelemetryView(View):
    def post(self, request, *args, **kwargs):
        return telemetry(request)
    def get(self, request, *args, **kwargs):
        return JsonResponse({"error": "method_not_allowed"}, status=405)

@method_decorator(csrf_exempt, name="dispatch")
class FeedbackView(View):
    def post(self, request, *args, **kwargs):
        return feedback(request)
    def get(self, request, *args, **kwargs):
        return JsonResponse({"error": "method_not_allowed"}, status=405)
