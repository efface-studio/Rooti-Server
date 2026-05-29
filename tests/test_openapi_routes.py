"""모든 도메인 라우터가 OpenAPI 에 마운트됐는지 확인.

마이그레이션 후 회귀 방지용 — 라우터 import 가 누락되면 즉시 잡힘.
"""

from __future__ import annotations

import pytest
from httpx import AsyncClient

# Java AuthController / CompanyController / ... 의 모든 경로.
# 누락 시 Rooti-Client 가 깨짐 → 잠금.
_EXPECTED_PATHS: list[str] = [
    # auth
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    "/api/v1/auth/caregivers/signup",
    "/api/v1/auth/me",
    # public
    "/api/v1/public/version",
    "/api/v1/public/ping",
    # company
    "/api/v1/companies",
    "/api/v1/companies/{company_id}",
    # worker
    "/api/v1/workers",
    "/api/v1/workers/{worker_id}",
    "/api/v1/workers/hire",
    "/api/v1/workers/company-workers/{company_worker_id}",
    "/api/v1/workers/by-company/{company_id}",
    "/api/v1/workers/{worker_id}/retire",
    "/api/v1/workers/{worker_id}/rehire",
    # caregiver
    "/api/v1/caregivers/me",
    "/api/v1/caregivers/me/relations",
    "/api/v1/caregivers/me/relations/{relation_id}",
    # kiosk
    "/api/v1/kiosks",
    "/api/v1/kiosks/by-company/{company_id}",
    "/api/v1/kiosks/{kiosk_id}",
    "/api/v1/kiosks/{kiosk_id}/assignee",
    # job
    "/api/v1/job-standards",
    "/api/v1/job-standards/{standard_id}",
    "/api/v1/job-standards/{standard_id}/processes",
    "/api/v1/job-workers",
    "/api/v1/job-workers/{job_worker_id}",
    # schedule
    "/api/v1/schedules/{schedule_id}",
    "/api/v1/schedules/by-job-worker/{job_worker_id}",
    "/api/v1/schedules/by-job-standard/{job_standard_id}",
    "/api/v1/schedules",
    "/api/v1/schedules/batch",
    "/api/v1/schedules/{schedule_id}/close",
    # work record
    "/api/v1/work-records/begin",
    "/api/v1/work-records/end",
    "/api/v1/work-records/by-schedule/{schedule_id}",
    "/api/v1/work-records/processes/begin",
    "/api/v1/work-records/processes/end",
    "/api/v1/work-records/processes/by-schedule/{schedule_id}",
    # board
    "/api/v1/boards",
    "/api/v1/boards/{board_id}",
    # notification
    "/api/v1/notifications/push",
    # document
    "/api/v1/documents",
    "/api/v1/documents/by-relation/{relation_id}",
    "/api/v1/documents/{document_id}/download",
    "/api/v1/documents/{document_id}",
    # work journal
    "/api/v1/work-journals/{schedule_id}/pdf",
    "/api/v1/work-journals/{schedule_id}/file",
    "/api/v1/work-journals/bulk-email",
    "/api/v1/work-journals/email-schedules",
    "/api/v1/work-journals/email-schedules/{schedule_id}",
    # admin management (PR A)
    "/api/v1/users",
    "/api/v1/users/{user_id}/enabled",
    "/api/v1/company-chargers",
    "/api/v1/company-chargers/{charger_id}",
    "/api/v1/schedules",
    "/api/v1/auth/me/password",
    # leaves (PR C)
    "/api/v1/leaves",
    "/api/v1/leaves/approved",
    "/api/v1/leaves/by-worker/{worker_id}",
    "/api/v1/leaves/{leave_id}/decision",
    "/api/v1/leaves/{leave_id}",
    # actuator
    "/actuator/health",
    "/actuator/info",
]


@pytest.mark.asyncio
async def test_all_expected_routes_present(client: AsyncClient) -> None:
    resp = await client.get("/v3/api-docs")
    assert resp.status_code == 200
    paths = set(resp.json()["paths"].keys())

    missing = [p for p in _EXPECTED_PATHS if p not in paths]
    assert not missing, f"라우터에서 빠진 경로 {len(missing)}개:\n  " + "\n  ".join(missing)
