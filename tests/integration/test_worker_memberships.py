"""근로자 소속 회사(채용) 목록 — list_memberships 서비스 검증.

``GET /workers/{id}/memberships`` 의 백엔드 로직. 근로자 상세 화면에서 한 근로자가
채용된 모든 회사-근로자 매핑을 조회한다.
"""

from __future__ import annotations

import pytest
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BusinessException
from app.models import Company
from app.schemas.worker import WorkerCreateRequest
from app.services.worker import WorkerService


@pytest.mark.asyncio
async def test_list_memberships_returns_hired_companies(db: AsyncSession) -> None:
    svc = WorkerService(db)
    worker = await svc.create(
        WorkerCreateRequest(
            username="memberships_worker",
            email="memberships_worker@rooti.io",
            password="password123",
            name="소속테스트",
        )
    )

    # 소속이 없으면 빈 리스트
    assert await svc.list_memberships(worker.id) == []

    # 두 회사에 채용
    company_a = Company(name="MembershipsCoA")
    company_b = Company(name="MembershipsCoB")
    db.add_all([company_a, company_b])
    await db.flush()
    await svc.hire(company_a.id, worker.id)
    await svc.hire(company_b.id, worker.id)

    memberships = await svc.list_memberships(worker.id)
    assert len(memberships) == 2
    assert {m.company_name for m in memberships} == {"MembershipsCoA", "MembershipsCoB"}
    assert all(m.worker.id == worker.id for m in memberships)
    assert all(m.hired for m in memberships)
    # CompanyWorker.id desc 정렬 — 나중에 채용된 회사 B 가 먼저 온다.
    assert memberships[0].company_id == company_b.id


@pytest.mark.asyncio
async def test_list_memberships_unknown_worker_raises(db: AsyncSession) -> None:
    svc = WorkerService(db)
    with pytest.raises(BusinessException):
        await svc.list_memberships(999_999_999)
