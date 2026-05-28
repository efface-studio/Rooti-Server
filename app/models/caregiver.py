"""caregivers + caregiver_worker_relations."""

from __future__ import annotations

from sqlalchemy import BigInteger, ForeignKey, Index, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class Caregiver(Base, TimestampMixin):
    __tablename__ = "caregivers"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("users.id", ondelete="RESTRICT"),
        unique=True,
        nullable=False,
    )

    @classmethod
    def of(cls, user_id: int) -> Caregiver:
        return cls(user_id=user_id)


class CaregiverWorkerRelation(Base, TimestampMixin):
    """보호자 ↔ 근로자 N:M."""

    __tablename__ = "caregiver_worker_relations"
    __table_args__ = (
        UniqueConstraint("caregiver_id", "challenged_worker_id", name="uq_caregiver_worker"),
        Index("idx_cwr_worker", "challenged_worker_id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    caregiver_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("caregivers.id", ondelete="CASCADE"),
        nullable=False,
    )
    challenged_worker_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("challenged_workers.id", ondelete="CASCADE"),
        nullable=False,
    )
