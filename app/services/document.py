"""Document service — caregiver-document CRUD + work-journal rendering.

NOTE:
- HWP 렌더링: 한글워드프로세서 포맷 — 별도 SDK 필요. 현재는 placeholder text 반환 (Java 도 동일).
- Bulk email: Resend 연동 + ZIP 패키징. 본 PR 에선 stub (501 NotImplementedError).
"""

from __future__ import annotations

import io
import logging
import zipfile
from datetime import UTC, date, datetime, time
from typing import BinaryIO

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import AuthForbiddenException, BusinessException, ErrorCode
from app.core.tenant import CompanyScopeValue, assert_company
from app.integrations.email import Attachment, send_email
from app.integrations.storage import StorageService, Uploaded
from app.integrations.xlsx import render_journal_xlsx
from app.models import (
    BulkEmailJob,
    Caregiver,
    CaregiverDocument,
    CaregiverDocumentActionType,
    CaregiverDocumentLog,
    CaregiverDocumentType,
    CaregiverWorkerRelation,
    ChallengedWorker,
    Company,
    CompanyWorker,
    JobStandard,
    JobWorker,
    User,
    UserRole,
    WorkRecord,
    WorkSchedule,
)
from app.schemas.document import (
    BulkEmailJobResponse,
    BulkPreviewItem,
    BulkPreviewResponse,
    DocumentResponse,
    JournalFormat,
)

log = logging.getLogger(__name__)

# 일괄 메일 1회당 렌더할 근무일지 상한. 백그라운드 워커가 단일 AsyncSession 으로
# 직렬 렌더(PDF 변환 포함)하므로, 비정상적으로 큰 회사+날짜 조합이 메모리/시간을
# 소진하지 않도록 방어. 초과분은 잘라내고 메일 본문·로그로 알린다.
_MAX_BULK_SCHEDULES = 300


def _now() -> datetime:
    """naive UTC — bulk_email_jobs 시각 컬럼이 timezone=False 라 tz 를 떼서 저장."""
    return datetime.now(UTC).replace(tzinfo=None)


