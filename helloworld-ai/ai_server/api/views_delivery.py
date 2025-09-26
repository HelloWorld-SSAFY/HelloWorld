# api/views_delivery.py
from datetime import timedelta
from django.utils import timezone
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import serializers
from rest_framework import status  # ✅

# Std
import os
import json
import base64
import logging
from typing import Optional, Tuple, Dict, Any, List

import requests  # ✅ whoami 보조 조회용

log = logging.getLogger(__name__)

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
    _require_user_ref,           # ← (fallback) 헤더 X-Couple-Id → "c{cid}"
    _access_token_from_request,  # ← Authorization / X-Access-Token 추출
    APP_TOKEN_PARAM,
    COUPLE_ID_PARAM,             # ← Swagger에 X-Couple-Id 노출
    ACCESS_TOKEN_PARAM,          # ← Swagger에 X-Access-Token 노출 (views.py에서 AUTH_HEADER_PARAM 별칭)
    AUTH_HEADER_PARAM,           # ← 실제 Authorization 헤더 파라미터
)

# ✅ 로컬 별칭(views.py에 AUTHZ_PARAM이 없으므로 여기서 매핑)
AUTHZ_PARAM = AUTH_HEADER_PARAM


# ───────────── 유틸 ─────────────
def _first(*vals):
    for v in vals:
        if v not in (None, "", {}):
            return v
    return None

def _ok_empty(category: str, session_id: Optional[str], msg: str):
    """빈 결과를 200 OK로 표준화 응답"""
    return Response({
        "ok": True,
        "category": category,
        "session_id": session_id,
        "has_delivery": False,
        "count": 0,
        "deliveries": [],
        "message": msg,
    }, status=status.HTTP_200_OK)

def _enforce_ttl(qs, ttl_min: Optional[int]) -> bool:
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


# ───────────── Access Token → coupleId/userId 추출 ─────────────
def _b64url_decode(b: str) -> Optional[bytes]:
    try:
        # base64url padding
        rem = len(b) % 4
        if rem:
            b += "=" * (4 - rem)
        return base64.urlsafe_b64decode(b.encode("utf-8"))
    except Exception:
        return None

def _try_extract_ids_from_jwt(token: str) -> Tuple[Optional[int], Optional[int]]:
    """
    JWT payload를 서명검증 없이 로컬 decode해서 coupleId/userId 추출.
    반환: (couple_id, user_id)
    """
    try:
        parts = token.split(".")
        if len(parts) < 2:
            return (None, None)
        payload_b = _b64url_decode(parts[1])
        if not payload_b:
            return (None, None)
        payload = json.loads(payload_b.decode("utf-8"))
    except Exception:
        return (None, None)

    def _pick_int(d: Dict[str, Any], keys: List[str]) -> Optional[int]:
        for k in keys:
            v = d.get(k)
            if isinstance(v, int):
                return v
            # 문자열 정수 처리
            if isinstance(v, str) and v.isdigit():
                return int(v)
        return None

    # 흔한 키 후보
    couple_id = _pick_int(payload, ["coupleId", "couple_id", "cid"])
    user_id = _pick_int(payload, ["userId", "user_id", "uid"])

    # sub에 정수 id가 오는 케이스 보완
    if user_id is None:
        sub = payload.get("sub")
        if isinstance(sub, str) and sub.isdigit():
            user_id = int(sub)

    return (couple_id, user_id)

def _http_get_json(url: str, token: str, timeout: float = 3.0) -> Optional[Dict[str, Any]]:
    try:
        r = requests.get(url, headers={"Authorization": f"Bearer {token}", "Accept": "application/json"}, timeout=timeout)
        if r.status_code == 200:
            return r.json()
        log.debug("whoami candidate %s -> %s %s", url, r.status_code, r.text[:200])
    except Exception as e:
        log.debug("whoami candidate %s error: %s", url, e)
    return None

