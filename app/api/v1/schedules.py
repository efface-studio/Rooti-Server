"""WorkSchedule endpoints."""

from __future__ import annotations

from datetime import date

from fastapi import Query

from app.api.deps import CurrentUser, RequireAdminOrCharger, ScheduleSvc
from app.core.pagination import PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.schemas.schedule import (
    BatchMakeRequest,
    CloseScheduleRequest,
    CreateScheduleRequest,
    ScheduleResponse,
)

router = RootiRouter(tags=["schedule"])


@router.get("", summary="List all schedules (paginated)")
async def list_schedules(
    svc: ScheduleSvc, page: PagedQuery, _: CurrentUser
) -> ApiResponse[PageResponse[ScheduleResponse]]:
    p = await svc.list_all(page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.get("/{schedule_id}")
async def get_schedule(
    schedule_id: int, svc: ScheduleSvc, _: CurrentUser
) -> ApiResponse[ScheduleResponse]:
    return ApiResponse.ok(await svc.get(schedule_id))


@router.get("/by-job-worker/{job_worker_id}")
async def list_by_job_worker(
    job_worker_id: int,
    svc: ScheduleSvc,
    _: CurrentUser,
    from_: date = Query(alias="from"),
    to: date = Query(...),
) -> ApiResponse[list[ScheduleResponse]]:
    return ApiResponse.ok(await svc.list_for_job_worker(job_worker_id, from_=from_, to=to))


@router.get("/by-job-standard/{job_standard_id}")
async def list_by_job_standard(
    job_standard_id: int,
    svc: ScheduleSvc,
    page: PagedQuery,
    _: CurrentUser,
    from_: date = Query(alias="from"),
    to: date = Query(...),
) -> ApiResponse[PageResponse[ScheduleResponse]]:
    p = await svc.list_for_standard(job_standard_id, from_=from_, to=to, params=page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.post("")
async def create_schedule(
    req: CreateScheduleRequest, svc: ScheduleSvc, _: RequireAdminOrCharger
) -> ApiResponse[ScheduleResponse]:
    return ApiResponse.ok(await svc.create(req))


@router.post("/batch")
async def batch_make(
    req: BatchMakeRequest, svc: ScheduleSvc, _: RequireAdminOrCharger
) -> ApiResponse[list[ScheduleResponse]]:
    return ApiResponse.ok(await svc.batch_make(req))


@router.post("/{schedule_id}/close")
async def close_schedule(
    schedule_id: int,
    svc: ScheduleSvc,
    _: CurrentUser,
    req: CloseScheduleRequest | None = None,
) -> ApiResponse[ScheduleResponse]:
    end_at = req.end_at if req else None
    return ApiResponse.ok(await svc.close(schedule_id, end_at))
