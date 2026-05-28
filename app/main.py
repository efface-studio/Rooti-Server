"""FastAPI app factory.

실행:
    cd Rooti-Server
    uv run uvicorn app.main:app --reload --port 8080

기존 Java 서버와 같은 환경변수(.env.dev / .env.prod) 그대로 사용 가능합니다.
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from slowapi.errors import RateLimitExceeded

from app import __version__
from app.api.health import router as actuator_router
from app.api.v1 import api_v1
from app.core.config import get_settings
from app.core.database import dispose_engine, init_engine
from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_logging, get_logger
from app.core.metrics import PrometheusMiddleware, metrics_endpoint
from app.core.middleware import SecurityHeadersMiddleware, TraceIdMiddleware
from app.core.rate_limit import limiter, rate_limit_handler
from app.core.redis import dispose_redis, init_redis

log = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    configure_logging(settings)
    log.info(
        "startup",
        app=settings.app_name,
        env=settings.app_env,
        port=settings.server_port,
        version=__version__,
    )
    init_engine(settings)
    init_redis(settings)
    try:
        yield
    finally:
        log.info("shutdown")
        await dispose_redis()
        await dispose_engine()


def create_app() -> FastAPI:
    settings = get_settings()

    app = FastAPI(
        title="Rooti API",
        version=__version__,
        description="Rooti backend — FastAPI port of the Spring Boot server.",
        # springdoc 등가물: 같은 경로 유지 → 기존 클라이언트/문서 링크 무수정.
        docs_url="/swagger-ui.html",
        openapi_url="/v3/api-docs",
        redoc_url=None,
        # NOTE: 0.115+ 부터는 default_response_class 불필요 —
        # FastAPI 가 Pydantic 으로 직접 JSON bytes 직렬화 (orjson 사용 시 같은 성능).
        lifespan=lifespan,
    )

    # ---- Rate limit (Redis-backed when REDIS_HOST is set) ----
    app.state.limiter = limiter
    app.add_exception_handler(RateLimitExceeded, rate_limit_handler)

    # ---- Middleware ----
    # 순서 (추가 역순으로 실행, 응답은 정순):
    #   외부 → CORS → GZip → SecurityHeaders → TraceId → 라우터
    #   응답  ← CORS ← GZip ← SecurityHeaders ← TraceId ← 라우터
    # GZip 은 SecurityHeaders 앞에 — 헤더가 압축에 영향 안 받게.
    # 500 byte 미만 응답은 압축이 손해라 minimum_size 그 위로.
    app.add_middleware(TraceIdMiddleware)
    app.add_middleware(SecurityHeadersMiddleware)
    app.add_middleware(GZipMiddleware, minimum_size=500, compresslevel=6)
    app.add_middleware(PrometheusMiddleware)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
        allow_headers=["*"],
        expose_headers=["Authorization", "Content-Disposition", "X-Trace-Id"],
        max_age=3600,
    )

    # ---- Error handlers ----
    register_exception_handlers(app)

    # ---- Routers ----
    app.include_router(actuator_router)
    app.include_router(api_v1)

    # ---- Metrics (Prometheus scrape) ----
    # prometheus-fastapi-instrumentator 대신 직접 등록 — 그 패키지가 starlette<1.0.0 을
    # 강제해서 CVE-2026-48710 패치(starlette>=1.0.1)를 막았기 때문.
    app.add_route("/actuator/prometheus", metrics_endpoint, methods=["GET"])

    return app


app = create_app()
