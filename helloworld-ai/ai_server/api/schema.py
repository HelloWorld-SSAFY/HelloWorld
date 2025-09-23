# api/schema.py  (이 파일 새로 추가)
from drf_spectacular.extensions import OpenApiAuthenticationExtension

class BearerTokenScheme(OpenApiAuthenticationExtension):
    target_class = "rest_framework_simplejwt.authentication.JWTAuthentication"
    name = "bearerAuth"  # 위 SECURITY 이름과 일치
    def get_security_definition(self, auto_schema):
        return {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"}
