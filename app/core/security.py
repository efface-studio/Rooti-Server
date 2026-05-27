"""JWT + 비밀번호 해싱 + 현재 사용자 dependency.

Java JwtTokenProvider / PrincipalDetails / @CurrentUser 등가물.

JWT 페이로드 (Java 와 호환 — 같은 토큰을 양쪽이 검증할 수 있어야 함):
  iss   : "rooti"
  sub   : userId (str)        ← Java 는 long.toString()
  iat   : epoch seconds
  exp   : epoch seconds
  usn   : username (str)
  roles : ["ADMIN"] 같은 list[str]
  typ   : "ACCESS" | "REFRESH"  ← 대문자
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Annotated, Any, Literal

import bcrypt
import jwt
from fastapi import Depends, Request, status
from fastapi.security import HTTPBearer

from app.core.config import Settings, get_settings
from app.core.exceptions import (
    AuthTokenExpiredException,
    AuthTokenInvalidException,
)
from app.core.time import KST

TokenType = Literal["ACCESS", "REFRESH"]

# Java BCryptPasswordEncoder(10) 와 동일한 cost. bcrypt 4.x 의 72 byte 제한은
# 입력을 잘라서 회피 (Spring Security 도 65byte 부터 무시되는 BCrypt 표준 동작 동일).
_BCRYPT_ROUNDS = 10
_BCRYPT_MAX_BYTES = 72


def _to_bcrypt_input(plain: str) -> bytes:
    raw = plain.encode("utf-8")
    return raw[:_BCRYPT_MAX_BYTES]


# =============================================================================
#  Password
# =============================================================================
def hash_password(plain: str) -> str:
    salt = bcrypt.gensalt(rounds=_BCRYPT_ROUNDS)
    return bcrypt.hashpw(_to_bcrypt_input(plain), salt).decode("ascii")


def verify_password(plain: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(_to_bcrypt_input(plain), hashed.encode("ascii"))
    except (ValueError, TypeError):
        return False


# =============================================================================
#  JWT
# =============================================================================
@dataclass(frozen=True)
class JwtPayload:
    """com.rooti.global.jwt.JwtPayload 등가."""

    user_id: int
    username: str
    roles: list[str] = field(default_factory=list)
    token_type: TokenType = "ACCESS"
    issued_at: int = 0
    expires_at: int = 0


def _now_epoch() -> int:
    return int(datetime.now(KST).timestamp())


def create_token(
    *,
    user_id: int,
    username: str,
    roles: list[str],
    token_type: TokenType,
    settings: Settings | None = None,
) -> str:
    settings = settings or get_settings()
    now = _now_epoch()
    ttl = (
        timedelta(minutes=settings.jwt_access_ttl_minutes)
        if token_type == "ACCESS"
        else timedelta(days=settings.jwt_refresh_ttl_days)
    )
    payload: dict[str, Any] = {
        "iss": settings.jwt_issuer,
        "sub": str(user_id),
        "iat": now,
        "exp": now + int(ttl.total_seconds()),
        "usn": username,
        "roles": roles,
        "typ": token_type,
    }
    return jwt.encode(
        payload,
        settings.jwt_secret.get_secret_value(),
        algorithm=settings.jwt_algorithm,
    )


def create_access_token(user_id: int, username: str, roles: list[str]) -> str:
    return create_token(
        user_id=user_id, username=username, roles=roles, token_type="ACCESS"
    )


def create_refresh_token(user_id: int, username: str, roles: list[str]) -> str:
    return create_token(
        user_id=user_id, username=username, roles=roles, token_type="REFRESH"
    )


def parse_token(token: str, *, expected_type: TokenType | None = None) -> JwtPayload:
    settings = get_settings()
    try:
        claims: dict[str, Any] = jwt.decode(
            token,
            settings.jwt_secret.get_secret_value(),
            algorithms=[settings.jwt_algorithm],
            issuer=settings.jwt_issuer,
            options={"require": ["exp", "iat", "sub", "typ"]},
        )
    except jwt.ExpiredSignatureError as e:
        raise AuthTokenExpiredException() from e
    except jwt.PyJWTError as e:
        raise AuthTokenInvalidException(str(e)) from e

    typ = claims.get("typ")
    if expected_type and typ != expected_type:
        raise AuthTokenInvalidException(
            f"Unexpected token type: expected {expected_type}, got {typ}"
        )

    try:
        user_id = int(claims["sub"])
    except (KeyError, TypeError, ValueError) as e:
        raise AuthTokenInvalidException("missing or invalid 'sub' claim") from e

    return JwtPayload(
        user_id=user_id,
        username=str(claims.get("usn", "")),
        roles=list(claims.get("roles", [])),
        token_type=typ if typ in ("ACCESS", "REFRESH") else "ACCESS",
        issued_at=int(claims.get("iat", 0)),
        expires_at=int(claims.get("exp", 0)),
    )


def access_ttl_seconds() -> int:
    return get_settings().jwt_access_ttl_minutes * 60


def refresh_ttl_seconds() -> int:
    return get_settings().jwt_refresh_ttl_days * 86400


# =============================================================================
#  PrincipalDetails — 현재 사용자 (Java 와 동명)
# =============================================================================
@dataclass(frozen=True)
class PrincipalDetails:
    user_id: int
    username: str
    roles: list[str]
    enabled: bool = True
    account_non_locked: bool = True

    @property
    def authorities(self) -> list[str]:
        """ROLE_ 접두사 자동 추가 (Spring Security authority 호환)."""
        return [r if r.startswith("ROLE_") else f"ROLE_{r}" for r in self.roles]

    def has_role(self, role: str) -> bool:
        bare = role.removeprefix("ROLE_")
        return any(r.removeprefix("ROLE_") == bare for r in self.roles)


# =============================================================================
#  FastAPI dependencies — @CurrentUser 등가
# =============================================================================
_bearer = HTTPBearer(auto_error=False, scheme_name="bearerAuth")


def _resolve_bearer_token(request: Request) -> str | None:
    """Authorization: Bearer <token> 또는 legacy 헤더/쿠키 fallback."""
    auth = request.headers.get("authorization") or request.headers.get("Authorization")
    if auth:
        prefix = "Bearer "
        if auth.startswith(prefix):
            return auth[len(prefix) :].strip()
    # Legacy mobile client (Django 시절)
    legacy_header = request.headers.get("accesstoken")
    if legacy_header:
        return legacy_header.strip()
    legacy_cookie = request.cookies.get("accessToken")
    if legacy_cookie:
        return legacy_cookie.strip()
    return None


async def current_user_optional(request: Request) -> PrincipalDetails | None:
    token = _resolve_bearer_token(request)
    if not token:
        return None
    payload = parse_token(token, expected_type="ACCESS")
    return PrincipalDetails(
        user_id=payload.user_id,
        username=payload.username,
        roles=payload.roles,
        enabled=True,
        account_non_locked=True,
    )


async def current_user(request: Request) -> PrincipalDetails:
    """@CurrentUser PrincipalDetails 등가. 미인증이면 401 (AUTH_TOKEN_INVALID)."""
    principal = await current_user_optional(request)
    if principal is None:
        raise AuthTokenInvalidException("missing bearer token")
    return principal


CurrentUser = Annotated[PrincipalDetails, Depends(current_user)]
CurrentUserOptional = Annotated[PrincipalDetails | None, Depends(current_user_optional)]


# =============================================================================
#  Backward-compat (스캐폴딩에서 썼던 이름 유지)
# =============================================================================
class JwtError(Exception):
    """기존 코드 호환용."""


def create_token_legacy(*, subject: str, token_type: Any, **_: Any) -> str:
    """deprecated — create_access_token/create_refresh_token 사용."""
    raise NotImplementedError("use create_access_token / create_refresh_token")
