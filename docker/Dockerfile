# syntax=docker/dockerfile:1.7
# Rooti backend — FastAPI on Python 3.12.
#
# WeasyPrint (PDF 렌더링) 시스템 의존성:
#   pango, gdk-pixbuf, libffi, harfbuzz, fontconfig — Debian 베이스에서 한 줄.

# --- builder: Poetry 가 런타임 venv(/opt/venv)에 의존성 설치 ---
FROM python:3.12-slim AS builder
ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    POETRY_NO_INTERACTION=1 \
    POETRY_VIRTUALENVS_CREATE=false

WORKDIR /app

# Poetry 는 빌더 시스템 python 에, 런타임용 venv 는 최종 경로(/opt/venv)에 미리 만든다
# (경로 재배치 없이 그대로 복사 → 셰뱅/pyvenv.cfg 깨짐 방지).
RUN pip install "poetry>=2.0" && python -m venv /opt/venv
ENV PATH="/opt/venv/bin:${PATH}" VIRTUAL_ENV="/opt/venv"

# 의존성만 먼저 (레이어 캐시). non-package 모드라 main 그룹만, dev 제외.
COPY pyproject.toml poetry.lock ./
RUN poetry install --only main

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
