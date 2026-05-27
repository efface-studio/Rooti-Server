"""Response envelopes — JSON 출력은 Java 측과 100% 동일해야 함.

Java 원본:
  com.rooti.global.response.ApiResponse
    { "success": true, "data": <T>, "timestamp": "2026-05-26T14:00:00+09:00" }
    NON_NULL: data == null 인 경우 키 자체를 생략.

  com.rooti.global.response.PageResponse
    { "content": [...], "page": 0, "size": 20,
      "totalElements": 123, "totalPages": 7, "hasNext": true, "hasPrevious": false }

라우터에서 `response_model_exclude_none=True` 필요 — 기본값은 `RootiRouter` 가 강제함.
"""

from __future__ import annotations

import math
from datetime import datetime
from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict, Field

from app.core.time import now_kst

T = TypeVar("T")
R = TypeVar("R")


class ApiResponse(BaseModel, Generic[T]):
    """Mirrors `com.rooti.global.response.ApiResponse`."""

    success: bool = True
    data: T | None = None
    timestamp: datetime = Field(default_factory=now_kst)

    model_config = ConfigDict(
        # 직렬화 시 timezone-aware datetime 은 자동으로 `...+09:00` 형식으로 출력.
        ser_json_inf_nan="null",
    )

    @classmethod
    def ok(cls, data: T | None = None) -> "ApiResponse[T]":
        return cls(data=data)


class PageResponse(BaseModel, Generic[T]):
    """Mirrors `com.rooti.global.response.PageResponse`.

    Spring `Page<T>` JSON 모양이 버전마다 흔들려서 평탄화된 별도 DTO 로 직렬화하는 관습 유지.
    """

    content: list[T]
    page: int = Field(description="Zero-based page index")
    size: int = Field(description="Items per page")
    total_elements: int = Field(serialization_alias="totalElements")
    total_pages: int = Field(serialization_alias="totalPages")
    has_next: bool = Field(serialization_alias="hasNext")
    has_previous: bool = Field(serialization_alias="hasPrevious")

    model_config = ConfigDict(populate_by_name=True)

    @classmethod
    def of(
        cls,
        content: list[T],
        *,
        page: int,
        size: int,
        total_elements: int,
    ) -> "PageResponse[T]":
        total_pages = max(1, math.ceil(total_elements / size)) if size > 0 else 0
        return cls(
            content=content,
            page=page,
            size=size,
            total_elements=total_elements,
            total_pages=total_pages,
            has_next=page + 1 < total_pages,
            has_previous=page > 0,
        )
