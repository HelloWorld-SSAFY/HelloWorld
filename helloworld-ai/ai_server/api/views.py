# api/views.py
import json
import uuid
from datetime import datetime, timezone

from django.http import JsonResponse, HttpRequest
from django.views.decorators.csrf import csrf_exempt

from services.anomaly import AnomalyDetector, AnomalyConfig, KST
from services.orm_stats_provider import OrmStatsProvider  # DB 기준선 Provider

# 저장용 모델
from api.models import (
    Content,
    RecommendationSession,
    ExposureCandidate,
    ItemRec,
    Feedback as FeedbackModel,
    Outcome as OutcomeModel,
)

# ── 전역 싱글턴: DB 기준선 + 연속/지속 상태 유지 ─────────────────────────────
_config = AnomalyConfig()
_provider = OrmStatsProvider()
_detector = AnomalyDetector(_provider, _config)


def _json(request: HttpRequest):
    try:
        return json.loads(request.body.decode("utf-8"))
    except Exception:
        return {}


def healthz(request: HttpRequest):
    return JsonResponse({"status": "ok", "model": "rec_v0.1"})


@csrf_exempt
def telemetry(request: HttpRequest):
    """
    POST /v1/telemetry
    - 이상/응급 판단
    - restrict/emergency 시 DB 로깅:
        * emergency: recommendation_session 1행만 (session_id 응답에 미포함)
        * restrict: recommendation_session + exposure_candidate + item_rec 생성
                    (session_id 응답에 포함)
    """
    if request.method != "POST":
        return JsonResponse({"error": "method_not_allowed"}, status=405)

    body = _json(request)
    user_ref = body.get("user_ref") or "u1"
    ts_str = body.get("ts")
    metrics = body.get("metrics") or {}

    # ts 파싱 (UTC ISO8601 기대)
    try:
        ts_utc = datetime.fromisoformat(ts_str.replace("Z", "+00:00")) if ts_str else datetime.now(timezone.utc)
    except Exception:
        return JsonResponse({"ok": False, "error": "invalid_ts"}, status=400)

    # 판단
    res = _detector.evaluate(user_ref=user_ref, ts_utc=ts_utc, metrics=metrics)

    # 공통 컨텍스트(로그용)
    kst = ts_utc.astimezone(KST)
    context = {
        "ts_kst": kst.isoformat(),
        "as_of": str(kst.date()),
        "bucket_4h": kst.hour // 4,   # 0..5
        "reasons": list(res.reasons),
        "cfg": {
            "z_anomaly_threshold": _config.z_anomaly_threshold,
            "consecutive_z_required": _config.consecutive_z_required,
            "restrict_cooldown_sec": _config.restrict_cooldown_sec,
        },
        "metrics_keys": list(metrics.keys()),
    }

    # ── EMERGENCY: 세션만 로깅, session_id는 응답에 미포함 ─────────────
    if res.mode == "emergency":
        RecommendationSession.objects.create(
            user_ref=user_ref,
            mode="emergency",
            trigger="anomaly",
            context=context,
        )
        return JsonResponse({
            "ok": True,
            "anomaly": True,
            "risk_level": "critical",
            "mode": "emergency",
            "reasons": list(res.reasons),
            "action": {"type": "EMERGENCY_CONTACT", "cooldown_min": 60},
            "safe_templates": [
                {"category": "BREATHING", "title": "안전 호흡 3분"},
                {"category": "BREATHING", "title": "안전 호흡 5분"},
            ],
        })

    # ── RESTRICT: 세션 + 후보/아이템 로깅, session_id 응답 포함 ──────────
    if res.anomaly:
        # 세션 생성
        session = RecommendationSession.objects.create(
            user_ref=user_ref,
            mode="restrict",
            trigger="anomaly",
            context=context,
        )

        # 간단한 더미 추천(카테고리/아이템) — 콘텐츠 테이블과 매핑 가능하도록 'sp:trk:*' 사용
        #  * Content가 없다면 생성(get_or_create)
        #  * ExposureCandidate 로깅(선택), ItemRec 저장
        # BREATHING (rank=1)
        cont1, _ = Content.objects.get_or_create(
            provider="sp",
            external_id="trk:123",
            defaults={"category": "BREATHING", "title": "4-7-8 호흡", "url": "https://example.com/breath1", "is_active": True},
        )
        # MEDITATION (rank=2)
        cont2, _ = Content.objects.get_or_create(
            provider="sp",
            external_id="trk:med1",
            defaults={"category": "MEDITATION", "title": "5분 명상", "url": "https://example.com/med1", "is_active": True},
        )

        # 후보(노출 풀) 로깅 — 간단히 선택된 2개만 chosen_flag=True로 기록
        ExposureCandidate.objects.create(session=session, content=cont1, pre_score=1.0, chosen_flag=True, x_item_vec={"demo": 1})
        ExposureCandidate.objects.create(session=session, content=cont2, pre_score=0.8, chosen_flag=True, x_item_vec={"demo": 1})

        # 최종 추천 N개
        ItemRec.objects.create(session=session, content=cont1, rank=1, score=1.0, reason="hr high" if "hr" in metrics else "stress high")
        ItemRec.objects.create(session=session, content=cont2, rank=2, score=0.8, reason="secondary")

        return JsonResponse({
            "ok": True,
            "anomaly": True,
            "risk_level": "high",
            "mode": "restrict",
            "reasons": list(res.reasons),
            "recommendation": {
                "session_id": str(session.id),   # ← 실제 세션 UUID를 반환
                "categories": [
                    {"category": "BREATHING", "rank": 1, "reason": "stress high" if "stress" in metrics else "hr high"},
                    {"category": "MEDITATION", "rank": 2}
                ],
                "items": [
                    {"category": "BREATHING", "title": "4-7-8 호흡", "url": "https://example.com/breath1"},
                    {"category": "MEDITATION", "title": "5분 명상", "url": "https://example.com/med1"}
                ]
            }
        })

    # ── 이상 아님: 필요 시 세션 로깅은 생략(운영 정책에 따라 on/off) ──────────
    return JsonResponse({
        "ok": True,
        "anomaly": False,
        "risk_level": "low",
        "mode": "normal",
    })


