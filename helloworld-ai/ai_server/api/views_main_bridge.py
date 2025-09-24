# api/views_main_bridge.py
from datetime import datetime
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import serializers
from drf_spectacular.utils import extend_schema

from services.anomaly import KST
from services.main_client import call_main
from services.stats_ingest import upsert_daily_buckets_payload

# 공용 유틸/스웨거 파라미터
from api.views import (
    _assert_app_token,
    APP_TOKEN_PARAM,
    COUPLE_ID_PARAM,
    ACCESS_TOKEN_PARAM,  # Swagger 문서에 Authorization/Access-Token 안내용
)

# ------------------------- 내부 유틸 -------------------------
def _strip_bearer(v):
    if not v:
        return None
    v = str(v).strip()
    low = v.lower()
    if low.startswith("bearer "):
        return v.split(" ", 1)[1].strip()
    return v

def _token_from_request(request):
    # Authorization / Access-Token 모두 허용
    raw = (
        request.headers.get("Authorization")
        or request.headers.get("Access-Token")
        or request.META.get("HTTP_AUTHORIZATION")
        or request.META.get("HTTP_ACCESS_TOKEN")
    )
    return _strip_bearer(raw)

# ---------------------------------------------------------------------------
# 1) (디버그) 메인 서버 API 호출 결과 그대로 보기
# ---------------------------------------------------------------------------
class MainEchoQuery(serializers.Serializer):
    path = serializers.CharField(
        help_text="메인 서버 상대 경로. 예) /health/api/wearable/daily-buckets?date=2025-09-21"
    )
    method = serializers.ChoiceField(choices=["GET", "POST", "PUT", "DELETE"], required=False, default="GET")
    body = serializers.JSONField(required=False)

class MainEchoView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM, COUPLE_ID_PARAM, ACCESS_TOKEN_PARAM],
        request=MainEchoQuery,
        responses={200: None},
        tags=["debug"],
        summary="(디버그) 메인 서버 API 호출 결과 그대로 보기",
        operation_id="postMainEcho",
    )
    def post(self, request):
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = MainEchoQuery(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        couple_id = request.headers.get("X-Couple-Id") or request.META.get("HTTP_X_COUPLE_ID")
        token = _token_from_request(request)
        if not token:
            return Response(
                {"ok": False, "error": "ACCESS_TOKEN_REQUIRED",
                 "hint": "Swagger 헤더 Authorization: Bearer <token> 로 입력하세요."},
                status=400,
            )

        path = d["path"].strip()
        if not path.startswith("/"):
            path = "/" + path

        code, data = call_main(
            path,
            method=d.get("method") or "GET",
            json_body=d.get("body"),
            couple_id=couple_id,
            access_token=token,  # ← 반드시 헤더 토큰
        )
        return Response({"status": code, "data": data}, status=code if 200 <= code < 300 else 200)

# ---------------------------------------------------------------------------
# 2) 메인 daily-buckets(stats) → 로컬 DB 업서트
# ---------------------------------------------------------------------------
class PullStepsBaselineIn(serializers.Serializer):
    date = serializers.DateField(required=False, help_text="YYYY-MM-DD (KST). 기본: 오늘")
    path = serializers.CharField(required=False, default="/health/api/wearable/daily-buckets")

class PullStepsBaselineView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM, COUPLE_ID_PARAM, ACCESS_TOKEN_PARAM],
        request=PullStepsBaselineIn,
        responses={200: None},
        tags=["steps"],
        summary="메인 서버 daily-buckets(stats) 가져와 저장(upsert)",
        operation_id="postStepsBaselinePull",
    )
    def post(self, request):
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = PullStepsBaselineIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        couple_id = request.headers.get("X-Couple-Id") or request.META.get("HTTP_X_COUPLE_ID")
        token = _token_from_request(request)
        if not token:
            return Response(
                {"ok": False, "error": "ACCESS_TOKEN_REQUIRED",
                 "hint": "Swagger 헤더 Authorization: Bearer <token> 로 입력하세요."},
                status=400,
            )

        d_kst = d.get("date") or datetime.now(KST).date()
        path = (d.get("path") or "/health/api/wearable/daily-buckets").strip()
        if not path.startswith("/"):
            path = "/" + path
        path_with_qs = f"{path}?date={d_kst.isoformat()}"

        code, data = call_main(
            path_with_qs,
            method="GET",
            couple_id=couple_id,
            access_token=token,  # ← 반드시 헤더 토큰
        )

        if not (200 <= code < 300):
            return Response({"ok": False, "source_status": code, "source": data}, status=200)

        if not isinstance(data, dict) or "stats" not in data:
            return Response({"ok": False, "error": "BAD_MAIN_RESPONSE", "source": data}, status=200)

        saved = upsert_daily_buckets_payload(data)
        return Response({
            "ok": True,
            "date": d_kst.isoformat(),
            "saved_rows": saved,
            "source_status": code,
        })
