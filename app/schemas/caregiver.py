"""Caregiver 요청/응답 DTO."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class CaregiverResponse(BaseModel):
    id: int
    user_id: int = Field(serialization_alias="userId")

    model_config = ConfigDict(from_attributes=True, populate_by_name=True)


class RegisterWorkerRequest(BaseModel):
    worker_id: int = Field(alias="workerId")

    model_config = ConfigDict(populate_by_name=True)


class CaregiverRelationResponse(BaseModel):
    id: int
    caregiver_id: int = Field(serialization_alias="caregiverId")
    challenged_worker_id: int = Field(serialization_alias="challengedWorkerId")

    model_config = ConfigDict(from_attributes=True, populate_by_name=True)
