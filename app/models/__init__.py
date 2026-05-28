"""ORM 모델 패키지.

이 패키지를 import 하면 `Base.metadata` 가 모든 테이블을 알게 된다.
Alembic / 통합 테스트의 `create_all` 도 여기를 보고 작동.
"""

from app.models.base import Base, TimestampMixin
from app.models.board import CaregiverBoard
from app.models.caregiver import Caregiver, CaregiverWorkerRelation
from app.models.company import Company, CompanyCharger
from app.models.document import (
    CaregiverDocument,
    CaregiverDocumentLog,
    CaregiverDocumentType,
)
from app.models.enums import (
    CaregiverDocumentActionType,
    CaregiverDocumentRequestOn,
    UserRole,
    WorkProcessRecordType,
    WorkRecordType,
)
from app.models.job import JobProcess, JobStandard, JobWorker
from app.models.kiosk import CompanyKiosk, OptionVariable
from app.models.user import User
from app.models.work import WorkProcessRecord, WorkRecord, WorkSchedule
from app.models.worker import ChallengedWorker, CompanyWorker

__all__ = [
    "Base",
    "Caregiver",
    "CaregiverBoard",
    "CaregiverDocument",
    "CaregiverDocumentActionType",
    "CaregiverDocumentLog",
    "CaregiverDocumentRequestOn",
    "CaregiverDocumentType",
    "CaregiverWorkerRelation",
    "ChallengedWorker",
    "Company",
    "CompanyCharger",
    "CompanyKiosk",
    "CompanyWorker",
    "JobProcess",
    "JobStandard",
    "JobWorker",
    "OptionVariable",
    "TimestampMixin",
    # tables
    "User",
    # enums
    "UserRole",
    "WorkProcessRecord",
    "WorkProcessRecordType",
    "WorkRecord",
    "WorkRecordType",
    "WorkSchedule",
]
