import base64
import io
import json
import zipfile
from datetime import UTC, date, datetime, timedelta

import httpx
import pytest

from openconnect.formats import export_activity
from openconnect.garmin.ingest import ingest_payload
from openconnect.garmin.personal import (
    BASE_URL,
    TOKEN_PATH,
    PersonalAPIError,
    PersonalApiSource,
    PersonalTokenProvider,
    collect_days,
    map_personal_payload,
)
from openconnect.storage import Database, Repository
from tests.factories import make_activity

NOW = datetime(2026, 5, 31, 12, 0, tzinfo=UTC)

DAILY = [
    {
        "calendarDate": "2026-05-02",
        "steps": {"value": 8123, "goal": 10000, "distanceInMeters": 6200.5},
        "calories": {"burnedActive": 410},
        "floorsAscended": {"value": 9},
        "intensityMinutes": {"moderate": 20, "vigorous": 5},
        "heartRate": {"restingValue": 52, "minValue": 48, "maxValue": 150},
        "overallStressLevel": 31,
        "maxStressLevel": 88,
        "lastNightAvg": 62,
        "pulseOx": {"avgValue": 96.0, "minValue": 93.0},
        "respiration": {"avgValue": 14.5},
        "sleepTimeSeconds": 27000,
        "deepSleepSeconds": 5400,
        "lightSleepSeconds": 14400,
        "remSleepSeconds": 5400,
        "awakeSleepSeconds": 1800,
        "overall": {"value": 84},
    }
]
BATTERY = [
    {
        "calendarDate": "2026-05-02",
        "bodyBatteryValueDescriptorDTOList": [
            {
                "bodyBatteryValueDescriptorKey": "bodyBatteryLevel",
                "bodyBatteryValueDescriptorIndex": 1,
            }
        ],
        "bodyBatteryValuesArray": [[0, 80], [3600, 20], [7200, -1]],
    }
]


def jwt(expiry: float = NOW.timestamp() + 3600) -> str:
    payload = base64.urlsafe_b64encode(json.dumps({"exp": expiry}).encode()).decode().rstrip("=")
    return "header." + payload + ".sig"


def client(handler) -> httpx.Client:
    return httpx.Client(transport=httpx.MockTransport(handler))


def test_token_is_exchanged_with_the_api_key_header_then_reused():
    seen = []

    def handler(request: httpx.Request):
        seen.append(request)
        return httpx.Response(200, text=jwt())

    provider = PersonalTokenProvider("secret", http=client(handler), clock=lambda: NOW.timestamp())
    assert provider() == jwt()
    assert provider() == jwt()
    assert len(seen) == 1
    assert str(seen[0].url) == BASE_URL + TOKEN_PATH
    assert seen[0].headers["Authorization"] == "ApiKey secret"


def test_token_is_renewed_near_expiry_and_when_forced():
    calls = []

    def handler(request: httpx.Request):
        calls.append(request)
        offset = 300 if len(calls) == 2 else 1500 + len(calls)
        return httpx.Response(200, text=jwt(NOW.timestamp() + offset))

    clock = [NOW.timestamp()]
    provider = PersonalTokenProvider("secret", http=client(handler), clock=lambda: clock[0])
    first = provider()
    clock[0] += 1000  # less than ten minutes left
    second = provider()
    assert first != second and provider(force_refresh=True) != second
    assert len(calls) == 3


def test_json_token_answer_and_rejected_key():
    provider = PersonalTokenProvider(
        "secret", http=client(lambda r: httpx.Response(200, json={"access_token": jwt()}))
    )
    assert provider() == jwt()
    for status in (401, 403):
        bad = PersonalTokenProvider("bad", http=client(lambda r, s=status: httpx.Response(s)))
        with pytest.raises(PersonalAPIError, match="did not accept"):
            bad()


def test_collect_days_uses_nested_names_and_the_calendar_date():
    days = collect_days(DAILY)
    leaves = days[next(iter(days))]
    assert leaves["steps.value"] == 8123 and leaves["steps.distanceInMeters"] == 6200.5
    assert leaves["heartRate.restingValue"] == 52 and leaves["overall.value"] == 84


