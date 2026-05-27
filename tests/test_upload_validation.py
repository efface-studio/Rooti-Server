"""파일 업로드 검증."""

from __future__ import annotations

import io
from unittest.mock import MagicMock

import pytest
from fastapi import UploadFile

from app.core.exceptions import BusinessException, ErrorCode
from app.core.upload_validation import sanitize_filename, validate_document_upload


def _upload(
    *, filename: str | None, content_type: str | None, size: int
) -> UploadFile:
    spool = io.BytesIO(b"x" * min(size, 4096))
    uf = UploadFile(file=spool, filename=filename, headers=MagicMock())
    uf.size = size  # type: ignore[assignment]
    uf.headers = {"content-type": content_type or ""}  # type: ignore[assignment]
    type(uf).content_type = property(lambda self: content_type)  # type: ignore[assignment]
    return uf


def test_empty_file_rejected() -> None:
    uf = _upload(filename="a.pdf", content_type="application/pdf", size=0)
    with pytest.raises(BusinessException) as exc:
        validate_document_upload(uf)
    assert exc.value.error_code is ErrorCode.INVALID_INPUT


def test_too_large_rejected() -> None:
    uf = _upload(filename="a.pdf", content_type="application/pdf", size=200 * 1024 * 1024)
    with pytest.raises(BusinessException) as exc:
        validate_document_upload(uf)
    assert exc.value.error_code is ErrorCode.STORAGE_FILE_TOO_LARGE


def test_disallowed_mime_rejected() -> None:
    uf = _upload(filename="a.exe", content_type="application/x-msdownload", size=100)
    with pytest.raises(BusinessException) as exc:
        validate_document_upload(uf)
    assert exc.value.error_code is ErrorCode.INVALID_INPUT


def test_path_traversal_filename_rejected() -> None:
    uf = _upload(filename="../etc/passwd", content_type="application/pdf", size=100)
    with pytest.raises(BusinessException) as exc:
        validate_document_upload(uf)
    assert exc.value.error_code is ErrorCode.INVALID_INPUT


def test_allowed_mime_passes() -> None:
    for mime in ("application/pdf", "image/png", "image/jpeg"):
        uf = _upload(filename="ok.bin", content_type=mime, size=100)
        validate_document_upload(uf)  # no exception


def test_sanitize_filename_drops_path_separators() -> None:
    """경로 separator (/, \\, control chars) 만 치환. dot 은 그대로 유지 — 우리 storage key 는
    ULID prefix 라 dot 단독은 traversal 위험 없음."""
    cleaned = sanitize_filename("../../etc/passwd")
    assert "/" not in cleaned and "\\" not in cleaned
    assert sanitize_filename("a\x00b") == "a_b"
    assert sanitize_filename("..") == "upload"  # dots only → fallback
    assert sanitize_filename("") == "upload"
    assert sanitize_filename(None) == "upload"
    assert sanitize_filename("증명서.pdf") == "증명서.pdf"
