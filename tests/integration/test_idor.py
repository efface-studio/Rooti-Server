"""IDOR(권한 없는 자원 접근) 차단 검증.

실 DB 로 caregiver A 가 caregiver B 의 문서를 download 못 함을 확인.
"""

from __future__ import annotations

import pytest
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import create_access_token, hash_password
from app.models import (
    Caregiver,
    CaregiverDocument,
    CaregiverDocumentType,
    CaregiverWorkerRelation,
    ChallengedWorker,
    Company,
    CompanyCharger,
    User,
    UserRole,
)


async def _create_caregiver_with_relation(
    db: AsyncSession, *, username: str, email: str
) -> tuple[User, Caregiver, CaregiverWorkerRelation]:
    user = User(
        username=username,
        email=email,
        password_hash=hash_password("hunter2!"),
        name="cg",
        role=UserRole.CAREGIVER,
        enabled=True,
    )
    db.add(user)
    await db.flush()
    caregiver = Caregiver(user_id=user.id)
    db.add(caregiver)
    await db.flush()

    wuser = User(
        username=f"{username}_w",
        password_hash=hash_password("x"),
        name="w",
        role=UserRole.WORKER,
        enabled=True,
    )
    db.add(wuser)
    await db.flush()
    worker = ChallengedWorker(user_id=wuser.id)
    db.add(worker)
    await db.flush()

    relation = CaregiverWorkerRelation(caregiver_id=caregiver.id, challenged_worker_id=worker.id)
    db.add(relation)
    await db.flush()
    return user, caregiver, relation


@pytest.mark.asyncio
async def test_caregiver_cannot_access_another_caregivers_document(
    live_client: AsyncClient, db: AsyncSession
) -> None:
    a_user, _, a_rel = await _create_caregiver_with_relation(
        db, username="cgA_idor", email="a@example.com"
    )
    b_user, _, _ = await _create_caregiver_with_relation(
        db, username="cgB_idor", email="b@example.com"
    )
    dt = CaregiverDocumentType(name="신분증_idor")
    db.add(dt)
    await db.flush()
    doc = CaregiverDocument(relation_id=a_rel.id, type_id=dt.id, filename="x/y.pdf", file_size=10)
    db.add(doc)
    await db.commit()

    # B 가 A 문서 다운로드 시도 → 403
    b_token = create_access_token(b_user.id, b_user.username, [b_user.role.value])
    resp = await live_client.get(
        f"/api/v1/documents/{doc.id}/download",
        headers={"Authorization": f"Bearer {b_token}"},
    )
    assert resp.status_code == 403
    assert resp.headers["content-type"].startswith("application/problem+json")
    assert resp.json()["code"] == "AUTH_FORBIDDEN"


