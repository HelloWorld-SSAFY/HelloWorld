# api/views_steps_check.py
from __future__ import annotations
from django.utils import timezone
from django.utils.dateparse import parse_datetime
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import serializers
from drf_spectacular.utils import extend_schema, inline_serializer, OpenApiParameter, OpenApiTypes

from services.steps_check import check_steps_low, KST

# ── Swagger 헤더 파라미터
APP_TOKEN_PARAM = OpenApiParameter(
    name="X-App-Token", type=OpenApiTypes.STR,
    location=OpenApiParameter.HEADER, required=True,
    description="App-level token (.env: APP_TOKEN). 미들웨어에서 검증"
)

COUPLE_ID_PARAM = OpenApiParameter(
    name="X-Couple-Id", type=OpenApiTypes.INT,
    location=OpenApiParameter.HEADER, required=False,
    description="커플 ID. 헤더 또는 바디(couple_id)로 전달 가능"
)

# ── 요청/응답 스키마(문서 전용)
class _StepsCheckReq(serializers.Serializer):
    ts = serializers.DateTimeField(required=True, help_text="ISO8601 (예: 2025-09-23T05:08:00Z)")
    cum_steps = serializers.IntegerField(required=True, help_text="현재까지의 '하루 누적' 걸음수")
    lat = serializers.FloatField(required=True, help_text="위도(-90~90)")
    lng = serializers.FloatField(required=True, help_text="경도(-180~180)")
    couple_id = serializers.IntegerField(required=False, help_text="헤더 대신 바디로 보낼 때만")
    limit = serializers.IntegerField(required=False, help_text="장소 추천 개수(기본 3)")

_PlacesItem = inline_serializer(
    name="PlacesItem",
    fields={
        "name": serializers.CharField(),
        "lat": serializers.FloatField(),
        "lng": serializers.FloatField(),
        "distance_m": serializers.IntegerField(required=False),
        "air_quality": serializers.CharField(required=False),
        "weather": serializers.CharField(required=False),
        "safety": serializers.CharField(required=False),
    },
)

class StepsCheckView(APIView):
    """
    POST /v1/steps-check
    입력: ts, cum_steps, lat, lng, (couple_id|X-Couple-Id), limit?
    동작: 저활동(steps_low)일 때 내부적으로 장소 추천까지 수행하여 같은 응답에 포함
    """

    @extend_schema(
        tags=["steps"],
        summary="누적 걸음수 저활동 판정(+ 필요 시 장소 추천)",
        description=(
            "`ts/cum_steps/lat/lng` 을 필수로 받습니다. "
            "커플 식별은 `X-Couple-Id` 헤더 또는 바디 `couple_id` 중 하나를 사용합니다. "
            "판정이 `steps_low`이면 내부에서 장소 추천을 수행하여 `places`를 포함해 반환합니다. "
            "(limit 기본값=3)"
        ),
        parameters=[APP_TOKEN_PARAM, COUPLE_ID_PARAM],
        operation_id="postStepsCheck",
        request=_StepsCheckReq,
        responses={
            200: inline_serializer(
                name="StepsCheckResponse",
                fields={
                    "ok": serializers.BooleanField(),
                    "status": serializers.ChoiceField(choices=["normal", "steps_low"]),
                    "session_id": serializers.CharField(required=False, help_text="steps_low일 때만 생성"),
                    "categories": serializers.ListField(child=serializers.CharField(), required=False),
                    "places": serializers.ListField(child=_PlacesItem, required=False),
                    "places_meta": inline_serializer(
                        name="PlacesMeta",
                        fields={
                            "limit": serializers.IntegerField(required=False),
                            "used_location": serializers.BooleanField(required=False),
                        },
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

        # couple_id: 헤더/바디 모두 허용
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

        # 필수값 파싱/검증
        ts_str = body.get("ts")
        if not ts_str:
            return Response({"ok": False, "error": "missing ts"}, status=400)
        dt = parse_datetime(ts_str)
        if not dt:
            return Response({"ok": False, "error": "invalid ts"}, status=400)
        ts_kst = (dt.astimezone(KST) if dt.tzinfo else timezone.localtime())

        try:
            steps = int(body.get("cum_steps"))
        except Exception:
            return Response({"ok": False, "error": "invalid cum_steps"}, status=400)

        try:
            lat = float(body.get("lat"))
            lng = float(body.get("lng"))
        except Exception:
            return Response({"ok": False, "error": "invalid lat/lng"}, status=400)
        if not (-90.0 <= lat <= 90.0 and -180.0 <= lng <= 180.0):
            return Response({"ok": False, "error": "lat/lng out of range"}, status=400)

        try:
            limit = int(body.get("limit", 3))
        except Exception:
            limit = 3
        limit = max(1, min(limit, 20))  # 간단한 가드

        # 판정
        result = check_steps_low(couple_id=couple_id, cum_steps=steps, ts_kst=ts_kst)

        base_payload = {
            "ok": True,
            "meta": {
                "bucket": result["bucket"],
                "baseline": result["baseline"],
                "steps": steps,
                "decision": result["decision"],
                "main": result["main"],
                "ts_kst": result["ts_kst_iso"],
            }
        }

        if result["status"] != "steps_low":
            base_payload["status"] = "normal"
            return Response(base_payload)

        # steps_low → 내부 장소 추천 수행
        import uuid
        session_id = str(uuid.uuid4())
        places = []
        places_meta = {"limit": limit, "used_location": True}

        try:
            from services.places_service import recommend_places
            places, pm = recommend_places(
                lat=lat, lng=lng, limit=limit, ts_kst=ts_kst, couple_id=couple_id
            )
            if isinstance(pm, dict):
                places_meta.update(pm)
        except Exception as e:
            # 실패해도 steps_low 응답은 주되, places는 생략/빈 리스트
            places = []
            places_meta["error"] = "places_unavailable"

        base_payload.update({
            "status": "steps_low",
            "session_id": session_id,
            "categories": ["WALK", "OUTING"],
            "places": places,
            "places_meta": places_meta,
        })
        return Response(base_payload)
