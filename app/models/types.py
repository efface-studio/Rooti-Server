"""SQLAlchemy custom column types.

`StrEnumType` — DB 의 VARCHAR <-> Python StrEnum 양방향 자동 변환.
JPA `@Enumerated(EnumType.STRING)` 등가. CHECK 제약은 DB 측에서 처리.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Any

from sqlalchemy import String
from sqlalchemy.types import TypeDecorator


class StrEnumType(TypeDecorator):
    """StrEnum 클래스 하나에 바인딩되는 String 타입."""

    impl = String
    cache_ok = True

    def __init__(self, enum_cls: type[StrEnum], length: int = 50) -> None:
        self.enum_cls = enum_cls
        super().__init__(length=length)

    def process_bind_param(self, value: Any, dialect: Any) -> str | None:
        if value is None:
            return None
        if isinstance(value, self.enum_cls):
            return value.value
        # 문자열로 들어와도 검증을 위해 enum 으로 한 번 통과시키고 다시 str.
        return self.enum_cls(value).value

    def process_result_value(self, value: Any, dialect: Any) -> StrEnum | None:
        if value is None:
            return None
        return self.enum_cls(value)
