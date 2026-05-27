"""Kiosk binding endpoints."""

from __future__ import annotations

from app.api.deps import CurrentUser, KioskSvc, RequireAdminOrCharger
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.schemas.kiosk import KioskBindRequest, KioskResponse

router = RootiRouter(tags=["kiosk"])


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


@router.delete("/{kiosk_id}")
async def unbind(kiosk_id: int, svc: KioskSvc, _: RequireAdminOrCharger) -> ApiResponse[None]:
    await svc.unbind(kiosk_id)
    return ApiResponse.ok()
