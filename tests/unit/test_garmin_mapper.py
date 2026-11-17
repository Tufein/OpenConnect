from datetime import UTC, date, datetime

import pytest

from openconnect.domain import Sport
from openconnect.garmin import mapper

ACTIVITY = {
    "summaryId": "sd1001-detail",
    "activityId": 1001,
    "activityName": "Morning Run",
    "activityType": "RUNNING",
    "startTimeInSeconds": 1777705200,
    "startTimeOffsetInSeconds": 7200,
    "durationInSeconds": 1800,
    "distanceInMeters": 5000.0,
    "averageHeartRateInBeatsPerMinute": 150,
    "maxHeartRateInBeatsPerMinute": 172,
    "activeKilocalories": 420,
    "totalElevationGainInMeters": 35.4,
    "deviceName": "Forerunner 965",
}

DAILY = {
    "summaryId": "d1",
    "calendarDate": "2026-05-02",
    "steps": 8123,
    "stepsGoal": 10000,
    "distanceInMeters": 6200.5,
    "activeKilocalories": 410,
    "floorsClimbed": 9,
    "moderateIntensityDurationInSeconds": 1200,
    "vigorousIntensityDurationInSeconds": 300,
    "minHeartRateInBeatsPerMinute": 48,
    "maxHeartRateInBeatsPerMinute": 150,
    "restingHeartRateInBeatsPerMinute": 52,
    "averageStressLevel": 31,
    "maxStressLevel": 88,
}

SLEEP = {
    "summaryId": "s1",
    "calendarDate": "2026-05-02",
    "durationInSeconds": 27000,
    "deepSleepDurationInSeconds": 5400,
    "lightSleepDurationInSeconds": 14400,
    "remSleepInSeconds": 5400,
    "awakeDurationInSeconds": 1800,
    "overallSleepScore": {"value": 84, "qualifierKey": "GOOD"},
}

HRV = {
    "summaryId": "h1",
    "calendarDate": "2026-05-02",
    "lastNightAvg": 62,
    "weeklyAvg": 58,
    "status": "BALANCED",
}

PULSE_OX = {
    "summaryId": "px1",
    "calendarDate": "2026-05-02",
    "timeOffsetSpo2Values": {"0": 96, "7200": 94, "14400": -1, "21600": 98},
}

RESPIRATION = {
    "summaryId": "r1",
    "calendarDate": "2026-05-02",
    "timeOffsetRespirationValues": {"0": 14.0, "7200": 16.0, "14400": 15.0},
}

BODY = {
    "summaryId": "bc1",
    "calendarDate": "2026-05-02",
    "measurementTimeInSeconds": 1777705200,
    "weightInGrams": 74200,
    "bodyFatInPercent": 18.4,
    "bodyMassIndex": 22.9,
    "muscleMassInGrams": 31900,
    "bodyWaterInPercent": 55.1,
    "boneMassInGrams": 3200,
    "visceralFatInPercent": 8.0,
    "basalMetabolicRateInKilocalories": 1650,
    "deviceName": "Index S2",
}


def test_map_activity_basic_fields():
    act = mapper.map_activity(ACTIVITY)
    assert act.id == "garmin:1001"
    assert act.source == "garmin" and act.external_id == "1001"
    assert act.name == "Morning Run"
    assert act.sport is Sport.RUNNING
    assert act.start_time == datetime.fromtimestamp(1777705200, UTC)
    assert act.duration_s == 1800 and act.distance_m == 5000.0
    assert act.avg_hr == 150 and act.max_hr == 172 and act.calories == 420
    assert act.elevation_gain_m == 35.4
    assert act.device_name == "Forerunner 965"
    assert act.track == []


def test_map_activity_falls_back_to_summary_id_and_tolerates_missing_fields():
    act = mapper.map_activity({"summaryId": "abc", "startTimeInSeconds": 1777705200})
    assert act.id == "garmin:abc"
    assert act.sport is Sport.OTHER and act.distance_m == 0.0 and act.avg_hr is None


