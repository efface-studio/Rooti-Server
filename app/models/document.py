"""caregiver_document_types + caregiver_documents + caregiver_document_logs."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    BigInteger,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    String,
    Text,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditedMixin, Base, TimestampMixin
from app.models.enums import CaregiverDocumentActionType, CaregiverDocumentRequestOn
from app.models.types import StrEnumType


class CaregiverDocumentType(Base, TimestampMixin):
    __tablename__ = "caregiver_document_types"
    __table_args__ = (
        CheckConstraint("request_on IN ('NOTHING','REGISTER')", name="cdt_request_on_chk"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(200), unique=True, nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    request_on: Mapped[CaregiverDocumentRequestOn] = mapped_column(
        StrEnumType(CaregiverDocumentRequestOn, length=20),
        nullable=False,
        default=CaregiverDocumentRequestOn.NOTHING,
    )


class CaregiverDocument(Base, AuditedMixin):
    __tablename__ = "caregiver_documents"
    __table_args__ = (
        Index("idx_cd_relation", "relation_id"),
        Index("idx_cd_type", "type_id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    relation_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("caregiver_worker_relations.id", ondelete="CASCADE"),
        nullable=False,
    )
    type_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("caregiver_document_types.id", ondelete="RESTRICT"),
        nullable=False,
    )
    filename: Mapped[str] = mapped_column(String(500), nullable=False)
    file_size: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    content_type: Mapped[str | None] = mapped_column(String(100), nullable=True)


class CaregiverDocumentLog(Base):
    """업로드/다운로드/삭제 로그. updated_at 없음 — append-only."""

    __tablename__ = "caregiver_document_logs"
    __table_args__ = (
        CheckConstraint("action_type IN ('UPLOAD','DOWNLOAD','DELETE')", name="cdl_action_chk"),
        Index("idx_cdl_document", "document_id"),
        Index("idx_cdl_user", "user_id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    document_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("caregiver_documents.id", ondelete="CASCADE"),
        nullable=False,
    )
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )
    action_type: Mapped[CaregiverDocumentActionType] = mapped_column(
        StrEnumType(CaregiverDocumentActionType, length=20), nullable=False
    )
    action_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=False),
        nullable=False,
        server_default=func.current_timestamp(),
    )
