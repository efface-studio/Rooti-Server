"""Job (standard / worker / process) DTO."""

from __future__ import annotations

from datetime import time
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ProcessUpsertRequest(BaseModel):
    id: int | None = None
    name: str = Field(min_length=1, max_length=200)
    sequence: int = Field(ge=0)
    video_path: str | None = Field(default=None, alias="videoPath")
    start_message: str | None = Field(default=None, alias="startMessage")
    end_message: str | None = Field(default=None, alias="endMessage")
    context: dict[str, Any] | None = None
    process_time_seconds: int = Field(ge=0, alias="processTimeSeconds")

    model_config = ConfigDict(populate_by_name=True)


class JobStandardCreateRequest(BaseModel):
    company_id: int = Field(alias="companyId")
    name: str = Field(min_length=1, max_length=200)
    routine_start_time: time | None = Field(default=None, alias="routineStartTime")
    standard_work_time_seconds: int | None = Field(default=0, ge=0, alias="standardWorkTimeSeconds")
    standard_rest_time_seconds: int | None = Field(default=0, ge=0, alias="standardRestTimeSeconds")
    start_message: str | None = Field(default=None, alias="startMessage")
    end_message: str | None = Field(default=None, alias="endMessage")
    context: dict[str, Any] | None = None
    for_journal: bool | None = Field(default=False, alias="forJournal")
    processes: list[ProcessUpsertRequest] | None = None

    model_config = ConfigDict(populate_by_name=True)


class JobStandardUpdateRequest(BaseModel):
    name: str | None = Field(default=None, max_length=200)
    routine_start_time: time | None = Field(default=None, alias="routineStartTime")
    standard_work_time_seconds: int | None = Field(default=None, ge=0, alias="standardWorkTimeSeconds")
    standard_rest_time_seconds: int | None = Field(default=None, ge=0, alias="standardRestTimeSeconds")
    start_message: str | None = Field(default=None, alias="startMessage")
    end_message: str | None = Field(default=None, alias="endMessage")
    context: dict[str, Any] | None = None
    for_journal: bool | None = Field(default=None, alias="forJournal")

    model_config = ConfigDict(populate_by_name=True)


class JobProcessResponse(BaseModel):
    id: int
    name: str
    sequence: int
    video_path: str | None = Field(default=None, serialization_alias="videoPath")
    start_message: str | None = Field(default=None, serialization_alias="startMessage")
    end_message: str | None = Field(default=None, serialization_alias="endMessage")
    context: dict[str, Any] | None = None
    process_time_seconds: int = Field(serialization_alias="processTimeSeconds")

    model_config = ConfigDict(populate_by_name=True)


class JobStandardResponse(BaseModel):
    id: int
    company_id: int = Field(serialization_alias="companyId")
    company_name: str | None = Field(default=None, serialization_alias="companyName")
    name: str
    use_flag: bool = Field(serialization_alias="useFlag")
    routine_start_time: time | None = Field(default=None, serialization_alias="routineStartTime")
    standard_work_time_seconds: int = Field(serialization_alias="standardWorkTimeSeconds")
    standard_rest_time_seconds: int = Field(serialization_alias="standardRestTimeSeconds")
    start_message: str | None = Field(default=None, serialization_alias="startMessage")
    end_message: str | None = Field(default=None, serialization_alias="endMessage")
    context: dict[str, Any] | None = None
    for_journal: bool = Field(serialization_alias="forJournal")
    processes: list[JobProcessResponse] = Field(default_factory=list)

    model_config = ConfigDict(populate_by_name=True)


class AssignJobWorkerRequest(BaseModel):
    company_worker_id: int = Field(alias="companyWorkerId")
    job_standard_id: int = Field(alias="jobStandardId")

    model_config = ConfigDict(populate_by_name=True)


class JobWorkerResponse(BaseModel):
    id: int
    job_standard_id: int = Field(serialization_alias="jobStandardId")
    job_standard_name: str | None = Field(default=None, serialization_alias="jobStandardName")
    company_worker_id: int = Field(serialization_alias="companyWorkerId")
    use_flag: bool = Field(serialization_alias="useFlag")

    model_config = ConfigDict(populate_by_name=True)
