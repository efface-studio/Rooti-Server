"""companies + company_chargers."""

from __future__ import annotations

from typing import Any

from sqlalchemy import BigInteger, Boolean, ForeignKey, Index, String
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import AuditedMixin, Base, TimestampMixin


class Company(Base, AuditedMixin):
    __tablename__ = "companies"
    __table_args__ = (
        Index("idx_companies_name", "name"),
        Index("idx_companies_use", "use_flag"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    location: Mapped[str | None] = mapped_column(String(500), nullable=True)
    use_flag: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    image_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    template_id: Mapped[str | None] = mapped_column(String(100), nullable=True)
    template_data: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)

    chargers: Mapped[list["CompanyCharger"]] = relationship(
        back_populates="company", cascade="all, delete-orphan"
    )


class CompanyCharger(Base, TimestampMixin):
    """회사 담당자 (CHARGER role 유저 ↔ company)."""

    __tablename__ = "company_chargers"
    __table_args__ = (
        Index("idx_company_chargers_company", "company_id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("users.id", ondelete="RESTRICT"),
        unique=True,
        nullable=False,
    )
    company_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("companies.id", ondelete="RESTRICT"),
        nullable=False,
    )
    is_hired: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    fcm_token: Mapped[str | None] = mapped_column(String(500), nullable=True)

    company: Mapped[Company] = relationship(back_populates="chargers")
