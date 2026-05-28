"""DB 에 VARCHAR 로 저장되는 enum 값들.

Java EnumType.STRING + CHECK 제약과 같은 정합성. Python 3.11+ `StrEnum` 으로
`Enum.value` 가 그대로 비교/저장 가능.
"""

from __future__ import annotations

from enum import StrEnum


class UserRole(StrEnum):
    ADMIN = "ADMIN"
    CHARGER = "CHARGER"
    WORKER = "WORKER"
    CAREGIVER = "CAREGIVER"

    @property
    def authority(self) -> str:
        return f"ROLE_{self.value}"


class WorkRecordType(StrEnum):
    """근무 기록 타입 — work_records.type CHECK."""

    ON = "ON"
    WORK = "WORK"
    REST = "REST"
    OFF = "OFF"


class WorkProcessRecordType(StrEnum):
    """work_process_records.type — 현재는 PROCESS 만 존재하지만 확장 가능."""

    PROCESS = "PROCESS"


class CaregiverDocumentRequestOn(StrEnum):
    """caregiver_document_types.request_on CHECK."""

    NOTHING = "NOTHING"
    REGISTER = "REGISTER"


class CaregiverDocumentActionType(StrEnum):
    """caregiver_document_logs.action_type CHECK."""

    UPLOAD = "UPLOAD"
    DOWNLOAD = "DOWNLOAD"
    DELETE = "DELETE"


class LeaveType(StrEnum):
    """leaves.type CHECK — 연차/월차/병가/기타."""

    ANNUAL = "ANNUAL"
    MONTHLY = "MONTHLY"
    SICK = "SICK"
    OTHER = "OTHER"


class LeaveStatus(StrEnum):
    """leaves.status CHECK — 대기/승인/반려."""

    PENDING = "PENDING"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"
