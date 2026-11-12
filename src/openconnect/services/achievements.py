"""Local 'badges'.

Garmin does not expose its badges or challenges through the Developer API, so OpenConnect computes
its own achievements from the data it has stored.
"""

from __future__ import annotations

from collections.abc import Callable
from datetime import date, timedelta

from openconnect.domain import Activity, DailySummary, Sport
from openconnect.storage import Repository

HALF_MARATHON_M = 21_097.5
EVEREST_M = 8_848.0
STEP_FLOOR = 10_000
ALL_TIME = (date(1900, 1, 1), date(2999, 12, 31))


def _result(id_: str, name: str, description: str, progress: float, unlocked_on: date | None):
    return {
        "id": id_,
        "name": name,
        "description": description,
        "unlocked": unlocked_on is not None,
        "unlocked_on": unlocked_on,
        "progress": 1.0 if unlocked_on else round(min(max(progress, 0.0), 1.0), 4),
    }


def _single_distance(pool: list[Activity], metres: float) -> tuple[date | None, float]:
    """First activity that reaches ``metres`` and how close the best attempt came."""
    hit = next((a for a in pool if a.distance_m >= metres), None)
    best = max((a.distance_m for a in pool), default=0.0)
    return (hit.start_time.date() if hit else None), best / metres


def _cumulative(
    activities: list[Activity], value: Callable[[Activity], float], target: float
) -> tuple[date | None, float]:
    total = 0.0
    for a in activities:
        total += value(a)
        if total >= target:
            return a.start_time.date(), 1.0
    return None, total / target


def _streak(
    dailies: list[DailySummary], meets_goal: Callable[[DailySummary], bool], length: int
) -> tuple[date | None, float]:
    """The day a run of ``length`` consecutive goal days completes, or the best progress."""
    best = run = 0
    previous: date | None = None
    for day in dailies:
        if not meets_goal(day):
            run, previous = 0, None
            continue
        consecutive = previous is not None and day.date - previous == timedelta(days=1)
        run = run + 1 if consecutive else 1
        previous = day.date
        best = max(best, run)
        if run >= length:
            return day.date, 1.0
    return None, best / length


def compute_achievements(repo: Repository) -> list[dict]:
    activities = sorted(repo.list_activities(limit=100_000), key=lambda a: a.start_time)
    dailies = repo.list_daily(*ALL_TIME)
    sleeps = repo.list_sleep(*ALL_TIME)
    runs = [a for a in activities if a.sport is Sport.RUNNING]
    rides = [a for a in activities if a.sport is Sport.CYCLING]

    first = activities[0].start_time.date() if activities else None
    results = [_result("first_activity", "Player One", "Record your first activity.", 0.0, first)]

    on, progress = _single_distance(runs, 10_000)
    results.append(_result("first_10k_run", "10K Club", "Run 10 km in one activity.", progress, on))
    on, progress = _single_distance(runs, HALF_MARATHON_M)
    results.append(
        _result("half_marathon", "Half Marathon", "Run 21.1 km in one go.", progress, on)
    )
    on, progress = _single_distance(rides, 100_000)
    results.append(_result("century_ride", "Century Ride", "Ride 100 km in one go.", progress, on))
    on, progress = _cumulative(activities, lambda a: a.distance_m, 100_000)
    results.append(
        _result(
            "distance_100k", "100 km Total", "Cover 100 km across all activities.", progress, on
        )
    )
    on, progress = _cumulative(activities, lambda a: a.elevation_gain_m, EVEREST_M)
    results.append(_result("climber", "Everest Climber", "Climb 8,848 m in total.", progress, on))

    step_day = next((d for d in dailies if d.steps >= STEP_FLOOR), None)
    best_steps = max((d.steps for d in dailies), default=0)
    results.append(
        _result(
            "steps_10k",
            "10K Steps",
            "Take 10,000 steps in a day.",
            best_steps / STEP_FLOOR,
            step_day.date if step_day else None,
        )
    )
    on, progress = _streak(dailies, lambda d: d.steps >= (d.steps_goal or STEP_FLOOR), 7)
    results.append(
        _result(
            "step_streak_7", "Seven-Day Streak", "Hit your step goal 7 days running.", progress, on
        )
    )
    rested = next((s for s in sleeps if s.duration_s >= 8 * 3600), None)
    best_sleep = max((s.duration_s for s in sleeps), default=0)
    results.append(
        _result(
            "sleep_8h",
            "Full Recharge",
            "Sleep at least 8 hours.",
            best_sleep / (8 * 3600),
            rested.date if rested else None,
        )
    )
    return results
