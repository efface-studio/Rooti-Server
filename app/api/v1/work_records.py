"""WorkRecord + ProcessRecord endpoints."""

from __future__ import annotations

from app.api.deps import CurrentUser, WorkRecordSvc
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.schemas.workrecord import (
    ProcessEndRequest,
    ProcessRecordResponse,
    ProcessStartRequest,
    RecordEndRequest,
    RecordResponse,
    RecordStartRequest,
)

router = RootiRouter(tags=["work-record"])


@router.post("/begin")
async def begin(
    req: RecordStartRequest, svc: WorkRecordSvc, _: CurrentUser
) -> ApiResponse[RecordResponse]:
    return ApiResponse.ok(await svc.begin(req))


@router.post("/end")
async def end(
    req: RecordEndRequest, svc: WorkRecordSvc, _: CurrentUser
) -> ApiResponse[RecordResponse]:
    return ApiResponse.ok(await svc.end(req))


@router.get("/by-schedule/{schedule_id}")
async def list_records(
    schedule_id: int, svc: WorkRecordSvc, _: CurrentUser
) -> ApiResponse[list[RecordResponse]]:
    return ApiResponse.ok(await svc.list(schedule_id))


@router.post("/processes/begin")
async def begin_process(
    req: ProcessStartRequest, svc: WorkRecordSvc, _: CurrentUser
) -> ApiResponse[ProcessRecordResponse]:
    return ApiResponse.ok(await svc.begin_process(req))


@router.post("/processes/end")
async def end_process(
    req: ProcessEndRequest, svc: WorkRecordSvc, _: CurrentUser
) -> ApiResponse[ProcessRecordResponse]:
    return ApiResponse.ok(await svc.end_process(req))


@router.get("/processes/by-schedule/{schedule_id}")
async def list_process_records(
    schedule_id: int, svc: WorkRecordSvc, _: CurrentUser
) -> ApiResponse[list[ProcessRecordResponse]]:
    return ApiResponse.ok(await svc.list_process(schedule_id))
