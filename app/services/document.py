"""Document service — caregiver-document CRUD + work-journal rendering.

NOTE:
- HWP 렌더링: 한글워드프로세서 포맷 — 별도 SDK 필요. 현재는 placeholder text 반환 (Java 도 동일).
- Bulk email: Resend 연동 + ZIP 패키징. 본 PR 에선 stub (501 NotImplementedError).
"""

from __future__ import annotations

from datetime import date
from typing import BinaryIO

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BusinessException, ErrorCode
from app.integrations.storage import StorageService, Uploaded
from app.integrations.xlsx import render_journal_xlsx
from app.models import (
    CaregiverDocument,
    CaregiverDocumentActionType,
    CaregiverDocumentLog,
    CaregiverDocumentType,
    CaregiverWorkerRelation,
    JobStandard,
    WorkRecord,
    WorkSchedule,
)
from app.schemas.document import DocumentResponse, JournalFormat


# =============================================================================
#  Caregiver document
# =============================================================================
class CaregiverDocumentService:
    def __init__(self, db: AsyncSession, storage: StorageService) -> None:
        self.db = db
        self.storage = storage

    async def upload(
        self,
        actor_user_id: int,
        relation_id: int,
        type_id: int,
        original_filename: str,
        file: BinaryIO,
        size: int,
        content_type: str | None,
    ) -> DocumentResponse:
        relation = await self.db.get(CaregiverWorkerRelation, relation_id)
        if relation is None:
            raise BusinessException(ErrorCode.CAREGIVER_NOT_FOUND)
        doc_type = await self.db.get(CaregiverDocumentType, type_id)
        if doc_type is None:
            raise BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND)

        try:
            uploaded: Uploaded = await self.storage.store(
                f"caregiver-documents/{relation_id}",
                original_filename,
                file,
                size,
                content_type,
            )
        except Exception as e:
            raise BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED, str(e), cause=e) from e

        doc = CaregiverDocument(
            relation_id=relation_id,
            type_id=type_id,
            filename=uploaded.key,
            file_size=uploaded.size,
            content_type=uploaded.content_type,
            created_by=actor_user_id,
            updated_by=actor_user_id,
        )
        self.db.add(doc)
        await self.db.flush()
        await self._log(actor_user_id, doc, CaregiverDocumentActionType.UPLOAD)
        return _to_response(doc, doc_type.name, uploaded.url)

    async def list_by_relation(self, relation_id: int) -> list[DocumentResponse]:
        rows = (
            (
                await self.db.execute(
                    select(CaregiverDocument, CaregiverDocumentType)
                    .join(CaregiverDocumentType, CaregiverDocumentType.id == CaregiverDocument.type_id)
                    .where(CaregiverDocument.relation_id == relation_id)
                    .order_by(CaregiverDocument.id.desc())
                )
            )
            .all()
        )
        return [_to_response(d, t.name, None) for d, t in rows]

    async def load_for_download(
        self, actor_user_id: int, document_id: int
    ) -> CaregiverDocument:
        doc = await self.db.get(CaregiverDocument, document_id)
        if doc is None:
            raise BusinessException(ErrorCode.DOCUMENT_NOT_FOUND)
        await self._log(actor_user_id, doc, CaregiverDocumentActionType.DOWNLOAD)
        return doc

    async def delete(self, actor_user_id: int, document_id: int) -> None:
        doc = await self.db.get(CaregiverDocument, document_id)
        if doc is None:
            raise BusinessException(ErrorCode.DOCUMENT_NOT_FOUND)
        await self._log(actor_user_id, doc, CaregiverDocumentActionType.DELETE)
        await self.storage.delete(doc.filename)
        await self.db.delete(doc)

    async def _log(
        self, user_id: int, doc: CaregiverDocument, action: CaregiverDocumentActionType
    ) -> None:
        self.db.add(
            CaregiverDocumentLog(document_id=doc.id, user_id=user_id, action_type=action)
        )


def _to_response(
    doc: CaregiverDocument, type_name: str | None, download_url: str | None
) -> DocumentResponse:
    return DocumentResponse(
        id=doc.id,
        relation_id=doc.relation_id,
        type_id=doc.type_id,
        type_name=type_name,
        filename=doc.filename,
        download_url=download_url,
        size=doc.file_size,
        content_type=doc.content_type,
        created_at=doc.created_at,
    )


