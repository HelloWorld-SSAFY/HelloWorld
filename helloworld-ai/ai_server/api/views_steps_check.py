# api/views_steps_check.py
import uuid
from typing import Dict, Any, Optional
from datetime import timedelta, datetime

from django.http import HttpRequest
from rest_framework.views import APIView
from rest_framework.response import Response
from drf_spectacular.utils import (
    extend_schema,
    PolymorphicProxySerializer,
)

# 내부에서 정의된 스키마/유틸 재사용 (중복 방지)
from .views import (
    # 공통 스웨거 헤더
    APP_TOKEN_PARAM, COUPLE_ID_PARAM, AUTH_HEADER_PARAM,
    # 요청/응답 스키마
    StepsCheckIn, StepsNormalResp, StepsRestrictResp,
    # 공통 유틸
    _assert_app_token, _require_user_ref, _access_token_from_request,
    _slot_for, _parse_couple_id, _fetch_steps_overall_avg,
    _run_places_delivery,
)

from services.policy_service import categories_for_trigger
from services.anomaly import KST
from api.models import RecommendationSession, UserStepsTodStatsDaily

# ---- 정책 상수 (env 안 씀) ----
STEPS_GAP_THRESHOLD = 500  # avg - cum_steps ≥ 500 → restrict

class StepsCheckView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM, COUPLE_ID_PARAM, AUTH_HEADER_PARAM],
        request=StepsCheckIn,
        responses={
            200: PolymorphicProxySerializer(
                component_name="StepsCheckResponse",
                resource_type_field_name="mode",
                serializers={"normal": StepsNormalResp, "restrict": StepsRestrictResp},
                many=False,
            )
        },
        tags=["steps"],
        summary="Compare cum_steps to avg (prefer body.avg_steps; fallback: external/stored); restrict if gap ≥ 500",
        operation_id="postStepsCheck",
        description=(
            "바디 avg_steps가 있으면 그 값을 최우선으로 사용하고, 없으면 메인서버 평균 → 저장값 순으로 폴백. "
            "(avg - cum_steps) ≥ 500이면 장소 추천을 내부에서 미리 실행해 recommend_delivery/PlaceExposure에 저장(응답엔 미포함)."
        ),
    )
    def post(self, request: HttpRequest):
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = StepsCheckIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        user_ref, missing = _require_user_ref(request, d.get("user_ref"))
        if missing:
            return missing

        access_token = _access_token_from_request(request)

        # user_ctx 구성(장소 프리컴퓨트에 사용)
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

        ts = d["ts"]
        try:
            ts_kst = ts.astimezone(KST)
        except Exception:
            ts_kst = ts
        slot = _slot_for(ts_kst)
        bucket = ts_kst.hour // 4

        # 1) body.avg_steps 최우선
        avg: Optional[float] = None
        source = None
        if d.get("avg_steps") is not None:
            try:
                avg = float(d["avg_steps"])
                source = "body"
            except Exception:
                avg = None

        # 2) 외부 평균 (바디 없을 때만)
        if avg is None:
            ext_avg = None
            couple_id_for_external = _parse_couple_id(request, user_ref)
            if couple_id_for_external is not None:
                ext_avg = _fetch_steps_overall_avg(
                    couple_id=couple_id_for_external, slot=slot, access_token=access_token
                )
            if ext_avg is not None:
                avg = float(ext_avg)
                source = "external"

        # 3) 저장값 폴백
        if avg is None:
            baseline = UserStepsTodStatsDaily.objects.filter(
                user_ref=user_ref, d=ts_kst.date(), bucket=bucket
            ).first()
            if baseline and baseline.cum_mu is not None:
                avg = float(baseline.cum_mu or 0.0)
                source = f"stored_bucket{bucket}"

        if avg is None:
            # 기준이 없으면 정상으로 처리
            return Response({"ok": True, "anomaly": False, "mode": "normal"})

        cum_steps = int(d["cum_steps"])
        gap = max(0.0, avg - float(cum_steps))

        if gap >= STEPS_GAP_THRESHOLD:
            # 카테고리 정책
            cats = categories_for_trigger("steps_low") or []
            session = RecommendationSession.objects.create(
                user_ref=user_ref, trigger="steps_low", mode="restrict", context={}
            )

            # 세션 컨텍스트 기록
            cat_payload = []
            if cats:
                cats_sorted = sorted(cats, key=lambda x: x.get("priority", 999))
                for i, c in enumerate(cats_sorted, 1):
                    item = {"category": c["code"], "rank": i, "reason": "steps low vs avg"}
                    cat_payload.append(item)

            ctx = {
                "session_id": str(session.id),
                "categories": cat_payload,
                "user_ctx": user_ctx,
                "ts": ts_kst.isoformat(),
            }
            try:
                session.set_context(ctx, save=True)
            except Exception:
                try:
                    session.update_context(ctx, save=True)
                except Exception:
                    session.context = ctx
                    session.save(update_fields=["context"])

            # ✅ 장소 프리컴퓨트 + 저장
            try:
                _run_places_delivery(session=session, user_ref=user_ref, ctx=user_ctx or {})
            except Exception:
                # 로깅만, 응답 포맷 영향 없음
                import logging
                logging.getLogger(__name__).exception("steps_low places delivery failed (user=%s)", user_ref)

            src_tag = source or "unknown"
            reasons = [f"avg-cum \u2265 {STEPS_GAP_THRESHOLD} (\u0394={int(gap)}) src:{src_tag} @{slot}"]
            return Response({
                "ok": True,
                "anomaly": True,
                "mode": "restrict",
                "trigger": "steps_low",
                "reasons": reasons,
                "recommendation": {
                    "session_id": str(session.id),
                    "categories": cat_payload,
                },
            })

        # 정상
        return Response({"ok": True, "anomaly": False, "mode": "normal"})
