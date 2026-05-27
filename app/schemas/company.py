"""Company 요청/응답 DTO."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class CompanyCreateRequest(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    location: str | None = Field(default=None, max_length=500)
    image_path: str | None = Field(default=None, max_length=500, alias="imagePath")
    template_id: str | None = Field(default=None, max_length=100, alias="templateId")
    template_data: dict[str, Any] | None = Field(default=None, alias="templateData")

    model_config = ConfigDict(populate_by_name=True)


class CompanyUpdateRequest(BaseModel):
    name: str | None = Field(default=None, max_length=200)
    location: str | None = Field(default=None, max_length=500)
    image_path: str | None = Field(default=None, max_length=500, alias="imagePath")
    template_id: str | None = Field(default=None, max_length=100, alias="templateId")
    template_data: dict[str, Any] | None = Field(default=None, alias="templateData")

    model_config = ConfigDict(populate_by_name=True)


class CompanyResponse(BaseModel):
    id: int
    name: str
    location: str | None = None
    use_flag: bool = Field(serialization_alias="useFlag")
    image_path: str | None = Field(default=None, serialization_alias="imagePath")
    template_id: str | None = Field(default=None, serialization_alias="templateId")
    template_data: dict[str, Any] | None = Field(default=None, serialization_alias="templateData")

    model_config = ConfigDict(from_attributes=True, populate_by_name=True)
