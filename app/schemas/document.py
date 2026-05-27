"""Document DTO."""

from __future__ import annotations

from datetime import date, datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class DocumentResponse(BaseModel):
    id: int
    relation_id: int = Field(serialization_alias="relationId")
    type_id: int = Field(serialization_alias="typeId")
    type_name: str | None = Field(default=None, serialization_alias="typeName")
    filename: str
    download_url: str | None = Field(default=None, serialization_alias="downloadUrl")
    size: int | None = None
    content_type: str | None = Field(default=None, serialization_alias="contentType")
    created_at: datetime = Field(serialization_alias="createdAt")

    model_config = ConfigDict(populate_by_name=True)


class JournalFormat(StrEnum):
    PDF = "PDF"
    HWP = "HWP"
    XLSX = "XLSX"

    @property
    def extension(self) -> str:
        return {"PDF": "pdf", "HWP": "hwp", "XLSX": "xlsx"}[self.value]

    @property
    def content_type(self) -> str:
        return {
            "PDF": "application/pdf",
            "HWP": "application/x-hwp",
            "XLSX": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        }[self.value]


class BulkEmailRequest(BaseModel):
    company_id: int = Field(alias="companyId")
    date: date
    recipient_email: EmailStr = Field(alias="recipientEmail")
    format: JournalFormat = JournalFormat.PDF

    model_config = ConfigDict(populate_by_name=True)


class BulkEmailResult(BaseModel):
    sent: bool
    schedule_count: int = Field(serialization_alias="scheduleCount")
    recipient_email: str = Field(serialization_alias="recipientEmail")
    message: str | None = None

    model_config = ConfigDict(populate_by_name=True)
