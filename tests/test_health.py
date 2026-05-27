"""스캐폴딩 스모크 — 앱이 부팅되고 actuator 가 응답하는지."""

from __future__ import annotations

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_actuator_health_up(client: AsyncClient) -> None:
    resp = await client.get("/actuator/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "UP"
    assert body["components"]["db"]["status"] == "UP"
    assert body["components"]["redis"]["status"] == "UP"


@pytest.mark.asyncio
async def test_actuator_info_includes_version(client: AsyncClient) -> None:
    resp = await client.get("/actuator/info")
    assert resp.status_code == 200
    body = resp.json()
    assert body["app"]["name"] == "rooti-server"
    assert body["app"]["runtime"] == "python-fastapi"
    assert isinstance(body["app"]["version"], str)


@pytest.mark.asyncio
async def test_openapi_doc_served(client: AsyncClient) -> None:
    """Spring 의 /v3/api-docs 경로가 그대로 열리는지 (외부 도구/링크 호환)."""
    resp = await client.get("/v3/api-docs")
    assert resp.status_code == 200
    assert resp.json()["info"]["title"] == "Rooti API"