def _extract_ids_from_whoami_payload(data: Dict[str, Any]) -> Tuple[Optional[int], Optional[int]]:
    """
    다양한 whoami 응답 스키마를 관대하게 파싱.
    반환: (couple_id, user_id)
    """
    keys = ["coupleId", "couple_id", "cid", "couple"]
    for k in keys:
        v = data.get(k)
        if isinstance(v, int):
            c = v
            break
        if isinstance(v, str) and v.isdigit():
            c = int(v)
            break
        if isinstance(v, dict):
            # {"couple": {"id": 10}}
            cv = v.get("id")
            if isinstance(cv, int):
                c = cv
                break
            if isinstance(cv, str) and cv.isdigit():
                c = int(cv)
                break
    else:
        c = None

    # userId 후보
    u = None
    for k in ["userId", "user_id", "uid", "id"]:
        v = data.get(k)
        if isinstance(v, int):
            u = v
            break
        if isinstance(v, str) and v.isdigit():
            u = int(v)
            break

    # 중첩 후보: data["user"]["id"], data["account"]["id"]
    if u is None:
        for p in ["user", "account", "profile"]:
            node = data.get(p)
            if isinstance(node, dict):
                iv = node.get("id")
                if isinstance(iv, int):
                    u = iv
                    break
                if isinstance(iv, str) and iv.isdigit():
                    u = int(iv)
                    break

    return (c, u)

