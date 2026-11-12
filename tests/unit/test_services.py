from datetime import UTC, date, datetime, timedelta

import pytest

from openconnect.domain import Sport
from openconnect.services.achievements import compute_achievements
from openconnect.services.dashboard import DashboardService, week_start
from openconnect.services.records import personal_records
from openconnect.storage import Database, Repository
from tests.factories import make_activity, make_daily, make_sleep

MONDAY = date(2026, 4, 27)


@pytest.fixture
def repo():
    return Repository(Database(":memory:"))


def at(day: date, hour: int = 7) -> datetime:
    return datetime(day.year, day.month, day.day, hour, tzinfo=UTC)


def put(repo, id, day_offset=0, sport=Sport.RUNNING, **kw):
    repo.upsert_activity(
        make_activity(id=id, sport=sport, start=at(MONDAY + timedelta(days=day_offset)), n=3, **kw)
    )


def seed_week(repo, monday=MONDAY):
    repo.upsert_activity(make_activity(id="a1", start=at(monday + timedelta(days=1)), n=3,
                                       distance_m=5000, duration_s=1500, elevation_gain_m=20,
                                       calories=400, avg_hr=150))  # fmt: skip
    repo.upsert_activity(make_activity(id="a2", start=at(monday + timedelta(days=3)), n=3,
                                       distance_m=8000, duration_s=2400, elevation_gain_m=40,
                                       calories=600, avg_hr=160))  # fmt: skip
    repo.upsert_activity(make_activity(id="a3", sport=Sport.CYCLING,
                                       start=at(monday + timedelta(days=5)), n=3,
                                       distance_m=40000, duration_s=5400, elevation_gain_m=300,
                                       calories=1100, avg_hr=140))  # fmt: skip
    for i in range(7):
        day = monday + timedelta(days=i)
        repo.upsert_daily(
            make_daily(
                day,
                steps=8000 + i * 1000,
                resting_hr=50 + i,
                avg_stress=30 + i,
                body_battery_high=80 + i,
            )
        )
        repo.upsert_sleep(make_sleep(day, hours=7 + (i % 2), score=70 + i))


def test_week_start_is_monday():
    assert week_start(date(2026, 4, 29)) == MONDAY  # Wednesday
    assert week_start(MONDAY) == MONDAY
    assert week_start(date(2026, 5, 3)) == MONDAY  # Sunday


def test_weekly_summary_totals_and_breakdown(repo):
    seed_week(repo)
    repo.upsert_activity(make_activity(id="outside", start=at(MONDAY + timedelta(days=7)), n=3))
    w = DashboardService(repo).weekly_summary(MONDAY)
    assert w.week_start == MONDAY and w.week_end == MONDAY + timedelta(days=6)
    assert w.activity_count == 3
    assert w.total_distance_m == 53000
    assert w.total_duration_s == 1500 + 2400 + 5400
    assert w.total_elevation_m == 360
    assert w.total_calories == 2100
    assert w.by_sport["running"].count == 2 and w.by_sport["running"].distance_m == 13000
    assert w.by_sport["cycling"].distance_m == 40000


def test_weekly_summary_health_averages(repo):
    seed_week(repo)
    w = DashboardService(repo).weekly_summary(MONDAY)
    assert w.days_with_data == 7
    assert w.total_steps == sum(8000 + i * 1000 for i in range(7))
    assert w.avg_steps == 11000
    assert w.avg_resting_hr == 53
    assert w.avg_stress == 33
    assert w.avg_sleep_score == 73
    assert 7.4 < w.avg_sleep_hours < 7.6
    assert w.goal_days == 5  # steps >= 10000 on Wed..Sun
    assert w.intensity_minutes == 7 * (20 + 2 * 5)
    assert w.avg_body_battery_high == 83


def test_weekly_summary_for_an_empty_week(repo):
    w = DashboardService(repo).weekly_summary(MONDAY)
    assert w.activity_count == 0 and w.total_distance_m == 0 and w.by_sport == {}
    assert w.avg_steps is None and w.avg_sleep_hours is None and w.days_with_data == 0


def test_weekly_summary_normalises_any_day_to_its_monday(repo):
    seed_week(repo)
    assert DashboardService(repo).weekly_summary(date(2026, 5, 1)).week_start == MONDAY


def test_trend_series_has_one_row_per_day_with_gaps_as_none(repo):
    seed_week(repo)
    rows = DashboardService(repo).trend_series(
        MONDAY - timedelta(days=1), MONDAY + timedelta(days=2)
    )
    assert [r["date"] for r in rows] == [
        MONDAY - timedelta(days=1) + timedelta(days=i) for i in range(4)
    ]
    assert rows[0]["steps"] is None and rows[0]["sleep_hours"] is None
    assert rows[1]["steps"] == 8000 and rows[2]["steps"] == 9000
    assert rows[2]["distance_m"] == 5000  # activity distance that day


