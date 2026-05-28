"""ErrorCode + BusinessException + RFC 7807 ProblemDetail 핸들러.

Java 의 com.rooti.global.exception.* 등가물. 모든 도메인이 BusinessException 을
raise 하면 자동으로 application/problem+json 응답으로 변환된다.

ProblemDetail JSON 모양 (Java 와 동일):
    {
      "type":      "https://docs.rooti.io/errors/<code_lower>",
      "title":     "AUTH_TOKEN_INVALID",
      "status":    401,
      "detail":    "...",
      "instance":  "/api/v1/...",
      "code":      "AUTH_TOKEN_INVALID",
      "timestamp": "2026-05-27T14:30:00+09:00",
      // 검증 에러는 fields/violations/missingParameter 가 추가됨
    }
"""

from __future__ import annotations

import enum
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.core.time import now_kst


# =============================================================================
#  ErrorCode — Java enum 1:1 매핑 (메시지 한국어 그대로 유지)
# =============================================================================
class ErrorCode(enum.Enum):
    INVALID_INPUT = (400, "요청 값이 올바르지 않습니다.")
    METHOD_NOT_ALLOWED = (405, "허용되지 않은 메서드입니다.")
    RESOURCE_NOT_FOUND = (404, "요청한 자원을 찾을 수 없습니다.")
    CONFLICT = (409, "리소스 충돌이 발생했습니다.")
    UNPROCESSABLE = (422, "요청을 처리할 수 없습니다.")

    AUTH_INVALID_CREDENTIALS = (401, "아이디 또는 비밀번호가 올바르지 않습니다.")
    AUTH_TOKEN_EXPIRED = (401, "토큰이 만료되었습니다.")
    AUTH_TOKEN_INVALID = (401, "유효하지 않은 토큰입니다.")
    AUTH_REFRESH_NOT_FOUND = (401, "리프레시 토큰이 존재하지 않습니다.")
    AUTH_FORBIDDEN = (403, "접근 권한이 없습니다.")
    AUTH_ACCOUNT_DISABLED = (403, "비활성화된 계정입니다.")

    USER_NOT_FOUND = (404, "사용자를 찾을 수 없습니다.")
    USER_USERNAME_DUPLICATED = (409, "이미 사용 중인 아이디입니다.")
    USER_EMAIL_DUPLICATED = (409, "이미 사용 중인 이메일입니다.")

    COMPANY_NOT_FOUND = (404, "회사를 찾을 수 없습니다.")

    WORKER_NOT_FOUND = (404, "근로자를 찾을 수 없습니다.")
    WORKER_ALREADY_HIRED = (409, "이미 채용된 근로자입니다.")

    CAREGIVER_NOT_FOUND = (404, "보호자를 찾을 수 없습니다.")
    CAREGIVER_RELATION_DUPLICATED = (409, "이미 연결된 보호자-근로자 관계입니다.")

    JOB_STANDARD_NOT_FOUND = (404, "업무 표준을 찾을 수 없습니다.")
    JOB_PROCESS_NOT_FOUND = (404, "업무 프로세스를 찾을 수 없습니다.")
    JOB_WORKER_NOT_FOUND = (404, "업무-근로자 매핑을 찾을 수 없습니다.")
    JOB_WORKER_ALREADY_ASSIGNED = (409, "이미 해당 근로자에게 할당된 업무입니다.")

    WORK_SCHEDULE_NOT_FOUND = (404, "근무 일정을 찾을 수 없습니다.")
    WORK_RECORD_NOT_FOUND = (404, "근무 기록을 찾을 수 없습니다.")
    WORK_RECORD_OUT_OF_RANGE = (422, "근무 기록 시간이 일정 범위를 벗어났습니다.")

    DOCUMENT_NOT_FOUND = (404, "문서를 찾을 수 없습니다.")
    DOCUMENT_TYPE_NOT_FOUND = (404, "문서 종류를 찾을 수 없습니다.")

    STORAGE_UPLOAD_FAILED = (500, "파일 업로드에 실패했습니다.")
    STORAGE_FILE_TOO_LARGE = (413, "파일 크기가 너무 큽니다.")

    BOARD_NOT_FOUND = (404, "게시글을 찾을 수 없습니다.")

    NOTIFICATION_SEND_FAILED = (502, "푸시 전송에 실패했습니다.")

    INTERNAL_ERROR = (500, "서버 내부 오류가 발생했습니다.")

    def __init__(self, status_code: int, default_message: str) -> None:
        self.status_code = status_code
        self.default_message = default_message


