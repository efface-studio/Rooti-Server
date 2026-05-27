"""Email integration — Resend (transactional).

`RESEND_ENABLED=false` 일 때는 로그만 찍고 no-op (dev/test). Java 의 Resend wrapper 등가.
"""

from __future__ import annotations

import asyncio
import base64
import logging
from dataclasses import dataclass

import resend

from app.core.config import get_settings

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Attachment:
    filename: str
    content: bytes
    content_type: str


async def send_email(
    *,
    to: str | list[str],
    subject: str,
    html: str,
    attachments: list[Attachment] | None = None,
    from_address: str | None = None,
) -> str | None:
    """이메일 발송. 비활성이면 None 반환. 성공 시 message id."""
    settings = get_settings()
    if not settings.resend_enabled or not settings.resend_api_key:
        log.info(
            "[email:disabled] to=%s subject=%s attachments=%d",
            to,
            subject,
            len(attachments or []),
        )
        return None

    resend.api_key = settings.resend_api_key.get_secret_value()
    payload: dict[str, object] = {
        "from": from_address or settings.resend_from,
        "to": [to] if isinstance(to, str) else to,
        "subject": subject,
        "html": html,
    }
    if attachments:
        payload["attachments"] = [
            {
                "filename": a.filename,
                "content": base64.b64encode(a.content).decode("ascii"),
                "content_type": a.content_type,
            }
            for a in attachments
        ]

    # resend SDK 는 동기 — to_thread.
    def _send() -> dict:
        return resend.Emails.send(payload)

    result = await asyncio.to_thread(_send)
    return str(result.get("id")) if isinstance(result, dict) else None
