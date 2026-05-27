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
    doc = CaregiverDocument(
        relation_id=a_rel.id, type_id=dt.id, filename="x/y.pdf", file_size=10
    )
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
