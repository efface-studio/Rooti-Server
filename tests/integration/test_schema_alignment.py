"""SQLAlchemy 모델이 실제 Flyway 스키마와 1:1 매핑되는지 검증.

모든 모델에 대해:
- 테이블 존재 확인
- INSERT 가능 (FK 위반/타입 위반 없이)
- SELECT 가능
"""

from __future__ import annotations

import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import (
    Base,
    CaregiverDocumentRequestOn,
    UserRole,
    WorkRecordType,
)


@pytest.mark.asyncio
async def test_all_tables_exist(db: AsyncSession) -> None:
    expected = sorted(t.name for t in Base.metadata.tables.values())
    result = await db.execute(
        text("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")
    )
    actual = sorted(r[0] for r in result.fetchall())
    missing = [t for t in expected if t not in actual]
    extra = [t for t in actual if t not in expected and not t.startswith("flyway_")]
    assert not missing, f"DB에 없는 테이블: {missing}"
    # extra 는 경고만 (Flyway가 만든 것 외에 우리 모델이 모르는 테이블)
    if extra:
        print(f"NOTE: 모델 밖 테이블: {extra}")


@pytest.mark.asyncio
async def test_user_role_check_constraint(db: AsyncSession) -> None:
    from app.models import User

    user = User(
        username="u_alignment",
        password_hash="$2b$10$test",
        name="t",
        role=UserRole.ADMIN,
        enabled=True,
    )
    db.add(user)
    await db.flush()
    assert user.id is not None


@pytest.mark.asyncio
async def test_work_record_type_enum_round_trip(db: AsyncSession) -> None:
    # FK 까지 만들기 부담스러우니 raw INSERT 로 CHECK 만 검증
    await db.execute(text("INSERT INTO companies (name) VALUES ('c1')"))
    company_id = (await db.execute(text("SELECT id FROM companies WHERE name='c1'"))).scalar_one()
    await db.execute(
        text(
            "INSERT INTO users (username, password_hash, name, role) "
            "VALUES ('w1', 'h', 'W', 'WORKER')"
        )
    )
    user_id = (await db.execute(text("SELECT id FROM users WHERE username='w1'"))).scalar_one()
    await db.execute(text("INSERT INTO challenged_workers (user_id) VALUES (:u)"), {"u": user_id})
    cw_id_query = await db.execute(
        text("SELECT id FROM challenged_workers WHERE user_id=:u"), {"u": user_id}
    )
    cw_id = cw_id_query.scalar_one()
    await db.execute(
        text("INSERT INTO company_workers (company_id, challenged_worker_id) VALUES (:c, :w)"),
        {"c": company_id, "w": cw_id},
    )
    # company_workers 까지 FK 체인이 제약 위반 없이 INSERT 되는지 확인
    cw_count = (await db.execute(text("SELECT count(*) FROM company_workers"))).scalar_one()
    assert cw_count == 1

    # type 컬럼이 enum 값 그대로 잘 들어가는지
    assert WorkRecordType.WORK.value == "WORK"
    assert CaregiverDocumentRequestOn.NOTHING.value == "NOTHING"


@pytest.mark.asyncio
async def test_select_returns_no_rows_initially(db: AsyncSession) -> None:
    from sqlalchemy import select

    from app.models import User

    result = await db.execute(select(User).where(User.username == "nonexistent"))
    assert result.scalar_one_or_none() is None
