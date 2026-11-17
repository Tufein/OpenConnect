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
    assert set(payload) == {
        "activities",
        "activityDetails",
        "dailies",
        "sleeps",
        "stressDetails",
        "hrv",
        "allDayRespiration",
        "pulseox",
        "bodyComps",
        "bloodPressures",
        "skinTemp",
        "userMetrics",
        "healthSnapshot",
        "epochSummaries",
    }
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
    for item in payload["hrv"]:
        _, patch = mapper.map_hrv(item)
        assert patch["hrv_last_night_ms"] > 0 and patch["hrv_status"]
    for item in payload["pulseox"]:
        _, patch = mapper.map_pulse_ox(item)
        assert 90 <= patch["spo2_avg_pct"] <= 100
        assert patch["spo2_min_pct"] <= patch["spo2_avg_pct"]
    for item in payload["allDayRespiration"]:
        _, patch = mapper.map_respiration(item)
        assert 8 <= patch["respiration_avg_brpm"] <= 25
        assert patch["respiration_min_brpm"] <= patch["respiration_max_brpm"]
    assert payload["bodyComps"]
    for item in payload["bodyComps"]:
        body = mapper.map_body_composition(item)
        assert 60 < body.weight_kg < 90 and body.bmi and body.device_name
    for item in payload["bloodPressures"]:
        _, patch = mapper.map_extra_metrics(item)
        assert 100 < patch["systolic_bp"] < 140 and patch["diastolic_bp"] > 60
    for item in payload["skinTemp"]:
        _, patch = mapper.map_extra_metrics(item)
        assert 30 < patch["skin_temp_c"] < 34
    for item in payload["userMetrics"]:
        _, patch = mapper.map_extra_metrics(item)
        assert 40 < patch["vo2max"] < 55 and 25 < patch["fitness_age"] < 40
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