@csrf_exempt
def feedback(request: HttpRequest):
    """
    POST /v1/feedback
    - Feedback 1행 저장
    - EFFECT면 Outcome 1~2행(hr_drop / stress_drop) 저장
    - session/content/item_rec 매핑은 있으면 연결, 없으면 None 허용
    """
    if request.method != "POST":
        return JsonResponse({"error": "method_not_allowed"}, status=405)

    body = _json(request)
    required = {"user_ref", "session_id", "type"}
    if not required.issubset(body.keys()):
        return JsonResponse({"ok": False, "error": "missing_fields"}, status=400)

    user_ref = body["user_ref"]
    ftype = body.get("type")
    session_obj = None
    content_obj = None
    item_rec_obj = None

    # 세션 매핑(UUID면 연결)
    try:
        sid = uuid.UUID(str(body.get("session_id")))
        session_obj = RecommendationSession.objects.filter(id=sid).first()
    except Exception:
        session_obj = None

    # content 매핑(external_id 파싱)
    external_id_raw = body.get("external_id")
    if external_id_raw and isinstance(external_id_raw, str):
        provider, ext = (external_id_raw.split(":", 1) + [None])[:2] if ":" in external_id_raw else (None, external_id_raw)
        if provider and ext:
            content_obj = Content.objects.filter(provider=provider, external_id=ext).first()

    # item_rec 매핑(세션+컨텐츠가 있어야 1개 추정 가능)
    if session_obj and content_obj:
        item_rec_obj = ItemRec.objects.filter(session=session_obj, content=content_obj).order_by("rank").first()

    fb = FeedbackModel.objects.create(
        user_ref=user_ref,
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

    return JsonResponse({"ok": True})
