"""bulk_email_jobs 테이블 (근무일지 일괄 메일 발송 잡/이력)."""

from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class BulkEmailJob(Base, TimestampMixin):
    """일괄 메일 발송 1회 = 1 row. 상태/이력 추적용.

    status: QUEUED → SENDING → SUCCESS | FAILED
    """

    __tablename__ = "bulk_email_jobs"
    __table_args__ = (
        CheckConstraint("status IN ('QUEUED','SENDING','SUCCESS','FAILED')", name="bej_status_chk"),
        CheckConstraint("format IN ('PDF','HWP','XLSX')", name="bej_format_chk"),
        Index("idx_bej_company_id", "company_id", "id"),
        Index("idx_bej_status", "status"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    company_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("companies.id", ondelete="CASCADE"), nullable=False
    )
    recipient_email: Mapped[str] = mapped_column(String(255), nullable=False)
    target_date: Mapped[date] = mapped_column(Date, nullable=False)
    format: Mapped[str] = mapped_column(String(10), nullable=False, default="PDF")
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="QUEUED")
    schedule_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    truncated: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    message_id: Mapped[str | None] = mapped_column(String(255), nullable=True)
    error_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    requested_by: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)