# =============================================================================
#  BusinessException — 모든 도메인 에러의 베이스
# =============================================================================
class BusinessException(Exception):
    """com.rooti.global.exception.BusinessException 등가."""

    def __init__(
        self,
        error_code: ErrorCode,
        message: str | None = None,
        *,
        extras: dict[str, Any] | None = None,
        cause: Exception | None = None,
    ) -> None:
        super().__init__(message or error_code.default_message)
        self.error_code = error_code
        self.message = message or error_code.default_message
        self.extras = extras or {}
        self.__cause__ = cause


# ---- 도메인별 구체 예외 (Java 파일과 동명) ----
class AuthInvalidCredentialsException(BusinessException):
    def __init__(self) -> None:
        super().__init__(ErrorCode.AUTH_INVALID_CREDENTIALS)


class AuthAccountDisabledException(BusinessException):
    def __init__(self) -> None:
        super().__init__(
            ErrorCode.AUTH_ACCOUNT_DISABLED, "비활성화된 계정입니다. 관리자에게 문의하세요."
        )


class AuthRefreshNotFoundException(BusinessException):
    def __init__(self) -> None:
        super().__init__(
            ErrorCode.AUTH_REFRESH_NOT_FOUND, "리프레시 토큰이 만료되었거나 폐기되었습니다."
        )


class AuthTokenInvalidException(BusinessException):
    def __init__(self, detail: str = "Invalid token") -> None:
        super().__init__(ErrorCode.AUTH_TOKEN_INVALID, detail)


class AuthTokenExpiredException(BusinessException):
    def __init__(self) -> None:
        super().__init__(ErrorCode.AUTH_TOKEN_EXPIRED, "Token expired")


class AuthForbiddenException(BusinessException):
    def __init__(self, detail: str | None = None) -> None:
        super().__init__(ErrorCode.AUTH_FORBIDDEN, detail)


class UserNotFoundException(BusinessException):
    def __init__(self, user_id: int | None = None, *, username: str | None = None) -> None:
        if user_id is not None:
            detail = f"사용자를 찾을 수 없습니다: id={user_id}"
        elif username is not None:
            detail = f"사용자를 찾을 수 없습니다: username={username!r}"
        else:
            detail = ErrorCode.USER_NOT_FOUND.default_message
        super().__init__(ErrorCode.USER_NOT_FOUND, detail)


class UserUsernameDuplicatedException(BusinessException):
    def __init__(self, username: str) -> None:
        super().__init__(
            ErrorCode.USER_USERNAME_DUPLICATED, f"이미 사용 중인 아이디입니다: {username}"
        )


class UserEmailDuplicatedException(BusinessException):
    def __init__(self, email: str) -> None:
        super().__init__(ErrorCode.USER_EMAIL_DUPLICATED, f"이미 사용 중인 이메일입니다: {email}")


# =============================================================================
#  ProblemDetail builder
# =============================================================================
_DOCS_BASE = "https://docs.rooti.io/errors"


def build_problem_detail(
    error_code: ErrorCode,
    *,
    detail: str,
    instance: str,
    extras: dict[str, Any] | None = None,
) -> JSONResponse:
    body: dict[str, Any] = {
        "type": f"{_DOCS_BASE}/{error_code.name.lower()}",
        "title": error_code.name,
        "status": error_code.status_code,
        "detail": detail,
        "instance": instance,
        "code": error_code.name,
        "timestamp": now_kst().isoformat(),
    }
    if extras:
        body.update(extras)
    return JSONResponse(
        status_code=error_code.status_code,
        content=body,
        media_type="application/problem+json",
    )


# =============================================================================
#  Handlers (Spring 의 ExceptionAdvice 4개 통합)
# =============================================================================
async def _business_handler(request: Request, exc: BusinessException) -> JSONResponse:
    return build_problem_detail(
        exc.error_code,
        detail=exc.message,
        instance=str(request.url.path),
        extras=exc.extras or None,
    )


