"""Health / readiness — `/actuator/health` Spring Boot 호환.

Spring `management.endpoint.health.show-details: when_authorized` 에 맞춰:
- 미인증 요청: `{"status": "UP"|"DOWN"}` 만 반환 (컴포넌트 상태 / 에러 메시지 X)
- 운영용 상세 정보가 필요하면 별도 `/actuator/health/detail` (auth 필요) 사용 예정.

Spring 응답 모양과 동일한 키 (`status`, `components`) 그대로 두되, error 문자열은 절대 노출 X
— 모니터링이 status 만 보고 알람.
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import status
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app.core.database import get_sessionmaker
from app.core.redis import init_redis
from app.core.router import RootiRouter

router = RootiRouter(tags=["actuator"])
log = logging.getLogger(__name__)


async def _check_db() -> str:
    try:
        async with get_sessionmaker()() as session:
            await session.execute(text("SELECT 1"))
        return "UP"
    except Exception:  # pragma: no cover
        log.exception("health: db check failed")
        return "DOWN"


async def _check_redis() -> str:
    try:
        client = init_redis()
        pong = await client.ping()
        return "UP" if pong else "DOWN"
    except Exception:  # pragma: no cover
        log.exception("health: redis check failed")
        return "DOWN"


@router.get(
    "/actuator/health",
    summary="Liveness + readiness",
    # OpenAPI 노이즈 줄이려고 401-tolerant 한 단순 응답으로 유지
    responses={503: {"description": "downstream service down"}},
)
async def health() -> JSONResponse:
    db_status = await _check_db()
    redis_status = await _check_redis()
    overall_up = db_status == "UP" and redis_status == "UP"
    body: dict[str, Any] = {
        "status": "UP" if overall_up else "DOWN",
        # 컴포넌트 단위 상태만 — error 문자열은 노출하지 않음 (정보 누출 방지)
        "components": {"db": {"status": db_status}, "redis": {"status": redis_status}},
    }
    code = status.HTTP_200_OK if overall_up else status.HTTP_503_SERVICE_UNAVAILABLE
    return JSONResponse(status_code=code, content=body)


@router.get("/actuator/info", summary="Build / version info")
async def info() -> dict[str, Any]:
    from app import __version__

    return {
        "app": {"name": "rooti-server", "version": __version__, "runtime": "python-fastapi"},
    }
