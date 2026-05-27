"""Auth 단위 테스트 — DB/Redis 없이 검증."""

from __future__ import annotations

from datetime import timedelta
from unittest.mock import AsyncMock, MagicMock

import jwt as pyjwt
import pytest
from freezegun import freeze_time
from httpx import AsyncClient

from app.core.config import get_settings
from app.core.exceptions import (
    AuthAccountDisabledException,
    AuthInvalidCredentialsException,
    AuthRefreshNotFoundException,
    AuthTokenExpiredException,
    AuthTokenInvalidException,
)
from app.core.security import (
    create_access_token,
    create_refresh_token,
    hash_password,
    parse_token,
    verify_password,
)
from app.models import User, UserRole
from app.schemas.auth import (
    LoginRequest,
    MeResponse,
    RefreshRequest,
    TokenResponse,
)
from app.services.auth import AuthService, RefreshTokenStore


# ---------- Password ----------
def test_password_hash_and_verify() -> None:
    hashed = hash_password("hunter2!")
    assert hashed != "hunter2!"
    assert verify_password("hunter2!", hashed) is True
    assert verify_password("wrong", hashed) is False


def test_password_verify_handles_garbage() -> None:
    assert verify_password("anything", "not-a-bcrypt-hash") is False


# ---------- JWT ----------
def test_access_token_round_trip() -> None:
    token = create_access_token(user_id=42, username="alice", roles=["ADMIN"])
    payload = parse_token(token, expected_type="ACCESS")
    assert payload.user_id == 42
    assert payload.username == "alice"
    assert payload.roles == ["ADMIN"]
    assert payload.token_type == "ACCESS"


def test_refresh_token_round_trip() -> None:
    token = create_refresh_token(user_id=7, username="bob", roles=["WORKER", "CAREGIVER"])
    payload = parse_token(token, expected_type="REFRESH")
    assert payload.user_id == 7
    assert payload.token_type == "REFRESH"
    assert payload.roles == ["WORKER", "CAREGIVER"]


def test_token_type_mismatch_rejected() -> None:
    access = create_access_token(user_id=1, username="u", roles=[])
    with pytest.raises(AuthTokenInvalidException):
        parse_token(access, expected_type="REFRESH")


def test_garbage_token_rejected() -> None:
    with pytest.raises(AuthTokenInvalidException):
        parse_token("not.a.jwt")


def test_expired_token_rejected() -> None:
    with freeze_time("2026-01-01 12:00:00") as frozen:
        token = create_access_token(user_id=1, username="u", roles=[])
        frozen.tick(delta=timedelta(hours=2))
        with pytest.raises(AuthTokenExpiredException):
            parse_token(token, expected_type="ACCESS")


def test_jwt_payload_has_java_compatible_claims() -> None:
    token = create_access_token(user_id=99, username="zoe", roles=["ADMIN"])
    settings = get_settings()
    raw = pyjwt.decode(
        token,
        settings.jwt_secret.get_secret_value(),
        algorithms=[settings.jwt_algorithm],
        issuer=settings.jwt_issuer,
    )
    assert raw["sub"] == "99"
    assert raw["usn"] == "zoe"
    assert raw["roles"] == ["ADMIN"]
    assert raw["typ"] == "ACCESS"
    assert raw["iss"] == "rooti"


# ---------- Schemas ----------
def test_login_request_accepts_camel_case_fcm_token() -> None:
    req = LoginRequest.model_validate({"username": "u", "password": "p", "fcmToken": "tok"})
    assert req.fcm_token == "tok"


def test_token_response_serializes_to_camel_case() -> None:
    resp = TokenResponse(
        access_token="A",
        refresh_token="R",
        access_ttl_seconds=900,
        refresh_ttl_seconds=1209600,
    )
    dumped = resp.model_dump(by_alias=True)
    assert set(dumped.keys()) == {
        "accessToken",
        "refreshToken",
        "accessTtlSeconds",
        "refreshTtlSeconds",
        "tokenType",
    }


def test_me_response_camel_case_phone_number() -> None:
    me = MeResponse(
        id=1, username="u", email=None, name="N", phone_number="010", role=UserRole.ADMIN
    )
    dumped = me.model_dump(by_alias=True, exclude_none=True)
    assert "phoneNumber" in dumped
    assert "phone_number" not in dumped


# ---------- AuthService (mocked db/store) ----------
def _user(**overrides: object) -> User:
    u = User(
        username="alice",
        email="alice@example.com",
        password_hash=hash_password("hunter2!"),
        name="Alice",
        phone_number="010-0000-0000",
        role=UserRole.ADMIN,
        enabled=True,
    )
    u.id = 1
    for k, v in overrides.items():
        setattr(u, k, v)
    return u


