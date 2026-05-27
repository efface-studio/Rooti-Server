"""Storage abstraction — local disk (dev/on-prem). S3 추가 시 같은 Protocol 구현.

Java StorageService 등가 — async I/O 는 to_thread 로 감싸 이벤트 루프 보호.
"""

from __future__ import annotations

import asyncio
import re
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO, Protocol

import ulid

from app.core.config import get_settings


@dataclass(frozen=True)
class Uploaded:
    key: str
    url: str
    size: int
    content_type: str | None


class StorageService(Protocol):
    async def store(
        self,
        prefix: str,
        original_filename: str,
        content: BinaryIO,
        size: int,
        content_type: str | None,
    ) -> Uploaded: ...

    async def open(self, key: str) -> BinaryIO: ...

    def resolve(self, key: str) -> Path: ...

    async def delete(self, key: str) -> None: ...


class LocalDiskStorage:
    def __init__(self) -> None:
        s = get_settings()
        self.root = Path(s.storage_local_root)
        self.public_url = s.storage_public_url.rstrip("/")
        self.root.mkdir(parents=True, exist_ok=True)

    async def store(
        self,
        prefix: str,
        original_filename: str,
        content: BinaryIO,
        size: int,
        content_type: str | None,
    ) -> Uploaded:
        safe_prefix = re.sub(r"[^a-zA-Z0-9_/\-]", "_", prefix or "misc")
        ext = Path(original_filename or "").suffix.lstrip(".")
        key = f"{safe_prefix}/{ulid.ULID()}{('.' + ext) if ext else ''}"
        target = self.root / key

        def _write() -> None:
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open("wb") as f:
                while chunk := content.read(64 * 1024):
                    f.write(chunk)

        await asyncio.to_thread(_write)
        return Uploaded(
            key=key, url=f"{self.public_url}/{key}", size=size, content_type=content_type
        )

    async def open(self, key: str) -> BinaryIO:
        path = self.resolve(key)

        def _open() -> BinaryIO:
            return path.open("rb")

        return await asyncio.to_thread(_open)

    def resolve(self, key: str) -> Path:
        # 디렉토리 이탈 방지 — 정규화 후 root 하위에 있는지 검증
        target = (self.root / key).resolve()
        if not str(target).startswith(str(self.root.resolve())):
            raise ValueError(f"path escape attempt: {key!r}")
        return target

    async def delete(self, key: str) -> None:
        path = self.resolve(key)

        def _del() -> None:
            path.unlink(missing_ok=True)

        await asyncio.to_thread(_del)


def get_storage() -> StorageService:
    """FastAPI dependency — STORAGE_DRIVER 에 따라 분기."""
    driver = get_settings().storage_driver
    if driver == "s3":
        # 지연 import — local-only 환경에서 boto3 evaluation 회피.
        from app.integrations.s3_storage import S3Storage

        return S3Storage()
    return LocalDiskStorage()
