"""실 DB 통합 테스트 픽스처.

testcontainers 로 일회용 Postgres 컨테이너를 띄우고, Flyway 마이그레이션(SQL 파일)을
DB 부팅 시 직접 실행한다. SQLAlchemy 의 `Base.metadata.create_all()` 을 쓰지 않는 이유:
운영과 동일한 Flyway 스키마를 검증해야 모델/스키마 불일치를 잡을 수 있기 때문.

스코프: session — 컨테이너 하나로 모든 테스트 공유. 테스트 간 격리는 각 테스트가 자체
트랜잭션 rollback 으로 처리.
"""

from __future__ import annotations

import os
from collections.abc import AsyncIterator, Iterator
from pathlib import Path
from unittest.mock import AsyncMock

# Colima / OrbStack / 비표준 Docker 경로 자동 탐지 — DOCKER_HOST 미설정 환경 지원.
if not os.environ.get("DOCKER_HOST"):
    for candidate in (
        Path.home() / ".colima/default/docker.sock",
        Path.home() / ".orbstack/run/docker.sock",
        Path.home() / ".docker/run/docker.sock",
        Path("/var/run/docker.sock"),
    ):
        if candidate.exists():
            os.environ["DOCKER_HOST"] = f"unix://{candidate}"
            break

# Ryuk(watchdog 컨테이너) 비활성 — Colima 환경에서는 socket bind-mount 가 안 됨.
# 트레이드오프: pytest 가 크래시하면 컨테이너 수동 정리 필요 (`docker ps -a | grep tc-`).
os.environ.setdefault("TESTCONTAINERS_RYUK_DISABLED", "true")

import asyncpg
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from testcontainers.postgres import PostgresContainer

_MIGRATIONS_DIR = Path(__file__).resolve().parents[2] / "migrations"


@pytest.fixture(scope="session")
def pg_container() -> Iterator[PostgresContainer]:
    with PostgresContainer("postgres:16-alpine") as pg:
        yield pg


@pytest_asyncio.fixture(scope="session")
async def db_url(pg_container: PostgresContainer) -> str:
    """Flyway SQL 을 직접 실행 후 asyncpg DSN 반환."""
    host = pg_container.get_container_host_ip()
    port = int(pg_container.get_exposed_port(5432))
    user = pg_container.username
    password = pg_container.password
    db = pg_container.dbname

    conn = await asyncpg.connect(host=host, port=port, user=user, password=password, database=db)
    try:
        for sql_path in sorted(_MIGRATIONS_DIR.glob("V*.sql")):
            await conn.execute(sql_path.read_text(encoding="utf-8"))
    finally:
        await conn.close()
    return f"postgresql+asyncpg://{user}:{password}@{host}:{port}/{db}"


@pytest_asyncio.fixture(scope="session")
async def engine(db_url: str) -> AsyncIterator[AsyncEngine]:
    eng = create_async_engine(db_url, future=True, pool_pre_ping=True)
    yield eng
    await eng.dispose()


@pytest_asyncio.fixture(scope="session")
async def sessionmaker(engine: AsyncEngine) -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)


@pytest_asyncio.fixture
async def db(sessionmaker: async_sessionmaker[AsyncSession]) -> AsyncIterator[AsyncSession]:
    """함수 단위 세션 — 테스트 끝나면 rollback (격리)."""
    async with sessionmaker() as session:
        try:
            yield session
        finally:
            await session.rollback()


@pytest_asyncio.fixture
async def live_client(
    sessionmaker: async_sessionmaker[AsyncSession],
    pg_container: PostgresContainer,
) -> AsyncIterator[AsyncClient]:
    """실 DB + fake Redis 가 연결된 FastAPI 클라이언트."""
    # JWT_SECRET 등 testenv는 root conftest가 이미 세팅. 여기선 DB만 진짜.
    os.environ["DB_HOST"] = pg_container.get_container_host_ip()
    os.environ["DB_PORT"] = str(pg_container.get_exposed_port(5432))
    os.environ["DB_NAME"] = pg_container.dbname
    os.environ["DB_USERNAME"] = pg_container.username
    os.environ["DB_PASSWORD"] = pg_container.password
    os.environ["DB_SSL_REQUIRED"] = "false"
    # Settings 의 lru_cache 무효화
    from app.core.config import get_settings

    get_settings.cache_clear()

    from app.core.database import get_db_session
    from app.core.redis import get_redis
    from app.main import create_app

    app = create_app()

    async def _real_db_dep() -> AsyncIterator[AsyncSession]:
        async with sessionmaker() as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise

    fake_redis = AsyncMock()
    fake_redis.ping = AsyncMock(return_value=True)
    fake_redis.get = AsyncMock(return_value=None)
    fake_redis.set = AsyncMock(return_value=True)
    fake_redis.delete = AsyncMock(return_value=1)

    async def _fake_redis_dep() -> AsyncIterator[AsyncMock]:
        yield fake_redis

    app.dependency_overrides[get_db_session] = _real_db_dep
    app.dependency_overrides[get_redis] = _fake_redis_dep

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as ac:
        yield ac

    app.dependency_overrides.clear()
    get_settings.cache_clear()
