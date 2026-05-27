"""XLSX rendering via openpyxl (Apache POI 대체)."""

from __future__ import annotations

import asyncio
import io
from typing import Any

from openpyxl import Workbook


async def render_journal_xlsx(rows: list[dict[str, Any]], *, header: list[str]) -> bytes:
    """간단한 평면 표를 XLSX 로. 후속에 셀 병합/스타일링 추가."""

    def _render() -> bytes:
        wb = Workbook()
        ws = wb.active
        ws.title = "WorkJournal"
        ws.append(header)
        for r in rows:
            ws.append([r.get(k) for k in header])
        buf = io.BytesIO()
        wb.save(buf)
        return buf.getvalue()

    return await asyncio.to_thread(_render)
