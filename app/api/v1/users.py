"""User management endpoints (admin)."""

from __future__ import annotations

from pydantic import BaseModel

from app.api.deps import RequireAdmin, UserSvc
from app.core.pagination import PagedQuery
from app.core.response import ApiResponse, PageResponse
from app.core.router import RootiRouter
from app.models import UserRole
from app.schemas.user import UserDTO

router = RootiRouter(tags=["user"])


class _EnabledRequest(BaseModel):
    enabled: bool


@router.get("", summary="List users (admin)")
async def list_users(
    svc: UserSvc,
    page: PagedQuery,
    _: RequireAdmin,
    keyword: str | None = None,
    role: UserRole | None = None,
) -> ApiResponse[PageResponse[UserDTO]]:
    p = await svc.search(keyword, role, page)
    return ApiResponse.ok(
        PageResponse.of(p.content, page=p.page, size=p.size, total_elements=p.total_elements)
    )


@router.patch("/{user_id}/enabled", summary="Enable/disable a user (admin)")
async def set_enabled(
    user_id: int, req: _EnabledRequest, svc: UserSvc, _: RequireAdmin
) -> ApiResponse[UserDTO]:
    return ApiResponse.ok(await svc.set_enabled(user_id, req.enabled))
