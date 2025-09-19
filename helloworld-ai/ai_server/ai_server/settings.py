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

# ---- DB (환경에 따라 SQLite ↔ Postgres 자동 전환) ---------------------------
# pip install dj-database-url psycopg[binary]
import dj_database_url

# 기본: 로컬 개발용 SQLite
DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": BASE_DIR / "db.sqlite3",
    }
}

DB_URL = os.getenv("DB_URL")  # 예: postgres://user:pass@host:5432/db?sslmode=require
if DB_URL:
    DATABASES["default"] = dj_database_url.parse(DB_URL, conn_max_age=600)
    # 안정성 옵션 보강
    DATABASES["default"].setdefault("OPTIONS", {})
    DATABASES["default"]["OPTIONS"].setdefault("connect_timeout", 5)

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


SPECTACULAR_SETTINGS = {
    "SECURITY": [
        {"X-App-Token": []},  # X-App-Token을 사용한 인증
    ],
    "SECURITY_SCHEMES": {
        "X-App-Token": {
            "type": "apiKey",  # apiKey 타입을 사용
            "in": "header",    # 헤더에 포함
            "name": "X-App-Token",  # 헤더 이름 설정
        },
    },
}

