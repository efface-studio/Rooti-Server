"""공통 FastAPI dependency.

서비스 팩토리는 한 줄짜리지만 명시적으로 둠 — 테스트에서 dependency_overrides 로 swap 쉬움.
"""

from __future__ import annotations

from typing import Annotated

import redis.asyncio as redis_lib
from fastapi import Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db_session
from app.core.exceptions import AuthForbiddenException
from app.core.redis import get_redis
from app.core.security import (
    CurrentUser,
    CurrentUserOptional,
    PrincipalDetails,
    current_user,
)
from app.core.tenant import CompanyScopeValue
from app.models import CompanyCharger, UserRole
from app.services.auth import AuthService, RefreshTokenStore
from app.services.board import CaregiverBoardService
from app.services.caregiver import CaregiverService
from app.services.charger import ChargerService
from app.services.company import CompanyService
from app.services.job import JobStandardService, JobWorkerService
from app.services.kiosk import KioskService
from app.services.leave import LeaveService
from app.services.notification import PushNotificationService
from app.services.schedule import WorkScheduleService
from app.services.user import UserService
from app.services.worker import WorkerService
from app.services.workrecord import WorkRecordService

# ---- DB / Redis ----
DbSession = Annotated[AsyncSession, Depends(get_db_session)]
RedisClient = Annotated[redis_lib.Redis, Depends(get_redis)]

# ---- 사용자 ----
__all__ = [
    "AuthSvc",
    "CurrentUser",
    "CurrentUserOptional",
    "DbSession",
    "RedisClient",
    "UserSvc",
    "get_auth_service",
    "get_refresh_store",
    "get_user_service",
]


# ---- 서비스 팩토리 ----
def get_user_service(db: DbSession) -> UserService:
    return UserService(db)


UserSvc = Annotated[UserService, Depends(get_user_service)]


def get_refresh_store(redis_client: RedisClient) -> RefreshTokenStore:
    return RefreshTokenStore(redis_client)


def get_auth_service(
    db: DbSession,
    store: Annotated[RefreshTokenStore, Depends(get_refresh_store)],
) -> AuthService:
    return AuthService(db, store)


AuthSvc = Annotated[AuthService, Depends(get_auth_service)]


# ---- Role guards (Spring @PreAuthorize 등가) ----
def require_roles(*roles: UserRole):
    """`Depends(require_roles(UserRole.ADMIN))` 형태로 사용."""

    allowed = {r.value for r in roles}

    def _check(me: Annotated[PrincipalDetails, Depends(current_user)]) -> PrincipalDetails:
        if not (allowed & set(me.roles)):
            raise AuthForbiddenException(f"required role: {sorted(allowed)}, actual: {me.roles}")
        return me

    return _check


RequireAdmin = Annotated[PrincipalDetails, Depends(require_roles(UserRole.ADMIN))]
RequireAdminOrCharger = Annotated[
    PrincipalDetails, Depends(require_roles(UserRole.ADMIN, UserRole.CHARGER))
]
RequireCaregiver = Annotated[PrincipalDetails, Depends(require_roles(UserRole.CAREGIVER))]


# ---- 멀티테넌트 스코프 (회사 단위 IDOR 가드) ----
async def get_company_scope(me: RequireAdminOrCharger, db: DbSession) -> CompanyScopeValue:
    """현재 사용자의 회사 스코프를 해석한다.

    - ADMIN → ``None`` (전체 회사 접근, 감독자).
    - CHARGER → 담당 ``company_id`` (company_chargers.user_id 는 UNIQUE).
      매핑이 없으면 담당 회사 미지정으로 보고 403.

    이 의존성은 ``RequireAdminOrCharger`` 를 거치므로 그 외 role(WORKER/CAREGIVER)은
    스코프 해석 전에 이미 403 으로 걸러진다.
    """
    if me.has_role(UserRole.ADMIN.value):
        return None
    company_id = (
        await db.execute(
            select(CompanyCharger.company_id).where(CompanyCharger.user_id == me.user_id)
        )
    ).scalar_one_or_none()
    if company_id is None:
        raise AuthForbiddenException("담당 회사가 지정되지 않았습니다.")
    return company_id


CompanyScope = Annotated[CompanyScopeValue, Depends(get_company_scope)]


# ---- 추가 서비스 팩토리 ----
def get_company_service(db: DbSession, redis: RedisClient) -> CompanyService:
    return CompanyService(db, redis)


CompanySvc = Annotated[CompanyService, Depends(get_company_service)]


def get_worker_service(db: DbSession) -> WorkerService:
    return WorkerService(db)


WorkerSvc = Annotated[WorkerService, Depends(get_worker_service)]


def get_caregiver_service(db: DbSession) -> CaregiverService:
    return CaregiverService(db)


CaregiverSvc = Annotated[CaregiverService, Depends(get_caregiver_service)]


def get_kiosk_service(db: DbSession) -> KioskService:
    return KioskService(db)


KioskSvc = Annotated[KioskService, Depends(get_kiosk_service)]


def get_job_standard_service(db: DbSession, redis: RedisClient) -> JobStandardService:
    return JobStandardService(db, redis)


JobStandardSvc = Annotated[JobStandardService, Depends(get_job_standard_service)]


def get_job_worker_service(db: DbSession) -> JobWorkerService:
    return JobWorkerService(db)


JobWorkerSvc = Annotated[JobWorkerService, Depends(get_job_worker_service)]


def get_schedule_service(db: DbSession) -> WorkScheduleService:
    return WorkScheduleService(db)


ScheduleSvc = Annotated[WorkScheduleService, Depends(get_schedule_service)]


def get_workrecord_service(db: DbSession) -> WorkRecordService:
    return WorkRecordService(db)


WorkRecordSvc = Annotated[WorkRecordService, Depends(get_workrecord_service)]


def get_board_service(db: DbSession) -> CaregiverBoardService:
    return CaregiverBoardService(db)


BoardSvc = Annotated[CaregiverBoardService, Depends(get_board_service)]


def get_push_service() -> PushNotificationService:
    return PushNotificationService()


PushSvc = Annotated[PushNotificationService, Depends(get_push_service)]


def get_charger_service(db: DbSession) -> ChargerService:
    return ChargerService(db)


ChargerSvc = Annotated[ChargerService, Depends(get_charger_service)]


def get_leave_service(db: DbSession) -> LeaveService:
    return LeaveService(db)


LeaveSvc = Annotated[LeaveService, Depends(get_leave_service)]
