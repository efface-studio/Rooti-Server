"""Caregiver board DTO."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class BoardWriteRequest(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    body: str = Field(min_length=1)
    published: bool | None = None

    model_config = ConfigDict(populate_by_name=True)


class BoardResponse(BaseModel):
    id: int
    title: str
    body: str
    author_name: str | None = Field(default=None, serialization_alias="authorName")
    author_id: int = Field(serialization_alias="authorId")
    published: bool
    created_at: datetime = Field(serialization_alias="createdAt")
    updated_at: datetime = Field(serialization_alias="updatedAt")

    model_config = ConfigDict(populate_by_name=True)
