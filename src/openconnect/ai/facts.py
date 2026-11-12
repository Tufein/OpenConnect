"""Build the structured facts that AI (or template) reports are written from.

Reports are generated from these facts only, never from raw database rows, which keeps prompts
small, makes the input cacheable/hashable, and limits what leaves the machine.
"""

from __future__ import annotations

from datetime import date, timedelta

from openconnect.domain import Activity, Sport, TrackPoint, haversine_m
from openconnect.services.dashboard import DashboardService, week_start
from openconnect.storage import Repository

PACE_SPORTS = {Sport.RUNNING, Sport.WALKING, Sport.HIKING}
MAX_SPLITS = 60


def format_pace(seconds_per_km: float | None) -> str | None:
    if not seconds_per_km:
        return None
    total = round(seconds_per_km)
    return f"{total // 60}:{total % 60:02d}"


def _cumulative_distance(track: list[TrackPoint]) -> list[float] | None:
    if track and all(p.distance_m is not None for p in track):
        return [p.distance_m for p in track]  # type: ignore[misc]
    positioned = [p for p in track if p.has_position]
    if len(positioned) < 2:
        return None
    distances, total = [0.0], 0.0
    for a, b in zip(positioned, positioned[1:], strict=False):
        total += haversine_m(a.lat, a.lon, b.lat, b.lon)  # type: ignore[arg-type]
        distances.append(total)
    return distances


def kilometre_splits(track: list[TrackPoint]) -> list[int]:
    """Seconds taken for each completed kilometre, interpolating the crossing instants."""
    distances = _cumulative_distance(track)
    if not distances:
        return []
    points = track if len(distances) == len(track) else [p for p in track if p.has_position]
    splits: list[int] = []
    boundary, last_t = 1000.0, points[0].time.timestamp()
    for i in range(1, len(points)):
        d0, d1 = distances[i - 1], distances[i]
        t0, t1 = points[i - 1].time.timestamp(), points[i].time.timestamp()
        while d1 >= boundary and d1 > d0 and len(splits) < MAX_SPLITS:
            crossing = t0 + (boundary - d0) / (d1 - d0) * (t1 - t0)
            splits.append(round(crossing - last_t))
            last_t = crossing
            boundary += 1000.0
    return splits


def _hr_halves(track: list[TrackPoint]) -> dict[str, int]:
    with_hr = [p for p in track if p.hr]
    if len(with_hr) < 4:
        return {}
    start, end = track[0].time, track[-1].time
    midpoint = start + (end - start) / 2
    first = [p.hr for p in with_hr if p.time < midpoint]
    second = [p.hr for p in with_hr if p.time >= midpoint]
    if not first or not second:
        return {}
    return {
        "first_half_avg": round(sum(first) / len(first)),
        "second_half_avg": round(sum(second) / len(second)),
    }


def activity_facts(repo: Repository, activity: Activity) -> dict:
    previous = [
        a
        for a in repo.list_activities(sport=activity.sport, end=activity.start_time, limit=1000)
        if a.id != activity.id
    ]
    context: dict = {"is_longest_of_sport": False}
    if previous:
        recent = previous[:5]
        paces = [a.pace_s_per_km for a in recent if a.pace_s_per_km]
        context["previous_same_sport"] = {
            "count": len(previous),
            "avg_distance_km": round(sum(a.distance_m for a in recent) / len(recent) / 1000, 2),
            "avg_pace_min_per_km": format_pace(sum(paces) / len(paces)) if paces else None,
        }
        context["is_longest_of_sport"] = activity.distance_m > max(a.distance_m for a in previous)

    speed = activity.avg_speed_mps
    return {
        "name": activity.name,
        "sport": activity.sport.value,
        "date": activity.start_time.date().isoformat(),
        "duration_min": round(activity.duration_s / 60, 1),
        "distance_km": round(activity.distance_m / 1000, 2),
        "pace_min_per_km": format_pace(activity.pace_s_per_km)
        if activity.sport in PACE_SPORTS
        else None,
        "avg_speed_kmh": round(speed * 3.6, 1)
        if speed and activity.sport not in PACE_SPORTS
        else None,
        "avg_hr": activity.avg_hr,
        "max_hr": activity.max_hr,
        "elevation_gain_m": round(activity.elevation_gain_m),
        "calories": activity.calories,
        "device": activity.device_name,
        "splits_s_per_km": kilometre_splits(activity.track),
        "hr": _hr_halves(activity.track),
        "context": context,
    }


def weekly_facts(dashboard: DashboardService, any_day: date) -> dict:
    monday = week_start(any_day)
    sunday = monday + timedelta(days=6)
    this_week = dashboard.weekly_summary(monday)
    previous = dashboard.weekly_summary(monday - timedelta(days=7))
    activities = dashboard._activities_between(monday, sunday)
    return {
        "week_start": monday.isoformat(),
        "week_end": sunday.isoformat(),
        "this_week": this_week.model_dump(mode="json"),
        "previous_week": previous.model_dump(mode="json"),
        "activities": [
            {
                "name": a.name,
                "sport": a.sport.value,
                "date": a.start_time.date().isoformat(),
                "distance_km": round(a.distance_m / 1000, 2),
                "duration_min": round(a.duration_s / 60, 1),
                "avg_hr": a.avg_hr,
            }
            for a in sorted(activities, key=lambda a: a.start_time)[:30]
        ],
    }
