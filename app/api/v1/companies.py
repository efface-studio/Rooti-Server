"""Company endpoints (admin-managed)."""

from __future__ import annotations

from app.api.deps import CompanySvc, CurrentUser, RequireAdmin
from app.core.pagination import Page, PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.schemas.company import (
    CompanyCreateRequest,
    CompanyResponse,
    CompanyUpdateRequest,
)

router = RootiRouter(tags=["company"])


def _to_page_response(page: Page[CompanyResponse]) -> PageResponse[CompanyResponse]:
    return PageResponse.of(
        page.content,
        page=page.page,
        size=page.size,
        total_elements=page.total_elements,
    )


@router.get("", summary="List active companies")
async def list_companies(
    svc: CompanySvc,
    page: PagedQuery,
    _: CurrentUser,
    keyword: str | None = None,
) -> ApiResponse[PageResponse[CompanyResponse]]:
    result = await svc.search(keyword, page)
    return ApiResponse.ok(_to_page_response(result))


@router.get("/{company_id}")
async def get_company(
    company_id: int, svc: CompanySvc, _: CurrentUser
) -> ApiResponse[CompanyResponse]:
    return ApiResponse.ok(await svc.get(company_id))


@router.post("", summary="Create a company (admin)")
async def create_company(
    req: CompanyCreateRequest, svc: CompanySvc, me: RequireAdmin
) -> ApiResponse[CompanyResponse]:
    return ApiResponse.ok(await svc.create(req, actor=me))


@router.patch("/{company_id}", summary="Update a company (admin)")
async def update_company(
    company_id: int,
    req: CompanyUpdateRequest,
    svc: CompanySvc,
    me: RequireAdmin,
) -> ApiResponse[CompanyResponse]:
    return ApiResponse.ok(await svc.update(company_id, req, actor=me))


@router.delete("/{company_id}", summary="Deactivate a company (admin)")
async def deactivate_company(
    company_id: int, svc: CompanySvc, me: RequireAdmin
) -> ApiResponse[None]:
    await svc.deactivate(company_id, actor=me)
    return ApiResponse.ok()
