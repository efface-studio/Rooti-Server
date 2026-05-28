"""Worker service."""

from __future__ import annotations

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import (
    BusinessException,
    ErrorCode,
    UserUsernameDuplicatedException,
)
from app.core.pagination import Page, PageParams
from app.core.security import hash_password
from app.models import (
    ChallengedWorker,
    Company,
    CompanyWorker,
    User,
    UserRole,
)
from app.schemas.worker import (
    CompanyWorkerResponse,
    WorkerCreateRequest,
    WorkerResponse,
)


class WorkerService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def get_or_throw(self, worker_id: int) -> ChallengedWorker:
        worker = await self.db.get(ChallengedWorker, worker_id)
        if worker is None:
            raise BusinessException(ErrorCode.WORKER_NOT_FOUND)
        return worker

    async def get(self, worker_id: int) -> WorkerResponse:
        worker = await self.get_or_throw(worker_id)
        user = await self.db.get(User, worker.user_id)
        assert user is not None
        return _to_worker_response(worker, user)

    async def create(self, req: WorkerCreateRequest) -> WorkerResponse:
        dup = (await self.db.execute(select(User.id).where(User.username == req.username))).first()
        if dup:
            raise UserUsernameDuplicatedException(req.username)

        user = User(
            username=req.username,
            email=req.email,
            password_hash=hash_password(req.password),
            name=req.name,
            phone_number=req.phone_number,
            role=UserRole.WORKER,
            enabled=True,
        )
        self.db.add(user)
        await self.db.flush()

        worker = ChallengedWorker(user_id=user.id)
        self.db.add(worker)
        await self.db.flush()
        return _to_worker_response(worker, user)

    async def search(self, keyword: str | None, params: PageParams) -> Page[WorkerResponse]:
        # User join — keyword 가 있으면 username/name/email 매치
        base = select(ChallengedWorker, User).join(User, User.id == ChallengedWorker.user_id)
        if keyword:
            like = f"%{keyword.strip()}%"
            base = base.where(
                or_(User.username.ilike(like), User.name.ilike(like), User.email.ilike(like))
            )

        total_q = select(func.count()).select_from(base.subquery())
        total = int((await self.db.execute(total_q)).scalar_one() or 0)

        page_q = base.order_by(ChallengedWorker.id.desc()).offset(params.offset).limit(params.limit)
        rows = (await self.db.execute(page_q)).all()
        content = [_to_worker_response(w, u) for w, u in rows]
        return Page.build(content, params=params, total_elements=total)

    # ---------- Hiring ----------
    async def hire(self, company_id: int, worker_id: int) -> CompanyWorkerResponse:
        dup = (
            await self.db.execute(
                select(CompanyWorker.id).where(
                    CompanyWorker.company_id == company_id,
                    CompanyWorker.challenged_worker_id == worker_id,
                )
            )
        ).first()
        if dup:
            raise BusinessException(ErrorCode.WORKER_ALREADY_HIRED)

        # 회사/근로자 존재 확인
        if await self.db.get(Company, company_id) is None:
            raise BusinessException(ErrorCode.COMPANY_NOT_FOUND)
        await self.get_or_throw(worker_id)

        cw = CompanyWorker(company_id=company_id, challenged_worker_id=worker_id, is_hired=True)
        self.db.add(cw)
        await self.db.flush()
        return CompanyWorkerResponse.model_validate(cw)

    async def fire(self, company_worker_id: int) -> None:
        cw = await self.db.get(CompanyWorker, company_worker_id)
        if cw is None:
            raise BusinessException(ErrorCode.WORKER_NOT_FOUND)
        cw.is_hired = False

    async def list_by_company(
        self, company_id: int, params: PageParams
    ) -> Page[CompanyWorkerResponse]:
        base = select(CompanyWorker).where(CompanyWorker.company_id == company_id)
        total = int(
            (await self.db.execute(select(func.count()).select_from(base.subquery()))).scalar_one()
            or 0
        )
        rows = (
            (
                await self.db.execute(
                    base.order_by(CompanyWorker.id.desc()).offset(params.offset).limit(params.limit)
                )
            )
            .scalars()
            .all()
        )
        return Page.build(
            [CompanyWorkerResponse.model_validate(r) for r in rows],
            params=params,
            total_elements=total,
        )


def _to_worker_response(worker: ChallengedWorker, user: User) -> WorkerResponse:
    return WorkerResponse(
        id=worker.id,
        user_id=user.id,
        username=user.username,
        name=user.name,
        email=user.email,
        phone_number=user.phone_number,
    )
