# api/views_debug_baseline.py
from datetime import datetime, timezone
import re
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import serializers
from rest_framework.permissions import AllowAny
from drf_spectacular.utils import extend_schema

from services.anomaly import to_kst, bucket_index_4h
from api.models import UserTodStatsDaily

# 공용 스웨거 헤더/토큰 검사 유틸
from api.views import _assert_app_token, APP_TOKEN_PARAM

BUCKET_KEYS = ["v_0_4", "v_4_8", "v_8_12", "v_12_16", "v_16_20", "v_20_24"]
_re_user = re.compile(r"^[cC]?0*(\d+)$")


def _norm_user_ref(v: str) -> str:
    s = str(v).strip()
    m = _re_user.match(s)
    return m.group(1) if m else s


class BaselineProbeQuery(serializers.Serializer):
    user_ref = serializers.CharField(help_text="예: 11 또는 c11 (자동 정규화)")
    ts = serializers.DateTimeField(
        required=False,
        help_text="ISO8601. 기본: 서버 now(UTC). KST 버킷 계산에 사용됩니다.",
    )
    metric = serializers.ChoiceField(choices=["hr", "stress"], default="hr")


class BaselineProbeView(APIView):
    """
    KST 기준 4시간 버킷의 기준선(μ,σ)을 raw DB에서 확인하는 GET 디버그 엔드포인트.
    - X-App-Token 헤더 필수
    - 쿼리: user_ref(필수), ts(옵션), metric(hr|stress)
    """
    authentication_classes = []          # 전역 인증 우회
    permission_classes = [AllowAny]      # 토큰은 앱 토큰만 검사

    @extend_schema(
        tags=["debug"],
        summary="(GET) 버킷 기준선(μ,σ) 프로브",
        description="쿼리스트링으로 user_ref, ts(옵션), metric(hr|stress) 전달. 헤더에 X-App-Token 필요.",
        parameters=[APP_TOKEN_PARAM],
        request=None,
        responses={200: None},
        operation_id="getDebugBaseline",
    )
    def get(self, request):
        # 앱 토큰 검사
        bad = _assert_app_token(request)
        if bad:
            return bad

        ser = BaselineProbeQuery(data=request.query_params)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data

        # 시각 → KST 버킷 결정
        ts = d.get("ts") or datetime.now(timezone.utc)
        if ts.tzinfo is None:
            ts = ts.replace(tzinfo=timezone.utc)
        kst = to_kst(ts)
        bucket_idx = bucket_index_4h(kst)
        bucket_key = BUCKET_KEYS[bucket_idx]

        ref = _norm_user_ref(d["user_ref"])
        metric = d["metric"]

        # 동일 날짜(as_of) raw 값 조회
        rows = list(
            UserTodStatsDaily.objects
            .filter(user_ref=ref, as_of=kst.date(), metric=metric)
            .values("stat", *BUCKET_KEYS)
        )
        mu = next((r.get(bucket_key) for r in rows if r["stat"] == "mean"), None)
        sd = next((r.get(bucket_key) for r in rows if r["stat"] == "stddev"), None)

        return Response({
            "user_ref": ref,                 # 정규화된 값
            "as_of": kst.date().isoformat(), # 해당 날짜(KST)
            "kst_ts": kst.isoformat(),       # 참고용
            "bucket_idx": bucket_idx,        # 0..5
            "bucket_key": bucket_key,        # v_0_4 등
            "metric": metric,
            "mu": mu,
            "sd": sd,
            "rows": rows,                    # mean/stddev 전 버킷 원자료 (디버깅용)
            "hint": "mu/sd가 null이면 이 버킷은 Z기반 감지가 불가합니다. 다른 버킷/날짜를 사용하거나 Provider fallback 로그를 확인하세요."
        })
