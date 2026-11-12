from datetime import UTC, date, datetime, timedelta

from openconnect.domain import Sport
from openconnect.garmin import mapper
from openconnect.garmin.fake import FakeGarminSource

TODAY = datetime(2026, 5, 31, 12, 0, tzinfo=UTC)


def test_fake_source_is_deterministic():
    a = FakeGarminSource(seed=1, today=TODAY).fetch(TODAY - timedelta(days=14), TODAY)
    b = FakeGarminSource(seed=1, today=TODAY).fetch(TODAY - timedelta(days=14), TODAY)
    c = FakeGarminSource(seed=2, today=TODAY).fetch(TODAY - timedelta(days=14), TODAY)
    assert a == b
    assert a != c


def test_fake_payload_has_all_kinds_and_maps_cleanly():
    payload = FakeGarminSource(seed=3, today=TODAY).fetch(TODAY - timedelta(days=28), TODAY)
    assert set(payload) == {"activities", "activityDetails", "dailies", "sleeps", "stressDetails"}
    assert len(payload["dailies"]) >= 27
    assert len(payload["sleeps"]) >= 27
    assert len(payload["activities"]) >= 8
    assert len(payload["activityDetails"]) == len(payload["activities"])

    for item in payload["dailies"]:
        mapper.map_daily(item)
    for item in payload["sleeps"]:
        mapper.map_sleep(item)
    for item in payload["stressDetails"]:
        _, patch = mapper.map_stress_details(item)
        assert patch["body_battery_high"] > patch["body_battery_low"]
    sports = set()
    for item in payload["activityDetails"]:
        act = mapper.map_activity_details(item)
        sports.add(act.sport)
        assert len(act.track) > 20
        assert act.duration_s > 0
        assert act.distance_m > 0 or not act.has_gps  # strength has no GPS track
    assert {Sport.RUNNING, Sport.CYCLING} <= sports


def test_fake_source_only_returns_items_inside_the_window():
    src = FakeGarminSource(seed=3, today=TODAY)
    start, end = TODAY - timedelta(days=7), TODAY - timedelta(days=3)
    payload = src.fetch(start, end)
    for item in payload["activities"]:
        assert start.timestamp() <= item["startTimeInSeconds"] < end.timestamp()
    days = {date.fromisoformat(d["calendarDate"]) for d in payload["dailies"]}
    assert min(days) >= (start - timedelta(days=1)).date()
    assert max(days) <= end.date()


def test_fake_never_invents_future_data_and_ids_are_stable_between_windows():
    src = FakeGarminSource(seed=5, today=TODAY)
    wide = src.fetch(TODAY - timedelta(days=30), TODAY + timedelta(days=30))
    assert all(item["startTimeInSeconds"] <= TODAY.timestamp() for item in wide["activities"])
    narrow = src.fetch(TODAY - timedelta(days=10), TODAY)
    wide_ids = {a["activityId"] for a in wide["activities"]}
    assert {a["activityId"] for a in narrow["activities"]} <= wide_ids
