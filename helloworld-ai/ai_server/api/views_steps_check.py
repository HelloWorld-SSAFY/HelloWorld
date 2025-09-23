# api/views_steps_check.py
from __future__ import annotations
from django.utils import timezone
from django.utils.dateparse import parse_datetime
from rest_framework.views import APIView
from rest_framework.response import Response

# 🔽 스키마용 추가 import
from rest_framework import serializers
from drf_spectacular.utils import (
    extend_schema, inline_serializer, OpenApiParameter, OpenApiTypes
)

from services.steps_check import check_steps_low, KST


# ─────────────────────────────────────────────────────────────
# Swagger 헤더 파라미터 정의(이 모듈에 직접 선언)
# ─────────────────────────────────────────────────────────────
APP_TOKEN_PARAM = OpenApiParameter(
    name="X-App-Token",
    type=OpenApiTypes.STR,
    location=OpenApiParameter.HEADER,
    required=True,
    description="App-level token (.env: APP_TOKEN). 미들웨어에서 검증"
)

COUPLE_ID_PARAM = OpenApiParameter(
    name="X-Couple-Id",
    type=OpenApiTypes.INT,
    location=OpenApiParameter.HEADER,
    required=False,  # 바디로도 받을 수 있으니 optional
    description="커플 ID. 헤더 또는 바디(couple_id)로 전달 가능"
)


class StepsCheckView(APIView):
    """
    POST /v1/steps-check
    바디: { user_ref, ts, cum_steps(or steps), couple_id? }
    판정 규칙: baseline(동시간대 평균, 어제까지)과의 차이가 500 이상 부족하면 steps_low
    """

    # 🔽 문서 스키마만 추가(런타임 영향 없음)
    @extend_schema(
        tags=["steps"],
        summary="누적 걸음수 저활동 판정",
        description=(
            "현재까지 누적 걸음수로 저활동 여부를 판정합니다. "
            "헤더의 `X-Couple-Id` 또는 바디의 `couple_id` 중 하나로 커플을 식별합니다. "
            "토큰은 `X-App-Token` 헤더로 전달하세요."
        ),
        parameters=[APP_TOKEN_PARAM, COUPLE_ID_PARAM],
        operation_id="postStepsCheck",
        request=inline_serializer(
            name="StepsCheckRequest",
            fields={
                "user_ref": serializers.CharField(required=False),
                "ts": serializers.DateTimeField(required=False, help_text="ISO8601 (예: 2025-09-23T00:00:00Z)"),
                "cum_steps": serializers.IntegerField(required=False, help_text="현재까지 누적 걸음수(우선)"),
                "steps": serializers.IntegerField(required=False, help_text="cum_steps 없을 때 대체 키"),
                "couple_id": serializers.IntegerField(required=False, help_text="헤더 대신 바디로 보낼 때 사용"),
            },
        ),
        responses={
            200: inline_serializer(
                name="StepsCheckResponse",
                fields={
                    "ok": serializers.BooleanField(),
                    "status": serializers.ChoiceField(choices=["normal", "steps_low"]),
                    "session_id": serializers.CharField(required=False, help_text="steps_low일 때만 생성"),
                    "categories": serializers.ListField(
                        child=serializers.CharField(), required=False,
                        help_text='steps_low일 때 ["WALK","OUTING"]'
                    ),
                    "meta": inline_serializer(
                        name="StepsCheckMeta",
                        fields={
                            "bucket": serializers.CharField(),
                            "baseline": serializers.IntegerField(),
                            "steps": serializers.IntegerField(),
                            "decision": serializers.CharField(),
                            "main": serializers.CharField(),
                            "ts_kst": serializers.CharField(),
                        },
                    ),
                },
            ),
            400: inline_serializer(
                name="StepsCheckBadRequest",
                fields={"ok": serializers.BooleanField(), "error": serializers.CharField()},
            ),
        },
    )
    def post(self, request):
        body = request.data or {}

        # couple_id: 헤더/바디 모두 허용 (WSGI 변형 헤더도 수용)
        couple_id = (
            body.get("couple_id")
            or request.headers.get("X-Couple-Id")
            or request.META.get("HTTP_X_COUPLE_ID")
        )
        if couple_id is None:
            return Response({"ok": False, "error": "missing couple_id"}, status=400)
        try:
            couple_id = int(couple_id)
        except Exception:
            return Response({"ok": False, "error": "invalid couple_id"}, status=400)

        # 누적 걸음수 키 수용(cum_steps 우선)
        steps = body.get("cum_steps", body.get("steps", 0))
        try:
            steps = int(steps)
        except Exception:
            steps = 0

        # ts → KST
        ts_str = body.get("ts")
        dt = parse_datetime(ts_str) if ts_str else None
        ts_kst = (dt.astimezone(KST) if dt and dt.tzinfo else timezone.localtime())

        result = check_steps_low(couple_id=couple_id, cum_steps=steps, ts_kst=ts_kst)

        if result["status"] == "steps_low":
            import uuid
            session_id = str(uuid.uuid4())
            return Response({
                "ok": True,
                "status": "steps_low",
                "session_id": session_id,
                "categories": ["WALK", "OUTING"],
                "meta": {
                    "bucket": result["bucket"],
                    "baseline": result["baseline"],
                    "steps": steps,
                    "decision": result["decision"],
                    "main": result["main"],
                    "ts_kst": result["ts_kst_iso"],
                }
            })

        return Response({
            "ok": True,
            "status": "normal",
            "meta": {
                "bucket": result["bucket"],
                "baseline": result["baseline"],
                "steps": steps,
                "decision": result["decision"],
                "main": result["main"],
                "ts_kst": result["ts_kst_iso"],
            }
        })
