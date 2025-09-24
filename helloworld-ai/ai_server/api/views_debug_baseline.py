# api/views_debug_baseline.py
from datetime import datetime, timezone
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import serializers
from services.anomaly import to_kst, bucket_index_4h
from api.models import UserTodStatsDaily

BUCKET_KEYS = ["v_0_4","v_4_8","v_8_12","v_12_16","v_16_20","v_20_24"]

class BaselineProbeIn(serializers.Serializer):
    user_ref = serializers.CharField()
    ts = serializers.DateTimeField(required=False)
    metric = serializers.ChoiceField(choices=["hr","stress"], default="hr")

class BaselineProbeView(APIView):
    def post(self, request):
        ser = BaselineProbeIn(data=request.data); ser.is_valid(raise_exception=True); d = ser.validated_data
        ts = d.get("ts") or datetime.now(timezone.utc)
        kst = to_kst(ts if ts.tzinfo else ts.replace(tzinfo=timezone.utc))
        bi = bucket_index_4h(kst); key = BUCKET_KEYS[bi]
        rows = list(UserTodStatsDaily.objects
                    .filter(user_ref=str(d["user_ref"]).lstrip("cC0"), as_of=kst.date(), metric=d["metric"])
                    .values("stat", *BUCKET_KEYS))
        mu = next((r[key] for r in rows if r["stat"]=="mean"), None)
        sd = next((r[key] for r in rows if r["stat"]=="stddev"), None)
        return Response({
            "user_ref": str(d["user_ref"]).lstrip("cC0"),
            "as_of": kst.date().isoformat(),
            "bucket_idx": bi, "bucket_key": key, "mu": mu, "sd": sd, "rows": rows
        })
