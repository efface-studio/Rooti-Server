"""PDF rendering via WeasyPrint.

NOTE: WeasyPrint 는 macOS / Linux 시스템 lib (pango, gdk-pixbuf) 필요.
    macOS: brew install pango gdk-pixbuf libffi
    Linux: apt-get install libcairo2 libpango-1.0-0 libpangoft2-1.0-0
"""

from __future__ import annotations

import asyncio
from typing import Any

from jinja2 import Environment, FileSystemLoader, select_autoescape

# 템플릿 디렉토리 — 후속 PR 에서 /Users/.../app/integrations/templates/ 로 추가.
_env = Environment(
    loader=FileSystemLoader(searchpath=["app/integrations/templates"]),
    autoescape=select_autoescape(["html", "xml"]),
)


def render_html(template_name: str, context: dict[str, Any]) -> str:
    return _env.get_template(template_name).render(**context)


async def html_to_pdf(html: str) -> bytes:
    """HTML → PDF bytes. WeasyPrint 는 동기라 to_thread 로 감쌈."""

    def _render() -> bytes:
        # 지연 import — WeasyPrint 미설치 환경에서도 모듈 import 자체는 성공.
        from weasyprint import HTML

        return HTML(string=html).write_pdf()

    return await asyncio.to_thread(_render)


async def render_pdf(template_name: str, context: dict[str, Any]) -> bytes:
    return await html_to_pdf(render_html(template_name, context))
