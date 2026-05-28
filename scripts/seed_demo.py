"""데모 데이터 시드 — 전 도메인을 일관된 샘플 데이터로 채운다.

idempotent: 실행할 때마다 모든 도메인 테이블을 TRUNCATE ... RESTART IDENTITY CASCADE
후 처음부터 다시 채운다. 운영 DB 가 아니라 로컬/프리뷰 DB(.env 의 DB_NAME)만 대상.

실행:
    cd Rooti-Server
    uv run python -m scripts.seed_demo

로그인 계정 (LoginPage 빠른 로그인 버튼과 동일):
    admin / admin1234        (ADMIN)
    charger / charger1234    (CHARGER)
    caregiver / caregiver1234(CAREGIVER)
    worker / worker1234       (WORKER)
"""

from __future__ import annotations

import asyncio
from datetime import datetime, time, timedelta

from sqlalchemy import text
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app.core.config import get_settings
from app.core.security import hash_password
from app.models import (
    Caregiver,
    CaregiverBoard,
    CaregiverDocument,
    CaregiverDocumentActionType,
    CaregiverDocumentLog,
    CaregiverDocumentRequestOn,
    CaregiverDocumentType,
    CaregiverWorkerRelation,
    ChallengedWorker,
    Company,
    CompanyCharger,
    CompanyKiosk,
    CompanyWorker,
    JobProcess,
    JobStandard,
    JobWorker,
    Leave,
    LeaveStatus,
    LeaveType,
    OptionVariable,
    User,
    UserRole,
    WorkProcessRecord,
    WorkRecord,
    WorkRecordType,
    WorkSchedule,
)

_ALL_TABLES = [
    "leaves",
    "caregiver_document_logs",
    "caregiver_documents",
    "caregiver_document_types",
    "work_process_records",
    "work_records",
    "work_schedules",
    "job_workers",
    "job_processes",
    "job_standards",
    "caregiver_worker_relations",
    "caregivers",
    "company_workers",
    "challenged_workers",
    "company_chargers",
    "company_kiosks",
    "caregiver_boards",
    "companies",
    "option_variables",
    "users",
]

# 데모 비밀번호 = 사용자명 + "1234" (LoginPage 빠른 로그인 버튼과 일치)
_PW = "1234"


