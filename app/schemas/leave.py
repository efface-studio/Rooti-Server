"""휴가(Leave) DTO. 프론트 MSW 계약과 동일한 camelCase 키."""

from __future__ import annotations

from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models import LeaveStatus, LeaveType


class LeaveCreateRequest(BaseModel):
    worker_id: int = Field(alias="workerId")
    company_id: int = Field(alias="companyId")
    type: LeaveType
    start_date: date = Field(alias="startDate")
    end_date: date = Field(alias="endDate")
    reason: str | None = None
    auto_approve: bool = Field(default=False, alias="autoApprove")

    model_config = ConfigDict(populate_by_name=True)


class LeaveDecisionRequest(BaseModel):
    status: LeaveStatus
    comment: str | None = None

    model_config = ConfigDict(populate_by_name=True)


class LeaveResponse(BaseModel):
    id: int
    worker_id: int = Field(serialization_alias="workerId")
    worker_name: str | None = Field(default=None, serialization_alias="workerName")
    company_id: int = Field(serialization_alias="companyId")
    company_name: str | None = Field(default=None, serialization_alias="companyName")
    type: LeaveType
    start_date: date = Field(serialization_alias="startDate")
    end_date: date = Field(serialization_alias="endDate")
    days: int
    status: LeaveStatus
    reason: str | None = None
    created_by: int | None = Field(default=None, serialization_alias="createdBy")
    created_by_name: str | None = Field(default=None, serialization_alias="createdByName")
    created_at: datetime = Field(serialization_alias="createdAt")

    model_config = ConfigDict(populate_by_name=True)