@pytest.mark.asyncio
async def test_worker_cannot_access_work_journal_pdf(
    live_client: AsyncClient, db: AsyncSession
) -> None:
    """WORKER role 은 /api/v1/work-journals/.../pdf 호출 불가 (403)."""
    user = User(
        username="worker_idor",
        password_hash=hash_password("x"),
        name="w",
        role=UserRole.WORKER,
        enabled=True,
    )
    db.add(user)
    await db.commit()

    token = create_access_token(user.id, user.username, [user.role.value])
    resp = await live_client.get(
        "/api/v1/work-journals/999/pdf",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 403
    assert resp.json()["code"] == "AUTH_FORBIDDEN"


# =============================================================================
#  멀티테넌트(회사) 스코프 — CHARGER 는 자기 회사 자원만, ADMIN 은 전체
# =============================================================================
async def _make_company(db: AsyncSession, name: str) -> Company:
    company = Company(name=name)
    db.add(company)
    await db.flush()
    return company


async def _make_charger(db: AsyncSession, *, username: str, company_id: int | None) -> User:
    """CHARGER 유저 생성. company_id 가 주어지면 company_chargers 매핑까지 만든다."""
    user = User(
        username=username,
        password_hash=hash_password("x"),
        name="charger",
        role=UserRole.CHARGER,
        enabled=True,
    )
    db.add(user)
    await db.flush()
    if company_id is not None:
        db.add(CompanyCharger(user_id=user.id, company_id=company_id))
        await db.flush()
    return user


def _bearer(user: User) -> dict[str, str]:
    token = create_access_token(user.id, user.username, [user.role.value])
    return {"Authorization": f"Bearer {token}"}


@pytest.mark.asyncio
async def test_charger_cannot_bulk_preview_other_company(
    live_client: AsyncClient, db: AsyncSession
) -> None:
    """CHARGER(A) 가 회사 B 의 일지 미리보기를 요청하면 403, 본인 회사 A 는 200."""
    company_a = await _make_company(db, "ScopeCoA_preview")
    company_b = await _make_company(db, "ScopeCoB_preview")
    charger = await _make_charger(db, username="scope_charger_prev", company_id=company_a.id)
    await db.commit()

    # 다른 회사(B) → 403
    resp_b = await live_client.get(
        "/api/v1/work-journals/bulk-preview",
        params={"companyId": company_b.id, "date": "2026-01-01"},
        headers=_bearer(charger),
    )
    assert resp_b.status_code == 403
    assert resp_b.json()["code"] == "AUTH_FORBIDDEN"

    # 본인 회사(A) → 200 (일정 0건이어도 회사 정보는 내려옴)
    resp_a = await live_client.get(
        "/api/v1/work-journals/bulk-preview",
        params={"companyId": company_a.id, "date": "2026-01-01"},
        headers=_bearer(charger),
    )
    assert resp_a.status_code == 200
    assert resp_a.json()["data"]["companyName"] == "ScopeCoA_preview"


@pytest.mark.asyncio
async def test_charger_cannot_filter_leaves_by_other_company(
    live_client: AsyncClient, db: AsyncSession
) -> None:
    """CHARGER(A) 가 leaves 를 회사 B 로 필터하면 403. 필터 없이는 자기 회사로 강제(200)."""
    company_a = await _make_company(db, "ScopeCoA_leaves")
    company_b = await _make_company(db, "ScopeCoB_leaves")
    charger = await _make_charger(db, username="scope_charger_leaves", company_id=company_a.id)
    await db.commit()

    resp_b = await live_client.get(
        "/api/v1/leaves",
        params={"companyId": company_b.id},
        headers=_bearer(charger),
    )
    assert resp_b.status_code == 403
    assert resp_b.json()["code"] == "AUTH_FORBIDDEN"

    resp_none = await live_client.get("/api/v1/leaves", headers=_bearer(charger))
    assert resp_none.status_code == 200


@pytest.mark.asyncio
async def test_charger_without_company_mapping_forbidden(
    live_client: AsyncClient, db: AsyncSession
) -> None:
    """company_chargers 매핑이 없는 CHARGER 는 스코프 해석 단계에서 403."""
    charger = await _make_charger(db, username="scope_charger_orphan", company_id=None)
    await db.commit()

    resp = await live_client.get("/api/v1/leaves", headers=_bearer(charger))
    assert resp.status_code == 403
    assert resp.json()["code"] == "AUTH_FORBIDDEN"


@pytest.mark.asyncio
async def test_admin_can_bulk_preview_any_company(
    live_client: AsyncClient, db: AsyncSession
) -> None:
    """ADMIN 은 스코프 None — 임의 회사의 일지 미리보기를 볼 수 있다."""
    company_b = await _make_company(db, "ScopeCoB_admin")
    admin = User(
        username="scope_admin",
        password_hash=hash_password("x"),
        name="admin",
        role=UserRole.ADMIN,
        enabled=True,
    )
    db.add(admin)
    await db.commit()

    resp = await live_client.get(
        "/api/v1/work-journals/bulk-preview",
        params={"companyId": company_b.id, "date": "2026-01-01"},
        headers=_bearer(admin),
    )
    assert resp.status_code == 200
    assert resp.json()["data"]["companyName"] == "ScopeCoB_admin"
