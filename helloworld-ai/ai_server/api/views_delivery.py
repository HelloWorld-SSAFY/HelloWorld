# api/views_delivery.py
from datetime import timedelta
from django.utils import timezone
from rest_framework.views import APIView
from rest_framework.response import Response

# ✅ 단일 소스: recommend_delivery 만 사용
from api.models import RecommendationDelivery as RecommendDelivery


# ───────────── 유틸 ─────────────
def _first(*vals):
    for v in vals:
        if v not in (None, "", {}):
            return v
    return None

def _enforce_ttl(qs, ttl_min: int | None) -> bool:
    if not ttl_min:
        return True
    edge = timezone.now() - timedelta(minutes=ttl_min)
    latest = qs.order_by("-created_at").values_list("created_at", flat=True).first()
    return bool(latest and latest >= edge)

def _has_field(model_cls, name: str) -> bool:
    # Django 5 안전: concrete 필드만 검사
    for f in model_cls._meta.get_fields():
        if hasattr(f, "attname") and f.name == name:
            return True
    return False


# ───────────── 직렬화 ─────────────
def _serialize_media_from_recommend(items):
    """ MUSIC / MEDITATION / YOGA → recommend_delivery에서 바로 직렬화 """
    out = []
    for i, r in enumerate(items, start=1):
        c = getattr(r, "content", None)           # 있으면 사용
        snap = getattr(r, "snapshot", None) or {} # 추천 시 스냅샷 저장했다면 사용

        out.append({
            "delivery_id": _first(getattr(r, "external_id", None), f"content:{r.id}"),
            "content_id":  _first(getattr(r, "content_id", None), getattr(c, "id", None)),
            "title":       _first(getattr(r, "title", None), getattr(c, "title", None), snap.get("title")),
            "provider":    _first(getattr(r, "provider", None), getattr(c, "provider", None), snap.get("provider")),
            "url": _first(
                getattr(r, "url", None), getattr(r, "watch_url", None), getattr(r, "external_url", None),
                getattr(c, "url", None), getattr(c, "watch_url", None), getattr(c, "external_url", None),
                snap.get("url")
            ),
            # 응답키는 thumbnail 고정(소스는 r.* / c.* / snapshot.*)
            "thumbnail": _first(
                getattr(r, "thumbnail_url", None), getattr(r, "thumbnail", None),
                getattr(c, "thumbnail_url", None), getattr(c, "thumbnail", None),
                snap.get("thumbnail_url"), snap.get("thumbnail")
            ),
            "duration_sec": _first(
                getattr(r, "duration_sec", None), getattr(r, "duration_seconds", None),
                getattr(c, "duration_sec", None), getattr(c, "duration_seconds", None),
                snap.get("duration_sec")
            ),
            "rank": getattr(r, "rank", i),
            "score": getattr(r, "score", None),
            "created_at": r.created_at.isoformat(),
            "meta": _first(getattr(r, "meta", None), getattr(c, "meta", None), snap.get("meta"), {}) or {},
        })
    return out


def _serialize_outing_from_recommend(items):
    """ OUTING → recommend_delivery에서만 직렬화 (조인/다른 테이블 전혀 안 씀) """
    out = []
    for i, r in enumerate(items, start=1):
        c = getattr(r, "content", None)           # 있을 수도 있음
        snap = getattr(r, "snapshot", None) or {}

        # place/위치 계열 필드는 r → c → snapshot 순으로 관대하게 매핑
        place_id = _first(getattr(r, "place_id", None),
                          getattr(r, "content_id", None),
                          getattr(c, "id", None),
                          snap.get("place_id"))

        lat = _first(getattr(r, "lat", None), getattr(r, "latitude", None),
                     getattr(c, "lat", None), getattr(c, "latitude", None),
                     snap.get("lat"), snap.get("latitude"))

        lng = _first(getattr(r, "lng", None), getattr(r, "longitude", None),
                     getattr(c, "lng", None), getattr(c, "longitude", None),
                     snap.get("lng"), snap.get("longitude"))

        address = _first(getattr(r, "address", None),
                         getattr(r, "address_road", None),
                         getattr(r, "address_jibun", None),
                         getattr(c, "address", None),
                         getattr(c, "address_road", None),
                         getattr(c, "address_jibun", None),
                         snap.get("address"), snap.get("address_road"), snap.get("address_jibun"))

        place_category = _first(getattr(r, "place_category", None),
                                getattr(r, "place_type", None),
                                getattr(c, "place_category", None),
                                snap.get("place_category"), snap.get("place_type"))

        weather_gate = _first(getattr(r, "weather_gate", None),
                              getattr(c, "weather_gate", None),
                              snap.get("weather_gate"))

        out.append({
            "delivery_id": _first(getattr(r, "external_id", None), f"place:{r.id}"),
            "place_id": place_id,
            "title": _first(getattr(r, "title", None), getattr(c, "title", None), snap.get("title")),
            "lat": lat,
            "lng": lng,
            "address": address,
            "place_category": place_category,
            "weather_gate": weather_gate,
            "reason": getattr(r, "reason", None) or snap.get("reason"),
            "rank": getattr(r, "rank", i),
            "created_at": r.created_at.isoformat(),
            "meta": _first(getattr(r, "meta", None), getattr(c, "meta", None), snap.get("meta"), {}) or {},
        })
    return out


