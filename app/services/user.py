"""User 조회/조작 헬퍼.

다른 도메인이 User 를 lifecycle 소유 없이 가져올 때 이걸 통해서 (Java UserQueryService 의도).
"""

from __future__ import annotations

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import UserNotFoundException
from app.core.pagination import Page, PageParams
from app.models import User, UserRole
from app.schemas.user import UserDTO


class UserService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    # ---------- Admin: 사용자 관리 ----------
    async def search(
        self, keyword: str | None, role: UserRole | None, params: PageParams
    ) -> Page[UserDTO]:
        base = select(User)
        if role is not None:
            base = base.where(User.role == role)
        if keyword:
            like = f"%{keyword.strip()}%"
            base = base.where(
                or_(User.name.ilike(like), User.username.ilike(like), User.email.ilike(like))
            )
        total = int(
            (await self.db.execute(select(func.count()).select_from(base.subquery()))).scalar_one()
            or 0
        )
        rows = (
            (
                await self.db.execute(
                    base.order_by(User.id).offset(params.offset).limit(params.limit)
                )
            )
            .scalars()
            .all()
        )
        return Page.build(
            [UserDTO.model_validate(u) for u in rows], params=params, total_elements=total
        )

    async def set_enabled(self, user_id: int, enabled: bool) -> UserDTO:
        user = await self.get_by_id(user_id)
        user.enabled = enabled
        return UserDTO.model_validate(user)

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
