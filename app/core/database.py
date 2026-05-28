"""Async DB engine + session helpers.

Base / TimestampMixin 정의는 `app.models.base` 에 있음 (한 곳에 모음).
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.core.config import Settings, get_settings

# Re-export so기존 import 경로(`from app.core.database import Base`) 호환.
from app.models.base import Base  # noqa: F401

_engine: AsyncEngine | None = None
_sessionmaker: async_sessionmaker[AsyncSession] | None = None


def init_engine(settings: Settings | None = None) -> AsyncEngine:
    """RDS / RDS Proxy 비용·성능 최적화 풀 설정.

    핵심 옵션:
      pool_size           DB_POOL_MAX (작은 RDS 인스턴스에서 conn 폭주 방지)
      max_overflow=0      한도 초과 금지 — 운영에서 100 conn 폭발 → CPU/메모리 폭발 차단
      pool_recycle=1700   28분. RDS 기본 idle timeout(30분) 전에 우리가 먼저 회수 (좀비 conn)
      pool_pre_ping=True  매번 SELECT 1 핑 — pgbouncer/RDS Proxy 가 끊은 연결 자동 갱신
      pool_use_lifo=True  recently used conn 우선 재사용 → 코어 절약 (idle conn TTL 빨리 발동)
      pool_timeout=5      얻기 5초 안 되면 풀 부족으로 빠른 실패

    asyncpg connect_args:
      statement_cache_size=0
      prepared_statement_cache_size=0
        → RDS Proxy (pgbouncer transaction-mode) 호환. Java 의 prepareThreshold=0 과 동일.
        직결 환경이면 settings 추가 후 0 보다 큰 값으로 올려 prepared stmt 캐시 이득 봄.

    query_cache_size=1024:
      SQLAlchemy 가 같은 모양 SQL 의 컴파일 결과를 캐싱. plan-build 단계 절약.
    """
    global _engine, _sessionmaker
    if _engine is not None:
        return _engine
    settings = settings or get_settings()
    _engine = create_async_engine(
        settings.database_url,
        pool_size=settings.db_pool_max,
        max_overflow=0,
        pool_recycle=1700,
        pool_pre_ping=True,
        pool_timeout=5,
        pool_use_lifo=True,  # LIFO — idle conn TTL 빨리 발동 (cost ↓)
        query_cache_size=1024,  # SQL 컴파일 캐시 (plan build 절약)
        connect_args={
            # RDS Proxy / pgbouncer transaction-mode 호환을 위한 안전한 디폴트.
            # 직결만 쓴다면 운영에서 settings 로 올려 prepared-stmt 캐시 효과를 봄.
            "statement_cache_size": 0,
            "prepared_statement_cache_size": 0,
        },
        echo=False,
        future=True,
    )
    _sessionmaker = async_sessionmaker(_engine, expire_on_commit=False, class_=AsyncSession)
    return _engine


async def dispose_engine() -> None:
    global _engine, _sessionmaker
    if _engine is not None:
        await _engine.dispose()
        _engine = None
        _sessionmaker = None


def get_sessionmaker() -> async_sessionmaker[AsyncSession]:
    if _sessionmaker is None:
        raise RuntimeError("Database not initialised. Call init_engine() in lifespan.")
    return _sessionmaker


async def get_db_session() -> AsyncIterator[AsyncSession]:
    """FastAPI dependency. 요청 단위 트랜잭션 (라우터 종료 시 commit/rollback)."""
    async with get_sessionmaker()() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


@asynccontextmanager
async def transactional_session() -> AsyncIterator[AsyncSession]:
    async with get_sessionmaker()() as session, session.begin():
        yield session
