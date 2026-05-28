"""Rate limit — slowapi 기반.

설계:
- Redis 가 있으면 Redis backend, 없으면 in-memory fallback (단일 인스턴스).
- key 함수는 인증 토큰의 sub (userId) 우선, 없으면 client IP.
- 라우터 함수에 `@limiter.limit("5/minute")` 데코레이터로 개별 한도 적용.

기본 한도: 200/minute (전역). 로그인 / signup 같이 비싼 곳만 더 조이는 데코레이터 사용.
"""

from __future__ import annotations

from fastapi import Request
from slowapi import Limiter
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address

from app.core.config import get_settings


def _identifier(request: Request) -> str:
    """JWT sub (userId) 가 있으면 그것, 없으면 client IP. 인증 안 된 요청도 한도 적용."""
    # JwtAuthenticationFilter 가 SecurityContext 에 principal 을 세팅하지만,
    # rate-limit 은 정적 dependency 없이 호출되므로 헤더에서 직접 파싱.
    auth = request.headers.get("authorization") or ""
    if auth.startswith("Bearer "):
        token = auth[7:].strip()
        try:
            # 가벼운 디코드 — 검증 실패해도 무방. 토큰 자체가 일관적 식별자면 충분.
            import jwt

            payload = jwt.decode(token, options={"verify_signature": False})
            sub = payload.get("sub")
            if sub:
                return f"user:{sub}"
        except Exception:
            pass
    return f"ip:{get_remote_address(request)}"


def build_limiter() -> Limiter:
    settings = get_settings()
    # 테스트 환경(또는 명시적으로 Redis 가 비어있는 경우)은 memory backend.
    # 운영: Redis backend + in-memory fallback (Redis 죽어도 요청 자체는 통과).
    storage_uri = "memory://" if settings.app_env == "test" else (settings.redis_url or "memory://")
    return Limiter(
        key_func=_identifier,
        default_limits=["200/minute"],
        storage_uri=storage_uri,
        strategy="fixed-window",
        in_memory_fallback_enabled=True,
        swallow_errors=True,
    )


# 단일 인스턴스 — 라우터 데코레이터에서 import.
limiter = build_limiter()


# slowapi 의 기본 핸들러는 dict 응답 — 우리 ProblemDetail 모양 맞추기.
async def rate_limit_handler(request: Request, exc: RateLimitExceeded):
    from app.core.exceptions import ErrorCode, build_problem_detail

    # 429 는 우리 ErrorCode 에 없으니 그대로 인라인 응답
    return build_problem_detail(
        ErrorCode.UNPROCESSABLE,  # 가장 가까운 매핑
        detail=f"rate limit exceeded: {exc.detail}",
        instance=str(request.url.path),
        extras={"retryAfter": getattr(exc, "retry_after", None)},
    )
