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
    """회사-근로자 채용 매핑 → 응답. 근로자 정보를 nested 로 포함.

    Rooti-Client 의 ``CompanyWorker`` 타입(worker 중첩 + companyName + hired)과
    1:1 매칭 — 회사 상세 화면이 ``cw.worker.name`` 을 직접 읽는다.
    """

    id: int
    company_id: int = Field(serialization_alias="companyId")
    company_name: str = Field(serialization_alias="companyName")
    worker: WorkerResponse
    hired: bool

    model_config = ConfigDict(populate_by_name=True)
