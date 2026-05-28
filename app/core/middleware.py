"""HTTP 미들웨어.

- TraceIdMiddleware: X-Trace-Id 발급 / structlog contextvars 바인딩 (Spring MDC 등가).
- SecurityHeadersMiddleware: OWASP 권장 보안 헤더 자동 부착 (CSP·HSTS·XCTO·XFO 등).
"""

from __future__ import annotations

import uuid

import structlog
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp

from app.core.config import get_settings


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


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    """OWASP Secure Headers 권장값. JSON API + Swagger UI 둘 다 깨지지 않게 튜닝.

    - HSTS: prod 환경에서만 (dev 의 self-signed / http localhost 깨짐 방지)
    - CSP: API 응답은 CSP 가 의미 없지만 Swagger UI 가 같은 도메인 → 'self' + Swagger 가
      필요로 하는 inline-script 최소 허용
    - Permissions-Policy: 카메라/마이크/지오로케이션 등 사용 안 하는 권한 전부 차단
    """

    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)
        self._is_prod = get_settings().app_env == "prod"

    async def dispatch(self, request: Request, call_next):  # type: ignore[override]
        response: Response = await call_next(request)
        h = response.headers

        # 컨텐츠 sniffing 차단 — Content-Type 그대로 신뢰
        h.setdefault("X-Content-Type-Options", "nosniff")
        # 외부 iframe 임베드 차단 — clickjacking 방지
        h.setdefault("X-Frame-Options", "DENY")
        # 리퍼러 정보 외부로 누출 최소화
        h.setdefault("Referrer-Policy", "strict-origin-when-cross-origin")
        # 사용 안 하는 브라우저 권한 전부 차단
        h.setdefault(
            "Permissions-Policy",
            "geolocation=(), microphone=(), camera=(), payment=(), usb=(), magnetometer=()",
        )
        # CSP — Swagger UI 가 inline script + unpkg CDN(swagger-ui-bundle) 을 쓰므로 허용
        # API JSON 응답에는 의미 없지만 우리 도메인의 모든 HTML 응답에 동일 정책 적용.
        h.setdefault(
            "Content-Security-Policy",
            "default-src 'self'; "
            "img-src 'self' data: https:; "
            "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com; "
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com; "
            "font-src 'self' data:; "
            "frame-ancestors 'none'; "
            "base-uri 'self'",
        )
        # HSTS — prod 만 (dev http 깨짐 방지)
        if self._is_prod:
            h.setdefault("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        # 응답이 캐시 가능한지 명시 — JSON API 는 보수적으로 no-store
        if response.headers.get("content-type", "").startswith("application/json"):
            h.setdefault("Cache-Control", "no-store")
        return response
