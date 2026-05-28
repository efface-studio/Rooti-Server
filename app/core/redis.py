"""Async Redis client. Replaces spring-data-redis (Lettuce)."""

from __future__ import annotations

from collections.abc import AsyncIterator

import redis.asyncio as redis

from app.core.config import Settings, get_settings

_client: redis.Redis | None = None


def init_redis(settings: Settings | None = None) -> redis.Redis:
    global _client
    if _client is not None:
        return _client

    settings = settings or get_settings()
    _client = redis.from_url(
        settings.redis_url,
        max_connections=16,  # application.yml 의 lettuce.pool.max-active
        socket_timeout=3,  # application.yml 의 timeout: 3s
        socket_connect_timeout=3,
        decode_responses=True,
    )
    return _client


async def dispose_redis() -> None:
    global _client
    if _client is not None:
        await _client.aclose()
        _client = None


async def get_redis() -> AsyncIterator[redis.Redis]:
    if _client is None:
        raise RuntimeError("Redis not initialised. Call init_redis() in lifespan.")
    yield _client
