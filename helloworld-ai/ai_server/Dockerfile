# ===== Build stage =====
FROM python:3.11-slim AS build
WORKDIR /workspace

# 시스템 deps (psycopg 등 빌드용), pip 업그레이드
RUN apt-get update && apt-get install -y --no-install-recommends build-essential && \
    pip install --no-cache-dir --upgrade pip && \
    rm -rf /var/lib/apt/lists/*

# 의존성
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 소스
COPY . .

# 정적파일 수집 (DEBUG와 관계없이 실패 없게)
RUN python manage.py collectstatic --noinput || true

# ===== Runtime stage =====
FROM python:3.11-slim
WORKDIR /app

# 런타임 deps (psycopg 바이너리만 쓰면 빌드툴 불필요)
RUN pip install --no-cache-dir --upgrade pip

# build에서 site-packages와 소스만 복사
COPY --from=build /usr/local/lib/python3.11/site-packages /usr/local/lib/python3.11/site-packages
COPY --from=build /workspace /app

EXPOSE 8000

# 마이그레이션 후 gunicorn 실행
ENTRYPOINT ["/bin/sh","-lc","python manage.py migrate && exec gunicorn ai_server.wsgi:application --bind 0.0.0.0:8000 --workers 2 --threads 4 --timeout 60"]
