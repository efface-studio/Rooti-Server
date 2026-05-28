"""Prometheus 메트릭 — prometheus_client 직접 사용.

prometheus-fastapi-instrumentator 를 대체. 그 패키지가 starlette<1.0.0 을 강제해서
CVE-2026-48710(Host 헤더 path 오염) 패치 버전(starlette>=1.0.1)을 못 쓰게 막았기 때문.

라벨 cardinality 주의: 경로는 **라우트 템플릿**(`/api/v1/companies/{company_id}`)을 쓴다.
raw path 를 쓰면 ID 마다 시계열이 폭발 → Prometheus 비용 폭주.
"""

from __future__ import annotations

import time

from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp

_REQUESTS = Counter(
    "http_requests_total",
    "Total HTTP requests",
    ["method", "path", "status"],
)
_LATENCY = Histogram(
    "http_request_duration_seconds",
    "HTTP request latency (seconds)",
    ["method", "path"],
)


def _route_template(request: Request) -> str:
    """매칭된 라우트 템플릿. 미매칭(404 등)은 카디널리티 보호를 위해 'unmatched'."""
    route = request.scope.get("route")
    path = getattr(route, "path", None)
    return path or "unmatched"


class PrometheusMiddleware(BaseHTTPMiddleware):
    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)

    async def dispatch(self, request: Request, call_next):  # type: ignore[override]
        start = time.perf_counter()
        response: Response = await call_next(request)
        elapsed = time.perf_counter() - start
        # call_next 이후엔 scope['route'] 가 채워져 있음 (라우팅이 그 안에서 일어남).
        path = _route_template(request)
        # /actuator/prometheus 자기 자신은 노이즈라 집계 제외.
        if path != "/actuator/prometheus":
            _REQUESTS.labels(request.method, path, str(response.status_code)).inc()
            _LATENCY.labels(request.method, path).observe(elapsed)
        return response


async def metrics_endpoint(_: Request) -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
