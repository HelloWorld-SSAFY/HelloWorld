# api/views_main_bridge.py
from datetime import date
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import serializers
from drf_spectacular.utils import extend_schema

from services.anomaly import KST
from services.main_client import call_main

# 공용 유틸/스웨거 파라미터
from api.views import (
    _assert_app_token,
    _require_user_ref,               # 헤더(X-Couple-Id) 우선 → 없으면 바디/쿼리
    _upsert_steps_baseline_records,  # 메인 응답(records) → 로컬 DB upsert
    APP_TOKEN_PARAM,
    COUPLE_ID_PARAM,
    ACCESS_TOKEN_PARAM,
)


# ---------------------------------------------------------------------------
# 1) (디버그) 메인 서버 API 호출 결과 그대로 보기
# ---------------------------------------------------------------------------
class MainEchoQuery(serializers.Serializer):
    path = serializers.CharField(
        help_text="메인 서버 상대 경로. 예) /v1/healthz 또는 /v1/steps/summary"
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
        # 앱 토큰 검사
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = MainEchoQuery(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        # 헤더 파스스루
        couple_id = request.headers.get("X-Couple-Id") or request.META.get("HTTP_X_COUPLE_ID")
        access_token = request.headers.get("Access-Token") or request.META.get("HTTP_ACCESS_TOKEN")

        # 경로 보정: 앞에 슬래시 없으면 붙여줌
        path = d["path"].strip()
        if not path.startswith("/"):
            path = "/" + path

        code, data = call_main(
            path,
            method=d.get("method") or "GET",
            json_body=d.get("body"),
            couple_id=couple_id,
            access_token=access_token,
        )
        # 성공이면 그대로 상태코드, 실패면 본문에 상태코드 포함해 200으로 감싸서 반환(디버그 편의)
        return Response({"status": code, "data": data}, status=code if 200 <= code < 300 else 200)


# ---------------------------------------------------------------------------
# 2) 메인 서버에서 일일 누적걸음 평균을 가져와 로컬 DB(user_steps_tod_stats_daily)에 저장
#    메인 응답 형태는 {"records":[{"hour_range":"00-12","avg_steps":1234}, ...]} 라고 가정
# ---------------------------------------------------------------------------
class PullStepsBaselineIn(serializers.Serializer):
    # user_ref는 헤더 X-Couple-Id를 우선 사용. 필요 시 바디로도 허용.
    user_ref = serializers.CharField(required=False)
    date = serializers.DateField(required=False, help_text="YYYY-MM-DD (KST). 기본: 오늘")
    # 메인 경로를 바꾸고 싶다면 옵션으로 허용(기본: /v1/steps/summary)
    path = serializers.CharField(required=False, default="/v1/steps/summary")

class PullStepsBaselineView(APIView):
    @extend_schema(
        parameters=[APP_TOKEN_PARAM, COUPLE_ID_PARAM, ACCESS_TOKEN_PARAM],
        request=PullStepsBaselineIn,
        responses={200: None},
        tags=["steps"],
        summary="메인 서버에서 일일 누적걸음 평균(records) 가져와 저장(upsert)",
        operation_id="postStepsBaselinePull",
    )
    def post(self, request):
        # 앱 토큰 검사
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = PullStepsBaselineIn(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        # user_ref 결정: 헤더 X-Couple-Id 우선
        user_ref, missing = _require_user_ref(request, d.get("user_ref"))
        if missing:
            return missing

        # 날짜 결정: 기본 KST 오늘
        d_kst = d.get("date") or date.today()  # 이미 naive date면 KST 기준 오늘로 충분
        # 메인 경로
        path = (d.get("path") or "/v1/steps/summary").strip()
        if not path.startswith("/"):
            path = "/" + path

        # 헤더 파스스루
        couple_id = request.headers.get("X-Couple-Id") or request.META.get("HTTP_X_COUPLE_ID")
        access_token = request.headers.get("Access-Token") or request.META.get("HTTP_ACCESS_TOKEN")

        # 메인 호출: 쿼리스트링으로 date 전달 (user는 헤더 couple_id로 식별)
        path_with_qs = f"{path}?date={d_kst.isoformat()}"

        code, data = call_main(
            path_with_qs,
            method="GET",
            couple_id=couple_id,
            access_token=access_token,
        )

        if not (200 <= code < 300):
            # 메인 실패를 그대로 전달
            return Response({"ok": False, "source_status": code, "source": data}, status=200)

        # 메인 응답에서 records 추출 후 upsert
        records = (data or {}).get("records") if isinstance(data, dict) else None
        if not isinstance(records, list):
            return Response({"ok": False, "error": "BAD_MAIN_RESPONSE", "source": data}, status=200)

        saved_buckets = _upsert_steps_baseline_records(
            user_ref=user_ref,
            d=d_kst,
            records=records,
        )
        return Response({
            "ok": True,
            "user_ref": user_ref,
            "date": d_kst.isoformat(),
            "saved_buckets": saved_buckets,
            "source_status": code,
        })
