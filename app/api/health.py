"""Health / readiness — `/actuator/health` 의 Spring Boot 호환 응답.

기존 모니터링(Prometheus / Alarm / k8s probe) 이 같은 경로/필드를 본다고 가정.
Spring 응답 예:
    { "status": "UP", "components": { "db": {"status":"UP"}, "redis": {"status":"UP"} } }
"""

from __future__ import annotations

from typing import Any

from fastapi import status
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app.core.database import get_sessionmaker
from app.core.redis import init_redis
from app.core.router import RootiRouter

router = RootiRouter(tags=["actuator"])


async def _check_db() -> dict[str, Any]:
    try:
        async with get_sessionmaker()() as session:
            await session.execute(text("SELECT 1"))
        return {"status": "UP"}
    except Exception as e:  # pragma: no cover
        return {"status": "DOWN", "error": str(e)}


async def _check_redis() -> dict[str, Any]:
    try:
        client = init_redis()
        pong = await client.ping()
        return {"status": "UP"} if pong else {"status": "DOWN"}
    except Exception as e:  # pragma: no cover
        return {"status": "DOWN", "error": str(e)}


@router.get("/actuator/health", summary="Liveness + readiness")
async def health() -> JSONResponse:
    components = {
        "db": await _check_db(),
        "redis": await _check_redis(),
    }
    overall_up = all(c["status"] == "UP" for c in components.values())
    body = {
        "status": "UP" if overall_up else "DOWN",
        "components": components,
    }
    code = status.HTTP_200_OK if overall_up else status.HTTP_503_SERVICE_UNAVAILABLE
    return JSONResponse(status_code=code, content=body)


@router.get("/actuator/info", summary="Build / version info")
async def info() -> dict[str, Any]:
    from app import __version__

    return {
        "app": {"name": "rooti-server", "version": __version__, "runtime": "python-fastapi"},
    }
