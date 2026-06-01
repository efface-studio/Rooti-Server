"""휴가(Leave) service."""

from __future__ import annotations

from datetime import date

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import aliased

from app.core.exceptions import BusinessException, ErrorCode
from app.core.pagination import Page, PageParams
from app.core.tenant import CompanyScopeValue, assert_company
from app.models import (
    ChallengedWorker,
    Company,
    Leave,
    LeaveStatus,
    LeaveType,
    User,
)
from app.schemas.leave import LeaveCreateRequest, LeaveResponse

# worker_id(challenged_workers.id) → worker name 은 challenged_workers ⋈ users 로 얻음.
_WORKER_NAME = (
    select(ChallengedWorker.id, User.name)
    .join(User, User.id == ChallengedWorker.user_id)
    .subquery()
)

# created_by(users.id) → 작성자 이름. NULL 가능하므로 LEFT JOIN 용 alias.
# (예전엔 행마다 db.get(User, ...) 로 N+1 발생 → base query 에 합쳐 1 쿼리로.)
_Creator = aliased(User)


class LeaveService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    def _base_query(self):
        # Leave ⋈ Company ⋈ (worker name) ⋈ (creator name) — 표시용 join.
        # 작성자는 LEFT JOIN(created_by NULL 허용).
        return (
            select(
                Leave,
                Company.name.label("company_name"),
                _WORKER_NAME.c.name.label("worker_name"),
                _Creator.name.label("creator_name"),
            )
            .join(Company, Company.id == Leave.company_id)
            .join(_WORKER_NAME, _WORKER_NAME.c.id == Leave.worker_id)
            .outerjoin(_Creator, _Creator.id == Leave.created_by)
        )

    def _row_to_response(
        self, leave: Leave, company_name: str, worker_name: str, creator_name: str | None
    ) -> LeaveResponse:
        return LeaveResponse(
            id=leave.id,
            worker_id=leave.worker_id,
            worker_name=worker_name,
            company_id=leave.company_id,
            company_name=company_name,
            type=leave.type,
            start_date=leave.start_date,
            end_date=leave.end_date,
            days=leave.days,
            status=leave.status,
            reason=leave.reason,
            created_by=leave.created_by,
            created_by_name=creator_name,
            created_at=leave.created_at,
        )

    async def search(
        self,
        *,
        keyword: str | None,
        company_id: int | None,
        worker_id: int | None,
        type_: LeaveType | None,
        status: LeaveStatus | None,
        from_: date | None,
        to: date | None,
        params: PageParams,
    ) -> Page[LeaveResponse]:
        q = self._base_query()
        if company_id is not None:
            q = q.where(Leave.company_id == company_id)
        if worker_id is not None:
            q = q.where(Leave.worker_id == worker_id)
        if type_ is not None:
            q = q.where(Leave.type == type_)
        if status is not None:
            q = q.where(Leave.status == status)
        if from_ is not None:
            q = q.where(Leave.end_date >= from_)
        if to is not None:
            q = q.where(Leave.start_date <= to)
        if keyword:
            like = f"%{keyword.strip()}%"
            q = q.where(or_(Company.name.ilike(like), Leave.reason.ilike(like)))

        total = int(
            (await self.db.execute(select(func.count()).select_from(q.subquery()))).scalar_one()
            or 0
        )
        rows = (
            await self.db.execute(
                q.order_by(Leave.start_date.desc()).offset(params.offset).limit(params.limit)
            )
        ).all()
        content = [self._row_to_response(lv, cn, wn, crn) for lv, cn, wn, crn in rows]
        return Page.build(content, params=params, total_elements=total)

    async def list_by_worker(
        self,
        worker_id: int,
        *,
        from_: date | None,
        to: date | None,
        company_scope: CompanyScopeValue = None,
    ) -> list[LeaveResponse]:
        q = self._base_query().where(Leave.worker_id == worker_id)
        # CHARGER 는 본인 회사 소속 휴가만 — 다른 회사 worker_id 조회해도 빈 결과.
        if company_scope is not None:
            q = q.where(Leave.company_id == company_scope)
        if from_ is not None:
            q = q.where(Leave.end_date >= from_)
        if to is not None:
            q = q.where(Leave.start_date <= to)
        rows = (await self.db.execute(q.order_by(Leave.start_date.desc()))).all()
        return [self._row_to_response(lv, cn, wn, crn) for lv, cn, wn, crn in rows]

    async def list_approved(
        self, *, company_id: int | None, from_: date | None, to: date | None
    ) -> list[LeaveResponse]:
        q = self._base_query().where(Leave.status == LeaveStatus.APPROVED)
        if company_id is not None:
            q = q.where(Leave.company_id == company_id)
        if from_ is not None:
            q = q.where(Leave.end_date >= from_)
        if to is not None:
            q = q.where(Leave.start_date <= to)
        rows = (await self.db.execute(q.order_by(Leave.start_date.desc()))).all()
        return [self._row_to_response(lv, cn, wn, crn) for lv, cn, wn, crn in rows]

    async def create(self, req: LeaveCreateRequest, actor_user_id: int) -> LeaveResponse:
        if req.end_date < req.start_date:
            raise BusinessException(ErrorCode.INVALID_INPUT, "종료일이 시작일보다 빠릅니다.")
        if await self.db.get(ChallengedWorker, req.worker_id) is None:
            raise BusinessException(ErrorCode.WORKER_NOT_FOUND)
        if await self.db.get(Company, req.company_id) is None:
            raise BusinessException(ErrorCode.COMPANY_NOT_FOUND)

        days = (req.end_date - req.start_date).days + 1
        leave = Leave(
            worker_id=req.worker_id,
            company_id=req.company_id,
            type=req.type,
            start_date=req.start_date,
            end_date=req.end_date,
            days=days,
            status=LeaveStatus.APPROVED if req.auto_approve else LeaveStatus.PENDING,
            reason=req.reason,
            created_by=actor_user_id,
        )
        self.db.add(leave)
        await self.db.flush()
        return await self._get_response(leave.id)

    async def decide(
        self,
        leave_id: int,
        status: LeaveStatus,
        comment: str | None,
        *,
        company_scope: CompanyScopeValue = None,
    ) -> LeaveResponse:
        leave = await self.db.get(Leave, leave_id)
        if leave is None:
            raise BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "휴가 신청을 찾을 수 없습니다.")
        assert_company(company_scope, leave.company_id)
        leave.status = status
        if comment:
            leave.reason = f"{leave.reason} · {comment}" if leave.reason else comment
        return await self._get_response(leave.id)

    async def delete(self, leave_id: int, *, company_scope: CompanyScopeValue = None) -> None:
        leave = await self.db.get(Leave, leave_id)
        if leave is not None:
            assert_company(company_scope, leave.company_id)
            await self.db.delete(leave)

    async def _get_response(self, leave_id: int) -> LeaveResponse:
        row = (await self.db.execute(self._base_query().where(Leave.id == leave_id))).one()
        leave, cn, wn, crn = row
        return self._row_to_response(leave, cn, wn, crn)
