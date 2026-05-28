"""로깅 설정이 실제로 로그를 뱉을 수 있는지 검증.

회귀 방지: configure_logging 이 PrintLoggerFactory + stdlib 프로세서 조합으로
AttributeError('PrintLogger' object has no attribute 'name') 를 내던 버그.
이 버그는 lifespan 을 실행 안 하는 단위 테스트에선 안 잡혔음.
"""

from __future__ import annotations

import pytest

from app.core.logging import configure_logging, get_logger


@pytest.mark.parametrize("env", ["local", "dev", "prod", "test"])
def test_configure_logging_then_emit_does_not_raise(
    env: str, monkeypatch: pytest.MonkeyPatch
) -> None:
    from app.core.config import Settings

    monkeypatch.setenv("SPRING_PROFILES_ACTIVE", env)
    settings = Settings()  # type: ignore[call-arg]
    configure_logging(settings)

    log = get_logger(__name__)
    # 모든 레벨이 예외 없이 방출돼야 함 (logger.name 접근 등으로 안 터짐)
    log.info("startup", app="rooti-server", env=env, port=8080)
    log.warning("warn-line", detail="something")
    log.error("error-line", code="X")
