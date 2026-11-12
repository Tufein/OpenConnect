from __future__ import annotations

import hashlib

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
