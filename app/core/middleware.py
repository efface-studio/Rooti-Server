"""HTTP 미들웨어.

- TraceIdMiddleware: 모든 요청마다 16-char hex traceId 발급. 응답 헤더 X-Trace-Id
  + structlog contextvars 에 바인딩 → 그 요청 동안의 모든 로그에 trace_id 자동 포함.
  Spring 의 MDC `traceId` 등가.
"""

from __future__ import annotations

import uuid

import structlog
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp


def _new_trace_id() -> str:
    # UUID4 의 hex 앞 16 자 — Java 와 동일한 모양.
    return uuid.uuid4().hex[:16]


class TraceIdMiddleware(BaseHTTPMiddleware):
    HEADER = "X-Trace-Id"

    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)

    async def dispatch(self, request: Request, call_next):  # type: ignore[override]
        # 외부에서 trace 가 따라왔으면 이어받음 (게이트웨이 → 백엔드 분산 트레이싱).
        trace_id = request.headers.get(self.HEADER) or _new_trace_id()
        # contextvars 에 바인딩 — 본 요청 동안 모든 structlog 로그에 trace_id 자동 포함.
        structlog.contextvars.bind_contextvars(trace_id=trace_id)
        try:
            response: Response = await call_next(request)
        finally:
            structlog.contextvars.unbind_contextvars("trace_id")
        response.headers[self.HEADER] = trace_id
        return response