def _make_service(user: User | None) -> tuple[AuthService, MagicMock, AsyncMock]:
    """user.find_by_username / db.get(User, id) 둘 다 user 반환하도록 모킹."""
    db = MagicMock()
    db.execute = AsyncMock()
    # find_by_username 은 select(User).where(...) → execute → scalar_one_or_none
    scalar_result = MagicMock()
    scalar_result.scalar_one_or_none.return_value = user
    scalar_result.first.return_value = None  # _exists_* 용
    db.execute.return_value = scalar_result
    db.get = AsyncMock(return_value=user)
    db.add = MagicMock()
    db.flush = AsyncMock()

    store = AsyncMock(spec=RefreshTokenStore)
    svc = AuthService(db, store)
    return svc, db, store


@pytest.mark.asyncio
async def test_login_unknown_user_raises_invalid_credentials() -> None:
    svc, _, _ = _make_service(None)
    with pytest.raises(AuthInvalidCredentialsException):
        await svc.login(LoginRequest(username="ghost", password="x"))


@pytest.mark.asyncio
async def test_login_disabled_user_raises_account_disabled() -> None:
    svc, _, _ = _make_service(_user(enabled=False))
    with pytest.raises(AuthAccountDisabledException):
        await svc.login(LoginRequest(username="alice", password="hunter2!"))


@pytest.mark.asyncio
async def test_login_wrong_password_raises_invalid_credentials() -> None:
    svc, _, _ = _make_service(_user())
    with pytest.raises(AuthInvalidCredentialsException):
        await svc.login(LoginRequest(username="alice", password="wrong"))


@pytest.mark.asyncio
async def test_login_success_issues_token_and_marks_login() -> None:
    user = _user()
    svc, _, store = _make_service(user)
    resp = await svc.login(LoginRequest(username="alice", password="hunter2!"))
    assert resp.token_type == "Bearer"
    assert resp.access_token and resp.refresh_token
    store.save.assert_awaited_once()
    saved_user_id, saved_token, _ttl = store.save.await_args.args
    assert saved_user_id == 1
    assert saved_token == resp.refresh_token
    assert user.last_login_at is not None


@pytest.mark.asyncio
async def test_refresh_with_unknown_token_raises_refresh_not_found() -> None:
    user = _user()
    svc, _, store = _make_service(user)
    store.matches = AsyncMock(return_value=False)
    valid = create_refresh_token(user_id=user.id, username=user.username, roles=[user.role.value])
    with pytest.raises(AuthRefreshNotFoundException):
        await svc.refresh(RefreshRequest(refresh_token=valid))


@pytest.mark.asyncio
async def test_refresh_with_access_token_rejected() -> None:
    user = _user()
    svc, _, _ = _make_service(user)
    access = create_access_token(user_id=user.id, username=user.username, roles=[user.role.value])
    with pytest.raises(AuthTokenInvalidException):
        await svc.refresh(RefreshRequest(refresh_token=access))


@pytest.mark.asyncio
async def test_logout_removes_refresh_token() -> None:
    svc, _, store = _make_service(_user())
    await svc.logout(42)
    store.remove.assert_awaited_once_with(42)


# ---------- OpenAPI 노출 ----------
@pytest.mark.asyncio
async def test_auth_endpoints_in_openapi(client: AsyncClient) -> None:
    resp = await client.get("/v3/api-docs")
    assert resp.status_code == 200
    paths = resp.json()["paths"]
    for p in [
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/caregivers/signup",
        "/api/v1/auth/me",
    ]:
        assert p in paths, f"missing {p}"


@pytest.mark.asyncio
async def test_me_requires_bearer_token(client: AsyncClient) -> None:
    resp = await client.get("/api/v1/auth/me")
    assert resp.status_code == 401
    assert resp.headers["content-type"].startswith("application/problem+json")
    body = resp.json()
    assert body["code"] == "AUTH_TOKEN_INVALID"
    assert body["status"] == 401
    assert body["instance"] == "/api/v1/auth/me"


@pytest.mark.asyncio
async def test_validation_error_returns_problem_detail(client: AsyncClient) -> None:
    resp = await client.post("/api/v1/auth/login", json={})
    assert resp.status_code == 400
    assert resp.headers["content-type"].startswith("application/problem+json")
    body = resp.json()
    assert body["code"] == "INVALID_INPUT"
    assert "fields" in body
    assert "username" in body["fields"]
    assert "password" in body["fields"]
