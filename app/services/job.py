"""Job (standard + worker) service."""

from __future__ import annotations

from redis.asyncio import Redis
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core import cache
from app.core.exceptions import BusinessException, ErrorCode
from app.core.pagination import Page, PageParams
from app.models import Company, CompanyWorker, JobProcess, JobStandard, JobWorker
from app.schemas.job import (
    JobProcessResponse,
    JobStandardCreateRequest,
    JobStandardResponse,
    JobStandardUpdateRequest,
    JobWorkerResponse,
    ProcessUpsertRequest,
)

_CACHE_NS = "job_standard"
_CACHE_TTL = 600


# =============================================================================
#  JobStandard
# =============================================================================
class JobStandardService:
    def __init__(self, db: AsyncSession, redis: Redis | None = None) -> None:
        self.db = db
        self.redis = redis

    async def get_or_throw(self, standard_id: int) -> JobStandard:
        s = await self.db.get(JobStandard, standard_id)
        if s is None:
            raise BusinessException(ErrorCode.JOB_STANDARD_NOT_FOUND)
        return s

    async def get(self, standard_id: int) -> JobStandardResponse:
        if self.redis is not None:
            hit = await cache.get_model(self.redis, JobStandardResponse, _CACHE_NS, standard_id)
            if hit is not None:
                return hit
        # processes relation 미리 로드
        result = await self.db.execute(
            select(JobStandard)
            .options(selectinload(JobStandard.processes))
            .where(JobStandard.id == standard_id)
        )
        s = result.scalar_one_or_none()
        if s is None:
            raise BusinessException(ErrorCode.JOB_STANDARD_NOT_FOUND)
        resp = _to_standard_response(s, await self._company_name(s.company_id))
        if self.redis is not None:
            await cache.set_model(self.redis, resp, _CACHE_NS, standard_id, ttl_seconds=_CACHE_TTL)
        return resp

    async def search(
        self, company_id: int | None, keyword: str | None, params: PageParams
    ) -> Page[JobStandardResponse]:
        # N+1 제거: Company JOIN 으로 company.name 을 한 번에 가져옴.
        base = select(JobStandard, Company.name).join(Company, Company.id == JobStandard.company_id)
        if company_id is not None:
            base = base.where(JobStandard.company_id == company_id)
        if keyword:
            base = base.where(JobStandard.name.ilike(f"%{keyword.strip()}%"))

        total = int(
            (await self.db.execute(select(func.count()).select_from(base.subquery()))).scalar_one()
            or 0
        )
        rows = (
            await self.db.execute(
                base.order_by(JobStandard.id.desc()).offset(params.offset).limit(params.limit)
            )
        ).all()
        # summary 모드 (processes 없이)
        content = [_to_standard_response(r, company_name=name, processes=False) for r, name in rows]
        return Page.build(content, params=params, total_elements=total)

    async def create(self, req: JobStandardCreateRequest) -> JobStandardResponse:
        if await self.db.get(Company, req.company_id) is None:
            raise BusinessException(ErrorCode.COMPANY_NOT_FOUND)

        s = JobStandard(
            company_id=req.company_id,
            name=req.name,
            use_flag=True,
            routine_start_time=req.routine_start_time,
            standard_work_time=req.standard_work_time_seconds or 0,
            standard_rest_time=req.standard_rest_time_seconds or 0,
            start_message=req.start_message,
            end_message=req.end_message,
            context=req.context,
            for_journal=bool(req.for_journal),
        )
        if req.processes:
            for p in req.processes:
                s.processes.append(_new_process(p))
        self.db.add(s)
        await self.db.flush()
        if self.redis is not None:
            await cache.evict(self.redis, _CACHE_NS, s.id)
        return _to_standard_response(s, await self._company_name(s.company_id))

    async def update(self, standard_id: int, req: JobStandardUpdateRequest) -> JobStandardResponse:
        s = await self.get_or_throw(standard_id)
        if req.name is not None:
            s.name = req.name
        if req.routine_start_time is not None:
            s.routine_start_time = req.routine_start_time
        if req.standard_work_time_seconds is not None:
            s.standard_work_time = req.standard_work_time_seconds
        if req.standard_rest_time_seconds is not None:
            s.standard_rest_time = req.standard_rest_time_seconds
        if req.start_message is not None:
            s.start_message = req.start_message
        if req.end_message is not None:
            s.end_message = req.end_message
        if req.context is not None:
            s.context = req.context
        if req.for_journal is not None:
            s.for_journal = req.for_journal
        if self.redis is not None:
            await cache.evict(self.redis, _CACHE_NS, standard_id)
        return _to_standard_response(s, await self._company_name(s.company_id))

    async def deactivate(self, standard_id: int) -> None:
        (await self.get_or_throw(standard_id)).use_flag = False
        if self.redis is not None:
            await cache.evict(self.redis, _CACHE_NS, standard_id)

    async def sync_processes(
        self, standard_id: int, incoming: list[ProcessUpsertRequest]
    ) -> JobStandardResponse:
        """Java syncProcesses 등가 — id 일치는 update, 새 id 는 add, 빠진 건 drop."""
        result = await self.db.execute(
            select(JobStandard)
            .options(selectinload(JobStandard.processes))
            .where(JobStandard.id == standard_id)
        )
        s = result.scalar_one_or_none()
        if s is None:
            raise BusinessException(ErrorCode.JOB_STANDARD_NOT_FOUND)

        incoming_by_id = {p.id: p for p in incoming if p.id is not None}
        # 빠진 것 삭제
        keep: list[JobProcess] = []
        for existing in s.processes:
            if existing.id is None or existing.id in incoming_by_id:
                keep.append(existing)
        s.processes[:] = keep

        # update / add
        for p in incoming:
            if p.id is None:
                s.processes.append(_new_process(p))
            else:
                existing = next((x for x in s.processes if x.id == p.id), None)
                if existing is None:
                    raise BusinessException(ErrorCode.JOB_PROCESS_NOT_FOUND)
                existing.name = p.name
                existing.sequence = p.sequence
                existing.video_path = p.video_path
                existing.start_message = p.start_message
                existing.end_message = p.end_message
                existing.context = p.context
                existing.process_time = p.process_time_seconds
        await self.db.flush()
        return _to_standard_response(s, await self._company_name(s.company_id))

    async def _company_name(self, company_id: int) -> str | None:
        c = await self.db.get(Company, company_id)
        return c.name if c else None


