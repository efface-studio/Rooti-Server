"""JWT 강화 검증 — 약한 secret 부팅 거부, refresh 재사용 탐지, 비활성 계정 refresh 차단."""

from __future__ import annotations

import os

import pytest
from pydantic import ValidationError


def test_short_jwt_secret_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    from app.core.config import Settings

    monkeypatch.setenv("JWT_SECRET", "tooshort")
    with pytest.raises(ValidationError) as exc:
        Settings()  # type: ignore[call-arg]
    assert "32 바이트" in str(exc.value)


def test_exactly_32_byte_secret_accepted(monkeypatch: pytest.MonkeyPatch) -> None:
    from app.core.config import Settings

    monkeypatch.setenv("JWT_SECRET", "a" * 32)
    # DB_* 같은 필수 alias 는 conftest 가 이미 채움.
    Settings()  # type: ignore[call-arg]
