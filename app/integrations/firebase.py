"""Firebase Admin SDK — push notification.

`FIREBASE_CREDENTIAL_JSON_PATH` 가 비어있으면 SDK 초기화 없이 no-op 모드 (로컬/테스트).
"""

from __future__ import annotations

import json
import logging
from typing import Any

import firebase_admin
from firebase_admin import credentials, messaging

from app.core.config import get_settings

log = logging.getLogger(__name__)

_app: firebase_admin.App | None = None


def init_firebase() -> firebase_admin.App | None:
    """앱 lifespan 시작 시 호출. 자격증명 없으면 None 반환 (no-op 모드)."""
    global _app
    if _app is not None:
        return _app
    path = get_settings().firebase_credential_json_path
    if not path:
        log.info("firebase: disabled (FIREBASE_CREDENTIAL_JSON_PATH not set)")
        return None
    cred = credentials.Certificate(path)
    _app = firebase_admin.initialize_app(cred)
    log.info("firebase: initialised")
    return _app


def is_enabled() -> bool:
    return _app is not None


async def send_to_token(
    *,
    token: str,
    title: str,
    body: str | None = None,
    deep_link: str | None = None,
    extra: dict[str, Any] | None = None,
) -> str | None:
    """단일 디바이스 토큰에 푸시. 비활성 모드면 로그만 찍고 None 반환."""
    if not is_enabled():
        log.info("[push:disabled] token=%s title=%s", token, title)
        return None

    data: dict[str, str] = {}
    if deep_link:
        data["deepLink"] = deep_link
    if extra:
        data.update({k: json.dumps(v) if not isinstance(v, str) else v for k, v in extra.items()})

    message = messaging.Message(
        token=token,
        notification=messaging.Notification(title=title, body=body),
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(channel_id="rooti_default"),
        ),
        data=data,
    )
    # firebase_admin.messaging.send 는 동기 함수 — 이벤트 루프 막지 않으려면 to_thread 사용.
    import asyncio

    return await asyncio.to_thread(messaging.send, message)
