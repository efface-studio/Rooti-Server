"""Authentication 오케스트레이션.

Java 의 RefreshTokenStore / TokenIssuer / AuthService 를 한 파일에 모음:
  - 셋 다 단순한 클래스고
  - 서로 강하게 결합돼 있어 분리해도 import 만 늘어남
  - FastAPI 에서는 한 파일 1 모듈이 가독성 더 좋음

Caregiver self-signup 과 FCM 토큰 sync 는 caregiver/worker 모델이 이미 있으니
지금 시점에서 활성화. (auth 가 caregiver/worker 도메인 service 가 아니라 모델만
참조하므로 import 사이클 없음.)
"""

from __future__ import annotations

import redis.asyncio as redis_lib
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import (
    AuthAccountDisabledException,
    AuthInvalidCredentialsException,
    AuthRefreshNotFoundException,
    UserEmailDuplicatedException,
    UserNotFoundException,
    UserUsernameDuplicatedException,
)
from app.core.security import (
    PrincipalDetails,
    access_ttl_seconds,
    create_access_token,
    create_refresh_token,
    hash_password,
    parse_token,
    refresh_ttl_seconds,
    verify_password,
)
from app.models import (
    Caregiver,
    ChallengedWorker,
    User,
    UserRole,
)
from app.schemas.auth import (
    CaregiverSignupRequest,
    LoginRequest,
    MeResponse,
    RefreshRequest,
    TokenResponse,
    UpdateMeRequest,
)


# =============================================================================
#  Refresh token store (Redis-backed)
# =============================================================================
class RefreshTokenStore:
    """rooti:refresh:<userId> 키 1개로 한 사용자당 활성 refresh 1개만 유지."""

    _KEY_PREFIX = "rooti:refresh:"

    def __init__(self, client: redis_lib.Redis) -> None:
        self.client = client

    @classmethod
    def _key(cls, user_id: int) -> str:
        return f"{cls._KEY_PREFIX}{user_id}"

    async def save(self, user_id: int, token: str, ttl_seconds: int) -> None:
        await self.client.set(self._key(user_id), token, ex=ttl_seconds)

    async def find(self, user_id: int) -> str | None:
        return await self.client.get(self._key(user_id))

    async def remove(self, user_id: int) -> None:
        await self.client.delete(self._key(user_id))

    async def matches(self, user_id: int, token: str) -> bool:
        stored = await self.find(user_id)
        return stored is not None and stored == token


# =============================================================================
#  Token issuer
# =============================================================================
async def issue_token_pair(user: User, store: RefreshTokenStore) -> TokenResponse:
    roles = [user.role.value]
    access = create_access_token(user.id, user.username, roles)
    refresh = create_refresh_token(user.id, user.username, roles)
    await store.save(user.id, refresh, refresh_ttl_seconds())
    return TokenResponse(
        access_token=access,
        refresh_token=refresh,
        access_ttl_seconds=access_ttl_seconds(),
        refresh_ttl_seconds=refresh_ttl_seconds(),
    )


