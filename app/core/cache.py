"""Redis cache helper — Java `@Cacheable` / `@CacheEvict` 등가.

설계 의도:
- 단순 헬퍼 (`cache_get`, `cache_set`, `cache_evict`). 데코레이터 매직 없음 — 서비스가
  명시적으로 호출하는 게 추적/디버깅이 쉬움.
- 값은 JSON 직렬화. Pydantic 모델은 `model_dump_json()` / `model_validate_json()` 사용.
- 키 네임스페이스: "rooti:cache:{namespace}:{id}".
- 미연결(Redis 다운) 시 silent fallback — None 반환, 캐시 미스로 처리.
"""

from __future__ import annotations

import logging
from typing import TypeVar

from pydantic import BaseModel
from redis.asyncio import Redis

log = logging.getLogger(__name__)

_PREFIX = "rooti:cache"

M = TypeVar("M", bound=BaseModel)


def cache_key(namespace: str, key: int | str) -> str:
    return f"{_PREFIX}:{namespace}:{key}"


async def get_model(
    client: Redis, model_cls: type[M], namespace: str, key: int | str
) -> M | None:
    try:
        raw = await client.get(cache_key(namespace, key))
    except Exception as e:  # Redis 다운 등 — 캐시 미스로 처리
        log.debug("cache get failed (%s:%s): %s", namespace, key, e)
        return None
    if raw is None:
        return None
    try:
        return model_cls.model_validate_json(raw)
    except Exception as e:  # 호환되지 않는 캐시 (스키마 변경 등) — 안전 무시
        log.warning("cache deserialize failed (%s:%s): %s", namespace, key, e)
        return None


async def set_model(
    client: Redis,
    model: BaseModel,
    namespace: str,
    key: int | str,
    *,
    ttl_seconds: int = 600,
) -> None:
    try:
        await client.set(
            cache_key(namespace, key),
            model.model_dump_json(by_alias=True),
            ex=ttl_seconds,
        )
    except Exception as e:
        log.debug("cache set failed (%s:%s): %s", namespace, key, e)


async def evict(client: Redis, namespace: str, key: int | str) -> None:
    try:
        await client.delete(cache_key(namespace, key))
    except Exception as e:
        log.debug("cache evict failed (%s:%s): %s", namespace, key, e)
