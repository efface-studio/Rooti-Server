"""보안 헤더 자동 부착 검증."""

from __future__ import annotations

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_baseline_security_headers_present(client: AsyncClient) -> None:
    resp = await client.get("/actuator/info")
    h = resp.headers
    assert h.get("x-content-type-options") == "nosniff"
    assert h.get("x-frame-options") == "DENY"
    assert h.get("referrer-policy") == "strict-origin-when-cross-origin"
    assert "geolocation=()" in h.get("permissions-policy", "")
    assert "default-src 'self'" in h.get("content-security-policy", "")
    # JSON 응답은 cache-control: no-store
    assert h.get("cache-control") == "no-store"


@pytest.mark.asyncio
async def test_hsts_only_in_prod(client: AsyncClient) -> None:
    """test 환경에선 HSTS 없어야 함 (dev/local 도 동일)."""
    resp = await client.get("/actuator/info")
    assert "strict-transport-security" not in {k.lower() for k in resp.headers.keys()}
