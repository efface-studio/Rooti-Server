"""Company charger (회사 담당자) management endpoints (admin)."""

from __future__ import annotations

from app.api.deps import ChargerSvc, RequireAdmin
from app.core.pagination import PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.schemas.charger import ChargerResponse, ChargerUpdateRequest

router = RootiRouter(tags=["company-charger"])


@router.get("", summary="List company chargers (admin)")
async def list_chargers(
    svc: ChargerSvc,
    page: PagedQuery,
    _: RequireAdmin,
    keyword: str | None = None,
    company_id: int | None = None,
) -> ApiResponse[PageResponse[ChargerResponse]]:
    p = await svc.search(keyword, company_id, page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.patch("/{charger_id}", summary="Update charger (admin)")
async def update_charger(
    charger_id: int, req: ChargerUpdateRequest, svc: ChargerSvc, _: RequireAdmin
) -> ApiResponse[ChargerResponse]:
    hired = req.hired if req.hired is not None else True
    return ApiResponse.ok(await svc.set_hired(charger_id, hired))
