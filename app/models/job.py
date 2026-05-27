"""job_standards + job_processes + job_workers."""

from __future__ import annotations

from datetime import time
from typing import Any

from sqlalchemy import (
    BigInteger,
    Boolean,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    Time,
    UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import AuditedMixin, Base, TimestampMixin


class JobStandard(Base, AuditedMixin):
    """업무 표준 (한 회사에 여러 표준)."""

    __tablename__ = "job_standards"
    __table_args__ = (
        Index("idx_job_standards_company", "company_id"),
        Index("idx_job_standards_use", "use_flag"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    company_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("companies.id", ondelete="RESTRICT"), nullable=False
    )
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    use_flag: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    routine_start_time: Mapped[time | None] = mapped_column(Time, nullable=True)
    standard_work_time: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    standard_rest_time: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    start_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    end_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    context: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)
    for_journal: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)

    processes: Mapped[list["JobProcess"]] = relationship(
        back_populates="standard", cascade="all, delete-orphan", order_by="JobProcess.sequence"
    )


class JobProcess(Base, TimestampMixin):
    __tablename__ = "job_processes"
    __table_args__ = (
        UniqueConstraint("job_standard_id", "sequence", name="uq_job_process_seq"),
        Index("idx_job_processes_standard", "job_standard_id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    job_standard_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("job_standards.id", ondelete="CASCADE"), nullable=False
    )
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    sequence: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    video_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    start_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    end_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    context: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)
    process_time: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    standard: Mapped[JobStandard] = relationship(back_populates="processes")


class JobWorker(Base, TimestampMixin):
    """근로자 ↔ 업무표준 매핑."""

    __tablename__ = "job_workers"
    __table_args__ = (
        UniqueConstraint("company_worker_id", "job_standard_id", name="uq_job_worker"),
        Index("idx_job_workers_standard", "job_standard_id"),
        Index("idx_job_workers_use", "job_standard_id", "use_flag"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    company_worker_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("company_workers.id", ondelete="CASCADE"), nullable=False
    )
    job_standard_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("job_standards.id", ondelete="CASCADE"), nullable=False
    )
    use_flag: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    context: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)