def test_map_personal_payload_produces_the_health_api_shape():
    payload = map_personal_payload({"dailySummaries": DAILY, "bodyBattery": BATTERY})
    daily = payload["dailies"][0]
    assert daily["calendarDate"] == "2026-05-02" and daily["steps"] == 8123
    assert daily["stepsGoal"] == 10000 and daily["distanceInMeters"] == 6200.5
    assert daily["moderateIntensityDurationInSeconds"] == 1200
    assert daily["restingHeartRateInBeatsPerMinute"] == 52
    assert payload["hrv"][0]["lastNightAvg"] == 62
    assert payload["pulseox"][0]["timeOffsetSpo2Values"] == {"0": 96.0}
    assert payload["allDayRespiration"][0]["avgRespirationValue"] == 14.5
    assert payload["stressDetails"][0]["timeOffsetBodyBatteryValues"] == {"0": 80, "3600": 20}
    assert payload["sleeps"][0]["overallSleepScore"]["value"] == 84

    repo = Repository(Database(":memory:"))
    result = ingest_payload(repo, payload)
    assert (result.dailies, result.sleeps, result.hrv, result.pulseox, result.respiration) == (
        1,
        1,
        1,
        1,
        1,
    )
    day = repo.get_daily(date(2026, 5, 2))
    assert (day.steps, day.body_battery_high, day.hrv_last_night_ms) == (8123, 80, 62)
    assert day.spo2_avg_pct == 96.0 and day.respiration_avg_brpm == 14.5


def test_empty_days_are_not_stored():
    payload = map_personal_payload(
        {"dailySummaries": [{"calendarDate": "2026-05-02", "steps": {"value": 0}}]}
    )
    assert payload["dailies"] == []


def test_source_imports_activities_with_their_original_fit_file():
    fit, _, _ = export_activity(make_activity(), "fit")
    archive = io.BytesIO()
    with zipfile.ZipFile(archive, "w") as zipped:
        zipped.writestr("activity.fit", fit)

    def handler(request: httpx.Request):
        if request.method == "POST":
            return httpx.Response(200, text=jwt())
        if "activities/search" in request.url.path:
            return httpx.Response(
                200,
                json=[
                    {
                        "activityId": "99001",
                        "activityName": "Morning Run",
                        "activityType": {"typeKey": "running"},
                        "startTimeGMT": "2026-05-30 07:00:00",
                        "duration": 1800.0,
                        "distance": 5000.0,
                    }
                ],
            )
        if "download-service" in request.url.path:
            return httpx.Response(200, content=archive.getvalue())
        return httpx.Response(200, json=[])

    source = PersonalApiSource("secret", http=client(handler), clock=lambda: NOW)
    payload = source.fetch(NOW - timedelta(days=2), NOW)
    assert str(payload["activities"][0]["activityId"]) == "99001"
    assert len(payload["activityFiles"]) == 1
    activity = payload["activityFiles"][0]
    assert activity.id == "garmin:99001" and activity.source == "garmin" and activity.track
    assert activity.name == "Morning Run" and activity.sport.value == "running"

    repo = Repository(Database(":memory:"))
    result = ingest_payload(repo, payload)
    assert result.activities == 1
    assert repo.get_activity("garmin:99001").track


def test_source_skips_a_denied_data_type_and_reports_it():
    def handler(request: httpx.Request):
        if request.method == "POST":
            return httpx.Response(200, text=jwt())
        if "stats/steps" in request.url.path:
            return httpx.Response(403)
        if "dailysummaries" in request.url.path:
            return httpx.Response(200, json=DAILY)
        return httpx.Response(200, json=[])

    source = PersonalApiSource("secret", http=client(handler), clock=lambda: NOW)
    payload = source.fetch(NOW - timedelta(days=2), NOW)
    assert source.skipped == {"steps": "HTTP 403"}
    assert payload["dailies"][0]["steps"] == 8123
