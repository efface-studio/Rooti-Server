"""Caregiver board endpoints."""

from __future__ import annotations

from app.api.deps import BoardSvc, CurrentUser
from app.core.pagination import PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.schemas.board import BoardResponse, BoardWriteRequest

router = RootiRouter(tags=["board"])


@router.get("")
async def list_boards(
    svc: BoardSvc, page: PagedQuery, _: CurrentUser, keyword: str | None = None
) -> ApiResponse[PageResponse[BoardResponse]]:
    p = await svc.list(keyword, page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.get("/{board_id}")
async def get_board(board_id: int, svc: BoardSvc, _: CurrentUser) -> ApiResponse[BoardResponse]:
    return ApiResponse.ok(await svc.get(board_id))


@router.post("")
async def create_board(
    req: BoardWriteRequest, me: CurrentUser, svc: BoardSvc
) -> ApiResponse[BoardResponse]:
    return ApiResponse.ok(await svc.create(me.user_id, req))


@router.patch("/{board_id}")
async def update_board(
    board_id: int, req: BoardWriteRequest, me: CurrentUser, svc: BoardSvc
) -> ApiResponse[BoardResponse]:
    return ApiResponse.ok(await svc.update(me.user_id, board_id, req))


@router.delete("/{board_id}")
async def delete_board(board_id: int, me: CurrentUser, svc: BoardSvc) -> ApiResponse[None]:
    await svc.delete(me.user_id, board_id)
    return ApiResponse.ok()