async def _http_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    # FastAPI HTTPException 을 ErrorCode 로 베스트-매칭.
    code = {
        400: ErrorCode.INVALID_INPUT,
        401: ErrorCode.AUTH_TOKEN_INVALID,
        403: ErrorCode.AUTH_FORBIDDEN,
        404: ErrorCode.RESOURCE_NOT_FOUND,
        405: ErrorCode.METHOD_NOT_ALLOWED,
        409: ErrorCode.CONFLICT,
        413: ErrorCode.STORAGE_FILE_TOO_LARGE,
        422: ErrorCode.UNPROCESSABLE,
    }.get(exc.status_code, ErrorCode.INTERNAL_ERROR)
    detail = exc.detail if isinstance(exc.detail, str) else code.default_message
    return build_problem_detail(code, detail=detail, instance=str(request.url.path))


async def _validation_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    # Java ValidationExceptionAdvice 의 handleBodyValidation 등가 — fields map.
    fields: dict[str, str] = {}
    for err in exc.errors():
        loc = ".".join(str(p) for p in err.get("loc", ()) if p not in ("body",))
        if loc:
            fields[loc] = err.get("msg", "invalid")
    return build_problem_detail(
        ErrorCode.INVALID_INPUT,
        detail=ErrorCode.INVALID_INPUT.default_message,
        instance=str(request.url.path),
        extras={"fields": fields} if fields else None,
    )


async def _unhandled_handler(request: Request, exc: Exception) -> JSONResponse:
    """마지막 그물. 운영에서는 내부 정보 노출 차단, dev/local 에서는 진단용 정보 노출.

    Spring `include-stacktrace: never` + `include-exception: false` 등가.
    """
    import logging

    from app.core.config import get_settings

    # 로그에는 트레이스 포함 (운영 트러블슈팅용)
    logging.getLogger(__name__).exception("unhandled-exception path=%s", str(request.url.path))
    # 응답 본문은 환경별로 결정:
    #   prod  → 일반 메시지만, 클래스명/메시지 노출 X
    #   dev/local/test → exception class + 메시지 (디버깅 편의)
    env = get_settings().app_env
    if env == "prod":
        detail = ErrorCode.INTERNAL_ERROR.default_message
    else:
        detail = f"{type(exc).__name__}: {exc!s}" if str(exc) else type(exc).__name__
    return build_problem_detail(
        ErrorCode.INTERNAL_ERROR, detail=detail, instance=str(request.url.path)
    )


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(BusinessException, _business_handler)  # type: ignore[arg-type]
    app.add_exception_handler(StarletteHTTPException, _http_handler)
    app.add_exception_handler(RequestValidationError, _validation_handler)  # type: ignore[arg-type]
    app.add_exception_handler(Exception, _unhandled_handler)


# =============================================================================
#  Backward-compat shims — 기존 코드(common/user)에서 import 한 이름들 유지
# =============================================================================
class NotFoundError(BusinessException):
    """기존 코드 호환용. 새 코드는 BusinessException(ErrorCode.RESOURCE_NOT_FOUND) 직접 사용."""

    def __init__(self, detail: str, *, extra: dict[str, Any] | None = None) -> None:
        super().__init__(ErrorCode.RESOURCE_NOT_FOUND, detail, extras=extra)


class ConflictError(BusinessException):
    def __init__(self, detail: str, *, extra: dict[str, Any] | None = None) -> None:
        super().__init__(ErrorCode.CONFLICT, detail, extras=extra)


class UnauthorizedError(BusinessException):
    def __init__(self, detail: str, *, extra: dict[str, Any] | None = None) -> None:
        super().__init__(ErrorCode.AUTH_TOKEN_INVALID, detail, extras=extra)


class ForbiddenError(BusinessException):
    def __init__(self, detail: str, *, extra: dict[str, Any] | None = None) -> None:
        super().__init__(ErrorCode.AUTH_FORBIDDEN, detail, extras=extra)


# 별칭: 도메인 코드에서 쓰는 이름
DomainError = BusinessException