@pytest.mark.parametrize(
    ("garmin_type", "sport"),
    [
        ("RUNNING", Sport.RUNNING), ("TRAIL_RUNNING", Sport.RUNNING),
        ("TREADMILL_RUNNING", Sport.RUNNING), ("CYCLING", Sport.CYCLING),
        ("ROAD_BIKING", Sport.CYCLING), ("MOUNTAIN_BIKING", Sport.CYCLING),
        ("INDOOR_CYCLING", Sport.CYCLING), ("LAP_SWIMMING", Sport.SWIMMING),
        ("OPEN_WATER_SWIMMING", Sport.SWIMMING), ("WALKING", Sport.WALKING),
        ("HIKING", Sport.HIKING), ("STRENGTH_TRAINING", Sport.STRENGTH),
        ("YOGA", Sport.YOGA), ("PADDLEBOARDING", Sport.OTHER), (None, Sport.OTHER),
    ],
)  # fmt: skip
def test_sport_mapping(garmin_type, sport):
    assert mapper.sport_from_garmin(garmin_type) is sport


def test_map_activity_details_builds_track_from_samples():
    item = {
        "summaryId": "sd1001-detail",
        "summary": ACTIVITY,
        "samples": [
            {"startTimeInSeconds": 1777705200, "latitudeInDegree": 52.37, "longitudeInDegree": 4.89,
             "elevationInMeters": 3.2, "heartRate": 120, "bikeCadenceInRPM": 80,
             "powerInWatts": 200, "totalDistanceInMeters": 0.0},
            {"startTimeInSeconds": 1777705210, "latitudeInDegree": 52.3701,
             "longitudeInDegree": 4.8901, "heartRate": 125},
            {"startTimeInSeconds": 1777705220, "heartRate": 130},  # indoor-style sample
        ],
    }  # fmt: skip
    act = mapper.map_activity_details(item)
    assert act.id == "garmin:1001" and act.sport is Sport.RUNNING
    assert len(act.track) == 3
    first, second, third = act.track
    assert (first.lat, first.lon, first.ele, first.hr) == (52.37, 4.89, 3.2, 120)
    assert first.cadence == 80 and first.power == 200 and first.distance_m == 0.0
    assert second.ele is None
    assert third.lat is None and third.hr == 130
    assert act.has_gps


def test_map_activity_details_without_summary_derives_metrics_from_samples():
    item = {
        "summaryId": "x",
        "samples": [
            {"startTimeInSeconds": 1777705200, "latitudeInDegree": 52.0,
             "longitudeInDegree": 5.0},
            {"startTimeInSeconds": 1777705260, "latitudeInDegree": 52.001,
             "longitudeInDegree": 5.0},
        ],
    }  # fmt: skip
    act = mapper.map_activity_details(item)
    assert act.id == "garmin:x"
    assert act.duration_s == 60 and 100 < act.distance_m < 120


def test_map_daily():
    d = mapper.map_daily(DAILY)
    assert d.date == date(2026, 5, 2)
    assert d.steps == 8123 and d.steps_goal == 10000
    assert d.distance_m == 6200.5 and d.active_kcal == 410 and d.floors_climbed == 9
    assert d.moderate_intensity_min == 20 and d.vigorous_intensity_min == 5
    assert d.intensity_minutes == 30
    assert (d.min_hr, d.max_hr, d.resting_hr) == (48, 150, 52)
    assert (d.avg_stress, d.max_stress) == (31, 88)
    assert d.body_battery_high is None


def test_map_daily_requires_calendar_date():
    with pytest.raises(mapper.MappingError):
        mapper.map_daily({"steps": 5})


def test_map_sleep():
    s = mapper.map_sleep(SLEEP)
    assert s.date == date(2026, 5, 2)
    assert s.duration_s == 27000 and s.hours == 7.5
    assert (s.deep_s, s.light_s, s.rem_s, s.awake_s) == (5400, 14400, 5400, 1800)
    assert s.score == 84


def test_map_sleep_without_score():
    item = {k: v for k, v in SLEEP.items() if k != "overallSleepScore"}
    assert mapper.map_sleep(item).score is None


