"""일괄 메일 발송 잡(이력/상태) — 라이프사이클 + 이력 조회 (V8).

상태 전이 QUEUED→SENDING→SUCCESS|FAILED 와 list/get(스코프 가드)을 서비스 레벨에서 검증.
실제 render/Resend 는 타지 않는다(일정 0건 = 즉시 SUCCESS, 실패는 send 를 몽키패치).
"""

from __future__ import annotations

from datetime import date

import pytest
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import AuthForbiddenException, BusinessException
from app.models import Company
from app.schemas.document import JournalFormat
from app.services.document import WorkJournalBulkEmailService

_DAY = date(2026, 1, 15)


async def _make_company(db: AsyncSession, name: str) -> Company:
    company = Company(name=name)
    db.add(company)
    await db.flush()
    return company


@pytest.mark.asyncio
async def test_job_lifecycle_success_with_no_schedules(db: AsyncSession) -> None:
    """일정이 0건이면 render/메일 없이 즉시 SUCCESS(첨부 0건)."""
    svc = WorkJournalBulkEmailService(db)
    company = await _make_company(db, "BulkJobCoSuccess")

    queued = await svc.create_job(company.id, _DAY, "boss@rooti.io", JournalFormat.PDF, None)
    assert queued.status == "QUEUED"
    assert queued.company_name == "BulkJobCoSuccess"
    assert queued.schedule_count == 0

    await svc.run_job(queued.id)

    done = await svc.get_job(queued.id)
    assert done.status == "SUCCESS"
    assert done.schedule_count == 0
    assert done.started_at is not None
    assert done.finished_at is not None
    assert done.error_message is None


@pytest.mark.asyncio
async def test_job_lifecycle_failure_records_error(db: AsyncSession) -> None:
    """send 가 터지면 FAILED + error_message 기록 (잡은 끝까지 닫힘)."""
    svc = WorkJournalBulkEmailService(db)
    company = await _make_company(db, "BulkJobCoFail")
    queued = await svc.create_job(company.id, _DAY, "boss@rooti.io", JournalFormat.PDF, None)

    async def _boom(*_args: object, **_kwargs: object) -> dict[str, object]:
        raise RuntimeError("resend down")

    svc.send = _boom  # type: ignore[method-assign]
    await svc.run_job(queued.id)

    failed = await svc.get_job(queued.id)
    assert failed.status == "FAILED"
    assert failed.error_message is not None
    assert "resend down" in failed.error_message
    assert failed.finished_at is not None


@pytest.mark.asyncio
async def test_list_jobs_history_and_scope_guard(db: AsyncSession) -> None:
    """이력은 회사 필터로 격리되고, get_job 은 다른 회사 스코프(CHARGER)면 403."""
    svc = WorkJournalBulkEmailService(db)
    company_a = await _make_company(db, "BulkJobCoA")
    company_b = await _make_company(db, "BulkJobCoB")
    job_a = await svc.create_job(company_a.id, _DAY, "a@rooti.io", JournalFormat.PDF, None)
    await svc.create_job(company_b.id, _DAY, "b@rooti.io", JournalFormat.XLSX, None)

    # 회사 A 로 필터 → A 잡만
    only_a = await svc.list_jobs(company_a.id)
    assert {j.company_id for j in only_a} == {company_a.id}
    assert any(j.id == job_a.id for j in only_a)

    # ADMIN(None) → 두 회사 모두 포함
    all_jobs = await svc.list_jobs(None)
    company_ids = {j.company_id for j in all_jobs}
    assert {company_a.id, company_b.id} <= company_ids

    # CHARGER 가 다른 회사 잡을 조회 → 403
    with pytest.raises(AuthForbiddenException):
        await svc.get_job(job_a.id, company_scope=company_b.id)
    # 본인 회사 스코프면 통과
    assert (await svc.get_job(job_a.id, company_scope=company_a.id)).id == job_a.id


@pytest.mark.asyncio
async def test_get_unknown_job_raises(db: AsyncSession) -> None:
    svc = WorkJournalBulkEmailService(db)
    with pytest.raises(BusinessException):
        await svc.get_job(999_999_999)
