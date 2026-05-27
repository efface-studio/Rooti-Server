"""FastAPI app factory.

실행:
    cd Rooti-Server
    uv run uvicorn app.main:app --reload --port 8080

기존 Java 서버와 같은 환경변수(.env.dev / .env.prod) 그대로 사용 가능합니다.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from prometheus_fastapi_instrumentator import Instrumentator
from slowapi.errors import RateLimitExceeded

from app.core.middleware import SecurityHeadersMiddleware, TraceIdMiddleware
from app.core.rate_limit import limiter, rate_limit_handler

from app import __version__
from app.api.health import router as actuator_router
from app.api.v1 import api_v1
from app.core.config import get_settings
from app.core.database import dispose_engine, init_engine
from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_logging, get_logger
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
    # 순서 주의: 추가 역순으로 실행. CORS 가 가장 바깥, 그 다음 SecurityHeaders,
    # 그 안에 TraceId — TraceId 가 가장 일찍 실행되어 contextvars 가 모든 로그에 적용.
    app.add_middleware(TraceIdMiddleware)
    app.add_middleware(SecurityHeadersMiddleware)
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

    # ---- Metrics (/metrics, Prometheus scrape) ----
    Instrumentator().instrument(app).expose(app, endpoint="/actuator/prometheus")

    return app


app = create_app()
