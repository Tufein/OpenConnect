from __future__ import annotations

from openconnect.ai.facts import kilometre_splits
from openconnect.domain import Activity, Sport, haversine_m
from openconnect.storage import Repository

MIN_PACE_DISTANCE_M = 1000.0
BEST_EFFORT_DISTANCES = (1000.0, 5000.0, 10000.0)


def _distance_points(activity: Activity) -> list[tuple[float, float]]:
    """(timestamp, cumulative metres) pairs, from FIT distances or from GPS."""
    track = activity.track
    if track and all(p.distance_m is not None for p in track):
        return [(p.time.timestamp(), float(p.distance_m)) for p in track]  # type: ignore[arg-type]
    positioned = [p for p in track if p.has_position]
    if len(positioned) < 2:
        return []
    points, total = [], 0.0
    for previous, point in zip(positioned, positioned[1:], strict=False):
        total += haversine_m(previous.lat, previous.lon, point.lat, point.lon)  # type: ignore[arg-type]
        points.append((previous.time.timestamp(), total))
    last = positioned[-1]
    points.append((last.time.timestamp(), total))
    return points


def fastest_segment(activity: Activity, distance_m: float) -> float | None:
    """Fastest time over any continuous stretch of ``distance_m`` metres in one activity."""
    points = _distance_points(activity)
    best: float | None = None
    for index, (start_time, start_distance) in enumerate(points):
        target = start_distance + distance_m
        for end_time, end_distance in points[index + 1 :]:
            if end_distance < target:
                continue
            fraction = (target - start_distance) / (end_distance - start_distance)
            elapsed = (end_time - start_time) * fraction
            best = elapsed if best is None else min(best, elapsed)
            break
    return best


def activity_splits(activity: Activity) -> list[dict]:
    """One row per completed kilometre, with the seconds taken for that kilometre."""
    return [
        {"km": index + 1, "seconds_s": seconds}
        for index, seconds in enumerate(kilometre_splits(activity.track))
    ]


def best_efforts(repo: Repository, sport: Sport = Sport.RUNNING) -> dict[str, dict]:
    """Fastest 1K/5K/10K per sport, found in the stored tracks."""
    result: dict[str, dict] = {}
    for activity in repo.list_activities(limit=100_000, include_track=True):
        if activity.sport is not sport:
            continue
        for distance in BEST_EFFORT_DISTANCES:
            seconds = fastest_segment(activity, distance)
            if seconds is None:
                continue
            key = str(int(distance))
            if key not in result or seconds < result[key]["seconds_s"]:
                result[key] = {
                    "seconds_s": round(seconds),
                    "activity_id": activity.id,
                    "name": activity.name,
                    "date": activity.start_time.date().isoformat(),
                }
    return result


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
