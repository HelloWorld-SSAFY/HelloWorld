# api/views_delivery.py
from datetime import timedelta
from django.utils import timezone
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import serializers

# Swagger
from drf_spectacular.utils import (
    extend_schema, extend_schema_view,
    OpenApiParameter, OpenApiTypes, inline_serializer
)

# ✅ 단일 소스: recommend_delivery 만 사용
from api.models import RecommendationDelivery as RecommendDelivery

# 공용 인증/헤더 유틸 재사용
from api.views import (
    _assert_app_token,
    _require_user_ref,           # ← 헤더(X-Couple-Id) 우선으로 user_ref 결정
    _access_token_from_request,  # ← 필요 시 액세스 토큰 조회
    APP_TOKEN_PARAM,
    COUPLE_ID_PARAM,             # ← Swagger에 X-Couple-Id 노출
    ACCESS_TOKEN_PARAM,          # ← Swagger에 X-Access-Token 노출
)


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
class DeliveryItem(serializers.Serializer):
    # MEDIA 공통
    delivery_id = serializers.CharField()
    content_id = serializers.IntegerField(required=False)
    title = serializers.CharField(required=False)
    url = serializers.URLField(required=False, allow_blank=True)
    thumbnail = serializers.URLField(required=False, allow_blank=True)
    duration_sec = serializers.IntegerField(required=False, allow_null=True)
    provider = serializers.CharField(required=False, allow_blank=True)
    # OUTING 전용
    place_id = serializers.IntegerField(required=False)
    lat = serializers.FloatField(required=False)
    lng = serializers.FloatField(required=False)
    address = serializers.CharField(required=False, allow_blank=True)
    place_category = serializers.CharField(required=False, allow_blank=True)
    weather_gate = serializers.CharField(required=False, allow_blank=True)
    # 공통
    rank = serializers.IntegerField()
    score = serializers.FloatField(required=False, allow_null=True)
    created_at = serializers.CharField()
    reason = serializers.CharField(required=False, allow_blank=True)
    meta = serializers.JSONField(required=False)

class DeliveryOut(serializers.Serializer):
    ok = serializers.BooleanField()
    category = serializers.CharField()
    session_id = serializers.CharField()
    count = serializers.IntegerField()
    deliveries = DeliveryItem(many=True)


def _serialize_media_from_recommend(items):
    """ MUSIC / MEDITATION / YOGA → recommend_delivery에서 바로 직렬화 """
    out = []
    for i, r in enumerate(items, start=1):
        c = getattr(r, "content", None)            # 있으면 사용
        snap = getattr(r, "snapshot", None) or {}  # 추천 시 스냅샷 저장했다면 사용

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
            "reason": getattr(r, "reason", None) or snap.get("reason"),
            "meta": _first(getattr(r, "meta", None), getattr(c, "meta", None), snap.get("meta"), {}) or {},
        })
    return out


def _serialize_outing_from_recommend(items):
    """ OUTING → recommend_delivery에서만 직렬화 (조인/다른 테이블 전혀 안 씀) """
    out = []
    for i, r in enumerate(items, start=1):
        c = getattr(r, "content", None)            # 있을 수도 있음
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

    @extend_schema(
        parameters=[
            APP_TOKEN_PARAM,
            COUPLE_ID_PARAM,      # ← 헤더로 user_ref 전달 가능(우선)
            ACCESS_TOKEN_PARAM,   # ← 액세스 토큰 입력 칸
            OpenApiParameter(
                "user_ref", OpenApiTypes.STR, OpenApiParameter.QUERY, required=False,
                description="유저 식별자. 헤더 X-Couple-Id가 있으면 그 값을 우선 사용합니다."
            ),
            OpenApiParameter("limit", OpenApiTypes.INT, OpenApiParameter.QUERY, required=False,
                             description="반환 개수(기본 3, 1~5)"),
            OpenApiParameter("session_id", OpenApiTypes.STR, OpenApiParameter.QUERY, required=False,
                             description="특정 세션으로 한정 조회"),
            OpenApiParameter("ttl_min", OpenApiTypes.INT, OpenApiParameter.QUERY, required=False,
                             description="최신 노출 TTL(분) — 세션 생성 시간이 TTL 밖이면 404"),
        ],
        responses={
            200: DeliveryOut,
            404: inline_serializer("DeliveryNotFound", {"ok": serializers.BooleanField(),
                                                        "error": serializers.CharField(),
                                                        "category": serializers.CharField()}),
            401: inline_serializer("AuthErr", {"ok": serializers.BooleanField(),
                                               "error": serializers.CharField()}),
            400: inline_serializer("BadReq", {"ok": serializers.BooleanField(),
                                              "error": serializers.CharField()}),
        },
        tags=["delivery"],
        summary="최근 세션의 전달물 조회",
        operation_id="getDeliveryBase",
    )
    def get(self, request):
        # 🔐 토큰 검사
        bad = _assert_app_token(request)
        if bad:
            return bad

        # 필요 시 외부 호출에 쓰려고 꺼내 두기(현재는 저장만)
        _ = _access_token_from_request(request)

        # 헤더(X-Couple-Id) 우선 → 없으면 쿼리의 user_ref 사용
        user_ref_qs = request.query_params.get("user_ref")
        user_ref, missing = _require_user_ref(request, user_ref_qs)
        if missing:
            return missing

        # 안전 파싱/클램핑
        try:
            limit = int(request.query_params.get("limit", 3))
        except Exception:
            limit = 3
        limit = max(1, min(5, limit))

        session_id = request.query_params.get("session_id")
        try:
            ttl_min = int(request.query_params.get("ttl_min")) if request.query_params.get("ttl_min") else None
        except Exception:
            ttl_min = None

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
@extend_schema_view(
    get=extend_schema(summary="MUSIC 전달물 조회", operation_id="getDeliveryMusic")
)
class MusicDeliveryView(_RecommendDeliveryBase):
    CATEGORY = "MUSIC"
    SERIALIZER_FN = staticmethod(_serialize_media_from_recommend)

@extend_schema_view(
    get=extend_schema(summary="MEDITATION 전달물 조회", operation_id="getDeliveryMeditation")
)
class MeditationDeliveryView(_RecommendDeliveryBase):
    CATEGORY = "MEDITATION"
    SERIALIZER_FN = staticmethod(_serialize_media_from_recommend)

@extend_schema_view(
    get=extend_schema(summary="YOGA 전달물 조회", operation_id="getDeliveryYoga")
)
class YogaDeliveryView(_RecommendDeliveryBase):
    CATEGORY = "YOGA"
    SERIALIZER_FN = staticmethod(_serialize_media_from_recommend)

@extend_schema_view(
    get=extend_schema(summary="OUTING 전달물 조회", operation_id="getDeliveryOuting")
)
class OutingDeliveryView(_RecommendDeliveryBase):
    CATEGORY = "OUTING"
    SERIALIZER_FN = staticmethod(_serialize_outing_from_recommend)
