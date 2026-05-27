"""Push notification DTO."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class PushRequest(BaseModel):
    token: str = Field(min_length=1)
    title: str = Field(min_length=1)
    body: str | None = None
    deep_link: str | None = Field(default=None, alias="deepLink")
    extra: dict[str, Any] | None = None

    model_config = ConfigDict(populate_by_name=True)
