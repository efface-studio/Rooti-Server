"""RootiRouter — APIRouter 서브클래스.

ApiResponse(data=None) 가 `data: null` 로 직렬화되지 않게 모든 라우트에
`response_model_exclude_none=True` 를 기본값으로 강제. (Java 의 JsonInclude.NON_NULL 등가)
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter


class RootiRouter(APIRouter):
    def add_api_route(self, path: str, endpoint: Any, **kwargs: Any) -> None:
        # NON_NULL 등가
        kwargs.setdefault("response_model_exclude_none", True)
        # Pydantic `serialization_alias` 사용을 자동 적용 → camelCase JSON 키 (Java 호환).
        kwargs.setdefault("response_model_by_alias", True)
        super().add_api_route(path, endpoint, **kwargs)
