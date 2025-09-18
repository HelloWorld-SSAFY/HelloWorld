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

# ---- 경로 -------------------------------------------------------------------
BASE_DIR = Path(__file__).resolve().parent.parent  # .../helloworld-ai/ai_server

# 보안 키 (운영에서는 env로 빼는 걸 권장)
SECRET_KEY = "django-insecure-vv_ci4xdaa5gq#ow!^_a4sf11+zpf+nse9acy9l7ip)(#^kdgh"

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

# ---- DB ---------------------------------------------------------------------
DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": os.getenv("DB_NAME"),
        "USER": os.getenv("DB_USER"),
        "PASSWORD": os.getenv("DB_PASSWORD"),
        "HOST": os.getenv("DB_HOST"),
        "PORT": os.getenv("DB_PORT", "5432"),
        "CONN_MAX_AGE": 60,
        "OPTIONS": {
            "sslmode": os.getenv("DB_SSLMODE", "prefer"),
            # 전용 스키마를 쓸 때만 ↓
            "options": f"-c search_path={os.getenv('DB_SEARCH_PATH','public')}",
        },
    }
}

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
