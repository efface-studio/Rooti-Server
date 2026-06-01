"""WorkRecord service — state machine for ON/WORK/REST/OFF + per-process records.

Concurrency note (Java 동일): 같은 schedule/type 의 open 기록 (end_at IS NULL) 이
있으면 두 번째 begin 거부. OFF 닫을 때 schedule 도 같이 닫는다.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BusinessException, ErrorCode
from app.models import (
    JobProcess,
    WorkProcessRecord,
    WorkRecord,
    WorkRecordType,
    WorkSchedule,
)
from app.schemas.workrecord import (
    ProcessEndRequest,
    ProcessRecordResponse,
    ProcessStartRequest,
    RecordEndRequest,
    RecordResponse,
    RecordStartRequest,
)


class WorkRecordService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    # ---------- WorkRecord ----------
    async def begin(self, req: RecordStartRequest) -> RecordResponse:
        await self._schedule_or_throw(req.work_schedule_id)
        open_existing = await self._find_open_record(req.work_schedule_id, req.type)
        if open_existing is not None:
            raise BusinessException(
                ErrorCode.WORK_RECORD_OUT_OF_RANGE,
                f"이미 동일 타입의 진행중인 기록이 있습니다 (id={open_existing.id})",
            )
        r = WorkRecord(
            work_schedule_id=req.work_schedule_id,
            type=req.type,
            start_at=req.at or datetime.now(),
        )
        self.db.add(r)
        await self.db.flush()
        return _to_record(r)

    async def end(self, req: RecordEndRequest) -> RecordResponse:
        r = await self._find_open_record(req.work_schedule_id, req.type)
        if r is None:
            raise BusinessException(ErrorCode.WORK_RECORD_NOT_FOUND)
        r.end_at = req.at or datetime.now()
        # OFF closes the schedule itself
        if req.type == WorkRecordType.OFF:
            schedule = await self.db.get(WorkSchedule, r.work_schedule_id)
            if schedule is not None:
                schedule.end_at = r.end_at
        return _to_record(r)

    async def list(self, schedule_id: int) -> list[RecordResponse]:
        rows = (
            (
                await self.db.execute(
                    select(WorkRecord)
                    .where(WorkRecord.work_schedule_id == schedule_id)
                    .order_by(WorkRecord.start_at)
                )
            )
            .scalars()
            .all()
        )
        return [_to_record(r) for r in rows]

    # ---------- WorkProcessRecord ----------
    async def begin_process(self, req: ProcessStartRequest) -> ProcessRecordResponse:
        await self._schedule_or_throw(req.work_schedule_id)
        job_process = await self.db.get(JobProcess, req.job_process_id)
        if job_process is None:
            raise BusinessException(ErrorCode.JOB_PROCESS_NOT_FOUND)
        r = WorkProcessRecord(
            work_schedule_id=req.work_schedule_id,
            job_process_id=req.job_process_id,
            type=req.type or "PROCESS",
            start_at=req.at or datetime.now(),
            start_condition=req.condition,
            start_answer=req.answer,
            start_voice_path=req.voice_path,
            process=req.process,
        )
        self.db.add(r)
        await self.db.flush()
        return _to_process(r, job_process.name)

    async def end_process(self, req: ProcessEndRequest) -> ProcessRecordResponse:
        result = await self.db.execute(
            select(WorkProcessRecord).where(
                WorkProcessRecord.work_schedule_id == req.work_schedule_id,
                WorkProcessRecord.job_process_id == req.job_process_id,
                WorkProcessRecord.end_at.is_(None),
            )
        )
        r = result.scalar_one_or_none()
        if r is None:
            raise BusinessException(ErrorCode.WORK_RECORD_NOT_FOUND)
        r.end_at = req.at or datetime.now()
        r.end_condition = req.condition
        r.end_answer = req.answer
        r.end_voice_path = req.voice_path
        if req.process is not None:
            r.process = req.process
        job_process = await self.db.get(JobProcess, r.job_process_id)
        return _to_process(r, job_process.name if job_process else "")

    async def list_process(self, schedule_id: int) -> list[ProcessRecordResponse]:
        # job_processes.name 을 함께 가져와 jobProcessName 으로 내려준다(일지 렌더 라벨).
        rows = (
            await self.db.execute(
                select(WorkProcessRecord, JobProcess.name)
                .join(JobProcess, JobProcess.id == WorkProcessRecord.job_process_id)
                .where(WorkProcessRecord.work_schedule_id == schedule_id)
                .order_by(WorkProcessRecord.start_at)
            )
        ).all()
        return [_to_process(r, name) for r, name in rows]

    # ---------- helpers ----------
    async def _schedule_or_throw(self, schedule_id: int) -> WorkSchedule:
        s = await self.db.get(WorkSchedule, schedule_id)
        if s is None:
            raise BusinessException(ErrorCode.WORK_SCHEDULE_NOT_FOUND)
        return s

    async def _find_open_record(self, schedule_id: int, type_: WorkRecordType) -> WorkRecord | None:
        result = await self.db.execute(
            select(WorkRecord)
            .where(
                WorkRecord.work_schedule_id == schedule_id,
                WorkRecord.type == type_,
                WorkRecord.end_at.is_(None),
            )
            .order_by(WorkRecord.start_at.desc())
        )
        return result.scalar_one_or_none()


def _to_record(r: WorkRecord) -> RecordResponse:
    return RecordResponse(
        id=r.id,
        work_schedule_id=r.work_schedule_id,
        type=r.type,
        start_at=r.start_at,
        end_at=r.end_at,
    )


def _to_process(r: WorkProcessRecord, job_process_name: str = "") -> ProcessRecordResponse:
    return ProcessRecordResponse(
        id=r.id,
        work_schedule_id=r.work_schedule_id,
        job_process_id=r.job_process_id,
        job_process_name=job_process_name,
        type=r.type,
        start_at=r.start_at,
        end_at=r.end_at,
        start_condition=r.start_condition,
        end_condition=r.end_condition,
        start_answer=r.start_answer,
        end_answer=r.end_answer,
        start_voice_path=r.start_voice_path,
        end_voice_path=r.end_voice_path,
        process=r.process,
    )
