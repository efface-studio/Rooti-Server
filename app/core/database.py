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

# Re-export so기존 import 경로(`from app.core.database import Base`) 호환.
from app.models.base import Base  # noqa: F401

from app.core.config import Settings, get_settings

_engine: AsyncEngine | None = None
_sessionmaker: async_sessionmaker[AsyncSession] | None = None


def init_engine(settings: Settings | None = None) -> AsyncEngine:
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
    async with get_sessionmaker()() as session:
        async with session.begin():
            yield session