# =============================================================================
#  Caregiver document
# =============================================================================
class CaregiverDocumentService:
    def __init__(self, db: AsyncSession, storage: StorageService) -> None:
        self.db = db
        self.storage = storage

    # =========================================================================
    #  IDOR 게이트 — 모든 mutating/read 액션이 이걸 통과해야 함.
    # =========================================================================
    async def _ensure_actor_owns_relation(
        self, actor_user_id: int, relation: CaregiverWorkerRelation
    ) -> None:
        """`relation` 의 소유자(caregiver) 가 actor_user_id 인지 확인.

        - actor 가 ADMIN 이면 통과 (감독자 접근).
        - actor 가 해당 relation 의 caregiver.user_id 이면 통과.
        - 그 외는 AUTH_FORBIDDEN. 단, 정보 누출 방지를 위해 메시지는 일반화.
        """
        actor = await self.db.get(User, actor_user_id)
        if actor is None:
            raise AuthForbiddenException()
        if actor.role == UserRole.ADMIN:
            return
        # caregiver 의 user_id 와 일치하는지
        caregiver = await self.db.get(Caregiver, relation.caregiver_id)
        if caregiver is None or caregiver.user_id != actor_user_id:
            raise AuthForbiddenException()

    async def _relation_or_404(self, relation_id: int) -> CaregiverWorkerRelation:
        rel = await self.db.get(CaregiverWorkerRelation, relation_id)
        if rel is None:
            raise BusinessException(ErrorCode.CAREGIVER_NOT_FOUND)
        return rel

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
        relation = await self._relation_or_404(relation_id)
        await self._ensure_actor_owns_relation(actor_user_id, relation)
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

    async def list_by_relation(
        self, actor_user_id: int, relation_id: int
    ) -> list[DocumentResponse]:
        relation = await self._relation_or_404(relation_id)
        await self._ensure_actor_owns_relation(actor_user_id, relation)
        rows = (
            await self.db.execute(
                select(CaregiverDocument, CaregiverDocumentType)
                .join(CaregiverDocumentType, CaregiverDocumentType.id == CaregiverDocument.type_id)
                .where(CaregiverDocument.relation_id == relation_id)
                .order_by(CaregiverDocument.id.desc())
            )
        ).all()
        return [_to_response(d, t.name, None) for d, t in rows]

    async def load_for_download(self, actor_user_id: int, document_id: int) -> CaregiverDocument:
        doc = await self.db.get(CaregiverDocument, document_id)
        if doc is None:
            raise BusinessException(ErrorCode.DOCUMENT_NOT_FOUND)
        relation = await self._relation_or_404(doc.relation_id)
        await self._ensure_actor_owns_relation(actor_user_id, relation)
        await self._log(actor_user_id, doc, CaregiverDocumentActionType.DOWNLOAD)
        return doc

    async def delete(self, actor_user_id: int, document_id: int) -> None:
        doc = await self.db.get(CaregiverDocument, document_id)
        if doc is None:
            raise BusinessException(ErrorCode.DOCUMENT_NOT_FOUND)
        relation = await self._relation_or_404(doc.relation_id)
        await self._ensure_actor_owns_relation(actor_user_id, relation)
        await self._log(actor_user_id, doc, CaregiverDocumentActionType.DELETE)
        await self.storage.delete(doc.filename)
        await self.db.delete(doc)

    async def _log(
        self, user_id: int, doc: CaregiverDocument, action: CaregiverDocumentActionType
    ) -> None:
        self.db.add(CaregiverDocumentLog(document_id=doc.id, user_id=user_id, action_type=action))


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

    async def render(
        self,
        schedule_id: int,
        fmt: JournalFormat,
        *,
        company_scope: CompanyScopeValue = None,
    ) -> bytes:
        schedule = await self.db.get(WorkSchedule, schedule_id)
        if schedule is None:
            raise BusinessException(ErrorCode.WORK_SCHEDULE_NOT_FOUND)
        # CHARGER 는 본인 회사 일정만 렌더 — schedule_id 순차 스캔(IDOR) 차단.
        # schedule → job_worker → company_worker → company 로 소유 회사 해석.
        if company_scope is not None:
            owner_company_id = (
                await self.db.execute(
                    select(CompanyWorker.company_id)
                    .join(JobWorker, JobWorker.company_worker_id == CompanyWorker.id)
                    .where(JobWorker.id == schedule.job_worker_id)
                )
            ).scalar_one_or_none()
            assert_company(company_scope, owner_company_id)
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

        # 자원 보호: 상한 초과분은 잘라낸다(오래된 순 우선). 초과 사실은 로그+메일로 알림.
        total = len(schedules)
        truncated = total > _MAX_BULK_SCHEDULES
        filtered = list(schedules[:_MAX_BULK_SCHEDULES])
        if truncated:
            log.warning(
                "[bulk-email:truncated] company=%s date=%s total=%d cap=%d",
                company_id,
                day.isoformat(),
                total,
                _MAX_BULK_SCHEDULES,
            )

        # 2. 각 schedule render → ZIP 으로 묶음.
        # NOTE: render 는 self.db(단일 AsyncSession)를 공유하므로 직렬 유지가 필수다.
        #       asyncio.gather 로 동시 실행하면 같은 세션을 여러 코루틴이 만져 깨진다.
        #       schedule/standard 는 이미 identity-map 에 올라와 재조회가 캐시 히트라 추가 부담은 작다.
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
            for schedule in filtered:
                body = await self._render.render(schedule.id, fmt)
                zf.writestr(f"work-journal-{schedule.id}.{fmt.extension}", body)
        zip_bytes = buf.getvalue()

        note = (
            f"<p>주의: 상한({_MAX_BULK_SCHEDULES}건)을 초과해 전체 {total}건 중 "
            f"{len(filtered)}건만 첨부했습니다. 날짜·회사 범위를 좁혀 다시 보내세요.</p>"
            if truncated
            else ""
        )
        # 3. Resend 로 발송
        msg_id = await send_email(
            to=recipient_email,
            subject=f"근무일지 일괄 ({day.isoformat()}) — {len(filtered)}건",
            html=(f"<p>{day.isoformat()} 자 근무일지 {len(filtered)}건을 첨부합니다.</p>{note}"),
            attachments=[
                Attachment(
                    filename=f"work-journals-{day.isoformat()}.zip",
                    content=zip_bytes,
                    content_type="application/zip",
                )
            ],
        )
        return {"count": len(filtered), "message_id": msg_id, "truncated": truncated}

    async def preview(self, company_id: int, day: date) -> BulkPreviewResponse:
        """'즉시 발송' 시 ZIP 에 묶일 근무일지 명단을 read-only 로 반환.

        send() 와 **동일한** company → company_worker → job_worker → work_schedule
        조인을 쓰되, render/ZIP/메일 대신 근로자명·업무명·기록 건수만 추려 돌려준다.
        관리자가 "누구 일지가 몇 건 나가는지" 를 보내기 전에 확인하게 하는 용도.
        쓰기가 전혀 없어 공유 dev RDS read-only 모드에서도 그대로 동작한다.
        """
        # 일정이 0건이어도 회사명은 보여줘야 하므로 회사는 따로 조회한다.
        company = await self.db.get(Company, company_id)
        company_name = company.name if company else ""

        start_at = datetime.combine(day, time.min)
        end_at_excl = datetime.combine(day, time.max)

        # 일정별 근무기록 건수 — 상관 서브쿼리로 한 방에 (N+1 회피).
        record_count_sq = (
            select(func.count(WorkRecord.id))
            .where(WorkRecord.work_schedule_id == WorkSchedule.id)
            .correlate(WorkSchedule)
            .scalar_subquery()
        )

        rows = (
            await self.db.execute(
                select(
                    WorkSchedule,
                    ChallengedWorker.id,
                    User.name,
                    JobStandard.name,
                    record_count_sq,
                )
                .join(JobWorker, JobWorker.id == WorkSchedule.job_worker_id)
                .join(CompanyWorker, CompanyWorker.id == JobWorker.company_worker_id)
                .join(ChallengedWorker, ChallengedWorker.id == CompanyWorker.challenged_worker_id)
                .join(User, User.id == ChallengedWorker.user_id)
                .join(JobStandard, JobStandard.id == WorkSchedule.job_standard_id)
                .where(
                    CompanyWorker.company_id == company_id,
                    WorkSchedule.start_at >= start_at,
                    WorkSchedule.start_at <= end_at_excl,
                )
                .order_by(User.name, WorkSchedule.start_at)
            )
        ).all()

        items = [
            BulkPreviewItem(
                schedule_id=ws.id,
                worker_id=worker_id,
                worker_name=worker_name,
                job_standard_id=ws.job_standard_id,
                job_standard_name=std_name,
                start_at=ws.start_at,
                end_at=ws.end_at,
                record_count=int(rec_count or 0),
            )
            for ws, worker_id, worker_name, std_name, rec_count in rows
        ]
        return BulkPreviewResponse(
            company_id=company_id,
            company_name=company_name,
            date=day,
            journal_count=len(items),
            items=items,
        )

    # ------------------------------------------------------------ 잡(이력/상태)
    async def create_job(
        self,
        company_id: int,
        day: date,
        recipient_email: str,
        fmt: JournalFormat,
        requested_by: int | None,
    ) -> BulkEmailJobResponse:
        """발송 요청을 QUEUED 로 기록. 엔드포인트가 동기로 호출해 잡 id 를 즉시 돌려준다."""
        job = BulkEmailJob(
            company_id=company_id,
            recipient_email=recipient_email,
            target_date=day,
            format=fmt.value,
            status="QUEUED",
            requested_by=requested_by,
        )
        self.db.add(job)
        await self.db.flush()
        company = await self.db.get(Company, company_id)
        return _to_job_response(job, company.name if company else None, None)

    async def mark_sending(self, job_id: int) -> BulkEmailJob | None:
        """QUEUED → SENDING. 백그라운드 러너가 렌더 시작 직전 호출 (flush-only)."""
        job = await self.db.get(BulkEmailJob, job_id)
        if job is None:
            return None
        job.status = "SENDING"
        job.started_at = _now()
        await self.db.flush()
        return job

    async def complete_job(self, job_id: int) -> None:
        """SENDING → SUCCESS|FAILED. render→ZIP→Resend 를 수행하고 결과/사유를 기록 (flush-only).

        ``send`` 는 읽기 + 외부 메일 호출만 하므로 예외가 나도 DB 트랜잭션을 오염시키지
        않는다 → 별도 rollback 없이 같은 세션에서 FAILED 를 기록할 수 있다.
        """
        job = await self.db.get(BulkEmailJob, job_id)
        if job is None:
            return
        try:
            result = await self.send(
                job.company_id, job.target_date, job.recipient_email, JournalFormat(job.format)
            )
            count = result.get("count")
            msg_id = result.get("message_id")
            job.schedule_count = count if isinstance(count, int) else 0
            job.message_id = msg_id if isinstance(msg_id, str) else None
            job.truncated = bool(result.get("truncated"))
            job.status = "SUCCESS"
        except Exception as e:  # 어떤 실패든 잡에 사유를 남기고 종료한다.
            job.status = "FAILED"
            job.error_message = str(e)[:1000]
            log.exception("[bulk-email:job-failed] job=%s", job_id)
        job.finished_at = _now()
        await self.db.flush()

    async def run_job(self, job_id: int) -> None:
        """SENDING→완료를 한 번에 (테스트/동기 호출용, flush-only).

        운영 백그라운드 경로(:func:`run_bulk_email_job`)는 단계별 commit 으로 SENDING
        상태를 실시간 노출한다.
        """
        if await self.mark_sending(job_id) is None:
            return
        await self.complete_job(job_id)

    async def get_job(
        self, job_id: int, *, company_scope: CompanyScopeValue = None
    ) -> BulkEmailJobResponse:
        row = (
            await self.db.execute(
                select(BulkEmailJob, Company.name, User.name)
                .join(Company, Company.id == BulkEmailJob.company_id)
                .outerjoin(User, User.id == BulkEmailJob.requested_by)
                .where(BulkEmailJob.id == job_id)
            )
        ).first()
        if row is None:
            raise BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        job, company_name, requester_name = row
        # CHARGER 는 본인 회사 잡만 — 잡 id 순차 스캔(IDOR) 차단.
        assert_company(company_scope, job.company_id)
        return _to_job_response(job, company_name, requester_name)

    async def list_jobs(
        self, company_id: int | None, limit: int = 50
    ) -> list[BulkEmailJobResponse]:
        """발송 이력 — 최신순. ADMIN 은 company_id=None 이면 전체, CHARGER 는 본인 회사로 강제됨."""
        stmt = (
            select(BulkEmailJob, Company.name, User.name)
            .join(Company, Company.id == BulkEmailJob.company_id)
            .outerjoin(User, User.id == BulkEmailJob.requested_by)
            .order_by(BulkEmailJob.id.desc())
            .limit(limit)
        )
        if company_id is not None:
            stmt = stmt.where(BulkEmailJob.company_id == company_id)
        rows = (await self.db.execute(stmt)).all()
        return [_to_job_response(j, cn, rn) for j, cn, rn in rows]


