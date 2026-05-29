"""Auth endpoints — login / refresh / logout / signup / me.

slowapi `@limiter.limit` 데코레이터는 `request: Request` 파라미터를 요구하므로 명시 추가.
무차별 대입 방지: login/refresh/signup 은 5/min, 그 외 기본(200/min).
"""

from __future__ import annotations

from fastapi import Request

from app.api.deps import AuthSvc, CurrentUser
from app.core.rate_limit import limiter
from app.core.response import ApiResponse
from app.core.router import RootiRouter
from app.schemas.auth import (
    CaregiverSignupRequest,
    ChangePasswordRequest,
    LoginRequest,
    MeResponse,
    RefreshRequest,
    TokenResponse,
    UpdateMeRequest,
)

router = RootiRouter(tags=["auth"])


@router.post("/login", summary="Login (all roles)")
@limiter.limit("5/minute")
async def login(request: Request, req: LoginRequest, svc: AuthSvc) -> ApiResponse[TokenResponse]:
    return ApiResponse.ok(await svc.login(req))


@router.post("/refresh", summary="Exchange refresh token for a new access token")
@limiter.limit("10/minute")
async def refresh(
    request: Request, req: RefreshRequest, svc: AuthSvc
) -> ApiResponse[TokenResponse]:
    return ApiResponse.ok(await svc.refresh(req))


@router.post("/logout", summary="Logout (invalidates the refresh token)")
async def logout(me: CurrentUser, svc: AuthSvc) -> ApiResponse[None]:
    await svc.logout(me.user_id)
    return ApiResponse.ok()


@router.post("/caregivers/signup", summary="Self-signup for a caregiver account")
@limiter.limit("5/minute")
async def signup_caregiver(
    request: Request, req: CaregiverSignupRequest, svc: AuthSvc
) -> ApiResponse[TokenResponse]:
    return ApiResponse.ok(await svc.signup_as_caregiver(req))


@router.get("/me", summary="Current principal info")
async def me(principal: CurrentUser, svc: AuthSvc) -> ApiResponse[MeResponse]:
    return ApiResponse.ok(await svc.me(principal))


@router.post("/me/password", summary="Change own password")
async def change_password(
    req: ChangePasswordRequest, me_principal: CurrentUser, svc: AuthSvc
) -> ApiResponse[None]:
    await svc.change_password(me_principal.user_id, req.current, req.next)
    return ApiResponse.ok()


@router.patch("/me", summary="Update own profile (name/email/phone)")
async def update_me(
    req: UpdateMeRequest, me_principal: CurrentUser, svc: AuthSvc
) -> ApiResponse[MeResponse]:
    return ApiResponse.ok(await svc.update_profile(me_principal.user_id, req))
