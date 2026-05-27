"""WorkRecord + WorkProcessRecord DTO."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from app.models import WorkRecordType


class RecordStartRequest(BaseModel):
    work_schedule_id: int = Field(alias="workScheduleId")
    type: WorkRecordType
    at: datetime | None = None

    model_config = ConfigDict(populate_by_name=True)


class RecordEndRequest(BaseModel):
    work_schedule_id: int = Field(alias="workScheduleId")
    type: WorkRecordType
    at: datetime | None = None

    model_config = ConfigDict(populate_by_name=True)


class RecordResponse(BaseModel):
    id: int
    work_schedule_id: int = Field(serialization_alias="workScheduleId")
    type: WorkRecordType
    start_at: datetime = Field(serialization_alias="startAt")
    end_at: datetime | None = Field(default=None, serialization_alias="endAt")

    model_config = ConfigDict(populate_by_name=True)


class ProcessStartRequest(BaseModel):
    work_schedule_id: int = Field(alias="workScheduleId")
    job_process_id: int = Field(alias="jobProcessId")
    type: str | None = None
    at: datetime | None = None
    condition: int | None = None
    answer: str | None = None
    voice_path: str | None = Field(default=None, alias="voicePath")
    process: dict[str, Any] | None = None

    model_config = ConfigDict(populate_by_name=True)


class ProcessEndRequest(BaseModel):
    work_schedule_id: int = Field(alias="workScheduleId")
    job_process_id: int = Field(alias="jobProcessId")
    at: datetime | None = None
    condition: int | None = None
    answer: str | None = None
    voice_path: str | None = Field(default=None, alias="voicePath")
    process: dict[str, Any] | None = None

    model_config = ConfigDict(populate_by_name=True)


class ProcessRecordResponse(BaseModel):
    id: int
    work_schedule_id: int = Field(serialization_alias="workScheduleId")
    job_process_id: int = Field(serialization_alias="jobProcessId")
    type: str
    start_at: datetime | None = Field(default=None, serialization_alias="startAt")
    end_at: datetime | None = Field(default=None, serialization_alias="endAt")
    start_condition: int | None = Field(default=None, serialization_alias="startCondition")
    end_condition: int | None = Field(default=None, serialization_alias="endCondition")
    start_answer: str | None = Field(default=None, serialization_alias="startAnswer")
    end_answer: str | None = Field(default=None, serialization_alias="endAnswer")
    start_voice_path: str | None = Field(default=None, serialization_alias="startVoicePath")
    end_voice_path: str | None = Field(default=None, serialization_alias="endVoicePath")
    process: dict[str, Any] | None = None

    model_config = ConfigDict(populate_by_name=True)
