"""company_kiosks + option_variables (자유형 옵션 변수)."""

from __future__ import annotations

from sqlalchemy import BigInteger, ForeignKey, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class CompanyKiosk(Base, TimestampMixin):
    __tablename__ = "company_kiosks"
    __table_args__ = (
        UniqueConstraint("company_id", "kiosk_id", name="uq_company_kiosk"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    company_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("companies.id", ondelete="CASCADE"), nullable=False
    )
    kiosk_id: Mapped[str] = mapped_column(String(100), nullable=False)


class OptionVariable(Base, TimestampMixin):
    """자유형 옵션 변수 (Django 시절 from-DB feature flag 흔적)."""

    __tablename__ = "option_variables"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(100), unique=True, nullable=False)
    for_what: Mapped[str | None] = mapped_column(String(100), nullable=True)
    value: Mapped[str | None] = mapped_column(Text, nullable=True)
