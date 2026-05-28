"""work_schedules + work_records + work_process_records."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    SmallInteger,
    String,
    Text,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditedMixin, Base, TimestampMixin
from app.models.enums import WorkRecordType
from app.models.types import StrEnumType


class WorkSchedule(Base, AuditedMixin):
    __tablename__ = "work_schedules"
    __table_args__ = (
        Index("idx_ws_jobworker_range", "job_worker_id", "start_at"),
        Index("idx_ws_standard_range", "job_standard_id", "start_at"),
        # partial index on end_at IS NULL: SQLAlchemy 는 postgresql_where 로 표현
        Index(
            "idx_ws_open",
            "job_worker_id",
            postgresql_where="end_at IS NULL",
        ),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    job_worker_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("job_workers.id", ondelete="CASCADE"), nullable=False
    )
    company_charger_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey("company_chargers.id", ondelete="SET NULL"),
        nullable=True,
    )
    job_standard_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("job_standards.id", ondelete="RESTRICT"), nullable=False
    )
    start_at: Mapped[datetime] = mapped_column(DateTime(timezone=False), nullable=False)
    end_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)
    work_doc_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    make_work_doc: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)

    @property
    def is_open(self) -> bool:
        return self.end_at is None


class WorkRecord(Base, TimestampMixin):
    """근무 기록 (ON / WORK / REST / OFF)."""

    __tablename__ = "work_records"
    __table_args__ = (
        CheckConstraint(
            "type IN ('ON','WORK','REST','OFF')",
            name="work_records_type_chk",
        ),
        Index("idx_wr_schedule", "work_schedule_id"),
        Index(
            "idx_wr_type_open",
            "work_schedule_id",
            "type",
            postgresql_where="end_at IS NULL",
        ),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    work_schedule_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("work_schedules.id", ondelete="CASCADE"), nullable=False
    )
    type: Mapped[WorkRecordType] = mapped_column(
        StrEnumType(WorkRecordType, length=10), nullable=False
    )
    start_at: Mapped[datetime] = mapped_column(DateTime(timezone=False), nullable=False)
    end_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)

    @property
    def is_open(self) -> bool:
        return self.end_at is None


class WorkProcessRecord(Base, TimestampMixin):
    """프로세스 단위 기록."""

    __tablename__ = "work_process_records"
    __table_args__ = (
        Index("idx_wpr_schedule", "work_schedule_id"),
        Index("idx_wpr_process", "job_process_id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    work_schedule_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("work_schedules.id", ondelete="CASCADE"), nullable=False
    )
    job_process_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("job_processes.id", ondelete="RESTRICT"), nullable=False
    )
    type: Mapped[str] = mapped_column(String(20), nullable=False)
    start_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)
    end_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)
    start_condition: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    end_condition: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    start_answer: Mapped[str | None] = mapped_column(Text, nullable=True)
    end_answer: Mapped[str | None] = mapped_column(Text, nullable=True)
    start_voice_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    end_voice_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    process: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)