# =============================================================================
#  Auth service
# =============================================================================
class AuthService:
    def __init__(self, db: AsyncSession, store: RefreshTokenStore) -> None:
        self.db = db
        self.store = store

    # ---------- Login ----------
    async def login(self, req: LoginRequest) -> TokenResponse:
        user = await self._fetch_by_username(req.username)
        if user is None:
            raise AuthInvalidCredentialsException()
        if not user.enabled:
            raise AuthAccountDisabledException()
        if not verify_password(req.password, user.password_hash):
            raise AuthInvalidCredentialsException()

        if req.fcm_token and req.fcm_token.strip():
            await self._sync_fcm(user, req.fcm_token.strip())

        user.mark_logged_in()
        return await issue_token_pair(user, self.store)

    # ---------- Refresh / Logout ----------
    async def refresh(self, req: RefreshRequest) -> TokenResponse:
        """Refresh-token 회전 + 재사용 탐지.

        OWASP 권장:
        1) 매 refresh 마다 새 token pair 발급 → 이전 refresh 즉시 무효 (store.save 가 덮어씀)
        2) 재사용 탐지: 서명 유효한 REFRESH 가 들어왔는데 store 와 매칭 안 되면
           이미 한 번 쓰인 token 의 재사용 (또는 탈취 후 늦은 사용) — 같은 user 의
           모든 refresh 를 폐기해 전 디바이스 강제 로그아웃.
        """
        import logging

        payload = parse_token(req.refresh_token, expected_type="REFRESH")
        if not await self.store.matches(payload.user_id, req.refresh_token):
            # 재사용 의심: 안전을 위해 해당 사용자의 모든 refresh 무효화.
            await self.store.remove(payload.user_id)
            logging.getLogger(__name__).warning(
                "refresh-token reuse detected user_id=%s — all sessions revoked",
                payload.user_id,
            )
            raise AuthRefreshNotFoundException()
        user = await self.db.get(User, payload.user_id)
        if user is None:
            raise UserNotFoundException(payload.user_id)
        if not user.enabled:
            # 비활성화된 계정의 토큰은 refresh 불가 (관리자가 비활성화한 후에도 토큰 살아있는 케이스 차단)
            await self.store.remove(payload.user_id)
            raise AuthRefreshNotFoundException()
        return await issue_token_pair(user, self.store)

    async def logout(self, user_id: int) -> None:
        await self.store.remove(user_id)

    async def change_password(self, user_id: int, current: str, new_password: str) -> None:
        """본인 비밀번호 변경. 현재 비번 검증 후 교체 + 모든 refresh 폐기(재로그인 유도)."""
        user = await self.db.get(User, user_id)
        if user is None:
            raise UserNotFoundException(user_id)
        if not verify_password(current, user.password_hash):
            raise AuthInvalidCredentialsException()
        user.change_password(hash_password(new_password))
        await self.store.remove(user_id)

    # ---------- Signup (caregiver) ----------
    async def signup_as_caregiver(self, req: CaregiverSignupRequest) -> TokenResponse:
        if await self._exists_username(req.username):
            raise UserUsernameDuplicatedException(req.username)
        if await self._exists_email(req.email):
            raise UserEmailDuplicatedException(req.email)

        user = User(
            username=req.username,
            email=req.email,
            password_hash=hash_password(req.password),
            name=req.name,
            phone_number=req.phone_number,
            role=UserRole.CAREGIVER,
            enabled=True,
        )
        self.db.add(user)
        await self.db.flush()  # user.id 확보

        caregiver = Caregiver(user_id=user.id)
        self.db.add(caregiver)
        await self.db.flush()

        return await issue_token_pair(user, self.store)

    # ---------- Me ----------
    async def me(self, principal: PrincipalDetails) -> MeResponse:
        user = await self.db.get(User, principal.user_id)
        if user is None:
            raise UserNotFoundException(principal.user_id)
        return MeResponse(
            id=user.id,
            username=user.username,
            email=user.email,
            name=user.name,
            phone_number=user.phone_number,
            role=user.role,
        )

    async def update_profile(self, user_id: int, req: UpdateMeRequest) -> MeResponse:
        """본인 프로필(name/email/phone) 수정. 보낸 필드만 갱신, 이메일 중복 차단."""
        user = await self.db.get(User, user_id)
        if user is None:
            raise UserNotFoundException(user_id)
        if req.email is not None and req.email != user.email:
            dup = (
                await self.db.execute(
                    select(User.id).where(User.email == req.email, User.id != user_id)
                )
            ).first()
            if dup is not None:
                raise UserEmailDuplicatedException(req.email)
            user.email = req.email
        if req.phone_number is not None:
            user.phone_number = req.phone_number
        user.name = req.name
        await self.db.flush()
        return MeResponse(
            id=user.id,
            username=user.username,
            email=user.email,
            name=user.name,
            phone_number=user.phone_number,
            role=user.role,
        )

    # ---------- private helpers ----------
    async def _fetch_by_username(self, username: str) -> User | None:
        result = await self.db.execute(select(User).where(User.username == username))
        return result.scalar_one_or_none()

    async def _exists_username(self, username: str) -> bool:
        result = await self.db.execute(select(User.id).where(User.username == username))
        return result.first() is not None

    async def _exists_email(self, email: str) -> bool:
        result = await self.db.execute(select(User.id).where(User.email == email))
        return result.first() is not None

    async def _sync_fcm(self, user: User, fcm_token: str) -> None:
        """현재는 WORKER 만 푸시 알림 대상. caregiver/charger 는 별도 매핑 없음 → no-op."""
        if user.role != UserRole.WORKER:
            return
        result = await self.db.execute(
            select(ChallengedWorker).where(ChallengedWorker.user_id == user.id)
        )
        worker = result.scalar_one_or_none()
        if worker is not None:
            worker.update_fcm_token(fcm_token)
