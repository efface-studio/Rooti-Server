"""Push notification — Firebase wrapper."""

from __future__ import annotations

from app.core.exceptions import BusinessException, ErrorCode
from app.integrations import firebase
from app.schemas.notification import PushRequest


class PushNotificationService:
    async def send(self, req: PushRequest) -> None:
        try:
            await firebase.send_to_token(
                token=req.token,
                title=req.title,
                body=req.body,
                deep_link=req.deep_link,
                extra=req.extra,
            )
        except Exception as e:
            raise BusinessException(ErrorCode.NOTIFICATION_SEND_FAILED, str(e), cause=e) from e
