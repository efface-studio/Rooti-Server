"""근무일지 메일 자동 스케줄 DTO. 프론트 MSW 계약과 동일."""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, EmailStr, Field

from app.schemas.document import JournalFormat


class Frequency(StrEnum):
    DAILY = "DAILY"
    WEEKLY = "WEEKLY"
    MONTHLY = "MONTHLY"


class Weekday(StrEnum):
    SUN = "SUN"
    MON = "MON"
    TUE = "TUE"
    WED = "WED"
    THU = "THU"
    FRI = "FRI"
    SAT = "SAT"


class JournalScheduleCreateRequest(BaseModel):
    company_id: int = Field(alias="companyId")
    recipient_email: EmailStr = Field(alias="recipientEmail")
    frequency: Frequency
    weekday: Weekday | None = None
    day_of_month: int | None = Field(default=None, ge=1, le=31, alias="dayOfMonth")
    time: str  # 'HH:MM'
    format: JournalFormat = JournalFormat.PDF
    enabled: bool = True

    model_config = ConfigDict(populate_by_name=True)


class JournalScheduleUpdateRequest(BaseModel):
    company_id: int | None = Field(default=None, alias="companyId")
    recipient_email: EmailStr | None = Field(default=None, alias="recipientEmail")
    frequency: Frequency | None = None
    weekday: Weekday | None = None
    day_of_month: int | None = Field(default=None, ge=1, le=31, alias="dayOfMonth")
    time: str | None = None
    format: JournalFormat | None = None
    enabled: bool | None = None

    model_config = ConfigDict(populate_by_name=True)


class JournalScheduleResponse(BaseModel):
    id: int
    company_id: int = Field(serialization_alias="companyId")
    company_name: str | None = Field(default=None, serialization_alias="companyName")
    recipient_email: str = Field(serialization_alias="recipientEmail")
    frequency: Frequency
    weekday: Weekday | None = None
    day_of_month: int | None = Field(default=None, serialization_alias="dayOfMonth")
    time: str
    format: JournalFormat
    enabled: bool
    next_run_at: datetime | None = Field(default=None, serialization_alias="nextRunAt")
    last_run_at: datetime | None = Field(default=None, serialization_alias="lastRunAt")
    created_at: datetime = Field(serialization_alias="createdAt")

    model_config = ConfigDict(populate_by_name=True)
