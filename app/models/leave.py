"""leaves 테이블 (휴가)."""

from __future__ import annotations

from datetime import date

from sqlalchemy import (
    BigInteger,
    CheckConstraint,
    Date,
    ForeignKey,
    Index,
    Integer,
    Text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin
from app.models.enums import LeaveStatus, LeaveType
from app.models.types import StrEnumType


class Leave(Base, TimestampMixin):
    __tablename__ = "leaves"
    __table_args__ = (
        CheckConstraint("type IN ('ANNUAL','MONTHLY','SICK','OTHER')", name="leaves_type_chk"),
        CheckConstraint("status IN ('PENDING','APPROVED','REJECTED')", name="leaves_status_chk"),
        CheckConstraint("end_date >= start_date", name="leaves_range_chk"),
        Index("idx_leaves_worker", "worker_id", "start_date"),
        Index("idx_leaves_company", "company_id", "start_date"),
        Index("idx_leaves_status", "status"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    worker_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("challenged_workers.id", ondelete="CASCADE"), nullable=False
    )
    company_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("companies.id", ondelete="RESTRICT"), nullable=False
    )
    type: Mapped[LeaveType] = mapped_column(StrEnumType(LeaveType, length=20), nullable=False)
    start_date: Mapped[date] = mapped_column(Date, nullable=False)
    end_date: Mapped[date] = mapped_column(Date, nullable=False)
    days: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    status: Mapped[LeaveStatus] = mapped_column(
        StrEnumType(LeaveStatus, length=20), nullable=False, default=LeaveStatus.PENDING
    )
    reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_by: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
