# ===== Build stage =====
FROM python:3.11-slim AS build
WORKDIR /w
RUN apt-get update && apt-get install -y --no-install-recommends build-essential \
 && pip install --no-cache-dir --upgrade pip \
 && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip wheel --no-cache-dir --wheel-dir /wheels -r requirements.txt
COPY . /src
# (선택) 빌드 단계에서 정적 수집
# WORKDIR /src
# RUN python manage.py collectstatic --noinput || true

# ===== Runtime stage =====
FROM python:3.11-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

COPY --from=build /wheels /wheels
COPY requirements.txt .
RUN pip install --no-cache-dir --no-index --find-links=/wheels -r requirements.txt

COPY --from=build /src /app
# (선택) 런타임에서 수집할 거면 유지
RUN python manage.py collectstatic --noinput || true

EXPOSE 8000
ENTRYPOINT ["/bin/sh","-lc","python manage.py migrate --noinput && exec gunicorn ai_server.wsgi:application --bind 0.0.0.0:8000 --workers 2 --threads 4 --timeout 60"]
