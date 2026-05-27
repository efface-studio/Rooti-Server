"""Work journal rendering — PDF/XLSX/HWP download + bulk email (stub)."""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends, Query
from fastapi.responses import Response

from app.api.deps import CurrentUser, DbSession, RequireAdminOrCharger
from app.core.exceptions import BusinessException, ErrorCode
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.schemas.document import BulkEmailRequest, BulkEmailResult, JournalFormat
from app.services.document import WorkJournalBulkEmailService, WorkJournalRenderService

router = RootiRouter(tags=["work-journal"])


def _render_service(db: DbSession) -> WorkJournalRenderService:
    return WorkJournalRenderService(db)


RenderSvc = Annotated[WorkJournalRenderService, Depends(_render_service)]


def _bulk_service(db: DbSession) -> WorkJournalBulkEmailService:
    return WorkJournalBulkEmailService(db)


BulkSvc = Annotated[WorkJournalBulkEmailService, Depends(_bulk_service)]


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


@router.get("/{schedule_id}/pdf", summary="근무일지 PDF (legacy 호환 경로)")
async def download_pdf(
    schedule_id: int, svc: RenderSvc, _: CurrentUser
) -> Response:
    body = await svc.render(schedule_id, JournalFormat.PDF)
    return _file_response(body, schedule_id, JournalFormat.PDF)


@router.get("/{schedule_id}/file", summary="근무일지 PDF/HWP/XLSX 다운로드")
async def download_file(
    schedule_id: int,
    svc: RenderSvc,
    _: CurrentUser,
    format: Annotated[JournalFormat, Query()] = JournalFormat.PDF,
) -> Response:
    body = await svc.render(schedule_id, format)
    return _file_response(body, schedule_id, format)


@router.post("/bulk-email", summary="회사+날짜로 근무일지 ZIP 메일 발송")
async def bulk_email(
    req: BulkEmailRequest, svc: BulkSvc, _: RequireAdminOrCharger
) -> ApiResponse[BulkEmailResult]:
    result = await svc.send(req.company_id, req.date, req.recipient_email, req.format)
    count = int(result.get("count", 0))
    return ApiResponse.ok(
        BulkEmailResult(
            sent=count > 0,
            schedule_count=count,
            recipient_email=req.recipient_email,
            message=(
                f"sent {count} journal(s), message_id={result.get('message_id')}"
                if count > 0
                else "no schedules matched company+date"
            ),
        )
    )
