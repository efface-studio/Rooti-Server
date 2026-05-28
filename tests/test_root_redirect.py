"""루트 / 가 Swagger UI 로 리다이렉트하는지 검증.

베이스 URL 로 들어온 사용자에게 404 대신 문서를 보여주기 위함.
"""

from __future__ import annotations

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_root_redirects_to_swagger(client: AsyncClient) -> None:
    resp = await client.get("/", follow_redirects=False)
    assert resp.status_code in (307, 308)
    assert resp.headers["location"] == "/swagger-ui.html"


@pytest.mark.asyncio
async def test_root_followed_reaches_swagger(client: AsyncClient) -> None:
    resp = await client.get("/", follow_redirects=True)
    assert resp.status_code == 200
    assert "swagger" in resp.text.lower()
