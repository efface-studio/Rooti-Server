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


class BulkPreviewItem(BaseModel):
    """발송 명단 한 줄 — ZIP 에 묶일 근무일지 하나(=근로자 1명의 그날 일정)."""

    schedule_id: int = Field(serialization_alias="scheduleId")
    # leaveApi.byWorker 가 쓰는 식별자(=challenged_workers.id). 일지에 휴가를 얹을 때 사용.
    worker_id: int = Field(serialization_alias="workerId")
    worker_name: str = Field(serialization_alias="workerName")
    job_standard_id: int = Field(serialization_alias="jobStandardId")
    job_standard_name: str = Field(serialization_alias="jobStandardName")
    start_at: datetime = Field(serialization_alias="startAt")
    end_at: datetime | None = Field(default=None, serialization_alias="endAt")
    # 그 일정에 쌓인 근무기록(WorkRecord) 건수 — 0 이면 "아직 기록 없음" 을 명단에서 바로 표시.
    record_count: int = Field(serialization_alias="recordCount")

    model_config = ConfigDict(populate_by_name=True)


class BulkPreviewResponse(BaseModel):
    """회사+날짜로 '즉시 발송' 시 ZIP 에 들어갈 근무일지 명단.

    실제 발송(bulk-email)과 동일한 조회 로직을 read-only 로 노출해, 관리자가
    누구의 일지가 몇 건 묶이는지 보내기 전에 확인할 수 있게 한다. 포맷
    (PDF/HWP/XLSX)은 문서 본문 렌더링에만 영향을 주므로 명단에는 포함하지 않는다.
    """

    company_id: int = Field(serialization_alias="companyId")
    company_name: str = Field(serialization_alias="companyName")
    date: date
    journal_count: int = Field(serialization_alias="journalCount")
    items: list[BulkPreviewItem]

    model_config = ConfigDict(populate_by_name=True)
