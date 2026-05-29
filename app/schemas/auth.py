"""Auth 요청/응답 DTO. JSON 키는 Java record 와 동일 (camelCase)."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, EmailStr, Field

from app.models import UserRole


# ----- Request -----
class LoginRequest(BaseModel):
    username: str = Field(min_length=1, max_length=150)
    password: str = Field(min_length=1, max_length=100)
    fcm_token: str | None = Field(default=None, alias="fcmToken")

    model_config = ConfigDict(populate_by_name=True)


class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=1, alias="refreshToken")

    model_config = ConfigDict(populate_by_name=True)


class CaregiverSignupRequest(BaseModel):
    username: str = Field(min_length=1, max_length=150)
    email: EmailStr = Field(max_length=255)
    password: str = Field(min_length=8, max_length=100)
    name: str = Field(min_length=1, max_length=100)
    phone_number: str | None = Field(default=None, max_length=32, alias="phoneNumber")

    model_config = ConfigDict(populate_by_name=True)


# ----- Response -----
class TokenResponse(BaseModel):
    access_token: str = Field(serialization_alias="accessToken")
    refresh_token: str = Field(serialization_alias="refreshToken")
    access_ttl_seconds: int = Field(serialization_alias="accessTtlSeconds")
    refresh_ttl_seconds: int = Field(serialization_alias="refreshTtlSeconds")
    token_type: str = Field(default="Bearer", serialization_alias="tokenType")

    model_config = ConfigDict(populate_by_name=True)


class ChangePasswordRequest(BaseModel):
    current: str = Field(min_length=1)
    next: str = Field(min_length=8, max_length=100)

    model_config = ConfigDict(populate_by_name=True)


class UpdateMeRequest(BaseModel):
    """본인 프로필 수정 (PATCH /auth/me). 보낸 필드만 갱신한다."""

    name: str = Field(min_length=1, max_length=100)
    email: EmailStr | None = Field(default=None, max_length=255)
    phone_number: str | None = Field(default=None, max_length=32, alias="phoneNumber")

    model_config = ConfigDict(populate_by_name=True)


class MeResponse(BaseModel):
    id: int
    username: str
    email: str | None = None
    name: str
    phone_number: str | None = Field(default=None, serialization_alias="phoneNumber")
    role: UserRole

    model_config = ConfigDict(populate_by_name=True)
