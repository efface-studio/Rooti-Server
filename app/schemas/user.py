"""User DTO."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models import UserRole


class UserDTO(BaseModel):
    id: int
    username: str
    email: str | None = None
    name: str
    phone_number: str | None = Field(default=None, serialization_alias="phoneNumber")
    role: UserRole
    enabled: bool
    last_login_at: datetime | None = Field(default=None, serialization_alias="lastLoginAt")

    model_config = ConfigDict(from_attributes=True, populate_by_name=True)
