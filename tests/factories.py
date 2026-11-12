"""Builders for deterministic test data."""

from __future__ import annotations

import math
from datetime import UTC, date, datetime, timedelta

from openconnect.domain import Activity, DailySummary, SleepSummary, Sport, TrackPoint


def make_track(
    n: int = 60,
    start: datetime | None = None,
    lat0: float = 52.3702,
    lon0: float = 4.8952,
    with_hr: bool = True,
) -> list[TrackPoint]:
    """A gently curving ~5 m/s track, one point per second, climbing a small hill."""
    start = start or datetime(2026, 5, 2, 7, 0, 0, tzinfo=UTC)
    points = []
    for i in range(n):
        points.append(
            TrackPoint(
                time=start + timedelta(seconds=i),
                lat=lat0 + i * 0.00004,
                lon=lon0 + math.sin(i / 10) * 0.00005 + i * 0.00002,
                ele=5.0 + 0.2 * i,
                hr=140 + (i % 20) if with_hr else None,
                cadence=84 if with_hr else None,
            )
        )
    return points


def make_activity(
    id: str = "garmin:1001",
    sport: Sport = Sport.RUNNING,
    start: datetime | None = None,
    n: int = 60,
    **overrides,
) -> Activity:
    start = start or datetime(2026, 5, 2, 7, 0, 0, tzinfo=UTC)
    data = dict(
        id=id,
        source="garmin",
        external_id=id.split(":")[-1],
        name="Morning Run",
        sport=sport,
        start_time=start,
        duration_s=float(n - 1),
        distance_m=5000.0,
        elevation_gain_m=12.0,
        avg_hr=150,
        max_hr=159,
        calories=420,
        device_name="Forerunner 965",
        track=make_track(n, start),
    )
    data.update(overrides)
    return Activity(**data)


def make_daily(day: date, steps: int = 8000, **overrides) -> DailySummary:
    data = dict(
        date=day,
        steps=steps,
        steps_goal=10000,
        distance_m=steps * 0.75,
        active_kcal=steps // 25,
        floors_climbed=8,
        moderate_intensity_min=20,
        vigorous_intensity_min=5,
        resting_hr=52,
        min_hr=48,
        max_hr=150,
        avg_stress=30,
        max_stress=80,
        body_battery_high=90,
        body_battery_low=20,
    )
    data.update(overrides)
    return DailySummary(**data)


def make_sleep(day: date, hours: float = 7.5, score: int | None = 80) -> SleepSummary:
    total = int(hours * 3600)
    return SleepSummary(
        date=day,
        duration_s=total,
        deep_s=int(total * 0.18),
        light_s=int(total * 0.52),
        rem_s=int(total * 0.22),
        awake_s=int(total * 0.08),
        score=score,
    )
