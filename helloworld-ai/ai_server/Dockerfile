# ===== Build stage =====
FROM python:3.11-slim AS build
WORKDIR /w

RUN apt-get update && apt-get install -y --no-install-recommends build-essential \
 && pip install --no-cache-dir --upgrade pip \
 && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
# wheel 미리 만들어두기 (네트워크 재시도 옵션도 유용)
RUN pip wheel --no-cache-dir --wheel-dir /wheels -r requirements.txt

COPY . /src

# ===== Runtime stage =====
FROM python:3.11-slim
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# wheels로 설치(빠르고 깨끗)
COPY --from=build /wheels /wheels
COPY requirements.txt .
RUN pip install --no-cache-dir --find-links=/wheels -r requirements.txt

# 앱 소스
COPY --from=build /src /app

EXPOSE 8000

# collectstatic은 이미지에 정적파일 포함하고 싶으면 여기서
# (환경에 따라 S3 Storage면 건너뛰거나 조건 분기)
RUN python manage.py collectstatic --noinput || true

# 마이그레이션 후 gunicorn
ENTRYPOINT ["/bin/sh","-lc","python manage.py migrate --noinput && exec gunicorn ai_server.wsgi:application --bind 0.0.0.0:8000 --workers 2 --threads 4 --timeout 60"]
