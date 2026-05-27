"""Job worker (assignment) endpoints."""

from __future__ import annotations

from app.api.deps import CurrentUser, JobWorkerSvc, RequireAdminOrCharger
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.schemas.job import AssignJobWorkerRequest, JobWorkerResponse

router = RootiRouter(tags=["job-worker"])


@router.get("")
async def list_assignments(
    svc: JobWorkerSvc,
    _: CurrentUser,
    job_standard_id: int | None = None,
    company_worker_id: int | None = None,
) -> ApiResponse[list[JobWorkerResponse]]:
    if job_standard_id is not None:
        return ApiResponse.ok(await svc.list_by_job_standard(job_standard_id))
    if company_worker_id is not None:
        return ApiResponse.ok(await svc.list_active_by_company_worker(company_worker_id))
    return ApiResponse.ok([])


@router.post("")
async def assign(
    req: AssignJobWorkerRequest, svc: JobWorkerSvc, _: RequireAdminOrCharger
) -> ApiResponse[JobWorkerResponse]:
    return ApiResponse.ok(await svc.assign(req.company_worker_id, req.job_standard_id))


@router.delete("/{job_worker_id}")
async def unassign(
    job_worker_id: int, svc: JobWorkerSvc, _: RequireAdminOrCharger
) -> ApiResponse[None]:
    await svc.unassign(job_worker_id)
    return ApiResponse.ok()
