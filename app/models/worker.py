"""challenged_workers + company_workers."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Index, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, TimestampMixin


class ChallengedWorker(Base, TimestampMixin):
    """근로자 본체 (WORKER role 유저 1:1)."""

    __tablename__ = "challenged_workers"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("users.id", ondelete="RESTRICT"),
        unique=True,
        nullable=False,
    )
    fcm_token: Mapped[str | None] = mapped_column(String(500), nullable=True)
    # NULL = 재직(ACTIVE), 값이 있으면 퇴직(RETIRED) 시각.
    retired_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=False), nullable=True)

    def update_fcm_token(self, token: str) -> None:
        self.fcm_token = token


class CompanyWorker(Base, TimestampMixin):
    """회사-근로자 채용 매핑."""

    __tablename__ = "company_workers"
    __table_args__ = (
        UniqueConstraint("company_id", "challenged_worker_id", name="uq_company_workers"),
        Index("idx_company_workers_worker", "challenged_worker_id"),
        Index("idx_company_workers_hired", "company_id", "is_hired"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    company_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("companies.id", ondelete="RESTRICT"), nullable=False
    )
    challenged_worker_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("challenged_workers.id", ondelete="RESTRICT"),
        nullable=False,
    )
    is_hired: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)

    worker: Mapped[ChallengedWorker] = relationship(lazy="joined")
