"""API v1 — 도메인 라우터 묶음.

새 도메인 라우터는 아래 include_router 라인을 추가하면 자동으로 /api/v1 아래에 마운트.
"""

from app.api.v1.auth import router as auth_router
from app.api.v1.boards import router as boards_router
from app.api.v1.caregivers import router as caregivers_router
from app.api.v1.companies import router as companies_router
from app.api.v1.company_chargers import router as company_chargers_router
from app.api.v1.documents import router as documents_router
from app.api.v1.job_standards import router as job_standards_router
from app.api.v1.job_workers import router as job_workers_router
from app.api.v1.kiosks import router as kiosks_router
from app.api.v1.leaves import router as leaves_router
from app.api.v1.notifications import router as notifications_router
from app.api.v1.public import router as public_router
from app.api.v1.schedules import router as schedules_router
from app.api.v1.users import router as users_router
from app.api.v1.work_journals import router as work_journals_router
from app.api.v1.work_records import router as work_records_router
from app.api.v1.workers import router as workers_router
from app.core.router import RootiRouter

api_v1 = RootiRouter(prefix="/api/v1")

api_v1.include_router(public_router, prefix="/public")
api_v1.include_router(auth_router, prefix="/auth")
api_v1.include_router(companies_router, prefix="/companies")
api_v1.include_router(workers_router, prefix="/workers")
api_v1.include_router(caregivers_router, prefix="/caregivers")
api_v1.include_router(kiosks_router, prefix="/kiosks")
api_v1.include_router(job_standards_router, prefix="/job-standards")
api_v1.include_router(job_workers_router, prefix="/job-workers")
api_v1.include_router(schedules_router, prefix="/schedules")
api_v1.include_router(work_records_router, prefix="/work-records")
api_v1.include_router(boards_router, prefix="/boards")
api_v1.include_router(notifications_router, prefix="/notifications")
api_v1.include_router(documents_router, prefix="/documents")
api_v1.include_router(work_journals_router, prefix="/work-journals")
api_v1.include_router(users_router, prefix="/users")
api_v1.include_router(company_chargers_router, prefix="/company-chargers")
api_v1.include_router(leaves_router, prefix="/leaves")
