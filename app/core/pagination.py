"""Pagination — Spring Pageable 등가의 page/size 쿼리 dependency."""

from __future__ import annotations

import math
from collections.abc import Sequence
from typing import Annotated, Generic, TypeVar

from fastapi import Depends, Query
from pydantic import BaseModel, ConfigDict, Field

T = TypeVar("T")


class PageParams(BaseModel):
    """?page=0&size=20 — Spring Pageable 기본값과 동일."""

    page: int = Field(0, ge=0)
    size: int = Field(20, ge=1, le=200)

    @property
    def offset(self) -> int:
        return self.page * self.size

    @property
    def limit(self) -> int:
        return self.size


def get_page_params(
    page: Annotated[int, Query(ge=0)] = 0,
    size: Annotated[int, Query(ge=1, le=200)] = 20,
) -> PageParams:
    return PageParams(page=page, size=size)


PagedQuery = Annotated[PageParams, Depends(get_page_params)]


class Page(BaseModel, Generic[T]):
    """PageResponse 와 같은 JSON 모양. service → router 사이 internal carrier."""

    content: list[T]
    page: int
    size: int
    total_elements: int = Field(serialization_alias="totalElements")
    total_pages: int = Field(serialization_alias="totalPages")
    has_next: bool = Field(serialization_alias="hasNext")
    has_previous: bool = Field(serialization_alias="hasPrevious")

    model_config = ConfigDict(populate_by_name=True)

    @classmethod
    def build(
        cls,
        content: Sequence[T],
        *,
        params: PageParams,
        total_elements: int,
    ) -> "Page[T]":
        total_pages = max(1, math.ceil(total_elements / params.size)) if params.size else 0
        return cls(
            content=list(content),
            page=params.page,
            size=params.size,
            total_elements=total_elements,
            total_pages=total_pages,
            has_next=params.page + 1 < total_pages,
            has_previous=params.page > 0,
        )
