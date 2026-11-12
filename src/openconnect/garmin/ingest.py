"""Turn a Garmin-shaped payload (from a pull, a push webhook or the fake) into stored data."""

from __future__ import annotations

from dataclasses import dataclass, field

from openconnect.domain import DailySummary
from openconnect.garmin import mapper
from openconnect.storage import Repository


@dataclass
class IngestResult:
    activities: int = 0
    dailies: int = 0
    sleeps: int = 0
    errors: list[str] = field(default_factory=list)

    def __iadd__(self, other: IngestResult) -> IngestResult:
        self.activities += other.activities
        self.dailies += other.dailies
        self.sleeps += other.sleeps
        self.errors += other.errors
        return self


def _store_daily(repo: Repository, daily: DailySummary) -> None:
    existing = repo.get_daily(daily.date)
    if existing is not None:
        # Body Battery comes from a different Garmin summary; do not lose it on re-ingest.
        daily = daily.model_copy(
            update={
                "body_battery_high": daily.body_battery_high or existing.body_battery_high,
                "body_battery_low": daily.body_battery_low or existing.body_battery_low,
            }
        )
    repo.upsert_daily(daily)


def ingest_payload(repo: Repository, payload: dict) -> IngestResult:
    result = IngestResult()

    def guarded(kind: str, items: list[dict], handler) -> int:
        stored = 0
        for item in items or []:
            try:
                if handler(item) is not False:  # a handler may return False to skip an item
                    stored += 1
            except (mapper.MappingError, KeyError, TypeError, ValueError) as exc:
                result.errors.append(f"{kind} {item.get('summaryId', '?')}: {exc}")
        return stored

    result.dailies = guarded(
        "dailies", payload.get("dailies"), lambda i: _store_daily(repo, mapper.map_daily(i))
    )

    def apply_stress(item: dict) -> None:
        day, patch = mapper.map_stress_details(item)
        if patch:
            base = repo.get_daily(day) or DailySummary(date=day)
            repo.upsert_daily(base.model_copy(update=patch))

    guarded("stressDetails", payload.get("stressDetails"), apply_stress)
    result.sleeps = guarded(
        "sleeps", payload.get("sleeps"), lambda i: repo.upsert_sleep(mapper.map_sleep(i))
    )

    details = payload.get("activityDetails") or []
    detailed_ids = set()
    for item in details:
        summary = item.get("summary") or {}
        key = summary.get("activityId", summary.get("summaryId", item.get("summaryId")))
        detailed_ids.add(str(key))

    def store_summary(item: dict) -> bool:
        activity = mapper.map_activity(item)
        if activity.external_id in detailed_ids:
            return False  # the detailed version (with its track) is stored below
        existing = repo.get_activity(activity.id)
        if existing is not None and existing.track:
            activity.track = existing.track
        repo.upsert_activity(activity)
        return True

    stored_summaries = guarded("activities", payload.get("activities"), store_summary)
    stored_details = guarded(
        "activityDetails",
        details,
        lambda i: repo.upsert_activity(mapper.map_activity_details(i)),
    )
    result.activities = stored_summaries + stored_details
    return result
