"""common 도메인 — PublicController 등가물."""

from __future__ import annotations

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_public_version_returns_envelope(client: AsyncClient) -> None:
    resp = await client.get("/api/v1/public/version")
    assert resp.status_code == 200
    body = resp.json()
    assert body["success"] is True
    assert "data" in body
    # Java VersionInfo record 필드명을 그대로 (camelCase).
    assert set(body["data"].keys()) == {"latest", "minSupported"}
    assert body["timestamp"].endswith("+09:00")


@pytest.mark.asyncio
async def test_public_ping(client: AsyncClient) -> None:
    resp = await client.get("/api/v1/public/ping")
    assert resp.status_code == 200
    body = resp.json()
    assert body["success"] is True
    assert body["data"] == "pong"
