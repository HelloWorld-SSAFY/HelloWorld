# ===== Build stage =====
FROM python:3.11-slim AS build
WORKDIR /w

# 휠 빌드용 도구만 빌드 스테이지에 설치
RUN apt-get update && apt-get install -y --no-install-recommends build-essential \
  && pip install --no-cache-dir --upgrade pip \
  && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip wheel --no-cache-dir --wheel-dir /wheels -r requirements.txt

COPY . /src
# (선택) 빌드 단계에서 static 수집하려면 주석 해제
# WORKDIR /src
# RUN python manage.py collectstatic --noinput || true


# ===== Runtime stage =====
FROM python:3.11-slim
WORKDIR /app

# 기본 파이썬/펩 설정
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    # Prometheus 멀티프로세스 스토리지 경로 (django-prometheus 필수)
    PROMETHEUS_MULTIPROC_DIR=/tmp/prom

# 의존성 설치 (빌드 스테이지에서 만든 wheel 사용)
COPY --from=build /wheels /wheels
COPY requirements.txt .
RUN pip install --no-cache-dir --no-index --find-links=/wheels -r requirements.txt

# 앱 소스 복사
COPY --from=build /src /app

# (선택) 런타임에서 static 수집
RUN python manage.py collectstatic --noinput || true

EXPOSE 8000

# 중요: Gunicorn/Django 시작 전에 멀티프로세스 디렉터리 생성/초기화
# 그리고 마이그레이션 후 서버 실행
ENTRYPOINT ["/bin/sh","-lc", "\
  rm -rf ${PROMETHEUS_MULTIPROC_DIR}/* 2>/dev/null || true; \
  mkdir -p ${PROMETHEUS_MULTIPROC_DIR}; \
  python manage.py migrate --noinput; \
  exec gunicorn ai_server.wsgi:application -w 2 --threads 4 -b 0.0.0.0:8000 --timeout 60 \
"]
