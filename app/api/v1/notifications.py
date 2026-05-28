"""Push notification endpoint (admin/charger)."""

from __future__ import annotations

from app.api.deps import PushSvc, RequireAdminOrCharger
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.schemas.notification import PushRequest

router = RootiRouter(tags=["notification"])


@router.post("/push")
async def push(req: PushRequest, svc: PushSvc, _: RequireAdminOrCharger) -> ApiResponse[None]:
    await svc.send(req)
    return ApiResponse.ok()
