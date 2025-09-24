# api/views_debug.py
from __future__ import annotations
import os, hmac
from typing import Optional

from rest_framework.decorators import (
    api_view, permission_classes, authentication_classes
)
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response

# 내부 게이트웨이 인증 (네가 사용하는 클래스)
from api.auth_internal import GatewayInternalAuth

APP_TOKEN = (os.getenv("APP_TOKEN") or "").strip()


# ── 유틸 ───────────────────────────────────────────────────────────────────────
def _mask(v: Optional[str]) -> Optional[str]:
    if not v:
        return None
    return f"{v[:3]}...{v[-3:]}"

def _from_auth(authorization: Optional[str]) -> Optional[str]:
    if not authorization:
        return None
    parts = authorization.split()
    if len(parts) == 2 and parts[0] in ("App", "Bearer", "Token"):
        return parts[1]
    return None

def _extract_app_token(request) -> Optional[str]:
    h = request.headers
    return (
        h.get("X-App-Token")
        or h.get("App-Token")
        or _from_auth(h.get("Authorization"))
    )


# ── 디버그: App-Token/Authorization 유무 확인 ─────────────────────────────────
@api_view(["GET"])
@authentication_classes([])          # 전역 인증 비활성화 (401 방지)
@permission_classes([AllowAny])
def debug_headers(request):
    h = request.headers
    vals = {
        "X-App-Token": h.get("X-App-Token"),
        "App-Token": h.get("App-Token"),
        "Authorization": h.get("Authorization"),
    }
    return Response({
        "has": {k: bool(v) for k, v in vals.items()},
        "peek": {k: _mask(v) for k, v in vals.items()},
    })


# ── 네가 요청한: 내부 헤더 에코 ────────────────────────────────────────────────
@api_view(["GET"])
@authentication_classes([])          # 전역 인증 비활성화
@permission_classes([AllowAny])
def echo_headers(request):
    keys = [
        "Authorization",
        "X-Internal-User-Id",
        "X-Internal-Couple-Id",
        "X-Internal-Role",
        "X-Internal-Ts",
        "X-Internal-Sig",
    ]
    return Response({k: request.headers.get(k) for k in keys})


# ── 네가 요청한: 게이트웨이 내부 인증 강제 whoami ─────────────────────────────
@api_view(["GET"])
@authentication_classes([GatewayInternalAuth])  # 내부 헤더 기반 인증 강제
@permission_classes([IsAuthenticated])
def whoami(request):
    u = request.user
    return Response({
        "user_id": getattr(u, "id", None),
        "username": getattr(u, "username", None),
        "is_authenticated": bool(getattr(u, "is_authenticated", False)),
        "auth_class": (
            request.successful_authenticator.__class__.__name__
            if getattr(request, "successful_authenticator", None) else None
        ),
    })


# ── 선택: App-Token 매칭 프로브(있으면 편함) ──────────────────────────────────
@api_view(["GET"])
@authentication_classes([])          # 전역 인증 비활성화
@permission_classes([AllowAny])
def app_token_probe(request):
    incoming = _extract_app_token(request)
    ok = False
    if incoming and APP_TOKEN:
        ok = hmac.compare_digest(incoming.strip(), APP_TOKEN)
    return Response({
        "provided": bool(incoming),
        "provided_peek": _mask(incoming),
        "expected_set": bool(APP_TOKEN),
        "match": ok,
    }, status=200 if ok else 401)
