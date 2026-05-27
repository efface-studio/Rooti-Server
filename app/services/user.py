"""User 조회/조작 헬퍼.

다른 도메인이 User 를 lifecycle 소유 없이 가져올 때 이걸 통해서 (Java UserQueryService 의도).
"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import UserNotFoundException
from app.models import User, UserRole


class UserService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def find_by_id(self, user_id: int) -> User | None:
        return await self.db.get(User, user_id)

    async def find_by_username(self, username: str) -> User | None:
        result = await self.db.execute(select(User).where(User.username == username))
        return result.scalar_one_or_none()

    async def find_by_email(self, email: str) -> User | None:
        result = await self.db.execute(select(User).where(User.email == email))
        return result.scalar_one_or_none()

    async def exists_by_username(self, username: str) -> bool:
        result = await self.db.execute(
            select(func.count()).select_from(User).where(User.username == username)
        )
        return (result.scalar_one() or 0) > 0

    async def exists_by_email(self, email: str) -> bool:
        result = await self.db.execute(
            select(func.count()).select_from(User).where(User.email == email)
        )
        return (result.scalar_one() or 0) > 0

    async def count_by_role(self, role: UserRole) -> int:
        result = await self.db.execute(
            select(func.count()).select_from(User).where(User.role == role)
        )
        return int(result.scalar_one() or 0)

    async def get_by_id(self, user_id: int) -> User:
        user = await self.find_by_id(user_id)
        if user is None:
            raise UserNotFoundException(user_id)
        return user

    async def get_by_username(self, username: str) -> User:
        user = await self.find_by_username(username)
        if user is None:
            raise UserNotFoundException(username=username)
        return user
