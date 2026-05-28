"""journal_email_schedules 테이블 (근무일지 메일 자동 발송 설정)."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class JournalEmailSchedule(Base, TimestampMixin):
    __tablename__ = "journal_email_schedules"
    __table_args__ = (
        CheckConstraint("frequency IN ('DAILY','WEEKLY','MONTHLY')", name="jes_frequency_chk"),
        CheckConstraint("format IN ('PDF','HWP','XLSX')", name="jes_format_chk"),
        Index("idx_jes_company", "company_id"),
        Index("idx_jes_enabled", "enabled"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    company_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("companies.id", ondelete="CASCADE"), nullable=False
    )
    recipient_email: Mapped[str] = mapped_column(String(255), nullable=False)
    frequency: Mapped[str] = mapped_column(String(20), nullable=False)
    weekday: Mapped[str | None] = mapped_column(String(3), nullable=True)
    day_of_month: Mapped[int | None] = mapped_column(Integer, nullable=True)
    send_time: Mapped[str] = mapped_column(String(5), nullable=False)  # 'HH:MM'
    format: Mapped[str] = mapped_column(String(10), nullable=False, default="PDF")
    enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    next_run_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)
    last_run_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)
