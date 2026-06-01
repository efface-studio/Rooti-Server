"""Kiosk binding / management endpoints."""

from __future__ import annotations

from fastapi import Query

from app.api.deps import CompanyScope, KioskSvc
from app.core.pagination import PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.core.tenant import assert_company, resolve_company_filter
from app.schemas.kiosk import KioskAssigneeRequest, KioskBindRequest, KioskResponse

router = RootiRouter(tags=["kiosk"])


@router.get("", summary="List all kiosks (paginated)")
async def list_kiosks(
    svc: KioskSvc,
    page: PagedQuery,
    scope: CompanyScope,
    keyword: str | None = None,
    company_id: int | None = Query(default=None, alias="companyId"),
    status: str | None = None,
) -> ApiResponse[PageResponse[KioskResponse]]:
    company_id = resolve_company_filter(scope, company_id)
    p = await svc.search(keyword, company_id, status, page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.get("/by-company/{company_id}")
async def list_by_company(
    company_id: int, svc: KioskSvc, scope: CompanyScope
) -> ApiResponse[list[KioskResponse]]:
    assert_company(scope, company_id)
    return ApiResponse.ok(await svc.list_by_company(company_id))


@router.post("")
async def bind(
    req: KioskBindRequest, svc: KioskSvc, scope: CompanyScope
) -> ApiResponse[KioskResponse]:
    assert_company(scope, req.company_id)
    return ApiResponse.ok(await svc.bind(req))


@router.patch("/{kiosk_id}/assignee", summary="Set kiosk assignee")
async def set_assignee(
    kiosk_id: int, req: KioskAssigneeRequest, svc: KioskSvc, scope: CompanyScope
) -> ApiResponse[KioskResponse]:
    return ApiResponse.ok(await svc.set_assignee(kiosk_id, req.assignee, company_scope=scope))


@router.delete("/{kiosk_id}")
async def unbind(kiosk_id: int, svc: KioskSvc, scope: CompanyScope) -> ApiResponse[None]:
    await svc.unbind(kiosk_id, company_scope=scope)
    return ApiResponse.ok()
