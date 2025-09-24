# api/auth_internal.py
import os, logging, re
from django.contrib.auth import get_user_model
from rest_framework.authentication import BaseAuthentication
from rest_framework import exceptions

log = logging.getLogger(__name__)

class InternalAuth(BaseAuthentication):
    def authenticate(self, request):
        cid  = request.headers.get("X-Internal-Couple-Id")

        # 1) 커플ID 헤더만 필수
        if not cid:
            log.debug("GW-AUTH: missing X-Internal-Couple-Id")
            return None

        # 2) 숫자만 허용 (공백/따옴표 정리)
        cid_str = str(cid).strip().strip('"').strip("'")
        if not re.fullmatch(r"\d+", cid_str):
            raise exceptions.AuthenticationFailed("invalid couple id")

        couple_id = int(cid_str)

        # 3) (선택) request에 보조 필드 넣어주기
        request.couple_id = couple_id

        # 4) OK → User 구성 (현재 코드 스타일 유지)
        User = get_user_model()
        try:
            user = User.objects.get(pk=couple_id)
        except User.DoesNotExist:
            user = User(id=couple_id, username=f"internal-{couple_id}")
            user.set_unusable_password()

        log.info("GW-AUTH: OK couple_id=%s", couple_id)
        return (user, None)
