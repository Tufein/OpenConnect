from datetime import UTC, date, datetime, timedelta

import pytest

from openconnect.domain import Sport
from openconnect.garmin.fake import FakeGarminSource
from openconnect.garmin.ingest import ingest_payload
from openconnect.services.sync import SyncService
from openconnect.storage import Database, Repository
from tests.unit.test_garmin_mapper import ACTIVITY, DAILY, SLEEP

NOW = datetime(2026, 5, 31, 12, 0, tzinfo=UTC)


@pytest.fixture
def repo():
    return Repository(Database(":memory:"))


STRESS = {
    "summaryId": "st1",
    "calendarDate": "2026-05-02",
    "timeOffsetBodyBatteryValues": {"0": 90, "3600": 25},
}
DETAILS = {
    "summaryId": "sd1001-detail",
    "summary": ACTIVITY,
    "samples": [
        {"startTimeInSeconds": 1777705200, "latitudeInDegree": 52.37, "longitudeInDegree": 4.89,
         "heartRate": 120},
        {"startTimeInSeconds": 1777705260, "latitudeInDegree": 52.371, "longitudeInDegree": 4.89,
         "heartRate": 140},
    ],
}  # fmt: skip


def test_ingest_stores_each_kind_and_reports_counts(repo):
    result = ingest_payload(
        repo,
        {"dailies": [DAILY], "sleeps": [SLEEP], "activities": [ACTIVITY], "unknownKind": [{}]},
    )
    assert (result.dailies, result.sleeps, result.activities) == (1, 1, 1)
    assert result.errors == []
    assert repo.get_daily(date(2026, 5, 2)).steps == 8123
    assert repo.list_sleep(date(2026, 5, 2), date(2026, 5, 2))[0].score == 84
    assert repo.get_activity("garmin:1001").name == "Morning Run"


@pytest.mark.parametrize("stress_first", [True, False])
def test_body_battery_merges_into_daily_regardless_of_arrival_order(repo, stress_first):
    first, second = ({"stressDetails": [STRESS]}, {"dailies": [DAILY]})
    if not stress_first:
        first, second = second, first
    ingest_payload(repo, first)
    ingest_payload(repo, second)
    day = repo.get_daily(date(2026, 5, 2))
    assert (day.body_battery_high, day.body_battery_low) == (90, 25)
    assert day.steps == 8123


def test_reingesting_dailies_keeps_previously_merged_body_battery(repo):
    ingest_payload(repo, {"dailies": [DAILY], "stressDetails": [STRESS]})
    ingest_payload(repo, {"dailies": [{**DAILY, "steps": 9000}]})
    day = repo.get_daily(date(2026, 5, 2))
    assert day.steps == 9000 and day.body_battery_high == 90


def test_activity_details_supersede_summary_and_add_track(repo):
    result = ingest_payload(repo, {"activities": [ACTIVITY], "activityDetails": [DETAILS]})
    assert result.activities == 1  # one activity, not two
    act = repo.get_activity("garmin:1001")
    assert len(act.track) == 2 and act.has_gps


def test_later_summary_push_does_not_wipe_an_existing_track(repo):
    ingest_payload(repo, {"activityDetails": [DETAILS]})
    ingest_payload(repo, {"activities": [{**ACTIVITY, "activityName": "Renamed"}]})
    act = repo.get_activity("garmin:1001")
    assert act.name == "Renamed" and len(act.track) == 2


def test_ingest_is_idempotent(repo):
    payload = {"dailies": [DAILY], "sleeps": [SLEEP], "activityDetails": [DETAILS]}
    ingest_payload(repo, payload)
    ingest_payload(repo, payload)
    assert repo.count_activities() == 1
    assert len(repo.list_daily(date(2026, 5, 1), date(2026, 5, 3))) == 1


def test_bad_items_are_collected_as_errors_without_aborting(repo):
    result = ingest_payload(
        repo,
        {"dailies": [{"steps": 1}, DAILY], "activities": [{"summaryId": "no-start"}, ACTIVITY]},
    )
    assert result.dailies == 1 and result.activities == 1
    assert len(result.errors) == 2
    assert any("calendarDate" in e for e in result.errors)


def test_first_sync_looks_back_and_stores_everything(repo):
    svc = SyncService(repo, FakeGarminSource(seed=1, today=NOW), clock=lambda: NOW, days_back=14)
    result = svc.sync()
    assert result.start == NOW - timedelta(days=14) and result.end == NOW
    assert result.ingest.dailies >= 14 and result.ingest.activities >= 4
    assert repo.count_activities() == result.ingest.activities
    assert repo.get_state("last_sync") == NOW.isoformat()


def test_next_sync_starts_where_the_last_one_ended(repo):
    src = FakeGarminSource(seed=1, today=NOW)
    SyncService(repo, src, clock=lambda: NOW, days_back=14).sync()
    later = NOW + timedelta(hours=6)
    result = SyncService(repo, src, clock=lambda: later, days_back=14).sync()
    assert result.start == NOW and result.end == later


def test_failed_sync_does_not_advance_the_cursor(repo):
    class Broken:
        def fetch(self, start, end):
            raise RuntimeError("garmin is down")

    repo.set_state("last_sync", NOW.isoformat())
    with pytest.raises(RuntimeError):
        SyncService(repo, Broken(), clock=lambda: NOW + timedelta(days=1)).sync()
    assert repo.get_state("last_sync") == NOW.isoformat()


def test_explicit_days_back_overrides_the_cursor(repo):
    src = FakeGarminSource(seed=1, today=NOW)
    repo.set_state("last_sync", NOW.isoformat())
    result = SyncService(repo, src, clock=lambda: NOW).sync(days_back=7)
    assert result.start == NOW - timedelta(days=7)


def test_synced_data_contains_expected_sports(repo):
    SyncService(repo, FakeGarminSource(seed=1, today=NOW), clock=lambda: NOW, days_back=28).sync()
    sports = {a.sport for a in repo.list_activities(limit=100)}
    assert {Sport.RUNNING, Sport.CYCLING, Sport.STRENGTH} <= sports
