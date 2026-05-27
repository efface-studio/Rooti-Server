"""S3 storage driver (aioboto3 기반).

LocalDiskStorage 와 동일한 Protocol 을 구현. `STORAGE_DRIVER=s3` 일 때 자동 선택.
LocalStack / MinIO 호환을 위해 `AWS_S3_ENDPOINT_URL` 지원.
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import BinaryIO

import aioboto3
import ulid

from app.core.config import get_settings
from app.integrations.storage import Uploaded


class S3Storage:
    """STORAGE_DRIVER=s3. AWS_S3_BUCKET 필수."""

    def __init__(self) -> None:
        s = get_settings()
        if not s.aws_s3_bucket:
            raise RuntimeError("STORAGE_DRIVER=s3 인데 AWS_S3_BUCKET 미설정")
        self.bucket = s.aws_s3_bucket
        self.region = s.aws_region
        self.endpoint_url = s.aws_s3_endpoint_url
        self.public_url = s.storage_public_url.rstrip("/")
        self._session = aioboto3.Session(
            aws_access_key_id=(
                s.aws_access_key_id.get_secret_value() if s.aws_access_key_id else None
            ),
            aws_secret_access_key=(
                s.aws_secret_access_key.get_secret_value()
                if s.aws_secret_access_key
                else None
            ),
            region_name=self.region,
        )

    def _client(self):
        # aioboto3.Session.client() 는 async context manager
        kwargs = {"region_name": self.region}
        if self.endpoint_url:
            kwargs["endpoint_url"] = self.endpoint_url
        return self._session.client("s3", **kwargs)

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

        extra: dict[str, str] = {}
        if content_type:
            extra["ContentType"] = content_type

        async with self._client() as s3:
            await s3.upload_fileobj(content, self.bucket, key, ExtraArgs=extra or None)

        # public URL — CDN/CloudFront 가 앞에 있으면 STORAGE_PUBLIC_URL 로 prefix.
        url = f"{self.public_url}/{key}"
        return Uploaded(key=key, url=url, size=size, content_type=content_type)

    async def open(self, key: str) -> BinaryIO:
        # 메모리에 통째 적재 — 대용량은 stream-download 헬퍼 별도 추가 (TODO).
        import io

        async with self._client() as s3:
            response = await s3.get_object(Bucket=self.bucket, Key=key)
            body = await response["Body"].read()
        return io.BytesIO(body)

    def resolve(self, key: str) -> Path:
        # S3 는 로컬 경로 없음 — 호출자가 .resolve() 를 쓰면 안 됨.
        raise NotImplementedError("S3Storage 는 로컬 path resolve 불가")

    async def delete(self, key: str) -> None:
        async with self._client() as s3:
            await s3.delete_object(Bucket=self.bucket, Key=key)
