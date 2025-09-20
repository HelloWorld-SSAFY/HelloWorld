"""
Django settings for ai_server project.
"""

from pathlib import Path
import os
from dotenv import load_dotenv

# ---- env 로드 ---------------------------------------------------------------
load_dotenv()  # ai_server/.env

# 환경변수(로컬 편의상 DEBUG 기본 True)
APP_TOKEN = os.getenv("APP_TOKEN")
DEBUG = os.getenv("DJANGO_DEBUG", "True") == "True"
ALLOWED_HOSTS = [h for h in os.getenv("DJANGO_ALLOWED_HOSTS", "").split(",") if h]
CORS_ALLOWED_ORIGINS = [h for h in os.getenv("CORS_ALLOWED_ORIGINS", "").split(",") if h]
# HTTPS(선택: Ingress가 X-Forwarded-Proto 세팅한다면)
SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')

# ---- 경로 -------------------------------------------------------------------
BASE_DIR = Path(__file__).resolve().parent.parent  # .../helloworld-ai/ai_server

# 보안 키 (운영에서는 env로 빼는 걸 권장)
SECRET_KEY = os.getenv("DJANGO_SECRET_KEY")

# ---- 앱 ---------------------------------------------------------------------
INSTALLED_APPS = [
    # 'django.contrib.admin',  # 관리자 안 쓰면 주석 유지
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',

    'api',
    'rest_framework',
    'drf_spectacular',
    'corsheaders',
]

# ---- 미들웨어 ---------------------------------------------------------------
MIDDLEWARE = [
    'corsheaders.middleware.CorsMiddleware',
    'api.middleware.app_token_mw',

    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'ai_server.urls'

# ---- 템플릿 (★ 중요: templates 폴더 등록) -----------------------------------
TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        # manage.py 옆: ai_server/templates/
        "DIRS": [ BASE_DIR / "templates" ],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    }
]

WSGI_APPLICATION = 'ai_server.wsgi.application'

# ---- DB (Postgres via env; optional DB_URL) --------------------------------
import os
import dj_database_url  # pip install dj-database-url
# psycopg2-binary 또는 psycopg[binary] 중 하나 설치 필요

def _add_pg_options(db: dict) -> dict:
    """sslmode / search_path / connect_timeout 주입"""
    opts = db.setdefault("OPTIONS", {})
    sslmode = os.getenv("DB_SSLMODE")        # e.g. prefer / require / disable
    if sslmode:
        opts["sslmode"] = sslmode
    search_path = os.getenv("DB_SEARCH_PATH")  # e.g. "public"
    if search_path:
        opts["options"] = f"-c search_path={search_path}"
    opts.setdefault("connect_timeout", 5)
    return db

DB_URL = os.getenv("DB_URL")  # e.g. postgres://user:pass@host:5432/db?sslmode=prefer

if DB_URL:
    # 방식 A: DSN 문자열 우선
    DATABASES = {
        "default": _add_pg_options(
            dj_database_url.parse(DB_URL, conn_max_age=600)
        )
    }
else:
    # 방식 B: 개별 ENV 조합 (ConfigMap/Secret)
    DATABASES = {
        "default": _add_pg_options({
            "ENGINE": "django.db.backends.postgresql",
            "NAME": os.getenv("DB_NAME"),
            "USER": os.getenv("DB_USER"),
            "PASSWORD": os.getenv("DB_PASSWORD"),
            "HOST": os.getenv("DB_HOST", "localhost"),
            "PORT": int(os.getenv("DB_PORT", "5432")),
            "CONN_MAX_AGE": 600,
        })
    }

# 운영 보호: SQLite로 떨어지는 사고 방지
if not DEBUG:
    assert DATABASES["default"]["ENGINE"].endswith("postgresql"), "PostgreSQL required in production"


# ---- 국제화 ------------------------------------------------------------------
LANGUAGE_CODE = "ko-kr"
TIME_ZONE = "Asia/Seoul"
USE_I18N = True
USE_TZ = True

# ---- 정적 파일 ---------------------------------------------------------------
STATIC_URL = "/static/"
# 개발에서 openapi.yaml 읽기(위치: ai_server/ai_server/static/openapi.yaml)
STATICFILES_DIRS = [ BASE_DIR / "ai_server" / "static" ]

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# ---- CORS / DRF -------------------------------------------------------------
CORS_ALLOW_ALL_ORIGINS = True  # 개발용. 운영에서는 False + 화이트리스트 권장

REST_FRAMEWORK = {
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    "DEFAULT_RENDERER_CLASSES": ["rest_framework.renderers.JSONRenderer"],
    "DEFAULT_PARSER_CLASSES": ["rest_framework.parsers.JSONParser"],
    "DEFAULT_THROTTLE_CLASSES": [
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.UserRateThrottle",
    ],
    "DEFAULT_THROTTLE_RATES": {"anon": "3/second", "user": "3/second"},
}

# ---- drf-spectacular (Swagger/OpenAPI) --------------------------------------
# settings.py
SPECTACULAR_SETTINGS = {
    "TITLE": "임산부 헬스케어 API",
    "VERSION": "v0.2.1",

    # ✅ 네가 지정한 전역 보안/서버 — 그대로 유지
    "SECURITY": [{"AppToken": []}],
    "SERVERS": [
        {"url": "/ai", "description": "via gateway"},
        {"url": "/",  "description": "in-cluster direct"},
    ],

    # ✅ securitySchemes에 AppToken 정의(Authorize 버튼이 X-App-Token을 인식)
    "COMPONENTS": {
        "securitySchemes": {
            "AppToken": {
                "type": "apiKey",
                "in": "header",
                "name": "X-App-Token",
            }
        }
    },

    # 보기 설정(원래 값 유지 + 필요 최소치만)
    "SORT_OPERATION_PARAMETERS": False,
    "SWAGGER_UI_SETTINGS": {
        "persistAuthorization": False,  # 전역 인증 저장 안 함
    },
}

