"""Caregiver board service — HTML 본문 sanitize 후 저장."""

from __future__ import annotations

from html import escape

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import AuthForbiddenException, BusinessException, ErrorCode
from app.core.pagination import Page, PageParams
from app.models import CaregiverBoard, User
from app.schemas.board import BoardResponse, BoardWriteRequest


# 최소 sanitize — 외부 도구(bleach) 추가 전까지는 HTML 엔티티 escape.
# 마크다운/HTML 허용 화이트리스트가 필요하면 bleach 의존성 추가 후 교체.
def _sanitize(body: str) -> str:
    return escape(body)


class CaregiverBoardService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db

    async def _get_or_throw(self, board_id: int) -> CaregiverBoard:
        b = await self.db.get(CaregiverBoard, board_id)
        if b is None:
            raise BusinessException(ErrorCode.BOARD_NOT_FOUND)
        return b

    async def _author_name(self, author_id: int) -> str | None:
        user = await self.db.get(User, author_id)
        return user.name if user else None

    async def get(self, board_id: int) -> BoardResponse:
        b = await self._get_or_throw(board_id)
        return _to_response(b, await self._author_name(b.author_id))

    async def list(self, keyword: str | None, params: PageParams) -> Page[BoardResponse]:
        base = select(CaregiverBoard).where(CaregiverBoard.is_published.is_(True))
        if keyword:
            like = f"%{keyword.strip()}%"
            base = base.where(or_(CaregiverBoard.title.ilike(like), CaregiverBoard.body.ilike(like)))

        total = int(
            (await self.db.execute(select(func.count()).select_from(base.subquery()))).scalar_one()
            or 0
        )
        rows = (
            (
                await self.db.execute(
                    base.order_by(CaregiverBoard.created_at.desc())
                    .offset(params.offset)
                    .limit(params.limit)
                )
            )
            .scalars()
            .all()
        )
        content = [_to_response(r, await self._author_name(r.author_id)) for r in rows]
        return Page.build(content, params=params, total_elements=total)

    async def create(self, author_user_id: int, req: BoardWriteRequest) -> BoardResponse:
        published = True if req.published is None else req.published
        b = CaregiverBoard(
            author_id=author_user_id,
            title=req.title,
            body=_sanitize(req.body),
            is_published=published,
            created_by=author_user_id,
            updated_by=author_user_id,
        )
        self.db.add(b)
        await self.db.flush()
        return _to_response(b, await self._author_name(author_user_id))

    async def update(
        self, author_user_id: int, board_id: int, req: BoardWriteRequest
    ) -> BoardResponse:
        b = await self._get_or_throw(board_id)
        if b.author_id != author_user_id:
            raise AuthForbiddenException("not the author")
        b.title = req.title
        b.body = _sanitize(req.body)
        if req.published is not None:
            b.is_published = req.published
        b.updated_by = author_user_id
        return _to_response(b, await self._author_name(b.author_id))

    async def delete(self, author_user_id: int, board_id: int) -> None:
        b = await self._get_or_throw(board_id)
        if b.author_id != author_user_id:
            raise AuthForbiddenException("not the author")
        await self.db.delete(b)


def _to_response(b: CaregiverBoard, author_name: str | None) -> BoardResponse:
    return BoardResponse(
        id=b.id,
        title=b.title,
        body=b.body,
        author_name=author_name,
        author_id=b.author_id,
        published=b.is_published,
        created_at=b.created_at,
        updated_at=b.updated_at,
    )
