"""KST helpers. Mirrors Spring's `spring.jackson.time-zone: Asia/Seoul`.

OffsetDateTime in Java is rendered as e.g. `2026-05-26T14:00:00+09:00`.
Python's `datetime.isoformat()` on a timezone-aware datetime gives the same shape.
"""

from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))


def now_kst() -> datetime:
    """Current time in Asia/Seoul (timezone-aware)."""
    return datetime.now(KST)
