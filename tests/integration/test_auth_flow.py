"""실 DB 로 login → me 시나리오 검증."""

from __future__ import annotations

import pytest
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import hash_password
from app.models import User, UserRole


@pytest.mark.asyncio
async def test_login_returns_token_and_me_uses_it(
    live_client: AsyncClient, db: AsyncSession
) -> None:
    # 사용자 직접 INSERT
    user = User(
        username="alice_int",
        email="alice@int.test",
        password_hash=hash_password("hunter2!"),
        name="Alice Int",
        role=UserRole.ADMIN,
        enabled=True,
    )
    db.add(user)
    await db.commit()

    # 1. 로그인
    resp = await live_client.post(
        "/api/v1/auth/login",
        json={"username": "alice_int", "password": "hunter2!"},
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["success"] is True
    token = body["data"]["accessToken"]
    assert token

    # 2. /me — 받은 토큰으로 인증
    resp = await live_client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200, resp.text
    me = resp.json()["data"]
    assert me["username"] == "alice_int"
    assert me["role"] == "ADMIN"


@pytest.mark.asyncio
async def test_login_wrong_password_returns_401_problem_json(
    live_client: AsyncClient, db: AsyncSession
) -> None:
    user = User(
        username="bob_int",
        password_hash=hash_password("right"),
        name="Bob",
        role=UserRole.WORKER,
        enabled=True,
    )
    db.add(user)
    await db.commit()

    resp = await live_client.post(
        "/api/v1/auth/login",
        json={"username": "bob_int", "password": "wrong"},
    )
    assert resp.status_code == 401
    assert resp.headers["content-type"].startswith("application/problem+json")
    assert resp.json()["code"] == "AUTH_INVALID_CREDENTIALS"


@pytest.mark.asyncio
async def test_caregiver_self_signup_then_login(live_client: AsyncClient) -> None:
    resp = await live_client.post(
        "/api/v1/auth/caregivers/signup",
        json={
            # `.test` 은 RFC 2606 reserved 로 email-validator 가 거부 — example.com 사용.
            "username": "newcaregiver",
            "email": "new@example.com",
            "password": "hunter2!supersafe",
            "name": "신규",
            "phoneNumber": "010-1234-5678",
        },
    )
    assert resp.status_code == 200, resp.text
    token = resp.json()["data"]["accessToken"]

    resp = await live_client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["data"]["role"] == "CAREGIVER"
