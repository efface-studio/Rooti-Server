"""Worker 요청/응답 DTO."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class WorkerCreateRequest(BaseModel):
    username: str = Field(min_length=1, max_length=150)
    email: EmailStr = Field(max_length=255)
    password: str = Field(min_length=8, max_length=100)
    name: str = Field(min_length=1, max_length=100)
    phone_number: str | None = Field(default=None, max_length=32, alias="phoneNumber")

    model_config = ConfigDict(populate_by_name=True)


class WorkerHireRequest(BaseModel):
    company_id: int = Field(alias="companyId")
    worker_id: int = Field(alias="workerId")

    model_config = ConfigDict(populate_by_name=True)


class WorkerResponse(BaseModel):
    """ChallengedWorker → 응답. user 정보도 평면 포함."""

    id: int
    user_id: int = Field(serialization_alias="userId")
    username: str
    name: str
    email: str | None = None
    phone_number: str | None = Field(default=None, serialization_alias="phoneNumber")

    model_config = ConfigDict(populate_by_name=True)


class CompanyWorkerResponse(BaseModel):
    id: int
    company_id: int = Field(serialization_alias="companyId")
    challenged_worker_id: int = Field(serialization_alias="challengedWorkerId")
    is_hired: bool = Field(serialization_alias="isHired")

    model_config = ConfigDict(from_attributes=True, populate_by_name=True)
