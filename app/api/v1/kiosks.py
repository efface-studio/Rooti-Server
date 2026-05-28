"""Kiosk binding / management endpoints."""

from __future__ import annotations

from app.api.deps import CurrentUser, KioskSvc, RequireAdminOrCharger
from app.core.pagination import PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.schemas.kiosk import KioskAssigneeRequest, KioskBindRequest, KioskResponse

router = RootiRouter(tags=["kiosk"])


@router.get("", summary="List all kiosks (paginated)")
async def list_kiosks(
    svc: KioskSvc,
    page: PagedQuery,
    _: CurrentUser,
    keyword: str | None = None,
    company_id: int | None = None,
    status: str | None = None,
) -> ApiResponse[PageResponse[KioskResponse]]:
    p = await svc.search(keyword, company_id, status, page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.get("/by-company/{company_id}")
async def list_by_company(
    company_id: int, svc: KioskSvc, _: CurrentUser
) -> ApiResponse[list[KioskResponse]]:
    return ApiResponse.ok(await svc.list_by_company(company_id))


@router.post("")
async def bind(
    req: KioskBindRequest, svc: KioskSvc, _: RequireAdminOrCharger
) -> ApiResponse[KioskResponse]:
    return ApiResponse.ok(await svc.bind(req))


@router.patch("/{kiosk_id}/assignee", summary="Set kiosk assignee")
async def set_assignee(
    kiosk_id: int, req: KioskAssigneeRequest, svc: KioskSvc, _: RequireAdminOrCharger
) -> ApiResponse[KioskResponse]:
    return ApiResponse.ok(await svc.set_assignee(kiosk_id, req.assignee))


@router.delete("/{kiosk_id}")
async def unbind(kiosk_id: int, svc: KioskSvc, _: RequireAdminOrCharger) -> ApiResponse[None]:
    await svc.unbind(kiosk_id)
    return ApiResponse.ok()
