"""Work journal rendering — PDF/XLSX/HWP download + bulk email (stub)."""

from __future__ import annotations

from typing import Annotated

from fastapi import BackgroundTasks, Depends, Query
from fastapi.responses import Response

from app.api.deps import CurrentUser, DbSession, RequireAdminOrCharger
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.schemas.document import BulkEmailRequest, BulkEmailResult, JournalFormat
from app.schemas.journal_schedule import (
    JournalScheduleCreateRequest,
    JournalScheduleResponse,
    JournalScheduleUpdateRequest,
)
from app.services.document import WorkJournalBulkEmailService, WorkJournalRenderService
from app.services.journal_schedule import JournalScheduleService

router = RootiRouter(tags=["work-journal"])


def _render_service(db: DbSession) -> WorkJournalRenderService:
    return WorkJournalRenderService(db)


RenderSvc = Annotated[WorkJournalRenderService, Depends(_render_service)]


def _bulk_service(db: DbSession) -> WorkJournalBulkEmailService:
    return WorkJournalBulkEmailService(db)


BulkSvc = Annotated[WorkJournalBulkEmailService, Depends(_bulk_service)]


def _schedule_service(db: DbSession) -> JournalScheduleService:
    return JournalScheduleService(db)


SchedSvc = Annotated[JournalScheduleService, Depends(_schedule_service)]


def _file_response(body: bytes, schedule_id: int, fmt: JournalFormat) -> Response:
    return Response(
        content=body,
        media_type=fmt.content_type,
        headers={
            "content-disposition": (
                f'attachment; filename="work-journal-{schedule_id}.{fmt.extension}"'
            )
        },
    )


# NOTE: 일지 다운로드는 ADMIN/CHARGER 만 — 일반 사용자가 schedule_id 를 순차로 시도하는
# IDOR (다른 사용자의 일지 노출) 차단. WORKER 본인이 받아야 하는 경우엔 mobile 앱에서
# /api/v1/schedules/by-job-worker/{id} → ADMIN/CHARGER 경유로 처리.
@router.get("/{schedule_id}/pdf", summary="근무일지 PDF (legacy 호환 경로)")
async def download_pdf(schedule_id: int, svc: RenderSvc, _: RequireAdminOrCharger) -> Response:
    body = await svc.render(schedule_id, JournalFormat.PDF)
    return _file_response(body, schedule_id, JournalFormat.PDF)


@router.get("/{schedule_id}/file", summary="근무일지 PDF/HWP/XLSX 다운로드")
async def download_file(
    schedule_id: int,
    svc: RenderSvc,
    _: RequireAdminOrCharger,
    format: Annotated[JournalFormat, Query()] = JournalFormat.PDF,
) -> Response:
    body = await svc.render(schedule_id, format)
    return _file_response(body, schedule_id, format)


@router.post("/bulk-email", summary="회사+날짜로 근무일지 ZIP 메일 발송 (비동기)")
async def bulk_email(
    req: BulkEmailRequest,
    svc: BulkSvc,
    background: BackgroundTasks,
    _: RequireAdminOrCharger,
) -> ApiResponse[BulkEmailResult]:
    """N건 schedule render + ZIP + Resend 는 수초~수십초 걸림 → background 로 위임.

    응답: 즉시 202-style (success=true, queued). 실제 발송 결과는 비동기 → 호출자가
    /api/v1/work-journals/jobs/{id} 같은 status API 가 필요하면 후속 PR.
    """
    background.add_task(svc.send, req.company_id, req.date, req.recipient_email, req.format)
    return ApiResponse.ok(
        BulkEmailResult(
            sent=False,
            schedule_count=0,
            recipient_email=req.recipient_email,
            message="queued — 백그라운드 발송 시작. 완료 시 Resend 가 수신자에게 메일.",
        )
    )


# ============================================================ 자동 메일 스케줄
@router.get("/email-schedules", summary="근무일지 메일 자동 스케줄 목록")
async def list_email_schedules(
    svc: SchedSvc, _: CurrentUser, company_id: int | None = None
) -> ApiResponse[list[JournalScheduleResponse]]:
    return ApiResponse.ok(await svc.list(company_id))


@router.post("/email-schedules", summary="자동 스케줄 생성")
async def create_email_schedule(
    req: JournalScheduleCreateRequest, svc: SchedSvc, _: RequireAdminOrCharger
) -> ApiResponse[JournalScheduleResponse]:
    return ApiResponse.ok(await svc.create(req))


@router.patch("/email-schedules/{schedule_id}", summary="자동 스케줄 수정/토글")
async def update_email_schedule(
    schedule_id: int,
    req: JournalScheduleUpdateRequest,
    svc: SchedSvc,
    _: RequireAdminOrCharger,
) -> ApiResponse[JournalScheduleResponse]:
    return ApiResponse.ok(await svc.update(schedule_id, req))


@router.delete("/email-schedules/{schedule_id}", summary="자동 스케줄 삭제")
async def delete_email_schedule(
    schedule_id: int, svc: SchedSvc, _: RequireAdminOrCharger
) -> ApiResponse[None]:
    await svc.delete(schedule_id)
    return ApiResponse.ok()
