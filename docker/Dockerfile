# syntax=docker/dockerfile:1.7
# Rooti backend — FastAPI on Python 3.12.
#
# WeasyPrint (PDF 렌더링) 시스템 의존성:
#   pango, gdk-pixbuf, libffi, harfbuzz, fontconfig — Debian 베이스에서 한 줄.

# --- builder: deps resolved by uv into a wheel cache ---
FROM python:3.12-slim AS builder
ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    UV_NO_CACHE=1

WORKDIR /app

# uv 설치 (정적 바이너리)
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && \
    curl -LsSf https://astral.sh/uv/install.sh | sh && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
ENV PATH="/root/.local/bin:${PATH}"

# 의존성만 먼저 (레이어 캐시)
COPY pyproject.toml ./
RUN uv venv /opt/venv && \
    UV_PROJECT_ENVIRONMENT=/opt/venv uv pip install -e . --no-deps && \
    UV_PROJECT_ENVIRONMENT=/opt/venv uv pip install .

# 소스
COPY app app
COPY migrations ./migrations

# --- runtime ---
FROM python:3.12-slim AS runtime
ENV PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:${PATH}"

# WeasyPrint 런타임 deps + 한글 폰트
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpango-1.0-0 libpangoft2-1.0-0 libharfbuzz0b libgdk-pixbuf2.0-0 \
        libcairo2 shared-mime-info fonts-noto-cjk fontconfig && \
    apt-get clean && rm -rf /var/lib/apt/lists/* && \
    addgroup --system rooti && adduser --system --ingroup rooti rooti

WORKDIR /opt/rooti
COPY --from=builder /opt/venv /opt/venv
COPY --from=builder /app/app /opt/rooti/app
COPY --from=builder /app/migrations /opt/rooti/migrations

USER rooti
EXPOSE 8080

# Production: uvicorn (worker 수는 --workers / gunicorn 으로 확장)
ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
