from datetime import UTC, datetime, timedelta

from openconnect.domain import (
    Activity,
    Sport,
    TrackPoint,
    elevation_gain,
    ensure_utc,
    finalize_activity,
    haversine_m,
)
from tests.factories import make_activity


def test_ensure_utc_assumes_naive_is_utc_and_converts_aware():
    naive = datetime(2026, 1, 1, 12, 0)
    assert ensure_utc(naive).tzinfo is UTC
    plus2 = datetime(2026, 1, 1, 14, 0, tzinfo=timezone_plus(2))
    assert ensure_utc(plus2) == datetime(2026, 1, 1, 12, 0, tzinfo=UTC)


def timezone_plus(hours: int):
    from datetime import timezone

    return timezone(timedelta(hours=hours))


def test_haversine_known_distance():
    # Amsterdam Centraal -> Utrecht Centraal is roughly 34.7 km as the crow flies
    d = haversine_m(52.3791, 4.9003, 52.0894, 5.1101)
    assert 33_000 < d < 36_000


def test_elevation_gain_ignores_noise_below_threshold():
    noisy = [10.0, 10.4, 9.8, 10.3, 9.9, 10.2]
    assert elevation_gain(noisy) == 0.0
    climb = [10.0, 12.0, 11.5, 14.0]
    # the 0.5 m dip is below the threshold, so the climb 10 -> 12 -> 14 counts as 4 m
    assert elevation_gain(climb) == 4.0


def test_activity_pace_and_speed():
    act = make_activity(distance_m=5000, duration_s=1500)
    assert act.avg_speed_mps == 5000 / 1500
    assert round(act.pace_s_per_km) == 300


def test_activity_pace_is_none_without_distance():
    act = make_activity(distance_m=0, duration_s=100)
    assert act.pace_s_per_km is None


def test_finalize_derives_missing_metrics_from_track():
    start = datetime(2026, 5, 2, 7, 0, tzinfo=UTC)
    track = [
        TrackPoint(
            time=start + timedelta(seconds=i * 10),
            lat=52.0 + i * 0.001,
            lon=5.0,
            ele=10 + i * 3,
            hr=140 + i,
        )
        for i in range(6)
    ]
    act = Activity(id="x", start_time=start, sport=Sport.RUNNING, track=track)
    finalize_activity(act)
    assert act.duration_s == 50
    assert 540 < act.distance_m < 570  # 5 * 0.001 deg lat ~ 556 m
    assert act.elevation_gain_m == 15
    assert act.avg_hr == 142  # mean 142.5, Python rounds half to even
    assert act.max_hr == 145


def test_finalize_prefers_cumulative_distance_when_present():
    start = datetime(2026, 5, 2, 7, 0, tzinfo=UTC)
    track = [TrackPoint(time=start + timedelta(seconds=i), distance_m=i * 3.0) for i in range(5)]
    act = finalize_activity(Activity(id="y", start_time=start, track=track))
    assert act.distance_m == 12.0


def test_start_time_is_normalised_to_utc():
    act = Activity(id="z", start_time=datetime(2026, 1, 1, 10, 0))
    assert act.start_time.tzinfo is UTC
