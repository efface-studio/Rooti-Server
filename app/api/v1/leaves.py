"""휴가(Leave) endpoints."""

from __future__ import annotations

from datetime import date

from fastapi import Query

from app.api.deps import CompanyScope, LeaveSvc, RequireAdminOrCharger
from app.core.pagination import PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.core.tenant import assert_company, resolve_company_filter
from app.models import LeaveStatus, LeaveType
from app.schemas.leave import LeaveCreateRequest, LeaveDecisionRequest, LeaveResponse

router = RootiRouter(tags=["leave"])


@router.get("", summary="List leaves (filters + pagination)")
async def list_leaves(
    svc: LeaveSvc,
    page: PagedQuery,
    scope: CompanyScope,
    keyword: str | None = None,
    company_id: int | None = Query(default=None, alias="companyId"),
    worker_id: int | None = Query(default=None, alias="workerId"),
    type: LeaveType | None = None,
    status: LeaveStatus | None = None,
    from_: date | None = Query(default=None, alias="from"),
    to: date | None = None,
) -> ApiResponse[PageResponse[LeaveResponse]]:
    company_id = resolve_company_filter(scope, company_id)
    p = await svc.search(
        keyword=keyword,
        company_id=company_id,
        worker_id=worker_id,
        type_=type,
        status=status,
        from_=from_,
        to=to,
        params=page,
    )
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.get("/approved", summary="Approved leaves (calendar overlay)")
async def list_approved(
    svc: LeaveSvc,
    scope: CompanyScope,
    company_id: int | None = Query(default=None, alias="companyId"),
    from_: date | None = Query(default=None, alias="from"),
    to: date | None = None,
) -> ApiResponse[list[LeaveResponse]]:
    company_id = resolve_company_filter(scope, company_id)
    return ApiResponse.ok(await svc.list_approved(company_id=company_id, from_=from_, to=to))


@router.get("/by-worker/{worker_id}", summary="Leaves for a worker")
async def list_by_worker(
    worker_id: int,
    svc: LeaveSvc,
    scope: CompanyScope,
    from_: date | None = Query(default=None, alias="from"),
    to: date | None = None,
) -> ApiResponse[list[LeaveResponse]]:
    return ApiResponse.ok(
        await svc.list_by_worker(worker_id, from_=from_, to=to, company_scope=scope)
    )


@router.post("", summary="Create a leave (admin/charger)")
async def create_leave(
    req: LeaveCreateRequest, svc: LeaveSvc, me: RequireAdminOrCharger, scope: CompanyScope
) -> ApiResponse[LeaveResponse]:
    assert_company(scope, req.company_id)
    return ApiResponse.ok(await svc.create(req, me.user_id))


@router.patch("/{leave_id}/decision", summary="Approve/reject a leave (admin/charger)")
async def decide_leave(
    leave_id: int, req: LeaveDecisionRequest, svc: LeaveSvc, scope: CompanyScope
) -> ApiResponse[LeaveResponse]:
    return ApiResponse.ok(await svc.decide(leave_id, req.status, req.comment, company_scope=scope))


@router.delete("/{leave_id}", summary="Delete a leave (admin/charger)")
async def delete_leave(leave_id: int, svc: LeaveSvc, scope: CompanyScope) -> ApiResponse[None]:
    await svc.delete(leave_id, company_scope=scope)
    return ApiResponse.ok()
