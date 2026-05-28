"""Pytest fixtures.

DB/Redis 가 필요 없는 단위·라우터 테스트용 픽스처.
실제 Postgres 가 필요한 통합 테스트는 별도 fixture(`live_db`) 를 만들어 override 풀면 됨.
"""

from __future__ import annotations

import os
from collections.abc import AsyncIterator, Iterator
from typing import Any
from unittest.mock import AsyncMock

import pytest
from httpx import ASGITransport, AsyncClient

# ---------- env defaults for tests ----------
_DUMMY_ENV = {
    "SPRING_PROFILES_ACTIVE": "test",
    "DB_HOST": "localhost",
    "DB_PORT": "5432",
    "DB_NAME": "rooti_test",
    "DB_USERNAME": "rooti_test",
    "DB_PASSWORD": "rooti_test",
    "DB_SSL_REQUIRED": "false",
    "REDIS_HOST": "localhost",
    "REDIS_PORT": "6379",
    "JWT_SECRET": "test-secret-do-not-use-in-prod-" + "x" * 32,
}
for k, v in _DUMMY_ENV.items():
    os.environ.setdefault(k, v)


# ---------- Fake DB / Redis ----------
class _FakeAsyncSession:
    """SELECT 1 한 줄만 응답하는 SQLAlchemy 세션 stub. health 체크용."""

    async def execute(self, *_: Any, **__: Any) -> Any:
        return None

    async def commit(self) -> None: ...
    async def rollback(self) -> None: ...
    async def close(self) -> None: ...
    async def __aenter__(self) -> _FakeAsyncSession:
        return self

    async def __aexit__(self, *_: object) -> None: ...
    def add(self, _obj: Any) -> None: ...
    async def flush(self) -> None: ...
    async def get(self, *_: Any, **__: Any) -> Any:
        return None


async def _fake_db_session_dep() -> AsyncIterator[_FakeAsyncSession]:
    yield _FakeAsyncSession()


def _make_fake_redis() -> AsyncMock:
    fake = AsyncMock()
    fake.ping = AsyncMock(return_value=True)
    fake.get = AsyncMock(return_value=None)
    fake.set = AsyncMock(return_value=True)
    fake.delete = AsyncMock(return_value=1)
    return fake


@pytest.fixture
def fake_redis() -> AsyncMock:
    return _make_fake_redis()


@pytest.fixture(autouse=True)
def _stub_external_io(fake_redis: AsyncMock) -> Iterator[None]:
    """앱 lifespan 의 init_engine/init_redis 가 실제 IO 안 하게 패치."""
    from app.core import database
    from app.core import redis as redis_mod

    # lifespan 무력화
    saved = {
        "init_engine": database.init_engine,
        "dispose_engine": database.dispose_engine,
        "init_redis": redis_mod.init_redis,
        "dispose_redis": redis_mod.dispose_redis,
    }
    database.init_engine = lambda *a, **k: None  # type: ignore[assignment]
    database.dispose_engine = AsyncMock()  # type: ignore[assignment]
    redis_mod.init_redis = lambda *a, **k: fake_redis  # type: ignore[assignment]
    redis_mod.dispose_redis = AsyncMock()  # type: ignore[assignment]

    # health 체크가 get_sessionmaker() 직접 호출 → fake 로 교체
    database._sessionmaker = lambda: _FakeAsyncSession()  # type: ignore[assignment]
    redis_mod._client = fake_redis  # type: ignore[assignment]

    try:
        yield
    finally:
        for k, v in saved.items():
            if k.startswith("init_") or k.startswith("dispose_"):
                setattr(database if "engine" in k else redis_mod, k, v)
        database._sessionmaker = None  # type: ignore[assignment]
        redis_mod._client = None  # type: ignore[assignment]


@pytest.fixture
async def client(fake_redis: AsyncMock) -> AsyncIterator[AsyncClient]:
    """ASGI 인-프로세스 클라이언트. DB/Redis dependency 는 fake 로 override."""
    from app.core.database import get_db_session
    from app.core.redis import get_redis
    from app.main import create_app

    app = create_app()
    app.dependency_overrides[get_db_session] = _fake_db_session_dep

    async def _fake_redis_dep() -> AsyncIterator[AsyncMock]:
        yield fake_redis

    app.dependency_overrides[get_redis] = _fake_redis_dep

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as ac:
        yield ac

    app.dependency_overrides.clear()