def _to_job_response(
    job: BulkEmailJob, company_name: str | None, requester_name: str | None
) -> BulkEmailJobResponse:
    return BulkEmailJobResponse(
        id=job.id,
        company_id=job.company_id,
        company_name=company_name,
        recipient_email=job.recipient_email,
        target_date=job.target_date,
        format=job.format,
        status=job.status,
        schedule_count=job.schedule_count,
        truncated=job.truncated,
        message_id=job.message_id,
        error_message=job.error_message,
        requested_by=job.requested_by,
        requested_by_name=requester_name,
        started_at=job.started_at,
        finished_at=job.finished_at,
        created_at=job.created_at,
    )


async def run_bulk_email_job(job_id: int) -> None:
    """백그라운드 발송 러너 — 요청 세션과 분리된 자체 세션을 연다.

    FastAPI BackgroundTasks 는 응답 이후 실행되므로 요청 스코프 세션 수명에 의존하면
    안 된다. SENDING 전이를 먼저 commit 해 진행 상태를 실시간 노출하고, 완료 후 한 번 더
    commit 해 SUCCESS|FAILED 를 확정한다.
    """
    from app.core.database import get_sessionmaker

    async with get_sessionmaker()() as session:
        svc = WorkJournalBulkEmailService(session)
        try:
            if await svc.mark_sending(job_id) is None:
                await session.rollback()
                return
            await session.commit()  # SENDING 노출
            await svc.complete_job(job_id)
            await session.commit()  # SUCCESS|FAILED 확정
        except Exception:
            await session.rollback()
            log.exception("[bulk-email:runner-crashed] job=%s", job_id)


async def run_bulk_email_inert(
    company_id: int, day: date, recipient_email: str, fmt: JournalFormat
) -> None:
    """read-only(dev RDS) 모드용 — 잡 기록 없이 기존처럼 발송만 시도(쓰기 없음).

    bulk_email_jobs 에 쓸 수 없는(read-only / 테이블 미적용) 환경에서 기존
    fire-and-forget 동작을 유지한다. 자체 세션을 열어 요청 세션 수명에 의존하지 않는다.
    """
    from app.core.database import get_sessionmaker

    async with get_sessionmaker()() as session:
        try:
            await WorkJournalBulkEmailService(session).send(company_id, day, recipient_email, fmt)
        finally:
            await session.rollback()
