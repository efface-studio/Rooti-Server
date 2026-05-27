"""WorkSchedule service — Reader + Writer 통합."""

from __future__ import annotations

from datetime import date, datetime, time

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BusinessException, ErrorCode
from app.core.pagination import Page, PageParams
from app.models import JobStandard, JobWorker, WorkSchedule
from app.schemas.schedule import (
    BatchMakeRequest,
    CreateScheduleRequest,
    ScheduleResponse,
)


class WorkScheduleService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    # ---------- Reader ----------
    async def get_or_throw(self, schedule_id: int) -> WorkSchedule:
        s = await self.db.get(WorkSchedule, schedule_id)
        if s is None:
            raise BusinessException(ErrorCode.WORK_SCHEDULE_NOT_FOUND)
        return s

    async def get(self, schedule_id: int) -> ScheduleResponse:
        s = await self.get_or_throw(schedule_id)
        return await self._to_response(s)

    async def list_for_job_worker(
        self, job_worker_id: int, *, from_: date, to: date
    ) -> list[ScheduleResponse]:
        rows = (
            (
                await self.db.execute(
                    select(WorkSchedule)
                    .where(
                        WorkSchedule.job_worker_id == job_worker_id,
                        WorkSchedule.start_at >= datetime.combine(from_, time.min),
                        WorkSchedule.start_at < datetime.combine(to, time.max),
                    )
                    .order_by(WorkSchedule.start_at)
                )
            )
            .scalars()
            .all()
        )
        return [await self._to_response(r) for r in rows]

    async def list_for_standard(
        self, job_standard_id: int, *, from_: date, to: date, params: PageParams
    ) -> Page[ScheduleResponse]:
        base = (
            select(WorkSchedule)
            .where(
                WorkSchedule.job_standard_id == job_standard_id,
                WorkSchedule.start_at >= datetime.combine(from_, time.min),
                WorkSchedule.start_at < datetime.combine(to, time.max),
            )
        )
        total = int(
            (await self.db.execute(select(func.count()).select_from(base.subquery()))).scalar_one()
            or 0
        )
        rows = (
            (
                await self.db.execute(
                    base.order_by(WorkSchedule.start_at)
                    .offset(params.offset)
                    .limit(params.limit)
                )
            )
            .scalars()
            .all()
        )
        return Page.build(
            [await self._to_response(r) for r in rows],
            params=params,
            total_elements=total,
        )

    # ---------- Writer ----------
    async def create(self, req: CreateScheduleRequest) -> ScheduleResponse:
        jw = await self.db.get(JobWorker, req.job_worker_id)
        if jw is None:
            raise BusinessException(ErrorCode.JOB_WORKER_NOT_FOUND)
        s = WorkSchedule(
            job_worker_id=req.job_worker_id,
            company_charger_id=req.company_charger_id,
            job_standard_id=jw.job_standard_id,
            start_at=req.start_at,
            end_at=req.end_at,
            make_work_doc=bool(req.make_work_doc),
        )
        self.db.add(s)
        await self.db.flush()
        return await self._to_response(s)

    async def batch_make(self, req: BatchMakeRequest) -> list[ScheduleResponse]:
        if await self.db.get(JobStandard, req.job_standard_id) is None:
            raise BusinessException(ErrorCode.JOB_STANDARD_NOT_FOUND)
        start_at = datetime.combine(req.date, time.min)
        schedules: list[WorkSchedule] = []
        for jw_id in req.job_worker_ids:
            jw = await self.db.get(JobWorker, jw_id)
            if jw is None or jw.job_standard_id != req.job_standard_id:
                continue
            s = WorkSchedule(
                job_worker_id=jw_id,
                company_charger_id=req.company_charger_id,
                job_standard_id=req.job_standard_id,
                start_at=start_at,
                make_work_doc=False,
            )
            self.db.add(s)
            schedules.append(s)
        await self.db.flush()
        return [await self._to_response(s) for s in schedules]

    async def close(self, schedule_id: int, end_at: datetime | None) -> ScheduleResponse:
        s = await self.get_or_throw(schedule_id)
        s.end_at = end_at or datetime.now()
        return await self._to_response(s)

    # ---------- Helpers ----------
    async def _to_response(self, s: WorkSchedule) -> ScheduleResponse:
        standard = await self.db.get(JobStandard, s.job_standard_id)
        return ScheduleResponse(
            id=s.id,
            job_worker_id=s.job_worker_id,
            job_standard_id=s.job_standard_id,
            job_standard_name=standard.name if standard else None,
            company_charger_id=s.company_charger_id,
            start_at=s.start_at,
            end_at=s.end_at,
            make_work_doc=s.make_work_doc,
            work_doc_path=s.work_doc_path,
        )
