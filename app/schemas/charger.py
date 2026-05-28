"""Company charger (회사 담당자) DTO."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class ChargerResponse(BaseModel):
    id: int
    user_id: int = Field(serialization_alias="userId")
    username: str
    name: str
    phone_number: str | None = Field(default=None, serialization_alias="phoneNumber")
    email: str | None = None
    company_id: int = Field(serialization_alias="companyId")
    company_name: str | None = Field(default=None, serialization_alias="companyName")
    hired: bool

    model_config = ConfigDict(populate_by_name=True)


class ChargerUpdateRequest(BaseModel):
    hired: bool | None = None

    model_config = ConfigDict(populate_by_name=True)
