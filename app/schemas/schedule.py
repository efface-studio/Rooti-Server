"""WorkSchedule DTO."""

from __future__ import annotations

from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field


class CreateScheduleRequest(BaseModel):
    job_worker_id: int = Field(alias="jobWorkerId")
    company_charger_id: int | None = Field(default=None, alias="companyChargerId")
    start_at: datetime = Field(alias="startAt")
    end_at: datetime | None = Field(default=None, alias="endAt")
    make_work_doc: bool | None = Field(default=False, alias="makeWorkDoc")

    model_config = ConfigDict(populate_by_name=True)


class BatchMakeRequest(BaseModel):
    job_standard_id: int = Field(alias="jobStandardId")
    date: date
    company_charger_id: int | None = Field(default=None, alias="companyChargerId")
    job_worker_ids: list[int] = Field(default_factory=list, alias="jobWorkerIds")

    model_config = ConfigDict(populate_by_name=True)


class CloseScheduleRequest(BaseModel):
    end_at: datetime | None = Field(default=None, alias="endAt")

    model_config = ConfigDict(populate_by_name=True)


class ScheduleResponse(BaseModel):
    id: int
    job_worker_id: int = Field(serialization_alias="jobWorkerId")
    job_standard_id: int = Field(serialization_alias="jobStandardId")
    job_standard_name: str | None = Field(default=None, serialization_alias="jobStandardName")
    company_charger_id: int | None = Field(default=None, serialization_alias="companyChargerId")
    start_at: datetime = Field(serialization_alias="startAt")
    end_at: datetime | None = Field(default=None, serialization_alias="endAt")
    make_work_doc: bool = Field(serialization_alias="makeWorkDoc")
    work_doc_path: str | None = Field(default=None, serialization_alias="workDocPath")

    model_config = ConfigDict(populate_by_name=True)
