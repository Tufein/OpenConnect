"""Turn a Garmin-shaped payload (from a pull, a push webhook or the fake) into stored data."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from typing import Any

from openconnect.domain import BodyComposition, DailySummary
from openconnect.garmin import mapper
from openconnect.storage import Repository

# These arrive in their own Garmin summaries (stressDetails, hrv, pulseox, allDayRespiration),
# not in ``dailies``. A daily that is ingested later must not wipe them.
DAILY_PATCH_FIELDS = (
    "body_battery_high",
    "body_battery_low",
    "hrv_last_night_ms",
    "hrv_weekly_avg_ms",
    "hrv_status",
    "spo2_avg_pct",
    "spo2_min_pct",
    "respiration_avg_brpm",
    "respiration_min_brpm",
    "respiration_max_brpm",
    "systolic_bp",
    "diastolic_bp",
    "skin_temp_c",
    "vo2max",
    "fitness_age",
)

# Offset->value series carried by summaries we already store; kept as raw series for charts.
SERIES_KINDS = {
    "dailies": {"timeOffsetHeartRateValues": "heart_rate"},
    "stressDetails": {
        "timeOffsetStressLevelValues": "stress",
        "timeOffsetBodyBatteryValues": "body_battery",
    },
    "sleeps": {
        "timeOffsetSpo2Values": "sleep_spo2",
        "timeOffsetRespirationValues": "sleep_respiration",
    },
}


@dataclass
class IngestResult:
    activities: int = 0
    dailies: int = 0
    sleeps: int = 0
    hrv: int = 0
    respiration: int = 0
    pulseox: int = 0
    body_comps: int = 0
    extra_metrics: int = 0
    errors: list[str] = field(default_factory=list)

    def __iadd__(self, other: IngestResult) -> IngestResult:
        self.activities += other.activities
        self.dailies += other.dailies
        self.sleeps += other.sleeps
        self.hrv += other.hrv
        self.respiration += other.respiration
        self.pulseox += other.pulseox
        self.body_comps += other.body_comps
        self.extra_metrics += other.extra_metrics
        self.errors += other.errors
        return self


def _store_daily(repo: Repository, daily: DailySummary) -> None:
    existing = repo.get_daily(daily.date)
    if existing is not None:
        # Keep what separate summaries already merged in (Body Battery, HRV, SpO2, respiration).
        for name in DAILY_PATCH_FIELDS:
            if getattr(daily, name) is None:
                setattr(daily, name, getattr(existing, name))
    repo.upsert_daily(daily)


def apply_day_patch(repo: Repository, day: date, patch: dict[str, Any]) -> bool:
    """Merge one summary type's fields into the day, creating the row when it does not exist yet.

    Returns ``False`` when the summary carried nothing usable, so it is not counted as stored.
    """
    if not patch:
        return False
    base = repo.get_daily(day) or DailySummary(date=day)
    repo.upsert_daily(base.model_copy(update=patch))
    return True


def _store_body_composition(repo: Repository, item: dict) -> None:
    body: BodyComposition = mapper.map_body_composition(item)
    repo.upsert_body_composition(body)


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
    result.sleeps = guarded(
        "sleeps", payload.get("sleeps"), lambda i: repo.upsert_sleep(mapper.map_sleep(i))
    )

    guarded(
        "stressDetails",
        payload.get("stressDetails"),
        lambda i: apply_day_patch(repo, *mapper.map_stress_details(i)),
    )
    result.hrv = guarded(
        "hrv",
        payload.get("hrv"),
        lambda i: apply_day_patch(repo, *mapper.map_hrv(i)),
    )
    result.respiration = guarded(
        "allDayRespiration",
        payload.get("allDayRespiration"),
        lambda i: apply_day_patch(repo, *mapper.map_respiration(i)),
    )
    result.pulseox = guarded(
        "pulseox",
        payload.get("pulseox"),
        lambda i: apply_day_patch(repo, *mapper.map_pulse_ox(i)),
    )
    for kind in ("bloodPressures", "skinTemp", "userMetrics"):
        result.extra_metrics += guarded(
            kind,
            payload.get(kind),
            lambda i: apply_day_patch(repo, *mapper.map_extra_metrics(i)),
        )
    result.body_comps = guarded(
        "bodyComps",
        payload.get("bodyComps"),
        lambda i: _store_body_composition(repo, i),
    )

    for kind, fields in SERIES_KINDS.items():
        for item in payload.get(kind) or []:
            try:
                day, series = mapper.map_series(item, fields)
            except (mapper.MappingError, TypeError, ValueError):
                continue  # the main mapper already reports the malformed item
            for name, values in series.items():
                repo.upsert_series(day, name, values)

    for kind, series_name in (
        ("healthSnapshot", "health_snapshot"),
        ("epochSummaries", "epoch_summary"),
    ):
        for item in payload.get(kind) or []:
            try:
                day, name, values = mapper.map_observation(item, series_name)
            except (mapper.MappingError, TypeError, ValueError):
                continue
            if values:
                repo.upsert_series(day, name, values)
                result.extra_metrics += 1

    details = payload.get("activityDetails") or []
    detailed_ids = set()
    for item in details:
        summary = item.get("summary") or {}
        key = summary.get("activityId", summary.get("summaryId", item.get("summaryId")))
        detailed_ids.add(str(key))
    files = payload.get("activityFiles") or []
    for activity in files:
        if activity.external_id:
            detailed_ids.add(str(activity.external_id))

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
    for activity in files:
        repo.upsert_activity(activity)
    result.activities += len(files)
    return result
