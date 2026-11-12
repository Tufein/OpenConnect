from datetime import UTC, date, datetime, timedelta

import pytest

from openconnect.domain import GeneratedReport, Sport
from openconnect.storage import Database, Repository
from tests.factories import make_activity, make_daily, make_sleep


@pytest.fixture
def repo():
    return Repository(Database(":memory:"))


def test_activity_roundtrip_including_track(repo):
    act = make_activity()
    repo.upsert_activity(act)
    loaded = repo.get_activity(act.id)
    assert loaded == act


def test_get_activity_without_track_is_lightweight(repo):
    repo.upsert_activity(make_activity())
    loaded = repo.get_activity("garmin:1001", include_track=False)
    assert loaded.track == []
    assert loaded.distance_m == 5000.0


def test_get_unknown_activity_returns_none(repo):
    assert repo.get_activity("nope") is None


def test_upsert_replaces_instead_of_duplicating(repo):
    repo.upsert_activity(make_activity(name="Old", n=30))
    repo.upsert_activity(make_activity(name="New", n=10))
    assert repo.count_activities() == 1
    loaded = repo.get_activity("garmin:1001")
    assert loaded.name == "New" and len(loaded.track) == 10


def test_list_activities_filters_orders_and_paginates(repo):
    base = datetime(2026, 5, 1, 7, 0, tzinfo=UTC)
    for i in range(5):
        repo.upsert_activity(
            make_activity(
                id=f"garmin:{i}",
                start=base + timedelta(days=i),
                n=3,
                sport=Sport.CYCLING if i % 2 else Sport.RUNNING,
            )
        )
    all_ = repo.list_activities()
    assert [a.id for a in all_] == [f"garmin:{i}" for i in (4, 3, 2, 1, 0)]
    assert all(a.track == [] for a in all_)
    runs = repo.list_activities(sport=Sport.RUNNING)
    assert {a.id for a in runs} == {"garmin:0", "garmin:2", "garmin:4"}
    assert repo.count_activities(sport=Sport.RUNNING) == 3
    window = repo.list_activities(start=base + timedelta(days=1), end=base + timedelta(days=3))
    assert {a.id for a in window} == {"garmin:1", "garmin:2"}  # end is exclusive
    page = repo.list_activities(limit=2, offset=1)
    assert [a.id for a in page] == ["garmin:3", "garmin:2"]


def test_delete_activity(repo):
    repo.upsert_activity(make_activity())
    assert repo.delete_activity("garmin:1001") is True
    assert repo.delete_activity("garmin:1001") is False
    assert repo.count_activities() == 0


def test_daily_summaries_upsert_and_range(repo):
    d0 = date(2026, 5, 1)
    for i in range(5):
        repo.upsert_daily(make_daily(d0 + timedelta(days=i), steps=1000 * (i + 1)))
    repo.upsert_daily(make_daily(d0, steps=42))  # replace
    days = repo.list_daily(d0, d0 + timedelta(days=2))
    assert [d.date for d in days] == [d0, d0 + timedelta(days=1), d0 + timedelta(days=2)]
    assert days[0].steps == 42
    assert repo.get_daily(d0 + timedelta(days=4)).steps == 5000
    assert repo.get_daily(date(2000, 1, 1)) is None


def test_sleep_upsert_and_range(repo):
    d0 = date(2026, 5, 1)
    repo.upsert_sleep(make_sleep(d0, hours=6))
    repo.upsert_sleep(make_sleep(d0, hours=8))
    repo.upsert_sleep(make_sleep(d0 + timedelta(days=1), hours=7))
    nights = repo.list_sleep(d0, d0 + timedelta(days=1))
    assert [round(n.hours) for n in nights] == [8, 7]


def test_reports_return_latest_for_subject(repo):
    t0 = datetime(2026, 5, 1, tzinfo=UTC)
    first = repo.save_report(
        GeneratedReport(
            kind="weekly", subject="2026-04-27", text="v1", provider="template", created_at=t0
        )
    )
    repo.save_report(
        GeneratedReport(
            kind="weekly",
            subject="2026-04-27",
            text="v2",
            provider="template",
            created_at=t0 + timedelta(hours=1),
        )
    )
    assert first.id is not None
    assert repo.get_report("weekly", "2026-04-27").text == "v2"
    assert repo.get_report("weekly", "2026-04-27").facts_hash is None
    assert repo.get_report("weekly", "2026-05-04") is None
    assert repo.get_report("activity", "2026-04-27") is None


def test_report_keeps_facts_hash_and_model(repo):
    repo.save_report(GeneratedReport(kind="activity", subject="a", text="t", provider="anthropic",
                                     model="claude-opus-5-5", facts_hash="abc",
                                     created_at=datetime(2026, 5, 1, tzinfo=UTC)))  # fmt: skip
    loaded = repo.get_report("activity", "a")
    assert (loaded.facts_hash, loaded.model, loaded.provider) == (
        "abc",
        "claude-opus-5-5",
        "anthropic",
    )


def test_oauth_tokens_and_state(repo):
    assert repo.get_tokens("garmin") is None
    repo.save_tokens("garmin", {"access_token": "a", "refresh_token": "r", "expires_at": 123})
    repo.save_tokens("garmin", {"access_token": "b", "refresh_token": "r", "expires_at": 456})
    assert repo.get_tokens("garmin")["access_token"] == "b"
    repo.delete_tokens("garmin")
    assert repo.get_tokens("garmin") is None

    assert repo.get_state("last_sync") is None
    repo.set_state("last_sync", "2026-05-01")
    repo.set_state("last_sync", "2026-05-02")
    assert repo.get_state("last_sync") == "2026-05-02"


def test_devices_are_derived_from_activities(repo):
    base = datetime(2026, 5, 1, 7, 0, tzinfo=UTC)
    repo.upsert_activity(make_activity(id="a", device_name="Forerunner 965", start=base, n=3))
    repo.upsert_activity(
        make_activity(id="b", device_name="Forerunner 965", start=base + timedelta(days=2), n=3)
    )
    repo.upsert_activity(
        make_activity(id="c", device_name="Edge 1050", start=base + timedelta(days=1), n=3)
    )
    repo.upsert_activity(make_activity(id="d", device_name=None, start=base, n=3))
    devices = repo.list_devices()
    assert [(d["name"], d["activities"]) for d in devices] == [
        ("Forerunner 965", 2),
        ("Edge 1050", 1),
    ]
    assert devices[0]["last_used"].startswith("2026-05-03")


def test_file_database_persists_and_migrates_idempotently(tmp_path):
    path = tmp_path / "oc.db"
    Repository(Database(path)).upsert_activity(make_activity())
    reopened = Repository(Database(path))  # runs migrations again
    assert reopened.count_activities() == 1
    assert reopened.get_activity("garmin:1001") == make_activity()