def test_map_stress_details_extracts_body_battery_range():
    item = {
        "summaryId": "st1",
        "calendarDate": "2026-05-02",
        "timeOffsetStressLevelValues": {"0": 25, "180": 40},
        "timeOffsetBodyBatteryValues": {"0": 38, "180": 91, "360": 22, "540": 55},
    }
    day, patch = mapper.map_stress_details(item)
    assert day == date(2026, 5, 2)
    assert patch == {"body_battery_high": 91, "body_battery_low": 22}


def test_map_stress_details_without_body_battery_gives_empty_patch():
    day, patch = mapper.map_stress_details(
        {"calendarDate": "2026-05-02", "timeOffsetStressLevelValues": {"0": 25}}
    )
    assert day == date(2026, 5, 2) and patch == {}


def test_map_hrv():
    day, patch = mapper.map_hrv(HRV)
    assert day == date(2026, 5, 2)
    assert patch == {
        "hrv_last_night_ms": 62,
        "hrv_weekly_avg_ms": 58,
        "hrv_status": "BALANCED",
    }


def test_map_hrv_ignores_missing_and_negative_values():
    day, patch = mapper.map_hrv(
        {"calendarDate": "2026-05-02", "lastNightAvg": -1, "weeklyAvg": -2, "status": "  "}
    )
    assert day == date(2026, 5, 2) and patch == {}


def test_map_hrv_requires_calendar_date():
    with pytest.raises(mapper.MappingError):
        mapper.map_hrv({"lastNightAvg": 62})


def test_map_pulse_ox_averages_and_ignores_not_measured():
    day, patch = mapper.map_pulse_ox(PULSE_OX)
    assert day == date(2026, 5, 2)
    assert patch == {"spo2_avg_pct": 96.0, "spo2_min_pct": 94.0}


def test_map_pulse_ox_without_readings_is_an_empty_patch():
    day, patch = mapper.map_pulse_ox({"calendarDate": "2026-05-02"})
    assert day == date(2026, 5, 2) and patch == {}


def test_map_respiration_summarises_the_series():
    day, patch = mapper.map_respiration(RESPIRATION)
    assert day == date(2026, 5, 2)
    assert patch == {
        "respiration_avg_brpm": 15.0,
        "respiration_max_brpm": 16.0,
        "respiration_min_brpm": 14.0,
    }


def test_map_respiration_prefers_garmins_own_values():
    _, patch = mapper.map_respiration(
        {
            "calendarDate": "2026-05-02",
            "avgRespirationValue": 14.5,
            "highestRespirationValue": 18.0,
            "lowestRespirationValue": 11.0,
            "timeOffsetRespirationValues": {"0": 1.0, "60": 99.0},
        }
    )
    assert patch == {
        "respiration_avg_brpm": 14.5,
        "respiration_max_brpm": 18.0,
        "respiration_min_brpm": 11.0,
    }


def test_map_body_composition():
    body = mapper.map_body_composition(BODY)
    assert body.measured_at == datetime.fromtimestamp(1777705200, UTC)
    assert body.date == date(2026, 5, 2)
    assert body.weight_kg == 74.2 and body.muscle_mass_kg == 31.9
    assert body.bone_mass_kg == 3.2 and body.body_fat_pct == 18.4
    assert body.bmi == 22.9 and body.body_water_pct == 55.1
    assert body.visceral_fat == 8.0 and body.basal_metabolic_rate_kcal == 1650
    assert body.device_name == "Index S2"


def test_map_body_composition_falls_back_to_noon_on_the_calendar_date():
    body = mapper.map_body_composition({"calendarDate": "2026-05-02", "weightInGrams": 80000})
    assert body.measured_at == datetime(2026, 5, 2, 12, tzinfo=UTC)
    assert body.weight_kg == 80.0 and body.body_fat_pct is None


def test_map_body_composition_requires_a_date():
    with pytest.raises(mapper.MappingError):
        mapper.map_body_composition({"weightInGrams": 74200})
