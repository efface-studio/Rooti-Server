"""Kiosk binding / management DTO."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class KioskBindRequest(BaseModel):
    company_id: int = Field(alias="companyId")
    kiosk_id: str = Field(min_length=1, max_length=100, alias="kioskId")
    name: str | None = Field(default=None, max_length=200)
    location: str | None = Field(default=None, max_length=500)
    capacity: int = 0

    model_config = ConfigDict(populate_by_name=True)


class KioskAssigneeRequest(BaseModel):
    assignee: str | None = None

    model_config = ConfigDict(populate_by_name=True)


class KioskResponse(BaseModel):
    id: int
    company_id: int = Field(serialization_alias="companyId")
    company_name: str | None = Field(default=None, serialization_alias="companyName")
    kiosk_id: str = Field(serialization_alias="kioskId")
    name: str | None = None
    location: str | None = None
    capacity: int = 0
    current: int = 0
    status: str = "OFFLINE"
    assignee: str | None = None
    last_reported_at: datetime | None = Field(default=None, serialization_alias="lastReportedAt")

    model_config = ConfigDict(populate_by_name=True)
