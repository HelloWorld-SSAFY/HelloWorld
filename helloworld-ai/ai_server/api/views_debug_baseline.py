from datetime import datetime, timezone
import re
from rest_framework.views import APIView
from rest_framework.response import Response
from drf_spectacular.utils import extend_schema, OpenApiParameter
from drf_spectacular.types import OpenApiTypes

from services.anomaly import to_kst, bucket_index_4h
from api.models import UserTodStatsDaily

# 공용: 앱 토큰 검사 & 스웨거 파라미터
from api.views import _assert_app_token, APP_TOKEN_PARAM  # ← ACCESS_TOKEN_PARAM 제거

BUCKET_KEYS = ["v_0_4", "v_4_8", "v_8_12", "v_12_16", "v_16_20", "v_20_24"]
_re_user = re.compile(r"^[cC]?0*(\d+)$")
def _norm_user_ref(v: str) -> str:
    s = str(v).strip()
    m = _re_user.match(s)
    return m.group(1) if m else s


class BaselineProbeView(APIView):
    """
    KST 기준 4시간 버킷의 기준선(μ,σ)을 raw DB에서 확인하는 GET 디버그 엔드포인트.
    - 헤더: X-App-Token (필수), Authorization은 전역 보안스키마를 통해 전달(Authorize 버튼)
    - 쿼리: user_ref(필수), ts(옵션 ISO8601), metric=hr|stress(기본 hr)
    """
    # ⚠️ 인증/권한 우회 제거! (그래야 Swagger가 Authorization 헤더를 붙임)
    # authentication_classes = []
    # permission_classes = [AllowAny]

    @extend_schema(
        tags=["debug"],
        summary="(GET) 버킷 기준선(μ,σ) 프로브",
        description="쿼리로 user_ref, ts(옵션), metric(hr|stress). 헤더에 X-App-Token 필요. Authorization은 상단 Authorize로 입력.",
        parameters=[
            # ── 헤더 ──
            APP_TOKEN_PARAM,  # X-App-Token
            # ── 쿼리 ──
            OpenApiParameter(
                name="user_ref", type=OpenApiTypes.STR, location=OpenApiParameter.QUERY,
                required=True, description="예: 11 또는 c11 (자동 정규화)"
            ),
            OpenApiParameter(
                name="ts", type=OpenApiTypes.DATETIME, location=OpenApiParameter.QUERY,
                required=False, description="ISO8601. 기본: 서버 now(UTC). KST 버킷 계산에 사용"
            ),
            OpenApiParameter(
                name="metric", type=OpenApiTypes.STR, location=OpenApiParameter.QUERY,
                required=False, enum=["hr", "stress"], description="기본 hr"
            ),
        ],
        request=None,
        responses={200: None},
        operation_id="getDebugBaseline",
    )
    def get(self, request):
        # 앱 토큰 검사 (우리 서버 레벨)
        bad = _assert_app_token(request)
        if bad:
            return bad

        # 쿼리 파싱
        user_ref = request.query_params.get("user_ref")
        if not user_ref:
            return Response({"error": "user_ref required"}, status=400)
        metric = (request.query_params.get("metric") or "hr").strip().lower()
        ts_q = request.query_params.get("ts")

        # ts → KST 버킷 결정
        if ts_q:
            try:
                ts = datetime.fromisoformat(ts_q)
                if ts.tzinfo is None:
                    ts = ts.replace(tzinfo=timezone.utc)
            except Exception:
                return Response({"error": "bad ts format"}, status=400)
        else:
            ts = datetime.now(timezone.utc)

        kst = to_kst(ts)
        bucket_idx = bucket_index_4h(kst)
        bucket_key = BUCKET_KEYS[bucket_idx]
        ref = _norm_user_ref(user_ref)

        # 동일 날짜(as_of) raw 조회
        rows = list(
            UserTodStatsDaily.objects
            .filter(user_ref=ref, as_of=kst.date(), metric=metric)
            .values("stat", *BUCKET_KEYS)
        )
        mu = next((r.get(bucket_key) for r in rows if r["stat"] == "mean"), None)
        sd = next((r.get(bucket_key) for r in rows if r["stat"] == "stddev"), None)

        return Response({
            "user_ref": ref,
            "metric": metric,
            "as_of": kst.date().isoformat(),
            "kst_ts": kst.isoformat(),
            "bucket_idx": bucket_idx,
            "bucket_key": bucket_key,
            "mu": mu,
            "sd": sd,
            "rows": rows,
            "hint": "mu/sd가 null이면 이 버킷에는 기준선이 없어 Z기반 감지가 불가합니다."
        })


### asdfasdf