def _resolve_user_refs_from_token(request) -> Tuple[Optional[str], Optional[str]]:
    """
    Access Token(Authorization/X-Access-Token)로부터 user_ref 후보를 도출.
    우선순위: coupleId → 'c{cid}', 없으면 userId → 'u{uid}'.
    반환: (c_ref, u_ref)
    """
    token = _access_token_from_request(request)
    if not token:
        return (None, None)

    # 1) JWT 로컬 decode 시도
    cid, uid = _try_extract_ids_from_jwt(token)
    if cid is not None or uid is not None:
        return (_first(f"c{cid}" if cid is not None else None, None),
                _first(f"u{uid}" if uid is not None else None, None))

    # 2) whoami 보조 호출(환경변수로 endpoint 유연화)
    base = os.getenv("MAIN_WHOAMI_URL")  # 완전한 URL이면 이것만 사용
    candidates: List[str] = []
    if base:
        candidates.append(base)
    else:
        host = os.getenv("MAIN_BASE_URL", "").rstrip("/")
        if host:
            candidates.extend([
                f"{host}/api/v1/me",
                f"{host}/api/me",
                f"{host}/v1/me",
                f"{host}/users/me",
                f"{host}/auth/whoami",
            ])

    for url in candidates:
        data = _http_get_json(url, token)
        if not data:
            continue
        cid, uid = _extract_ids_from_whoami_payload(data)
        if cid is not None or uid is not None:
            return (_first(f"c{cid}" if cid is not None else None, None),
                    _first(f"u{uid}" if uid is not None else None, None))

    return (None, None)


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
    session_id = serializers.CharField(required=False, allow_null=True)  # ✅ null 허용
    has_delivery = serializers.BooleanField()  # ✅
    count = serializers.IntegerField()
    deliveries = DeliveryItem(many=True)
    message = serializers.CharField(required=False, allow_blank=True)  # ✅


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

    def _latest_session_for_category_pref_couple(self, c_ref: Optional[str], u_ref: Optional[str]) -> Tuple[Optional[str], Optional[str]]:
        """
        우선 c_ref('c{cid}')에서 최신 세션을 찾고, 없으면 u_ref('u{uid}')에서 찾는다.
        반환: (chosen_user_ref, chosen_session_id)
        """
        if c_ref:
            sess = (RecommendDelivery.objects
                    .filter(user_ref=c_ref, category=self.CATEGORY)
                    .order_by("-created_at")
                    .values_list("session_id", flat=True)
                    .first())
            if sess:
                return (c_ref, sess)

        if u_ref:
            sess = (RecommendDelivery.objects
                    .filter(user_ref=u_ref, category=self.CATEGORY)
                    .order_by("-created_at")
                    .values_list("session_id", flat=True)
                    .first())
            if sess:
                return (u_ref, sess)

        return (None, None)

    @extend_schema(
        parameters=[
            AUTHZ_PARAM,         # ← Authorization 헤더 (JWT에서 coupleId/userId 추출)
            APP_TOKEN_PARAM,
            COUPLE_ID_PARAM,     # ← (fallback) 헤더로 couple id 전달 가능
            ACCESS_TOKEN_PARAM,  # ← (대안) X-Access-Token
            OpenApiParameter(
                "user_ref", OpenApiTypes.STR, OpenApiParameter.QUERY, required=False,
                description="최종 fallback 전용. 예: c10 / u7"
            ),
            OpenApiParameter("limit", OpenApiTypes.INT, OpenApiParameter.QUERY, required=False,
                             description="반환 개수(기본 3, 1~5)"),
            OpenApiParameter("session_id", OpenApiTypes.STR, OpenApiParameter.QUERY, required=False,
                             description="특정 세션으로 한정 조회"),
            OpenApiParameter("ttl_min", OpenApiTypes.INT, OpenApiParameter.QUERY, required=False,
                             description="최신 노출 TTL(분). TTL 밖이면 200 OK + 빈 결과 반환"),
        ],
        responses={
            200: DeliveryOut,
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
        # 🔐 앱 토큰 검사
        bad = _assert_app_token(request)
        if bad:
            return bad

        # ── 1) Access Token에서 user_ref 후보(cN/uM) 추출 ──
        c_ref, u_ref = _resolve_user_refs_from_token(request)

        # ── 2) 그래도 없으면: X-Couple-Id → 'c{cid}' / 마지막으로 ?user_ref ──
        if not (c_ref or u_ref):
            user_ref_qs = request.query_params.get("user_ref")
            header_ref, missing = _require_user_ref(request, user_ref_qs)
            if missing:
                return missing
            # header_ref는 이미 c{cid} 형태로 정규화됨
            c_ref = header_ref if header_ref and header_ref.startswith("c") else None
            u_ref = header_ref if header_ref and header_ref.startswith("u") else None

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

        # ── 3) 세션 결정 (우선 c_ref → 없으면 u_ref) ──
        if session_id:
            # session_id가 주어지면 어떤 ref에서 나온 건지 알 수 없으므로,
            # 우선 c_ref가 있으면 그걸로, 없으면 u_ref로 조회 시도
            chosen_user_ref = _first(c_ref, u_ref)
            if not chosen_user_ref:
                # 이 경우는 거의 없지만, user_ref 전혀 없으면 실패
                return Response({"ok": False, "error": "user_ref not resolved"}, status=status.HTTP_400_BAD_REQUEST)
            chosen_session_id = session_id
        else:
            chosen_user_ref, chosen_session_id = self._latest_session_for_category_pref_couple(c_ref, u_ref)
            if not chosen_session_id:
                return _ok_empty(self.CATEGORY, None, "no delivery for category")

        # ── 4) 세션 + 카테고리 고정 조회 ──
        qs = (RecommendDelivery.objects
              .filter(user_ref=chosen_user_ref, session_id=chosen_session_id, category=self.CATEGORY))

        # content FK가 실제로 있으면 N+1 예방
        if _has_field(RecommendDelivery, "content"):
            qs = qs.select_related("content")

        # ── 5) TTL 검사 ──
        if not _enforce_ttl(qs, ttl_min):
            return _ok_empty(self.CATEGORY, chosen_session_id, "delivery expired")

        # ── 6) 정렬 (rank > score > created_at) ──
        if _has_field(RecommendDelivery, "rank"):
            order_by = ["rank", "-created_at"]
        elif _has_field(RecommendDelivery, "score"):
            order_by = ["-score", "-created_at"]
        else:
            order_by = ["-created_at"]

        items = list(qs.order_by(*order_by)[:limit])
        if not items:
            return _ok_empty(self.CATEGORY, chosen_session_id, "no delivery for category")

        return Response({
            "ok": True,
            "category": self.CATEGORY,
            "session_id": chosen_session_id,
            "has_delivery": True,
            "count": len(items),
            "deliveries": self.SERIALIZER_FN(items),
        }, status=status.HTTP_200_OK)


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
