# api/views.py
import json
import uuid
from datetime import datetime, timezone, date

from django.http import JsonResponse, HttpRequest
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from django.views import View

from services.anomaly import AnomalyDetector, AnomalyConfig, KST
from services.orm_stats_provider import OrmStatsProvider

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


# ── 유틸 ─────────────────────────────────────────────────────────────────────
def _json(request: HttpRequest):
    try:
        return json.loads(request.body.decode("utf-8"))
    except Exception:
        return {}

def _iso_to_utc(ts_str: str):
    # "2025-09-16T10:00:00+09:00" / "...Z" 모두 지원
    return datetime.fromisoformat(ts_str.replace("Z", "+00:00")).astimezone(timezone.utc)

def _infer_trigger_type(reasons):
    t = " ".join(reasons or [])
    if "HR" in t:
        return "hr"
    if "Z>=" in t or "z>=" in t:
        return "z"
    return "unknown"

def _session_model_fields():
    # RecommendationSession 실제 필드 집합
    return {f.name for f in RecommendationSession._meta.get_fields()
            if getattr(f, "concrete", False) and not getattr(f, "auto_created", False)}

def _create_session_dynamic(*, mode: str, user_ref: str, trigger: str,
                            reasons, as_of_date: date, bucket4h: int):
    """
    모델 스키마에 맞춰 안전하게 세션 생성:
      - user_ref 없고 user_id만 있으면 user_id 사용
      - reasons/as_of/bucket_4h 없으면 context 칼럼(있을 때)로 저장
      - reasons가 TextField면 문자열로 대체
    """
    fields = _session_model_fields()
    kwargs = {"mode": mode, "trigger": trigger}

    # user 필드 매핑
    if "user_ref" in fields:
        kwargs["user_ref"] = user_ref
    elif "user_id" in fields:
        kwargs["user_id"] = user_ref

    # 가능한 개별 필드는 그대로
    have_reasons = "reasons" in fields
    have_as_of = "as_of" in fields
    have_bucket = "bucket_4h" in fields
    have_context = "context" in fields

    # 개별 필드가 있으면 넣고, 없으면 context로 모아서
    context_payload = {
        "reasons": reasons,
        "as_of": as_of_date.isoformat(),
        "bucket_4h": bucket4h,
    }

    if have_as_of:
        kwargs["as_of"] = as_of_date
    if have_bucket:
        kwargs["bucket_4h"] = bucket4h

    if have_reasons:
        # JSONField일 수도, TextField일 수도 있으니 시도 후 실패 시 문자열로
        try:
            kwargs["reasons"] = reasons
            return RecommendationSession.objects.create(**kwargs)
        except Exception:
            kwargs["reasons"] = " | ".join(map(str, reasons))
            return RecommendationSession.objects.create(**kwargs)
    else:
        if have_context:
            # context가 JSONField/TextField일 수 있음
            try:
                kwargs["context"] = context_payload
            except Exception:
                kwargs["context"] = json.dumps(context_payload, ensure_ascii=False)
        # context가 없으면 그냥 필드 없는 값은 버리고 저장
        return RecommendationSession.objects.create(**kwargs)


# ── /v1/healthz ──────────────────────────────────────────────────────────────
def healthz(request: HttpRequest):
    return JsonResponse({"status": "ok", "model": "rec_v0.1"})


# ── /v1/telemetry ────────────────────────────────────────────────────────────
@csrf_exempt
def telemetry(request: HttpRequest):
    """
    명세서 준수:
      REQUEST  { "user_ref": "...", "ts": "ISO8601", "metrics": {"hr":..., "stress":...} }
      RESPONSE normal/restrict/emergency (ok/anomaly/risk_level/mode/…)
    """
    if request.method != "POST":
        return JsonResponse({"error": "method_not_allowed"}, status=405)

    try:
        body = _json(request)

        # 입력 스키마 호환
        user_ref = body.get("user_ref") or body.get("user_id") or "u1"
        if body.get("ts"):
            ts_utc = _iso_to_utc(body["ts"])
        elif body.get("timestamp"):
            ts_utc = _iso_to_utc(body["timestamp"])
        else:
            ts_utc = datetime.now(timezone.utc)

        metrics = body.get("metrics") or {}
        if "hr" in body and body["hr"] is not None:
            metrics["hr"] = float(body["hr"])
        if "stress" in body and body["stress"] is not None:
            metrics["stress"] = float(body["stress"])

        # 판단
        result = _detector.evaluate(user_ref=user_ref, ts_utc=ts_utc, metrics=metrics)

        # 컨텍스트
        kst = ts_utc.astimezone(KST)
        as_of_date = kst.date()
        bucket4h = kst.hour // 4
        reasons = list(result.reasons)
        trigger_type = _infer_trigger_type(reasons)

        # emergency
        if result.mode == "emergency":
            _create_session_dynamic(
                mode="emergency", user_ref=user_ref, trigger=trigger_type,
                reasons=reasons, as_of_date=as_of_date, bucket4h=bucket4h
            )
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
        if result.mode == "restrict":
            session = _create_session_dynamic(
                mode="restrict", user_ref=user_ref, trigger=trigger_type,
                reasons=reasons, as_of_date=as_of_date, bucket4h=bucket4h
            )
            return JsonResponse({
                "ok": True,
                "anomaly": True,
                "risk_level": "high",
                "mode": "restrict",
                "reasons": reasons,
                "recommendation": {
                    "session_id": str(session.id),
                    "categories": [],
                    "items": []
                },
            }, status=200)

        # normal
        return JsonResponse({
            "ok": True,
            "anomaly": False,
            "risk_level": "low",
            "mode": "normal",
        }, status=200)

    except Exception as e:
        import logging
        logging.getLogger(__name__).exception("telemetry error")
        return JsonResponse({"detail": "internal error", "error": type(e).__name__, "message": str(e)}, status=500)


# ── /v1/feedback ─────────────────────────────────────────────────────────────
@csrf_exempt
def feedback(request: HttpRequest):
    """
    명세서 준수: { "ok": true } 만 반환
    """
    if request.method != "POST":
        return JsonResponse({"error": "method_not_allowed"}, status=405)

    body = _json(request)
    if not {"user_ref", "type"}.issubset(body):
        return JsonResponse({"ok": False, "error": "missing_fields"}, status=400)

    user_ref = body["user_ref"]
    ftype = body.get("type")
    session_obj = None
    content_obj = None
    item_rec_obj = None

    # 세션 매핑
    sid = body.get("session_id")
    if sid:
        try:
            session_obj = RecommendationSession.objects.filter(id=uuid.UUID(str(sid))).first()
        except Exception:
            session_obj = None

    # 콘텐츠 매핑(옵션)
    cid = body.get("content_id")
    if cid:
        try:
            content_obj = Content.objects.filter(id=int(cid)).first()
        except Exception:
            content_obj = None

    # 추천 아이템 매핑(옵션)
    if session_obj and content_obj:
        item_rec_obj = ItemRec.objects.filter(session=session_obj, content=content_obj).order_by("score").last()
        if not item_rec_obj:
            item_rec_obj = ItemRec.objects.filter(session=session_obj).order_by("score").last()

    # feedback 저장
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

    # EFFECT → outcome 저장
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
