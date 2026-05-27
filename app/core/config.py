"""Application settings.

Reads the **same environment variables** as the Spring Boot app so a single
`.env.dev` / `.env.prod` works for both runtimes during the migration.

Mapping reference (Java `application.yml` → here):
  SPRING_PROFILES_ACTIVE  → app_env
  SERVER_PORT             → server_port
  DB_*                    → db_*
  REDIS_*                 → redis_*
  JWT_SECRET / JWT_*_TTL  → jwt_*
  STORAGE_*               → storage_*
  FIREBASE_*              → firebase_*
  RESEND_*                → resend_*
  CORS_ALLOWED_ORIGINS    → cors_allowed_origins
"""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr, computed_field
from pydantic_settings import BaseSettings, SettingsConfigDict

Env = Literal["local", "dev", "prod", "test"]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", ".env.local", ".env.dev", ".env.prod"),
        env_file_encoding="utf-8",
        extra="ignore",  # Spring 전용 키(SPRING_*) 무시
        case_sensitive=False,
    )

    # ---- App ----
    app_name: str = "rooti-server"
    app_env: Env = Field(default="local", alias="SPRING_PROFILES_ACTIVE")
    app_base_url: str = Field(default="http://localhost:8080", alias="APP_BASE_URL")
    server_port: int = Field(default=8080, alias="SERVER_PORT")

    # ---- Database (PostgreSQL) ----
    db_host: str = Field(alias="DB_HOST")
    db_port: int = Field(default=5432, alias="DB_PORT")
    db_name: str = Field(alias="DB_NAME")
    db_username: str = Field(alias="DB_USERNAME")
    db_password: SecretStr = Field(alias="DB_PASSWORD")
    db_ssl_required: bool = Field(default=True, alias="DB_SSL_REQUIRED")
    db_pool_max: int = Field(default=5, alias="DB_POOL_MAX")
    db_pool_min: int = Field(default=1, alias="DB_POOL_MIN")

    @computed_field  # type: ignore[prop-decorator]
    @property
    def database_url(self) -> str:
        """asyncpg DSN. SSL은 prod RDS 기본 require."""
        ssl = "?ssl=require" if self.db_ssl_required else ""
        return (
            f"postgresql+asyncpg://{self.db_username}:{self.db_password.get_secret_value()}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}{ssl}"
        )

    # ---- Redis ----
    redis_host: str = Field(default="localhost", alias="REDIS_HOST")
    redis_port: int = Field(default=6379, alias="REDIS_PORT")
    redis_password: SecretStr | None = Field(default=None, alias="REDIS_PASSWORD")

    @computed_field  # type: ignore[prop-decorator]
    @property
    def redis_url(self) -> str:
        auth = f":{self.redis_password.get_secret_value()}@" if self.redis_password else ""
        return f"redis://{auth}{self.redis_host}:{self.redis_port}/0"

    # ---- JWT (mirrors rooti.security.jwt.*) ----
    jwt_secret: SecretStr = Field(alias="JWT_SECRET")
    jwt_access_ttl_minutes: int = Field(default=15, alias="JWT_ACCESS_TTL_MIN")
    jwt_refresh_ttl_days: int = Field(default=14, alias="JWT_REFRESH_TTL_DAYS")
    jwt_issuer: str = "rooti"
    jwt_algorithm: str = "HS256"

    # ---- CORS ----
    cors_allowed_origins: str = Field(
        default="http://localhost:5173", alias="CORS_ALLOWED_ORIGINS"
    )

    @computed_field  # type: ignore[prop-decorator]
    @property
    def cors_origins(self) -> list[str]:
        return [o.strip() for o in self.cors_allowed_origins.split(",") if o.strip()]

    # ---- Storage ----
    storage_driver: Literal["local", "s3"] = Field(default="local", alias="STORAGE_DRIVER")
    storage_local_root: str = Field(default="./var/storage", alias="STORAGE_LOCAL_ROOT")
    storage_public_url: str = Field(
        default="http://localhost:8080/files", alias="STORAGE_PUBLIC_URL"
    )
    # S3 (STORAGE_DRIVER=s3 일 때만 의미 있음)
    aws_region: str = Field(default="ap-northeast-2", alias="AWS_REGION")
    aws_s3_bucket: str | None = Field(default=None, alias="AWS_S3_BUCKET")
    aws_s3_endpoint_url: str | None = Field(default=None, alias="AWS_S3_ENDPOINT_URL")
    aws_access_key_id: SecretStr | None = Field(default=None, alias="AWS_ACCESS_KEY_ID")
    aws_secret_access_key: SecretStr | None = Field(default=None, alias="AWS_SECRET_ACCESS_KEY")

    # ---- Firebase / FCM ----
    firebase_credential_json_path: str = Field(default="", alias="FIREBASE_CREDENTIAL_JSON_PATH")

    @computed_field  # type: ignore[prop-decorator]
    @property
    def firebase_enabled(self) -> bool:
        return bool(self.firebase_credential_json_path)

    # ---- Resend (email) ----
    resend_enabled: bool = Field(default=False, alias="RESEND_ENABLED")
    resend_api_key: SecretStr | None = Field(default=None, alias="RESEND_API_KEY")
    resend_from: str = Field(default="Rooti <noreply@rooti.io>", alias="RESEND_FROM")

    # ---- App version ----
    version_latest: str = "1.0.0"
    version_min_supported: str = "1.0.0"

    # ---- Multipart limits (matches application.yml) ----
    multipart_max_file_bytes: int = 50 * 1024 * 1024
    multipart_max_request_bytes: int = 100 * 1024 * 1024


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Singleton. FastAPI Depends(get_settings) 로 주입."""
    return Settings()  # type: ignore[call-arg]