async def seed() -> None:
    settings = get_settings()
    if settings.app_env == "prod":
        raise SystemExit("거부: prod 환경에서는 데모 시드를 실행하지 않습니다.")

    engine = create_async_engine(settings.database_url, future=True)
    sm = async_sessionmaker(engine, expire_on_commit=False)

    async with sm() as db:
        # ---------- 0. 초기화 ----------
        await db.execute(text(f"TRUNCATE {', '.join(_ALL_TABLES)} RESTART IDENTITY CASCADE"))

        # ---------- 1. Users ----------
        def mkuser(username: str, name: str, role: UserRole, email: str | None = None) -> User:
            u = User(
                username=username,
                email=email or f"{username}@rooti.io",
                password_hash=hash_password(username + _PW),
                name=name,
                phone_number="010-0000-0000",
                role=role,
                enabled=True,
            )
            db.add(u)
            return u

        admin = mkuser("admin", "관리자", UserRole.ADMIN)
        charger = mkuser("charger", "회사 담당자", UserRole.CHARGER)
        caregiver_user = mkuser("caregiver", "보호자", UserRole.CAREGIVER)
        worker_user = mkuser("worker", "근로자", UserRole.WORKER)

        # 추가 근로자/보호자/담당자 (목록 화면이 비지 않게)
        extra_worker_users = [
            mkuser(f"worker{i}", f"근로자{i}", UserRole.WORKER) for i in range(2, 7)
        ]
        caregiver2 = mkuser("caregiver2", "보호자2", UserRole.CAREGIVER)
        charger2 = mkuser("charger2", "담당자2", UserRole.CHARGER)
        charger3 = mkuser("charger3", "담당자3", UserRole.CHARGER)
        await db.flush()

        # ---------- 2. Companies ----------
        companies = [
            Company(
                name="데모 회사",
                location="서울 강남구",
                use_flag=True,
                template_id="tpl-default",
                template_data={"theme": "blue"},
                created_by=admin.id,
                updated_by=admin.id,
            ),
            Company(
                name="청주 바른기준치과",
                location="충북 청주시",
                use_flag=True,
                created_by=admin.id,
                updated_by=admin.id,
            ),
            Company(
                name="서울 행복요양원",
                location="서울 송파구",
                use_flag=True,
                created_by=admin.id,
                updated_by=admin.id,
            ),
        ]
        db.add_all(companies)
        await db.flush()

        # ---------- 3. Chargers ↔ company ----------
        charger_rows = [
            CompanyCharger(user_id=charger.id, company_id=companies[0].id, is_hired=True),
            CompanyCharger(user_id=charger2.id, company_id=companies[1].id, is_hired=True),
            CompanyCharger(user_id=charger3.id, company_id=companies[2].id, is_hired=True),
        ]
        db.add_all(charger_rows)
        await db.flush()
        # company_id → company_chargers.id (work_schedules.company_charger_id 가 참조하는 건 이 row id)
        charger_by_company = {cc.company_id: cc.id for cc in charger_rows}

        # ---------- 4. Workers (challenged_workers) ----------
        worker_users = [worker_user, *extra_worker_users]
        challenged = [ChallengedWorker(user_id=u.id, fcm_token=None) for u in worker_users]
        db.add_all(challenged)
        await db.flush()

        # 회사별로 근로자 채용 (company_workers) — 라운드로빈
        company_workers: list[CompanyWorker] = []
        for idx, cw in enumerate(challenged):
            comp = companies[idx % len(companies)]
            row = CompanyWorker(company_id=comp.id, challenged_worker_id=cw.id, is_hired=True)
            db.add(row)
            company_workers.append(row)
        await db.flush()

        # ---------- 5. Caregivers + relations ----------
        cg1 = Caregiver(user_id=caregiver_user.id)
        cg2 = Caregiver(user_id=caregiver2.id)
        db.add_all([cg1, cg2])
        await db.flush()
        # caregiver 가 근로자 몇 명을 담당
        db.add_all(
            [
                CaregiverWorkerRelation(caregiver_id=cg1.id, challenged_worker_id=challenged[0].id),
                CaregiverWorkerRelation(caregiver_id=cg1.id, challenged_worker_id=challenged[1].id),
                CaregiverWorkerRelation(caregiver_id=cg2.id, challenged_worker_id=challenged[2].id),
            ]
        )

        # ---------- 6. Job standards + processes ----------
        job_standards: list[JobStandard] = []
        for comp in companies:
            for n in range(1, 3):  # 회사당 2개
                js = JobStandard(
                    company_id=comp.id,
                    name=f"{comp.name} 업무표준 {n}",
                    use_flag=True,
                    routine_start_time=time(9, 0),
                    standard_work_time=8 * 3600,
                    standard_rest_time=3600,
                    start_message="업무를 시작합니다.",
                    end_message="수고하셨습니다.",
                    context={"category": "care" if n == 1 else "clean"},
                    for_journal=True,
                )
                # 프로세스 3개
                for seq in range(1, 4):
                    js.processes.append(
                        JobProcess(
                            name=f"공정 {seq}",
                            sequence=seq,
                            process_time=1800,
                            start_message=f"공정 {seq} 시작",
                            end_message=f"공정 {seq} 종료",
                            context={"step": seq},
                        )
                    )
                db.add(js)
                job_standards.append(js)
        await db.flush()

        # ---------- 7. Job workers (배정) ----------
        # 각 company_worker 를 같은 회사의 첫 job_standard 에 배정
        std_by_company: dict[int, JobStandard] = {}
        for js in job_standards:
            std_by_company.setdefault(js.company_id, js)
        std_by_id: dict[int, JobStandard] = {js.id: js for js in job_standards}
        job_workers: list[JobWorker] = []
        for cw in company_workers:
            js = std_by_company.get(cw.company_id)
            if js is None:
                continue
            jw = JobWorker(company_worker_id=cw.id, job_standard_id=js.id, use_flag=True)
            db.add(jw)
            job_workers.append(jw)
        await db.flush()

        # ---------- 8. Work schedules + records (최근 5일) ----------
        # charger_by_company 는 위(3단계)에서 company_chargers.id 로 구성됨.
        # job_worker → company_id 역참조
        cw_company = {cw.id: cw.company_id for cw in company_workers}
        today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        for jw in job_workers:
            comp_id = cw_company.get(jw.company_worker_id)
            for d in range(5):  # 최근 5일
                day = today - timedelta(days=d)
                start_at = day.replace(hour=9)
                end_at = day.replace(hour=18) if d > 0 else None  # 오늘 건은 진행중(open)
                sch = WorkSchedule(
                    job_worker_id=jw.id,
                    company_charger_id=charger_by_company.get(comp_id),
                    job_standard_id=jw.job_standard_id,
                    start_at=start_at,
                    end_at=end_at,
                    make_work_doc=True,
                    work_doc_path=None,
                )
                db.add(sch)
                await db.flush()
                # 근무 기록 — 완료된 날만 ON/WORK/REST/OFF, 오늘 건은 ON/WORK open
                if end_at is not None:
                    db.add_all(
                        [
                            WorkRecord(
                                work_schedule_id=sch.id,
                                type=WorkRecordType.ON,
                                start_at=start_at,
                                end_at=start_at + timedelta(minutes=5),
                            ),
                            WorkRecord(
                                work_schedule_id=sch.id,
                                type=WorkRecordType.WORK,
                                start_at=start_at + timedelta(minutes=5),
                                end_at=start_at + timedelta(hours=4),
                            ),
                            WorkRecord(
                                work_schedule_id=sch.id,
                                type=WorkRecordType.REST,
                                start_at=start_at + timedelta(hours=4),
                                end_at=start_at + timedelta(hours=5),
                            ),
                            WorkRecord(
                                work_schedule_id=sch.id,
                                type=WorkRecordType.WORK,
                                start_at=start_at + timedelta(hours=5),
                                end_at=end_at,
                            ),
                            WorkRecord(
                                work_schedule_id=sch.id,
                                type=WorkRecordType.OFF,
                                start_at=end_at,
                                end_at=end_at,
                            ),
                        ]
                    )
                else:
                    db.add_all(
                        [
                            WorkRecord(
                                work_schedule_id=sch.id,
                                type=WorkRecordType.ON,
                                start_at=start_at,
                                end_at=start_at + timedelta(minutes=5),
                            ),
                            WorkRecord(
                                work_schedule_id=sch.id,
                                type=WorkRecordType.WORK,
                                start_at=start_at + timedelta(minutes=5),
                                end_at=None,
                            ),
                        ]
                    )
                # 프로세스 기록 1건 (해당 표준의 첫 공정)
                first_proc = next(
                    (p for p in std_by_id[jw.job_standard_id].processes if p.sequence == 1),
                    None,
                )
                if first_proc is not None and first_proc.id is not None:
                    db.add(
                        WorkProcessRecord(
                            work_schedule_id=sch.id,
                            job_process_id=first_proc.id,
                            type="PROCESS",
                            start_at=start_at + timedelta(minutes=10),
                            end_at=(start_at + timedelta(minutes=40)) if end_at else None,
                            start_condition=1,
                            end_condition=1 if end_at else None,
                            start_answer="정상",
                            process={"note": "데모"},
                        )
                    )

        # ---------- 9. Document types + documents + logs ----------
        doc_types = [
            CaregiverDocumentType(
                name="신분증",
                description="신분 확인용",
                request_on=CaregiverDocumentRequestOn.REGISTER,
            ),
            CaregiverDocumentType(
                name="자격증",
                description="요양보호사 자격증",
                request_on=CaregiverDocumentRequestOn.NOTHING,
            ),
            CaregiverDocumentType(
                name="계약서",
                description="근로 계약서",
                request_on=CaregiverDocumentRequestOn.NOTHING,
            ),
        ]
        db.add_all(doc_types)
        await db.flush()
        # cg1 의 첫 relation 에 문서 2개
        first_rel = (
            await db.execute(text("SELECT id FROM caregiver_worker_relations ORDER BY id LIMIT 1"))
        ).scalar_one()
        d1 = CaregiverDocument(
            relation_id=first_rel,
            type_id=doc_types[0].id,
            filename="caregiver-documents/demo/id-card.pdf",
            file_size=204800,
            content_type="application/pdf",
            created_by=caregiver_user.id,
            updated_by=caregiver_user.id,
        )
        d2 = CaregiverDocument(
            relation_id=first_rel,
            type_id=doc_types[1].id,
            filename="caregiver-documents/demo/license.jpg",
            file_size=512000,
            content_type="image/jpeg",
            created_by=caregiver_user.id,
            updated_by=caregiver_user.id,
        )
        db.add_all([d1, d2])
        await db.flush()
        db.add_all(
            [
                CaregiverDocumentLog(
                    document_id=d1.id,
                    user_id=caregiver_user.id,
                    action_type=CaregiverDocumentActionType.UPLOAD,
                ),
                CaregiverDocumentLog(
                    document_id=d2.id,
                    user_id=caregiver_user.id,
                    action_type=CaregiverDocumentActionType.UPLOAD,
                ),
            ]
        )

        # ---------- 10. Boards ----------
        db.add_all(
            [
                CaregiverBoard(
                    author_id=admin.id,
                    title="공지: 5월 근무일지 제출 안내",
                    body="이번 달 근무일지는 말일까지 제출해 주세요.",
                    is_published=True,
                    created_by=admin.id,
                    updated_by=admin.id,
                ),
                CaregiverBoard(
                    author_id=admin.id,
                    title="시스템 점검 안내",
                    body="주말 새벽 정기 점검이 있습니다.",
                    is_published=True,
                    created_by=admin.id,
                    updated_by=admin.id,
                ),
                CaregiverBoard(
                    author_id=caregiver_user.id,
                    title="질문있어요",
                    body="자격증은 어디에 올리나요?",
                    is_published=True,
                    created_by=caregiver_user.id,
                    updated_by=caregiver_user.id,
                ),
                CaregiverBoard(
                    author_id=admin.id,
                    title="(임시저장) 미발행 글",
                    body="아직 발행 전입니다.",
                    is_published=False,
                    created_by=admin.id,
                    updated_by=admin.id,
                ),
            ]
        )

        # ---------- 11. Kiosks ----------
        _kiosk_status = ["IN_USE", "FULL", "OFFLINE"]
        for i, comp in enumerate(companies):
            db.add(
                CompanyKiosk(
                    company_id=comp.id,
                    kiosk_id=f"KIOSK-{i + 1:03d}",
                    name=f"{comp.name} 키오스크",
                    location=f"{comp.location} 1층 로비",
                    capacity=10,
                    current_count=[3, 10, 0][i % 3],
                    status=_kiosk_status[i % 3],
                    assignee=["김담당", "이담당", None][i % 3],
                    last_reported_at=today.replace(hour=8, minute=30),
                )
            )

        # ---------- 12. Option variables ----------
        db.add_all(
            [
                OptionVariable(name="journal.autoemail", for_what="document", value="true"),
                OptionVariable(name="kiosk.idle_timeout_sec", for_what="kiosk", value="120"),
                OptionVariable(name="app.maintenance", for_what="system", value="false"),
            ]
        )

        # ---------- 13. Leaves (휴가) ----------
        # challenged[i] 의 company 는 company_workers 라운드로빈과 동일 (idx % len(companies))
        _leave_types = [LeaveType.ANNUAL, LeaveType.SICK, LeaveType.MONTHLY, LeaveType.OTHER]
        _leave_status = [LeaveStatus.APPROVED, LeaveStatus.PENDING, LeaveStatus.REJECTED]
        for i, cw in enumerate(challenged):
            comp = companies[i % len(companies)]
            start = (today - timedelta(days=3 * i + 1)).date()
            end = start + timedelta(days=i % 3)  # 1~3일
            db.add(
                Leave(
                    worker_id=cw.id,
                    company_id=comp.id,
                    type=_leave_types[i % len(_leave_types)],
                    start_date=start,
                    end_date=end,
                    days=(end - start).days + 1,
                    status=_leave_status[i % len(_leave_status)],
                    reason=["개인 사정", "병원 방문", "가족 행사", None][i % 4],
                    created_by=admin.id,
                )
            )

        await db.commit()

        # ---------- 요약 ----------
        summary = {}
        for t in _ALL_TABLES:
            cnt = (await db.execute(text(f"SELECT count(*) FROM {t}"))).scalar_one()
            summary[t] = cnt

    await engine.dispose()
    print("=== 데모 시드 완료 ===")
    for t, c in summary.items():
        print(f"  {t:30s} {c}")


if __name__ == "__main__":
    asyncio.run(seed())
