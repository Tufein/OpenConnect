from __future__ import annotations

from collections.abc import Callable
from datetime import UTC, date, datetime, time, timedelta

from pydantic import BaseModel, Field

from openconnect.domain import Activity, DailySummary
from openconnect.services.sync import utcnow
from openconnect.storage import Repository


def week_start(day: date) -> date:
    return day - timedelta(days=day.weekday())


class SportTotals(BaseModel):
    count: int = 0
    distance_m: float = 0.0
    duration_s: float = 0.0


class WeeklySummary(BaseModel):
    week_start: date
    week_end: date
    activity_count: int = 0
    total_distance_m: float = 0.0
    total_duration_s: float = 0.0
    total_elevation_m: float = 0.0
    total_calories: int = 0
    by_sport: dict[str, SportTotals] = Field(default_factory=dict)
    days_with_data: int = 0
    total_steps: int = 0
    avg_steps: float | None = None
    goal_days: int = 0
    intensity_minutes: int = 0
    avg_resting_hr: float | None = None
    avg_stress: float | None = None
    avg_body_battery_high: float | None = None
    avg_sleep_hours: float | None = None
    avg_sleep_score: float | None = None


def _mean(values: list[float | int]) -> float | None:
    return sum(values) / len(values) if values else None


def _day_bounds(start: date, end_inclusive: date) -> tuple[datetime, datetime]:
    return (
        datetime.combine(start, time.min, UTC),
        datetime.combine(end_inclusive + timedelta(days=1), time.min, UTC),
    )


class DashboardService:
    def __init__(self, repo: Repository, clock: Callable[[], datetime] = utcnow) -> None:
        self.repo = repo
        self.clock = clock

    def _activities_between(self, start: date, end: date) -> list[Activity]:
        lo, hi = _day_bounds(start, end)
        return self.repo.list_activities(start=lo, end=hi, limit=10_000)

    def summary(self, start: date, end: date) -> WeeklySummary:
        """Totals and averages for an inclusive date range (used for weeks and rolling windows)."""
        summary = WeeklySummary(week_start=start, week_end=end)
        for a in self._activities_between(start, end):
            summary.activity_count += 1
            summary.total_distance_m += a.distance_m
            summary.total_duration_s += a.duration_s
            summary.total_elevation_m += a.elevation_gain_m
            summary.total_calories += a.calories or 0
            totals = summary.by_sport.setdefault(a.sport.value, SportTotals())
            totals.count += 1
            totals.distance_m += a.distance_m
            totals.duration_s += a.duration_s

        dailies = self.repo.list_daily(start, end)
        summary.days_with_data = len(dailies)
        summary.total_steps = sum(d.steps for d in dailies)
        summary.avg_steps = _mean([d.steps for d in dailies])
        summary.goal_days = sum(1 for d in dailies if d.steps >= (d.steps_goal or 10_000))
        summary.intensity_minutes = sum(d.intensity_minutes for d in dailies)
        summary.avg_resting_hr = _mean([d.resting_hr for d in dailies if d.resting_hr])
        summary.avg_stress = _mean([d.avg_stress for d in dailies if d.avg_stress is not None])
        summary.avg_body_battery_high = _mean(
            [d.body_battery_high for d in dailies if d.body_battery_high is not None]
        )
        sleeps = self.repo.list_sleep(start, end)
        summary.avg_sleep_hours = _mean([s.hours for s in sleeps if s.duration_s])
        summary.avg_sleep_score = _mean([s.score for s in sleeps if s.score is not None])
        return summary

    def weekly_summary(self, any_day_in_week: date) -> WeeklySummary:
        monday = week_start(any_day_in_week)
        return self.summary(monday, monday + timedelta(days=6))

    def trend_series(self, start: date, end: date) -> list[dict]:
        """One row per day in [start, end]; days without data have ``None`` values."""
        dailies: dict[date, DailySummary] = {d.date: d for d in self.repo.list_daily(start, end)}
        sleeps = {s.date: s for s in self.repo.list_sleep(start, end)}
        distance: dict[date, float] = {}
        for a in self._activities_between(start, end):
            distance[a.start_time.date()] = distance.get(a.start_time.date(), 0.0) + a.distance_m
        rows, day = [], start
        while day <= end:
            d, s = dailies.get(day), sleeps.get(day)
            rows.append(
                {
                    "date": day,
                    "steps": d.steps if d else None,
                    "resting_hr": d.resting_hr if d else None,
                    "avg_stress": d.avg_stress if d else None,
                    "body_battery_high": d.body_battery_high if d else None,
                    "sleep_hours": round(s.hours, 2) if s and s.duration_s else None,
                    "sleep_score": s.score if s else None,
                    "distance_m": distance.get(day, 0.0),
                }
            )
            day += timedelta(days=1)
        return rows

    def overview(self, trend_days: int = 30) -> dict:
        today = self.clock().date()
        last7 = self.summary(today - timedelta(days=6), today)
        previous7 = self.summary(today - timedelta(days=13), today - timedelta(days=7))
        change = None
        if last7.avg_steps is not None and previous7.avg_steps:
            change = round((last7.avg_steps - previous7.avg_steps) / previous7.avg_steps * 100, 1)
        recent_days = self.repo.list_daily(today - timedelta(days=6), today)
        return {
            "today": recent_days[-1] if recent_days else None,
            "this_week": self.weekly_summary(today),
            "last7": last7.model_dump(),
            "previous7": previous7.model_dump(),
            "steps_change_pct": change,
            "trend": self.trend_series(today - timedelta(days=trend_days - 1), today),
            "recent_activities": self.repo.list_activities(limit=5),
        }
