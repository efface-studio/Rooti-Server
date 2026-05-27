"""TraceId 미들웨어 — 응답 헤더 + structlog 바인딩."""

from __future__ import annotations

import re

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_trace_id_generated_when_absent(client: AsyncClient) -> None:
    resp = await client.get("/actuator/info")
    assert resp.status_code == 200
    trace_id = resp.headers.get("x-trace-id")
    assert trace_id is not None
    assert re.fullmatch(r"[0-9a-f]{16}", trace_id), f"expected 16-hex, got {trace_id!r}"


@pytest.mark.asyncio
async def test_trace_id_propagated_from_request(client: AsyncClient) -> None:
    sent = "abc123def4567890"
    resp = await client.get("/actuator/info", headers={"X-Trace-Id": sent})
    assert resp.headers.get("x-trace-id") == sent
