"""Public — 미인증 엔드포인트.

- /api/v1/public/version : force-upgrade 게이트
- /api/v1/public/ping    : 헬스체크 (앱이 부팅 시 호출)
"""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends

from app.core.config import Settings, get_settings
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.schemas.common import VersionInfo

router = RootiRouter(tags=["public"])


@router.get("/version", summary="Latest / minimum-supported app versions")
async def version(
    settings: Annotated[Settings, Depends(get_settings)],
) -> ApiResponse[VersionInfo]:
    return ApiResponse.ok(
        VersionInfo(latest=settings.version_latest, min_supported=settings.version_min_supported)
    )


@router.get("/ping")
async def ping() -> ApiResponse[str]:
    return ApiResponse.ok("pong")
