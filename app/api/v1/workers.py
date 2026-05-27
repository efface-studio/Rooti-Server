"""Worker endpoints — create, list, hire/fire, list by company."""

from __future__ import annotations

from app.api.deps import CurrentUser, RequireAdminOrCharger, WorkerSvc
from app.core.pagination import PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.schemas.worker import (
    CompanyWorkerResponse,
    WorkerCreateRequest,
    WorkerHireRequest,
    WorkerResponse,
)

router = RootiRouter(tags=["worker"])


@router.get("")
async def list_workers(
    svc: WorkerSvc,
    page: PagedQuery,
    _: CurrentUser,
    keyword: str | None = None,
) -> ApiResponse[PageResponse[WorkerResponse]]:
    p = await svc.search(keyword, page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.get("/{worker_id}")
async def get_worker(worker_id: int, svc: WorkerSvc, _: CurrentUser) -> ApiResponse[WorkerResponse]:
    return ApiResponse.ok(await svc.get(worker_id))


@router.post("", summary="Create a worker (admin/charger)")
async def create_worker(
    req: WorkerCreateRequest, svc: WorkerSvc, _: RequireAdminOrCharger
) -> ApiResponse[WorkerResponse]:
    return ApiResponse.ok(await svc.create(req))


@router.post("/hire", summary="Hire a worker into a company")
async def hire_worker(
    req: WorkerHireRequest, svc: WorkerSvc, _: RequireAdminOrCharger
) -> ApiResponse[CompanyWorkerResponse]:
    return ApiResponse.ok(await svc.hire(req.company_id, req.worker_id))


@router.delete("/company-workers/{company_worker_id}", summary="Fire (deactivate company-worker)")
async def fire_company_worker(
    company_worker_id: int, svc: WorkerSvc, _: RequireAdminOrCharger
) -> ApiResponse[None]:
    await svc.fire(company_worker_id)
    return ApiResponse.ok()


@router.get("/by-company/{company_id}")
async def list_by_company(
    company_id: int, svc: WorkerSvc, page: PagedQuery, _: CurrentUser
) -> ApiResponse[PageResponse[CompanyWorkerResponse]]:
    p = await svc.list_by_company(company_id, page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )
