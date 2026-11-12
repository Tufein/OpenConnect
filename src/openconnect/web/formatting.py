from __future__ import annotations

DASH = "–"


def km(metres: float | None) -> str:
    return DASH if metres is None else f"{metres / 1000:.2f} km"


def duration(seconds: float | None) -> str:
    if seconds is None:
        return DASH
    total = round(seconds)
    hours, rest = divmod(total, 3600)
    minutes, secs = divmod(rest, 60)
    return f"{hours}:{minutes:02d}:{secs:02d}" if hours else f"{minutes}:{secs:02d}"


def pace(seconds_per_km: float | None) -> str:
    if not seconds_per_km:
        return DASH
    total = round(seconds_per_km)
    return f"{total // 60}:{total % 60:02d} /km"


def hours(value: float | None) -> str:
    return DASH if value is None else f"{value:.1f} h"