# ───────────── 공통 베이스(모든 카테고리 recommend_delivery 사용) ─────────────
class _RecommendDeliveryBase(APIView):
    CATEGORY = None
    SERIALIZER_FN = None

    def _latest_session_for_category(self, user_ref: str) -> str | None:
        return (
            RecommendDelivery.objects
            .filter(user_ref=user_ref, category=self.CATEGORY)
            .order_by("-created_at")
            .values_list("session_id", flat=True)
            .first()
        )

    def get(self, request):
        user_ref = request.query_params.get("user_ref")
        if not user_ref:
            return Response({"ok": False, "error": "MISSING_USER_REF"}, status=400)

        limit      = int(request.query_params.get("limit", 3))
        session_id = request.query_params.get("session_id")
        ttl_min_q  = request.query_params.get("ttl_min")
        ttl_min    = int(ttl_min_q) if ttl_min_q else None

        # 1) 세션 결정 (user_ref + category 스코프 고정)
        chosen_session_id = session_id or self._latest_session_for_category(user_ref)
        if not chosen_session_id:
            return Response({"ok": False, "error": "NO_DELIVERY_FOR_CATEGORY", "category": self.CATEGORY}, status=404)

        # 2) 세션 + 카테고리 고정 조회
        qs = (RecommendDelivery.objects
              .filter(user_ref=user_ref, session_id=chosen_session_id, category=self.CATEGORY))

        # content FK가 실제로 있으면 N+1 예방
        if _has_field(RecommendDelivery, "content"):
            qs = qs.select_related("content")

        # 3) TTL 검사
        if not _enforce_ttl(qs, ttl_min):
            return Response({"ok": False, "error": "DELIVERY_EXPIRED", "category": self.CATEGORY}, status=404)

        # 4) 정렬 (rank > score > created_at) — 필드 없으면 안전하게 fallback
        if _has_field(RecommendDelivery, "rank"):
            order_by = ["rank", "-created_at"]
        elif _has_field(RecommendDelivery, "score"):
            order_by = ["-score", "-created_at"]
        else:
            order_by = ["-created_at"]

        items = list(qs.order_by(*order_by)[:limit])
        if not items:
            return Response({"ok": False, "error": "NO_DELIVERY_FOR_CATEGORY", "category": self.CATEGORY}, status=404)

        return Response({
            "ok": True,
            "category": self.CATEGORY,
            "session_id": chosen_session_id,
            "count": len(items),
            "deliveries": self.SERIALIZER_FN(items),
        })


# ───────────── 카테고리 뷰 ─────────────
class MusicDeliveryView(_RecommendDeliveryBase):
    CATEGORY = "MUSIC"
    SERIALIZER_FN = staticmethod(_serialize_media_from_recommend)

class MeditationDeliveryView(_RecommendDeliveryBase):
    CATEGORY = "MEDITATION"
    SERIALIZER_FN = staticmethod(_serialize_media_from_recommend)

class YogaDeliveryView(_RecommendDeliveryBase):
    CATEGORY = "YOGA"
    SERIALIZER_FN = staticmethod(_serialize_media_from_recommend)

class OutingDeliveryView(_RecommendDeliveryBase):
    CATEGORY = "OUTING"
    SERIALIZER_FN = staticmethod(_serialize_outing_from_recommend)
