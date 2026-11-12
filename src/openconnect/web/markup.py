"""A deliberately tiny Markdown renderer for AI reports.

It supports headings, bullet lists, paragraphs and **bold** only, and HTML-escapes everything
first, so report text (which may echo user-controlled strings) can never inject markup.
"""

from __future__ import annotations

import html
import re

from markupsafe import Markup

_BOLD = re.compile(r"\*\*(.+?)\*\*")


def _inline(text: str) -> str:
    return _BOLD.sub(r"<strong>\1</strong>", html.escape(text, quote=True))


def render_markdown(text: str) -> Markup:
    out: list[str] = []
    in_list = False
    paragraph: list[str] = []

    def flush_paragraph() -> None:
        if paragraph:
            out.append(f"<p>{' '.join(paragraph)}</p>")
            paragraph.clear()

    for raw in text.splitlines():
        line = raw.strip()
        bullet = re.match(r"^[-*]\s+(.*)", line)
        heading = re.match(r"^#{1,6}\s+(.*)", line)
        if bullet:
            flush_paragraph()
            if not in_list:
                out.append("<ul>")
                in_list = True
            out.append(f"<li>{_inline(bullet.group(1))}</li>")
            continue
        if in_list:
            out.append("</ul>")
            in_list = False
        if heading:
            flush_paragraph()
            out.append(f"<h3>{_inline(heading.group(1))}</h3>")
        elif not line:
            flush_paragraph()
        else:
            paragraph.append(_inline(line))
    flush_paragraph()
    if in_list:
        out.append("</ul>")
    return Markup("\n".join(out))
