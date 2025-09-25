# api/views_debug_effective.py
from datetime import datetime, timezone
import re
from rest_framework.views import APIView
from rest_framework.response import Response
from drf_spectacular.utils import extend_schema, OpenApiParameter
from drf_spectacular.types import OpenApiTypes

from services.anomaly import to_kst, bucket_index_4h
from services.orm_stats_provider import OrmStatsProvider, BUCKET_FIELDS
from api.views import _assert_app_token, APP_TOKEN_PARAM  # X-App-Token 검사

_re_user = re.compile(r"^[cC]?0*(\d+)$")
def _norm_user_ref(v: str) -> str:
    m = _re_user.match(str(v).strip())
    return m.group(1) if m else str(v).strip()

_provider = OrmStatsProvider()

class BaselineEffectiveView(APIView):
    """
    Provider가 '실제로 사용할' μ/σ와 그 출처(날짜/버킷)를 보여주는 디버그 GET.
    Authorization은 전역 스키마(Authorize 버튼), X-App-Token 헤더 필수.
    """
    @extend_schema(
        tags=["debug"],
        summary="(GET) Effective Baseline (provider 선택 결과)",
        description="user_ref, ts(옵션), metric(hr|stress) → Provider가 선택한 μ/σ와 source를 반환",
        parameters=[
            APP_TOKEN_PARAM,
            OpenApiParameter(name="user_ref", type=OpenApiTypes.STR, location=OpenApiParameter.QUERY, required=True),
            OpenApiParameter(name="metric", type=OpenApiTypes.STR, location=OpenApiParameter.QUERY, required=False, enum=["hr","stress"], description="기본 hr"),
            OpenApiParameter(name="ts", type=OpenApiTypes.DATETIME, location=OpenApiParameter.QUERY, required=False, description="기본: now(UTC)"),
        ],
        responses={200: None},
        operation_id="getDebugBaselineEffective",
    )
    def get(self, request):
        bad = _assert_app_token(request)
        if bad:
            return bad

        user_ref = request.query_params.get("user_ref")
        if not user_ref:
            return Response({"error": "user_ref required"}, status=400)
        metric = (request.query_params.get("metric") or "hr").strip().lower()
        ts_q = request.query_params.get("ts")

        # ts → KST 버킷
        if ts_q:
            try:
                ts = datetime.fromisoformat(ts_q)
                if ts.tzinfo is None:
                    ts = ts.replace(tzinfo=timezone.utc)
            except Exception:
                return Response({"error": "bad ts"}, status=400)
        else:
            ts = datetime.now(timezone.utc)

        kst = to_kst(ts)
        bidx = bucket_index_4h(kst)
        bkey = BUCKET_FIELDS[bidx]

        ref = _norm_user_ref(user_ref)
        got = _provider.get_bucket_stats_meta(ref, kst.date(), metric, bidx)
        if not got:
            return Response({
                "user_ref": ref,
                "metric": metric,
                "kst_ts": kst.isoformat(),
                "bucket_idx": bidx,
                "bucket_key": bkey,
                "mu": None,
                "sd": None,
                "source": None,
                "note": "Provider가 사용할 기준선을 찾지 못함"
            })

        (mu, sd), meta = got
        return Response({
            "user_ref": ref,
            "metric": metric,
            "kst_ts": kst.isoformat(),
            "bucket_idx": bidx,
            "bucket_key": bkey,
            "mu": mu,
            "sd": sd,
            "source": meta,   # {"source_date": "...", "source_bucket_key": "...", "reason": "..."}
        })
