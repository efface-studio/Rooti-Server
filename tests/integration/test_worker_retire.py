"""근로자 퇴직/재고용 + employmentStatus 필터 (V7)."""

from __future__ import annotations

import pytest
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.pagination import PageParams
from app.schemas.worker import WorkerCreateRequest
from app.services.worker import WorkerService


@pytest.mark.asyncio
async def test_retire_then_rehire_and_filter(db: AsyncSession) -> None:
    svc = WorkerService(db)
    created = await svc.create(
        WorkerCreateRequest(
            username="retire_test_worker",
            email="retire_test_worker@rooti.io",
            password="password123",
            name="퇴직테스트",
        )
    )
    assert created.employment_status == "ACTIVE"
    assert created.retired_at is None

    # 퇴직 → RETIRED + retired_at 기록
    retired = await svc.retire(created.id)
    assert retired.employment_status == "RETIRED"
    assert retired.retired_at is not None

    # employmentStatus 필터: RETIRED 에 포함, ACTIVE 에서 제외
    params = PageParams(page=0, size=100)
    only_retired = await svc.search(None, params, employment_status="RETIRED")
    assert any(w.id == created.id for w in only_retired.content)
    only_active = await svc.search(None, params, employment_status="ACTIVE")
    assert all(w.id != created.id for w in only_active.content)

    # 재고용 → 다시 ACTIVE, retired_at 해제
    rehired = await svc.rehire(created.id)
    assert rehired.employment_status == "ACTIVE"
    assert rehired.retired_at is None
