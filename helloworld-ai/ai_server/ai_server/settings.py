"""
Django settings for ai_server project.
"""

from pathlib import Path
import os
from dotenv import load_dotenv
from corsheaders.defaults import default_headers

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
    'django_prometheus',

    'api',
    'rest_framework',
    'drf_spectacular',
    'corsheaders',
]


# ---- 미들웨어 ---------------------------------------------------------------
MIDDLEWARE = [
    'django_prometheus.middleware.PrometheusBeforeMiddleware',
    'corsheaders.middleware.CorsMiddleware',
    'api.middleware.app_token_mw',
    'api.middleware.couple_id_mw', 
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
    'django_prometheus.middleware.PrometheusAfterMiddleware',
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
# 요구사항: 로컬 기본은 SQLite, 단 .env에서 명시적으로 켤 때만 Postgres 사용
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

# 스위치: USE_DB_URL=true 일 때에만 Postgres로 전환 (없으면/false면 SQLite 유지)
USE_DB_URL = (os.getenv("USE_DB_URL") or "").strip().lower() in ("1", "true", "yes", "on")
DB_URL = (os.getenv("DB_URL") or "").strip()  # e.g. postgres://user:pass@host:5432/db?sslmode=prefer

# 기본: 로컬 개발용 SQLite
DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": BASE_DIR / "db.sqlite3",
    }
}

if USE_DB_URL:
    if DB_URL:
        # 방식 A: DSN 문자열 우선
        # 로컬 호스트면 sslmode 누락 시 disable로 보정(Windows SSL 네고 이슈 회피)
        if ("localhost" in DB_URL or "127.0.0.1" in DB_URL) and "sslmode=" not in DB_URL:
            DB_URL = DB_URL + ("&" if "?" in DB_URL else "?") + "sslmode=disable"

        DATABASES["default"] = _add_pg_options(
            dj_database_url.parse(DB_URL, conn_max_age=600)
        )
    else:
        # 방식 B: 개별 ENV 조합 (ConfigMap/Secret)
        # 필수 값(DB_NAME/DB_USER/DB_PASSWORD 중 하나라도 없으면 SQLite 유지)
        DB_NAME = os.getenv("DB_NAME")
        DB_USER = os.getenv("DB_USER")
        DB_PASSWORD = os.getenv("DB_PASSWORD")
        DB_HOST = os.getenv("DB_HOST", "localhost")
        DB_PORT = int(os.getenv("DB_PORT", "5432"))
        if DB_NAME and DB_USER and DB_PASSWORD:
            DATABASES["default"] = _add_pg_options({
                "ENGINE": "django.db.backends.postgresql",
                "NAME": DB_NAME,
                "USER": DB_USER,
                "PASSWORD": DB_PASSWORD,
                "HOST": DB_HOST,
                "PORT": DB_PORT,
                "CONN_MAX_AGE": 600,
            })
        # else: 값이 불완전하면 안전하게 SQLite 유지

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
CORS_ALLOW_HEADERS = list(default_headers) + [
    "x-app-token",
    "authorization",      # ← 추가
    "x-app-token",
    "x-access-token",
    "x-couple-id",
]
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
SPECTACULAR_SETTINGS = {
    "TITLE": "임산부 헬스케어 API",
    "VERSION": "v0.2.1",

    # 전역 보안 요구 OFF → 각 API에서 헤더 파라미터로 입력
    "SECURITY": [],

    "SERVERS": [
        {"url": "/ai", "description": "via gateway"},
        {"url": "/",  "description": "in-cluster direct"},
    ],

    # securitySchemes은 유지(원하면 Authorize로도 사용 가능)
    "COMPONENTS": {
        "securitySchemes": {
            # X-App-Token 필요하다면 남기고,
            "AppToken": {
                "type": "apiKey",
                "in": "header",
                "name": "X-App-Token",
            },

            # Bearer JWT
            "bearerAuth": {
                "type": "http",
                "scheme": "bearer",
                "bearerFormat": "JWT",
            },
        }
    },

    # 보기 설정(원래 값 유지 + 필요 최소치만)
    "SORT_OPERATION_PARAMETERS": False,
    "SWAGGER_UI_SETTINGS": {
        "persistAuthorization": False,  # 전역 인증 저장 안 함
    },
}

# 게이트웨이 베이스 경로는 미들웨어에서 처리(기본 "/ai"). 필요 시 환경변수로 조정 가능.
# BASE_PATH_PREFIX = os.getenv("BASE_PATH_PREFIX", "/ai")