"""company_kiosks + option_variables (자유형 옵션 변수)."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    BigInteger,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class CompanyKiosk(Base, TimestampMixin):
    __tablename__ = "company_kiosks"
    __table_args__ = (
        UniqueConstraint("company_id", "kiosk_id", name="uq_company_kiosk"),
        CheckConstraint("status IN ('IN_USE','FULL','OFFLINE')", name="company_kiosks_status_chk"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    company_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("companies.id", ondelete="CASCADE"), nullable=False
    )
    kiosk_id: Mapped[str] = mapped_column(String(100), nullable=False)
    # V4: 표시/상태 컬럼
    name: Mapped[str | None] = mapped_column(String(200), nullable=True)
    location: Mapped[str | None] = mapped_column(String(500), nullable=True)
    capacity: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    current_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="OFFLINE")
    assignee: Mapped[str | None] = mapped_column(String(200), nullable=True)
    last_reported_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=False), nullable=True
    )


class OptionVariable(Base, TimestampMixin):
    """자유형 옵션 변수 (Django 시절 from-DB feature flag 흔적)."""

    __tablename__ = "option_variables"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(100), unique=True, nullable=False)
    for_what: Mapped[str | None] = mapped_column(String(100), nullable=True)
    value: Mapped[str | None] = mapped_column(Text, nullable=True)
