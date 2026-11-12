"""Dependency-free inline SVG charts (no JavaScript, no CDN: the dashboard works offline)."""

from __future__ import annotations

import math
from collections.abc import Sequence
from typing import TypeVar

from markupsafe import Markup, escape

from openconnect.domain import TrackPoint

MAX_ROUTE_POINTS = 300
T = TypeVar("T")


def _downsample(items: list[T], limit: int) -> list[T]:
    if len(items) <= limit:
        return items
    step = (len(items) - 1) / (limit - 1)
    return [items[round(i * step)] for i in range(limit)]


def route_svg(
    track: Sequence[TrackPoint], width: int = 480, height: int = 320, padding: int = 14
) -> Markup:
    points = _downsample([p for p in track if p.has_position], MAX_ROUTE_POINTS)
    if len(points) < 2:
        return Markup('<p class="muted">No GPS data for this activity.</p>')

    lats = [p.lat for p in points]
    lons = [p.lon for p in points]
    lat_mid = (min(lats) + max(lats)) / 2  # type: ignore[operator]
    x_span = (max(lons) - min(lons)) * math.cos(math.radians(lat_mid))  # type: ignore[operator]
    y_span = max(lats) - min(lats)  # type: ignore[operator]
    inner_w, inner_h = width - 2 * padding, height - 2 * padding
    scale = min(inner_w / x_span if x_span else math.inf, inner_h / y_span if y_span else math.inf)
    if math.isinf(scale):
        scale = 1.0
    offset_x = padding + (inner_w - x_span * scale) / 2
    offset_y = padding + (inner_h - y_span * scale) / 2

    def project(p: TrackPoint) -> tuple[float, float]:
        x = offset_x + (p.lon - min(lons)) * math.cos(math.radians(lat_mid)) * scale  # type: ignore[operator]
        y = offset_y + (max(lats) - p.lat) * scale  # type: ignore[operator]
        return x, y

    coords = [project(p) for p in points]
    path = " ".join(f"{x:.1f},{y:.1f}" for x, y in coords)
    (sx, sy), (ex, ey) = coords[0], coords[-1]
    return Markup(
        f'<svg class="route" viewBox="0 0 {width} {height}" role="img" aria-label="Route map" '
        f'xmlns="http://www.w3.org/2000/svg">'
        f'<polyline points="{path}" fill="none" stroke="currentColor" stroke-width="3" '
        f'stroke-linejoin="round" stroke-linecap="round"/>'
        f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="5" class="start"/>'
        f'<circle cx="{ex:.1f}" cy="{ey:.1f}" r="5" class="finish"/></svg>'
    )


def sparkline(values: Sequence[float | None], width: int = 120, height: int = 28) -> Markup:
    real = [v for v in values if v is not None]
    head = (
        f'<svg class="spark" viewBox="0 0 {width} {height}" preserveAspectRatio="none" '
        f'xmlns="http://www.w3.org/2000/svg">'
    )
    if not real:
        return Markup(head + "</svg>")
    lo, hi = min(real), max(real)
    span = (hi - lo) or 1.0
    n = max(len(values) - 1, 1)

    def xy(i: int, v: float) -> tuple[float, float]:
        y = height / 2 if hi == lo else height - 3 - (v - lo) / span * (height - 6)
        return i / n * (width - 2) + 1, y

    parts, segment = [], []
    for i, v in enumerate([*values, None]):
        if v is not None:
            segment.append(xy(i, v))
            continue
        if len(segment) >= 2:
            pts = " ".join(f"{x:.1f},{y:.1f}" for x, y in segment)
            parts.append(
                f'<polyline points="{pts}" fill="none" stroke="currentColor" stroke-width="2"/>'
            )
        elif len(segment) == 1:
            x, y = segment[0]
            parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="2" fill="currentColor"/>')
        segment = []
    return Markup(head + "".join(parts) + "</svg>")


def bar_chart(
    items: Sequence[tuple[str, float | None]], width: int = 320, height: int = 120
) -> Markup:
    label_h, top = 16, 6
    plot_h = height - label_h - top
    peak = max((v for _, v in items if v), default=0) or 1.0
    slot = width / max(len(items), 1)
    bar_w = slot * 0.62
    parts = [
        f'<svg class="bars" viewBox="0 0 {width} {height}" xmlns="http://www.w3.org/2000/svg">'
    ]
    for i, (label, value) in enumerate(items):
        x = i * slot + (slot - bar_w) / 2
        if value:
            h = value / peak * plot_h
            parts.append(
                f'<rect x="{x:.1f}" y="{top + plot_h - h:.1f}" width="{bar_w:.1f}" '
                f'height="{h:.1f}" rx="2"/>'
            )
        parts.append(
            f'<text x="{i * slot + slot / 2:.1f}" y="{height - 3}" text-anchor="middle">'
            f"{escape(label)}</text>"
        )
    parts.append("</svg>")
    return Markup("".join(parts))
