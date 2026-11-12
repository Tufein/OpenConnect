from __future__ import annotations

from openconnect.domain import Activity, Sport
from openconnect.storage import Repository

MIN_PACE_DISTANCE_M = 1000.0


def _entry(a: Activity, value: float) -> dict:
    return {
        "value": value,
        "activity_id": a.id,
        "name": a.name,
        "date": a.start_time.date(),
    }


def personal_records(repo: Repository) -> dict[str, dict[str, dict]]:
    """Best activity-level results per sport (longest, most climbing, fastest running pace)."""
    by_sport: dict[Sport, list[Activity]] = {}
    for a in repo.list_activities(limit=100_000):
        by_sport.setdefault(a.sport, []).append(a)

    records: dict[str, dict[str, dict]] = {}
    for sport, activities in by_sport.items():
        entries: dict[str, dict] = {}
        for key, attr in (
            ("longest_distance", "distance_m"),
            ("longest_duration", "duration_s"),
            ("most_elevation", "elevation_gain_m"),
        ):
            best = max(activities, key=lambda a, attr=attr: getattr(a, attr))
            if getattr(best, attr) > 0:
                entries[key] = _entry(best, getattr(best, attr))
        if sport is Sport.RUNNING:
            paced = [
                a
                for a in activities
                if a.distance_m >= MIN_PACE_DISTANCE_M and a.pace_s_per_km is not None
            ]
            if paced:
                best = min(paced, key=lambda a: a.pace_s_per_km)  # type: ignore[arg-type,return-value]
                entries["fastest_pace"] = _entry(best, best.pace_s_per_km)  # type: ignore[arg-type]
        if entries:
            records[sport.value] = entries
    return records
