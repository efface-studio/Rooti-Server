"""Structured logging via structlog.

Replaces logback + logstash-logback-encoder. 운영에서는 JSON 한 줄, dev/local 은 컬러 콘솔.
traceId 는 미들웨어/ASGI scope 에서 주입할 수 있게 contextvars 키 'trace_id' 사용.
"""

from __future__ import annotations

import logging
import sys
from typing import Any

import structlog

from app.core.config import Settings, get_settings


def configure_logging(settings: Settings | None = None) -> None:
    settings = settings or get_settings()
    is_prod = settings.app_env == "prod"

    timestamper = structlog.processors.TimeStamper(fmt="iso", utc=False)
    # NOTE: PrintLoggerFactory 를 쓰므로 stdlib 전용 프로세서(add_logger_name)는 넣지 않는다.
    # add_logger_name 은 logger.name 을 요구하는데 PrintLogger 엔 없어서 AttributeError 발생.
    # logger 이름은 get_logger(name) 호출 시 bind 로 직접 넣음 (_LoggerNameProcessor).
    shared_processors: list[Any] = [
        structlog.contextvars.merge_contextvars,
        structlog.processors.add_log_level,
        timestamper,
        structlog.processors.StackInfoRenderer(),
    ]

    if is_prod:
        renderer: Any = structlog.processors.JSONRenderer()
        shared_processors.append(structlog.processors.format_exc_info)
    else:
        renderer = structlog.dev.ConsoleRenderer(colors=True)

    structlog.configure(
        processors=[*shared_processors, renderer],
        wrapper_class=structlog.make_filtering_bound_logger(
            logging.INFO if is_prod else logging.DEBUG
        ),
        context_class=dict,
        logger_factory=structlog.PrintLoggerFactory(file=sys.stdout),
        cache_logger_on_first_use=True,
    )

    # 표준 logging 도 같은 핸들러로 (uvicorn, sqlalchemy 등)
    # 운영은 WARNING 부터 — INFO 로그는 매 요청마다 발생해 CloudWatch 비용 폭주.
    # uvicorn.access 는 별도 (요청 트레이스 용도) — 운영 INFO 유지.
    logging.basicConfig(
        format="%(message)s",
        stream=sys.stdout,
        level=logging.WARNING if is_prod else logging.INFO,
    )
    # SQLAlchemy SQL 로깅: 운영 WARNING (쿼리 로그 자체가 비용). dev 는 INFO (full DEBUG 는 시끄러움).
    logging.getLogger("sqlalchemy.engine").setLevel(
        logging.WARNING if is_prod else logging.INFO
    )
    # uvicorn access log 는 운영에서도 INFO — 요청 한 줄 기록 (디버깅/모니터링용).
    # health check 등 noise 는 ALB 의 액세스로그 비활성화 권장.
    logging.getLogger("uvicorn.access").setLevel(logging.INFO)
    # botocore/aioboto3 매 호출마다 INFO 가 너무 많음 → WARNING.
    logging.getLogger("botocore").setLevel(logging.WARNING)
    logging.getLogger("aiobotocore").setLevel(logging.WARNING)
    logging.getLogger("urllib3").setLevel(logging.WARNING)


def get_logger(name: str | None = None) -> structlog.stdlib.BoundLogger:
    # logger 이름을 event dict 에 'logger' 키로 직접 bind — PrintLogger 에 .name 이 없어서
    # add_logger_name 프로세서를 못 쓰는 대신, 호출부에서 명시적으로 부착.
    logger = structlog.get_logger()
    return logger.bind(logger=name) if name else logger
