"""근무일지 메일 자동 스케줄 service."""

from __future__ import annotations

from datetime import datetime, timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BusinessException, ErrorCode
from app.core.tenant import CompanyScopeValue, assert_company
from app.models import Company, JournalEmailSchedule
from app.schemas.journal_schedule import (
    JournalScheduleCreateRequest,
    JournalScheduleResponse,
    JournalScheduleUpdateRequest,
)


def _compute_next_run(time_str: str) -> datetime:
    """단순화: 오늘 그 시각, 이미 지났으면 내일. (실 스케줄러가 frequency 까지 반영해 정밀화)"""
    try:
        h, m = (int(x) for x in time_str.split(":", 1))
    except ValueError:
        h, m = 9, 0
    now = datetime.now()
    nxt = now.replace(hour=h, minute=m, second=0, microsecond=0)
    if nxt < now:
        nxt += timedelta(days=1)
    return nxt


class JournalScheduleService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    def _to_response(
        self, s: JournalEmailSchedule, company_name: str | None
    ) -> JournalScheduleResponse:
        return JournalScheduleResponse(
            id=s.id,
            company_id=s.company_id,
            company_name=company_name,
            recipient_email=s.recipient_email,
            frequency=s.frequency,  # type: ignore[arg-type]
            weekday=s.weekday,  # type: ignore[arg-type]
            day_of_month=s.day_of_month,
            time=s.send_time,
            format=s.format,  # type: ignore[arg-type]
            enabled=s.enabled,
            next_run_at=s.next_run_at,
            last_run_at=s.last_run_at,
            created_at=s.created_at,
        )

    async def list(self, company_id: int | None) -> list[JournalScheduleResponse]:
        # company_name 을 행마다 db.get 으로 가져오면 N+1 → Company LEFT JOIN 으로 1 쿼리.
        q = (
            select(JournalEmailSchedule, Company.name.label("company_name"))
            .outerjoin(Company, Company.id == JournalEmailSchedule.company_id)
            .order_by(JournalEmailSchedule.id.desc())
        )
        if company_id is not None:
            q = q.where(JournalEmailSchedule.company_id == company_id)
        rows = (await self.db.execute(q)).all()
        return [self._to_response(s, cn) for s, cn in rows]

    async def create(self, req: JournalScheduleCreateRequest) -> JournalScheduleResponse:
        company = await self.db.get(Company, req.company_id)
        if company is None:
            raise BusinessException(ErrorCode.COMPANY_NOT_FOUND)
        s = JournalEmailSchedule(
            company_id=req.company_id,
            recipient_email=req.recipient_email,
            frequency=req.frequency.value,
            weekday=req.weekday.value if req.weekday else None,
            day_of_month=req.day_of_month,
            send_time=req.time,
            format=req.format.value,
            enabled=req.enabled,
            next_run_at=_compute_next_run(req.time),
        )
        self.db.add(s)
        await self.db.flush()
        return self._to_response(s, company.name)

    async def update(
        self,
        schedule_id: int,
        req: JournalScheduleUpdateRequest,
        *,
        company_scope: CompanyScopeValue = None,
    ) -> JournalScheduleResponse:
        s = await self.db.get(JournalEmailSchedule, schedule_id)
        if s is None:
            raise BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "스케줄이 없습니다.")
        # CHARGER 는 본인 회사 스케줄만 수정 — 현재 회사 + (변경 시) 대상 회사 모두 검증.
        assert_company(company_scope, s.company_id)
        if req.enabled is not None:
            s.enabled = req.enabled
        if req.company_id is not None and req.company_id != s.company_id:
            assert_company(company_scope, req.company_id)
            if await self.db.get(Company, req.company_id) is None:
                raise BusinessException(ErrorCode.COMPANY_NOT_FOUND)
            s.company_id = req.company_id
        if req.recipient_email:
            s.recipient_email = req.recipient_email
        if req.frequency:
            s.frequency = req.frequency.value
            if req.frequency.value == "DAILY":
                s.weekday = None
                s.day_of_month = None
            elif req.frequency.value == "WEEKLY":
                s.weekday = (req.weekday.value if req.weekday else s.weekday) or "MON"
                s.day_of_month = None
            elif req.frequency.value == "MONTHLY":
                s.day_of_month = req.day_of_month or s.day_of_month or 1
                s.weekday = None
        if req.time:
            s.send_time = req.time
            s.next_run_at = _compute_next_run(req.time)
        if req.format:
            s.format = req.format.value
        # 단건 갱신 — company_id 가 바뀌었어도 위 검증에서 이미 로드돼 identity map 히트(추가 쿼리 없음).
        company = await self.db.get(Company, s.company_id)
        return self._to_response(s, company.name if company else None)

    async def delete(self, schedule_id: int, *, company_scope: CompanyScopeValue = None) -> None:
        s = await self.db.get(JournalEmailSchedule, schedule_id)
        if s is not None:
            assert_company(company_scope, s.company_id)
            await self.db.delete(s)
