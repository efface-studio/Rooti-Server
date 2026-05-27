"""Kiosk binding DTO."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class KioskBindRequest(BaseModel):
    company_id: int = Field(alias="companyId")
    kiosk_id: str = Field(min_length=1, max_length=100, alias="kioskId")

    model_config = ConfigDict(populate_by_name=True)


class KioskResponse(BaseModel):
    id: int
    company_id: int = Field(serialization_alias="companyId")
    kiosk_id: str = Field(serialization_alias="kioskId")

    model_config = ConfigDict(from_attributes=True, populate_by_name=True)