def _new_process(p: ProcessUpsertRequest) -> JobProcess:
    return JobProcess(
        name=p.name,
        sequence=p.sequence,
        video_path=p.video_path,
        start_message=p.start_message,
        end_message=p.end_message,
        context=p.context,
        process_time=p.process_time_seconds,
    )


def _to_standard_response(
    s: JobStandard, company_name: str | None, *, processes: bool = True
) -> JobStandardResponse:
    return JobStandardResponse(
        id=s.id,
        company_id=s.company_id,
        company_name=company_name,
        name=s.name,
        use_flag=s.use_flag,
        routine_start_time=s.routine_start_time,
        standard_work_time_seconds=s.standard_work_time,
        standard_rest_time_seconds=s.standard_rest_time,
        start_message=s.start_message,
        end_message=s.end_message,
        context=s.context,
        for_journal=s.for_journal,
        processes=[
            JobProcessResponse(
                id=p.id,
                name=p.name,
                sequence=p.sequence,
                video_path=p.video_path,
                start_message=p.start_message,
                end_message=p.end_message,
                context=p.context,
                process_time_seconds=p.process_time,
            )
            for p in (s.processes if processes else [])
        ],
    )


# =============================================================================
#  JobWorker (assignment)
# =============================================================================
class JobWorkerService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def assign(self, company_worker_id: int, job_standard_id: int) -> JobWorkerResponse:
        if await self.db.get(CompanyWorker, company_worker_id) is None:
            raise BusinessException(ErrorCode.WORKER_NOT_FOUND)
        standard = await self.db.get(JobStandard, job_standard_id)
        if standard is None:
            raise BusinessException(ErrorCode.JOB_STANDARD_NOT_FOUND)

        existing = (
            await self.db.execute(
                select(JobWorker).where(
                    JobWorker.company_worker_id == company_worker_id,
                    JobWorker.job_standard_id == job_standard_id,
                )
            )
        ).scalar_one_or_none()
        if existing is not None:
            if existing.use_flag:
                raise BusinessException(ErrorCode.JOB_WORKER_ALREADY_ASSIGNED)
            existing.use_flag = True
            return _to_jw_response(existing, standard)

        jw = JobWorker(
            company_worker_id=company_worker_id,
            job_standard_id=job_standard_id,
            use_flag=True,
        )
        self.db.add(jw)
        await self.db.flush()
        return _to_jw_response(jw, standard)

    async def unassign(self, job_worker_id: int) -> None:
        jw = await self.db.get(JobWorker, job_worker_id)
        if jw is None:
            raise BusinessException(ErrorCode.JOB_WORKER_NOT_FOUND)
        jw.use_flag = False

    async def list_by_job_standard(self, job_standard_id: int) -> list[JobWorkerResponse]:
        rows = (
            await self.db.execute(
                select(JobWorker, JobStandard)
                .join(JobStandard, JobStandard.id == JobWorker.job_standard_id)
                .where(JobWorker.job_standard_id == job_standard_id, JobWorker.use_flag.is_(True))
            )
        ).all()
        return [_to_jw_response(jw, s) for jw, s in rows]

    async def list_active_by_company_worker(
        self, company_worker_id: int
    ) -> list[JobWorkerResponse]:
        rows = (
            await self.db.execute(
                select(JobWorker, JobStandard)
                .join(JobStandard, JobStandard.id == JobWorker.job_standard_id)
                .where(
                    JobWorker.company_worker_id == company_worker_id,
                    JobWorker.use_flag.is_(True),
                )
            )
        ).all()
        return [_to_jw_response(jw, s) for jw, s in rows]


def _to_jw_response(jw: JobWorker, standard: JobStandard) -> JobWorkerResponse:
    return JobWorkerResponse(
        id=jw.id,
        job_standard_id=standard.id,
        job_standard_name=standard.name,
        company_worker_id=jw.company_worker_id,
        use_flag=jw.use_flag,
    )
