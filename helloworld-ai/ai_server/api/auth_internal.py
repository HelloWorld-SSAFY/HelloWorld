# api/auth_internal.py
import base64, hmac, hashlib, time, os
import logging
from django.contrib.auth import get_user_model
from rest_framework.authentication import BaseAuthentication
from rest_framework import exceptions

log = logging.getLogger(__name__)

GATEWAY_HMAC_SECRET = os.getenv("GATEWAY_HMAC_SECRET", "").encode("utf-8")
INTERNAL_SKEW_SECONDS = int(os.getenv("INTERNAL_SKEW_SECONDS", "300"))

class GatewayInternalAuth(BaseAuthentication):
    def authenticate(self, request):
        uid = request.headers.get("X-Internal-User-Id")
        ts  = request.headers.get("X-Internal-Ts")
        sig = request.headers.get("X-Internal-Sig")
        cid = request.headers.get("X-Internal-Couple-Id") or ""
        role= request.headers.get("X-Internal-Role") or ""

        # 내부 헤더 없으면 다음 인증(JWT)으로
        if not (uid and ts and sig):
            log.debug("GW-AUTH: missing internal headers uid=%s ts=%s sig=%s", uid, ts, bool(sig))
            return None

        # timestamp 검증
        try:
            now, tsi = int(time.time()), int(ts)
            if abs(now - tsi) > INTERNAL_SKEW_SECONDS:
                log.warning("GW-AUTH: stale ts now=%s ts=%s skew=%s", now, tsi, INTERNAL_SKEW_SECONDS)
                raise exceptions.AuthenticationFailed("stale internal timestamp")
        except ValueError:
            log.warning("GW-AUTH: invalid ts header ts=%r", ts)
            raise exceptions.AuthenticationFailed("invalid internal timestamp")

        # HMAC 서명 검증
        payload = f"{uid}|{cid}|{role}|{ts}".encode("utf-8")
        expect  = hmac.new(GATEWAY_HMAC_SECRET, payload, hashlib.sha256).digest()
        try:
            recvd = base64.b64decode(sig)
        except Exception:
            log.warning("GW-AUTH: invalid base64 sig")
            raise exceptions.AuthenticationFailed("invalid internal signature")

        if not hmac.compare_digest(expect, recvd):
            log.warning("GW-AUTH: signature mismatch uid=%s", uid)
            raise exceptions.AuthenticationFailed("bad internal signature")

        # OK → User 구성
        User = get_user_model()
        try:
            user = User.objects.get(pk=int(uid))
        except User.DoesNotExist:
            user = User(id=int(uid), username=f"internal-{uid}")
            user.set_unusable_password()

        log.info("GW-AUTH: OK uid=%s", uid)
        return (user, None)
