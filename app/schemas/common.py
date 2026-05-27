"""공통 응답 DTO — public 엔드포인트 등에서 사용."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class VersionInfo(BaseModel):
    """force-upgrade 게이트용 — Java VersionInfo record 와 JSON 동일."""

    latest: str
    min_supported: str = Field(serialization_alias="minSupported")

    model_config = ConfigDict(populate_by_name=True)
