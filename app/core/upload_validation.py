"""파일 업로드 검증.

- 크기 제한: 50 MB (settings.multipart_max_file_bytes)
- MIME 화이트리스트: caregiver 증명서 / 일반 첨부
- 파일명 sanitize: 경로 트래버설 / null byte / 제어 문자 차단

운영 노출되는 모든 업로드 엔드포인트는 이 모듈의 헬퍼 한 곳을 통과.
"""

from __future__ import annotations

import re

from fastapi import UploadFile

from app.core.config import get_settings
from app.core.exceptions import BusinessException, ErrorCode

# Caregiver 증명서 / 첨부에 허용하는 MIME — 보수적으로.
ALLOWED_DOCUMENT_MIME: frozenset[str] = frozenset(
    {
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif",
        "application/pdf",
        # 한글 / Office 문서 (담당자가 업로드하는 시나리오)
        "application/x-hwp",
        "application/haansofthwp",
        "application/vnd.hancom.hwp",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/msword",
        "application/vnd.ms-excel",
    }
)


_BAD_FILENAME_CHARS = re.compile(r"[\x00-\x1f/\\]")


def sanitize_filename(name: str | None) -> str:
    """경로 트래버설 / null byte / 제어 문자 차단.

    반환은 파일 표시용 — 실제 storage key 는 ULID 기반이므로 이걸 그대로 path 로 쓰지 않음.
    """
    if not name:
        return "upload"
    cleaned = _BAD_FILENAME_CHARS.sub("_", name).strip()
    # 점만 있거나 빈 문자열 방어
    if not cleaned or set(cleaned) <= {"."}:
        return "upload"
    return cleaned[:255]  # 한 컴포넌트는 일반적으로 255 byte 제한


def validate_document_upload(file: UploadFile) -> None:
    """업로드 파일을 검증. 위반 시 BusinessException raise.

    호출 시점: 라우터 진입 직후. 서비스 호출 전.
    """
    settings = get_settings()

    # 1. 크기
    size = file.size or 0
    if size <= 0:
        raise BusinessException(
            ErrorCode.INVALID_INPUT, "빈 파일은 업로드할 수 없습니다."
        )
    if size > settings.multipart_max_file_bytes:
        raise BusinessException(
            ErrorCode.STORAGE_FILE_TOO_LARGE,
            f"파일 크기가 너무 큽니다 (최대 {settings.multipart_max_file_bytes // (1024 * 1024)} MB).",
        )

    # 2. MIME 화이트리스트
    mime = (file.content_type or "").lower().split(";", 1)[0].strip()
    if mime not in ALLOWED_DOCUMENT_MIME:
        raise BusinessException(
            ErrorCode.INVALID_INPUT,
            f"지원하지 않는 파일 형식입니다: {mime or 'unknown'}",
        )

    # 3. 파일명에 위험 문자가 있으면 비즈니스 에러 (조용히 sanitize 가 아니라 명시적 거부)
    if file.filename and _BAD_FILENAME_CHARS.search(file.filename):
        raise BusinessException(
            ErrorCode.INVALID_INPUT,
            "파일명에 사용할 수 없는 문자가 포함돼 있습니다.",
        )
