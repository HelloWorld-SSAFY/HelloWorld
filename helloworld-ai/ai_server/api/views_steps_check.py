# api/views_steps_check.py
from __future__ import annotations
from django.utils import timezone
from django.utils.dateparse import parse_datetime
from rest_framework.views import APIView
from rest_framework.response import Response
from services.steps_check import check_steps_low, KST

class StepsCheckView(APIView):
    """
    POST /v1/steps-check
    바디: { user_ref, ts, cum_steps, ... }, 헤더 또는 바디로 couple_id 전달
    판정 규칙: baseline(동시간대 평균, 어제까지)과의 차이가 500 이상 부족하면 steps_low
    """
    def post(self, request):
        body = request.data or {}
        # couple_id: 헤더/바디 모두 허용
        couple_id = body.get("couple_id") or request.headers.get("X-Couple-Id")
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
            # 실제로는 RecommendationSession 저장 후 UUID 반환
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
