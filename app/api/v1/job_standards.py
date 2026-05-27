"""Job standard endpoints."""

from __future__ import annotations

from app.api.deps import CurrentUser, JobStandardSvc, RequireAdminOrCharger
from app.core.pagination import PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.schemas.job import (
    JobStandardCreateRequest,
    JobStandardResponse,
    JobStandardUpdateRequest,
    ProcessUpsertRequest,
)

router = RootiRouter(tags=["job-standard"])


@router.get("")
async def list_standards(
    svc: JobStandardSvc,
    page: PagedQuery,
    _: CurrentUser,
    company_id: int | None = None,
    keyword: str | None = None,
) -> ApiResponse[PageResponse[JobStandardResponse]]:
    p = await svc.search(company_id, keyword, page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.get("/{standard_id}")
async def get_standard(
    standard_id: int, svc: JobStandardSvc, _: CurrentUser
) -> ApiResponse[JobStandardResponse]:
    return ApiResponse.ok(await svc.get(standard_id))


@router.post("")
async def create_standard(
    req: JobStandardCreateRequest, svc: JobStandardSvc, _: RequireAdminOrCharger
) -> ApiResponse[JobStandardResponse]:
    return ApiResponse.ok(await svc.create(req))


@router.patch("/{standard_id}")
async def update_standard(
    standard_id: int,
    req: JobStandardUpdateRequest,
    svc: JobStandardSvc,
    _: RequireAdminOrCharger,
) -> ApiResponse[JobStandardResponse]:
    return ApiResponse.ok(await svc.update(standard_id, req))


@router.put("/{standard_id}/processes")
async def sync_processes(
    standard_id: int,
    processes: list[ProcessUpsertRequest],
    svc: JobStandardSvc,
    _: RequireAdminOrCharger,
) -> ApiResponse[JobStandardResponse]:
    return ApiResponse.ok(await svc.sync_processes(standard_id, processes))


@router.delete("/{standard_id}")
async def deactivate_standard(
    standard_id: int, svc: JobStandardSvc, _: RequireAdminOrCharger
) -> ApiResponse[None]:
    await svc.deactivate(standard_id)
    return ApiResponse.ok()
