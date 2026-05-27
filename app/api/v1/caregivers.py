"""Caregiver endpoints (CAREGIVER role only)."""

from __future__ import annotations

from app.api.deps import CaregiverSvc, RequireCaregiver
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.schemas.caregiver import (
    CaregiverRelationResponse,
    CaregiverResponse,
    RegisterWorkerRequest,
)

router = RootiRouter(tags=["caregiver"])


@router.get("/me")
async def me(me_principal: RequireCaregiver, svc: CaregiverSvc) -> ApiResponse[CaregiverResponse]:
    return ApiResponse.ok(await svc.me(me_principal.user_id))


@router.get("/me/relations")
async def list_my_relations(
    me_principal: RequireCaregiver, svc: CaregiverSvc
) -> ApiResponse[list[CaregiverRelationResponse]]:
    return ApiResponse.ok(await svc.list_relations(me_principal.user_id))


@router.post("/me/relations")
async def register_worker(
    req: RegisterWorkerRequest, me_principal: RequireCaregiver, svc: CaregiverSvc
) -> ApiResponse[CaregiverRelationResponse]:
    return ApiResponse.ok(await svc.register_worker(me_principal.user_id, req.worker_id))


@router.delete("/me/relations/{relation_id}")
async def remove_relation(
    relation_id: int, me_principal: RequireCaregiver, svc: CaregiverSvc
) -> ApiResponse[None]:
    await svc.remove_relation(me_principal.user_id, relation_id)
    return ApiResponse.ok()
