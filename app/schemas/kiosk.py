"""Kiosk binding / management DTO."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class KioskBindRequest(BaseModel):
    company_id: int = Field(alias="companyId")
    kiosk_id: str = Field(min_length=1, max_length=100, alias="kioskId")
    name: str | None = Field(default=None, max_length=200)
    location: str | None = Field(default=None, max_length=500)
    # 수용 인원은 음수가 될 수 없음. 비현실적 큰 값도 방어.
    capacity: int = Field(default=0, ge=0, le=100_000)

    model_config = ConfigDict(populate_by_name=True)


class KioskAssigneeRequest(BaseModel):
    # company_kiosks.assignee 는 VARCHAR(200) — 컬럼 길이와 맞춰 제한.
    assignee: str | None = Field(default=None, max_length=200)

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
