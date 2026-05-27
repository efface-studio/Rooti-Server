"""SQLAlchemy Base + TimestampMixin.

`Base` 는 단일 metadata 를 공유 → `Base.metadata.create_all()` 한 번이면
모든 테이블 등록. 통합 테스트의 테이블 생성에 사용.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, func
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    """모든 모델의 베이스."""


class TimestampMixin:
    """created_at / updated_at — DB 트리거 `trg_set_updated_at()` 가 UPDATE 시 갱신."""

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=False),
        nullable=False,
        server_default=func.current_timestamp(),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=False),
        nullable=False,
        server_default=func.current_timestamp(),
    )


class AuditedMixin(TimestampMixin):
    """created_by / updated_by 가 있는 테이블용."""

    from sqlalchemy import BigInteger

    created_by: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    updated_by: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
