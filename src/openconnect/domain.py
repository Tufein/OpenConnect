"""Core domain models. Everything else (storage, formats, Garmin mappers) converts to/from these."""

from __future__ import annotations

import math
from datetime import UTC, date, datetime
from enum import StrEnum

from pydantic import BaseModel, Field, field_validator


class Sport(StrEnum):
    RUNNING = "running"
    CYCLING = "cycling"
    SWIMMING = "swimming"
    WALKING = "walking"
    HIKING = "hiking"
    STRENGTH = "strength"
    YOGA = "yoga"
    OTHER = "other"


def ensure_utc(value: datetime) -> datetime:
    """Return a timezone-aware UTC datetime. Naive values are assumed to already be UTC."""
    if value.tzinfo is None:
        return value.replace(tzinfo=UTC)
    return value.astimezone(UTC)


class TrackPoint(BaseModel):
    time: datetime
    lat: float | None = None
    lon: float | None = None
    ele: float | None = None  # metres
    hr: int | None = None  # beats per minute
    cadence: int | None = None
    power: int | None = None  # watts
    distance_m: float | None = None  # cumulative, optional

    _utc = field_validator("time")(ensure_utc)

    @property
    def has_position(self) -> bool:
        return self.lat is not None and self.lon is not None


class Activity(BaseModel):
    id: str
    source: str = "import"  # "garmin", "import", ...
    external_id: str | None = None
    name: str = ""
    sport: Sport = Sport.OTHER
    start_time: datetime
    duration_s: float = 0.0
    distance_m: float = 0.0
    elevation_gain_m: float = 0.0
    avg_hr: int | None = None
    max_hr: int | None = None
    calories: int | None = None
    device_name: str | None = None
    track: list[TrackPoint] = Field(default_factory=list)

    _utc = field_validator("start_time")(ensure_utc)

    @property
    def avg_speed_mps(self) -> float | None:
        if self.duration_s <= 0 or self.distance_m <= 0:
            return None
        return self.distance_m / self.duration_s

    @property
    def pace_s_per_km(self) -> float | None:
        speed = self.avg_speed_mps
        return None if not speed else 1000.0 / speed

    @property
    def has_gps(self) -> bool:
        return any(p.has_position for p in self.track)


class DailySummary(BaseModel):
    """One calendar day of all-day health data (Garmin Health API 'dailies' + derived fields)."""

    date: date
    steps: int = 0
    steps_goal: int | None = None
    distance_m: float = 0.0
    active_kcal: int = 0
    floors_climbed: int = 0
    moderate_intensity_min: int = 0
    vigorous_intensity_min: int = 0
    resting_hr: int | None = None
    min_hr: int | None = None
    max_hr: int | None = None
    avg_stress: int | None = None
    max_stress: int | None = None
    body_battery_high: int | None = None
    body_battery_low: int | None = None

    @property
    def intensity_minutes(self) -> int:
        # Garmin counts vigorous minutes double towards the weekly goal.
        return self.moderate_intensity_min + 2 * self.vigorous_intensity_min


class SleepSummary(BaseModel):
    date: date  # the calendar day the sleep ends on
    duration_s: int = 0
    deep_s: int = 0
    light_s: int = 0
    rem_s: int = 0
    awake_s: int = 0
    score: int | None = None

    @property
    def hours(self) -> float:
        return self.duration_s / 3600.0


class GeneratedReport(BaseModel):
    """An AI-generated report, stored so it is not regenerated (and re-billed) needlessly."""

    id: int | None = None
    kind: str  # "activity" | "weekly"
    subject: str  # activity id or ISO week start date
    text: str
    provider: str
    model: str | None = None
    created_at: datetime


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6_371_008.8
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = p2 - p1
    dlmb = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(a))


def finalize_activity(activity: Activity) -> Activity:
    """Fill derived summary metrics from the track when they are missing or zero."""
    track = activity.track
    if not track:
        return activity

    if activity.duration_s <= 0 and len(track) > 1:
        activity.duration_s = (track[-1].time - track[0].time).total_seconds()

    if activity.distance_m <= 0:
        cumulative = [p.distance_m for p in track if p.distance_m is not None]
        if cumulative:
            activity.distance_m = max(cumulative)
        else:
            positioned = [p for p in track if p.has_position]
            activity.distance_m = sum(
                haversine_m(a.lat, a.lon, b.lat, b.lon)  # type: ignore[arg-type]
                for a, b in zip(positioned, positioned[1:], strict=False)
            )

    if activity.elevation_gain_m <= 0:
        activity.elevation_gain_m = elevation_gain([p.ele for p in track if p.ele is not None])

    hrs = [p.hr for p in track if p.hr]
    if hrs:
        activity.avg_hr = activity.avg_hr or round(sum(hrs) / len(hrs))
        activity.max_hr = activity.max_hr or max(hrs)
    return activity


def elevation_gain(elevations: list[float], threshold_m: float = 1.0) -> float:
    """Total ascent with a small hysteresis threshold so GPS/barometer noise is not counted."""
    if not elevations:
        return 0.0
    gain = 0.0
    reference = elevations[0]
    for ele in elevations[1:]:
        if ele - reference >= threshold_m:
            gain += ele - reference
            reference = ele
        elif reference - ele >= threshold_m:
            reference = ele
    return gain