# =============================================================================
#  Work journal rendering
# =============================================================================
class WorkJournalRenderService:
    """근무일지 단일 다운로드.

    Java 의 JournalRendererRegistry → PDF/XLSX/HWP 분기 등가. 데이터는 schedule + records
    조합으로 만들고, 포맷별 렌더러에 위임.
    """

    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def render(self, schedule_id: int, fmt: JournalFormat) -> bytes:
        schedule = await self.db.get(WorkSchedule, schedule_id)
        if schedule is None:
            raise BusinessException(ErrorCode.WORK_SCHEDULE_NOT_FOUND)
        standard = await self.db.get(JobStandard, schedule.job_standard_id)
        records = (
            (
                await self.db.execute(
                    select(WorkRecord)
                    .where(WorkRecord.work_schedule_id == schedule_id)
                    .order_by(WorkRecord.start_at)
                )
            )
            .scalars()
            .all()
        )

        rows = [
            {
                "type": r.type.value,
                "start_at": r.start_at.isoformat(),
                "end_at": r.end_at.isoformat() if r.end_at else "",
            }
            for r in records
        ]
        title = f"근무일지 - schedule={schedule_id} - {standard.name if standard else ''}"

        if fmt is JournalFormat.PDF:
            return await self._render_pdf(title, rows)
        if fmt is JournalFormat.XLSX:
            return await render_journal_xlsx(rows, header=["type", "start_at", "end_at"])
        if fmt is JournalFormat.HWP:
            # Java 와 동일하게 placeholder — hwpx 렌더러 미구현. 운영 직전 SDK 추가 시 교체.
            return f"[HWP placeholder] {title}\n(렌더러 미구현)\n".encode()
        raise BusinessException(ErrorCode.INVALID_INPUT, f"unknown format: {fmt}")

    async def _render_pdf(self, title: str, rows: list[dict[str, str]]) -> bytes:
        # 외부 템플릿 파일이 없어도 동작하도록 인라인 HTML.
        from app.integrations.pdf import html_to_pdf

        rows_html = "".join(
            f"<tr><td>{r['type']}</td><td>{r['start_at']}</td><td>{r['end_at']}</td></tr>"
            for r in rows
        )
        html = f"""
        <!doctype html>
        <html lang="ko">
        <head><meta charset="utf-8"><title>{title}</title>
        <style>body{{font-family:sans-serif}}table{{border-collapse:collapse;width:100%}}
        th,td{{border:1px solid #999;padding:6px}}</style></head>
        <body><h1>{title}</h1>
        <table><thead><tr><th>type</th><th>start</th><th>end</th></tr></thead>
        <tbody>{rows_html}</tbody></table></body></html>
        """
        return await html_to_pdf(html)


# =============================================================================
#  Bulk email — render → ZIP → Resend
# =============================================================================
import io
import zipfile
from datetime import datetime, time

from app.integrations.email import Attachment, send_email
from app.models import JobWorker


class WorkJournalBulkEmailService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db
        self._render = WorkJournalRenderService(db)

    async def send(
        self, company_id: int, day: date, recipient_email: str, fmt: JournalFormat
    ) -> dict[str, object]:
        """회사+날짜의 모든 근무 스케줄을 render → ZIP → 메일.

        반환: {"count": <n>, "message_id": <str|None>}
        """
        from sqlalchemy import select

        from app.models import CompanyWorker

        start_at = datetime.combine(day, time.min)
        end_at_excl = datetime.combine(day, time.max)

        # company → company_worker → job_worker → work_schedule
        schedules = (
            (
                await self.db.execute(
                    select(WorkSchedule)
                    .join(JobWorker, JobWorker.id == WorkSchedule.job_worker_id)
                    .join(CompanyWorker, CompanyWorker.id == JobWorker.company_worker_id)
                    .where(
                        CompanyWorker.company_id == company_id,
                        WorkSchedule.start_at >= start_at,
                        WorkSchedule.start_at <= end_at_excl,
                    )
                    .order_by(WorkSchedule.start_at)
                )
            )
            .scalars()
            .all()
        )

        if not schedules:
            return {"count": 0, "message_id": None}
        filtered = list(schedules)

        # 2. 각 schedule render → ZIP 으로 묶음
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
            for schedule in filtered:
                body = await self._render.render(schedule.id, fmt)
                zf.writestr(f"work-journal-{schedule.id}.{fmt.extension}", body)
        zip_bytes = buf.getvalue()

        # 3. Resend 로 발송
        msg_id = await send_email(
            to=recipient_email,
            subject=f"근무일지 일괄 ({day.isoformat()}) — {len(filtered)}건",
            html=(
                f"<p>{day.isoformat()} 자 근무일지 {len(filtered)}건을 첨부합니다.</p>"
            ),
            attachments=[
                Attachment(
                    filename=f"work-journals-{day.isoformat()}.zip",
                    content=zip_bytes,
                    content_type="application/zip",
                )
            ],
        )
        return {"count": len(filtered), "message_id": msg_id}