def test_overview_compares_last_7_days_with_the_previous_7(repo):
    for i in range(14):
        day = MONDAY + timedelta(days=i)
        repo.upsert_daily(make_daily(day, steps=5000 if i < 7 else 10000))
    ov = DashboardService(repo, clock=lambda: at(MONDAY + timedelta(days=13), 20)).overview()
    assert ov["today"].date == MONDAY + timedelta(days=13)
    assert ov["last7"]["avg_steps"] == 10000 and ov["previous7"]["avg_steps"] == 5000
    assert ov["steps_change_pct"] == 100.0
    assert len(ov["trend"]) == 30


def test_overview_without_data_does_not_crash(repo):
    ov = DashboardService(repo, clock=lambda: at(MONDAY)).overview()
    assert ov["today"] is None and ov["steps_change_pct"] is None
    assert ov["recent_activities"] == []


# ------------------------------------------------------------ records


def test_personal_records_per_sport(repo):
    put(repo, "r1", 0, distance_m=5000, duration_s=1500, elevation_gain_m=10)
    put(repo, "r2", 1, distance_m=10000, duration_s=3500, elevation_gain_m=80)
    put(repo, "r3", 2, distance_m=3000, duration_s=700, elevation_gain_m=5)
    put(repo, "c1", 0, Sport.CYCLING, distance_m=60000, duration_s=7200)
    records = personal_records(repo)
    run = records["running"]
    assert run["longest_distance"]["activity_id"] == "r2"
    assert run["longest_distance"]["value"] == 10000
    assert run["longest_duration"]["activity_id"] == "r2"
    assert run["most_elevation"]["value"] == 80
    assert run["fastest_pace"]["activity_id"] == "r3"  # 700 s / 3 km = 233 s/km
    assert round(run["fastest_pace"]["value"]) == 233
    assert records["cycling"]["longest_distance"]["value"] == 60000
    assert "fastest_pace" not in records["cycling"]  # pace is a running metric


def test_fastest_pace_ignores_tiny_activities(repo):
    put(repo, "tiny", 0, distance_m=200, duration_s=20)
    put(repo, "real", 1, distance_m=5000, duration_s=1800)
    assert personal_records(repo)["running"]["fastest_pace"]["activity_id"] == "real"


def test_personal_records_empty(repo):
    assert personal_records(repo) == {}


# ------------------------------------------------------------ achievements


def by_id(items):
    return {a["id"]: a for a in items}


def test_achievements_start_locked_with_zero_progress(repo):
    ach = by_id(compute_achievements(repo))
    assert ach and all(not a["unlocked"] for a in ach.values())
    assert ach["first_activity"]["progress"] == 0


def test_activity_based_achievements_unlock(repo):
    put(repo, "half", 0, distance_m=21500, duration_s=7000)
    put(repo, "ride", 1, Sport.CYCLING, distance_m=101000, duration_s=14000)
    ach = by_id(compute_achievements(repo))
    assert ach["first_activity"]["unlocked"] and ach["first_activity"]["unlocked_on"] == MONDAY
    assert ach["first_10k_run"]["unlocked"] and ach["half_marathon"]["unlocked"]
    assert ach["century_ride"]["unlocked"]
    assert ach["century_ride"]["unlocked_on"] == MONDAY + timedelta(days=1)
    assert ach["distance_100k"]["unlocked"]


def test_cumulative_progress_is_partial_until_reached(repo):
    put(repo, "a", 0, distance_m=25000, duration_s=9000)
    ach = by_id(compute_achievements(repo))
    assert not ach["distance_100k"]["unlocked"]
    assert ach["distance_100k"]["progress"] == pytest.approx(0.25)


def test_health_achievements(repo):
    for i in range(7):
        repo.upsert_daily(make_daily(MONDAY + timedelta(days=i), steps=10500))
    repo.upsert_sleep(make_sleep(MONDAY, hours=8.5))
    ach = by_id(compute_achievements(repo))
    assert ach["steps_10k"]["unlocked"] and ach["steps_10k"]["unlocked_on"] == MONDAY
    assert ach["step_streak_7"]["unlocked"]
    assert ach["step_streak_7"]["unlocked_on"] == MONDAY + timedelta(days=6)
    assert ach["sleep_8h"]["unlocked"]


def test_streak_requires_consecutive_days(repo):
    for i in range(7):
        steps = 10500 if i != 3 else 2000  # a bad day breaks the streak
        repo.upsert_daily(make_daily(MONDAY + timedelta(days=i), steps=steps))
    assert not by_id(compute_achievements(repo))["step_streak_7"]["unlocked"]
