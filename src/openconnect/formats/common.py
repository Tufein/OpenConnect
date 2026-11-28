from __future__ import annotations

import hashlib
import math

from openconnect.domain import Activity, Sport


class FormatError(ValueError):
    """Raised when a file cannot be parsed or an activity cannot be exported in a format."""


_SPORT_ALIASES = {
    "running": Sport.RUNNING,
    "run": Sport.RUNNING,
    "trail_running": Sport.RUNNING,
    "cycling": Sport.CYCLING,
    "biking": Sport.CYCLING,
    "bike": Sport.CYCLING,
    "ride": Sport.CYCLING,
    "swimming": Sport.SWIMMING,
    "swim": Sport.SWIMMING,
    "walking": Sport.WALKING,
    "walk": Sport.WALKING,
    "hiking": Sport.HIKING,
    "hike": Sport.HIKING,
    "strength": Sport.STRENGTH,
    "strength_training": Sport.STRENGTH,
    "yoga": Sport.YOGA,
}


def finite_number(text: str | None) -> float | None:
    """A number from a file, or None when missing; FormatError when it is not a real number.

    Python's float() accepts "nan" and "inf"; such a value used to slip into an activity, and
    int() of it crashed the import."""
    if text is None or not text.strip():
        return None
    try:
        value = float(text.strip())
    except ValueError as exc:
        raise FormatError(f"Invalid number: {text!r}") from exc
    if not math.isfinite(value):
        raise FormatError(f"Not a real number: {text!r}")
    return value


def check_position(lat: float | None, lon: float | None) -> None:
    """Refuse a coordinate pair that cannot exist on Earth."""
    if (
        lat is not None
        and not -90.0 <= lat <= 90.0
        or lon is not None
        and not -180.0 <= lon <= 180.0
    ):
        raise FormatError(f"Invalid position: {lat}, {lon}")


def sport_from_text(text: str | None) -> Sport:
    if not text:
        return Sport.OTHER
    key = text.strip().lower().replace(" ", "_").replace("-", "_")
    return _SPORT_ALIASES.get(key, Sport.OTHER)


def import_id(activity: Activity) -> str:
    """Stable id for imported files, so re-importing an activity updates it instead of cloning."""
    fingerprint = f"{activity.start_time.isoformat()}|{round(activity.duration_s)}"
    return "import:" + hashlib.sha1(fingerprint.encode()).hexdigest()[:16]


def default_name(activity: Activity) -> str:
    return f"{activity.sport.value.title()} {activity.start_time:%Y-%m-%d}"
