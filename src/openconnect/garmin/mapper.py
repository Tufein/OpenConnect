"""Map Garmin Connect Developer Program JSON summaries to OpenConnect domain models.

Field names follow Garmin's Health and Activity API summaries. Every field is optional so that
partial or older payloads still import; only the identifying fields are required.
"""

from __future__ import annotations

from datetime import UTC, date, datetime
from typing import Any

from openconnect.domain import (
    Activity,
    DailySummary,
    SleepSummary,
    Sport,
    TrackPoint,
    finalize_activity,
)


class MappingError(ValueError):
    """The payload is missing a field we cannot do without."""


_SPORTS = {
    "RUNNING": Sport.RUNNING,
    "TRAIL_RUNNING": Sport.RUNNING,
    "TREADMILL_RUNNING": Sport.RUNNING,
    "TRACK_RUNNING": Sport.RUNNING,
    "VIRTUAL_RUN": Sport.RUNNING,
    "CYCLING": Sport.CYCLING,
    "ROAD_BIKING": Sport.CYCLING,
    "MOUNTAIN_BIKING": Sport.CYCLING,
    "GRAVEL_CYCLING": Sport.CYCLING,
    "INDOOR_CYCLING": Sport.CYCLING,
    "VIRTUAL_RIDE": Sport.CYCLING,
    "LAP_SWIMMING": Sport.SWIMMING,
    "OPEN_WATER_SWIMMING": Sport.SWIMMING,
    "SWIMMING": Sport.SWIMMING,
    "WALKING": Sport.WALKING,
    "INDOOR_WALKING": Sport.WALKING,
    "HIKING": Sport.HIKING,
    "STRENGTH_TRAINING": Sport.STRENGTH,
    "YOGA": Sport.YOGA,
}


def sport_from_garmin(activity_type: str | None) -> Sport:
    return _SPORTS.get((activity_type or "").upper(), Sport.OTHER)


def _ts(seconds: int | float) -> datetime:
    return datetime.fromtimestamp(seconds, UTC)


def _int(value: Any) -> int | None:
    return None if value is None else int(round(float(value)))


def _non_negative(value: Any) -> int | None:
    """Garmin reports -1/-2 for 'not enough data' on stress and similar metrics."""
    number = _int(value)
    return number if number is not None and number >= 0 else None


def _calendar_date(item: dict) -> date:
    try:
        return date.fromisoformat(item["calendarDate"])
    except (KeyError, ValueError, TypeError) as exc:
        raise MappingError(f"Summary has no valid calendarDate: {item.get('summaryId')!r}") from exc


def _activity_id(item: dict) -> tuple[str, str]:
    external = item.get("activityId", item.get("summaryId"))
    if external is None:
        raise MappingError("Activity summary has neither activityId nor summaryId")
    return f"garmin:{external}", str(external)


def map_activity(item: dict) -> Activity:
    activity_id, external_id = _activity_id(item)
    if "startTimeInSeconds" not in item:
        raise MappingError(f"Activity {external_id} has no startTimeInSeconds")
    return Activity(
        id=activity_id,
        source="garmin",
        external_id=external_id,
        name=item.get("activityName") or "",
        sport=sport_from_garmin(item.get("activityType")),
        start_time=_ts(item["startTimeInSeconds"]),
        duration_s=float(item.get("durationInSeconds") or 0),
        distance_m=float(item.get("distanceInMeters") or 0),
        elevation_gain_m=float(item.get("totalElevationGainInMeters") or 0),
        avg_hr=_int(item.get("averageHeartRateInBeatsPerMinute")),
        max_hr=_int(item.get("maxHeartRateInBeatsPerMinute")),
        calories=_int(item.get("activeKilocalories")),
        device_name=item.get("deviceName"),
    )


def map_activity_details(item: dict) -> Activity:
    """An ``activityDetails`` entry: a summary plus per-sample GPS/heart-rate data."""
    summary = dict(item.get("summary") or {})
    summary.setdefault("summaryId", item.get("summaryId"))
    samples = item.get("samples") or []
    if "startTimeInSeconds" not in summary and samples:
        summary["startTimeInSeconds"] = samples[0]["startTimeInSeconds"]
    activity = map_activity(summary)
    activity.track = [
        TrackPoint(
            time=_ts(s["startTimeInSeconds"]),
            lat=s.get("latitudeInDegree"),
            lon=s.get("longitudeInDegree"),
            ele=s.get("elevationInMeters"),
            hr=_int(s.get("heartRate")),
            cadence=_int(s.get("bikeCadenceInRPM", s.get("stepsPerMinute"))),
            power=_int(s.get("powerInWatts")),
            distance_m=s.get("totalDistanceInMeters"),
        )
        for s in samples
        if "startTimeInSeconds" in s
    ]
    return finalize_activity(activity)


def map_daily(item: dict) -> DailySummary:
    return DailySummary(
        date=_calendar_date(item),
        steps=_int(item.get("steps")) or 0,
        steps_goal=_int(item.get("stepsGoal")),
        distance_m=float(item.get("distanceInMeters") or 0),
        active_kcal=_int(item.get("activeKilocalories")) or 0,
        floors_climbed=_int(item.get("floorsClimbed")) or 0,
        moderate_intensity_min=(_int(item.get("moderateIntensityDurationInSeconds")) or 0) // 60,
        vigorous_intensity_min=(_int(item.get("vigorousIntensityDurationInSeconds")) or 0) // 60,
        resting_hr=_non_negative(item.get("restingHeartRateInBeatsPerMinute")),
        min_hr=_non_negative(item.get("minHeartRateInBeatsPerMinute")),
        max_hr=_non_negative(item.get("maxHeartRateInBeatsPerMinute")),
        avg_stress=_non_negative(item.get("averageStressLevel")),
        max_stress=_non_negative(item.get("maxStressLevel")),
    )


def map_sleep(item: dict) -> SleepSummary:
    score = item.get("overallSleepScore")
    return SleepSummary(
        date=_calendar_date(item),
        duration_s=_int(item.get("durationInSeconds")) or 0,
        deep_s=_int(item.get("deepSleepDurationInSeconds")) or 0,
        light_s=_int(item.get("lightSleepDurationInSeconds")) or 0,
        rem_s=_int(item.get("remSleepInSeconds")) or 0,
        awake_s=_int(item.get("awakeDurationInSeconds")) or 0,
        score=_non_negative(score.get("value")) if isinstance(score, dict) else None,
    )


def map_stress_details(item: dict) -> tuple[date, dict[str, int]]:
    """Body Battery arrives in ``stressDetails`` as an offset->value series; keep its range."""
    values = [
        v
        for v in (item.get("timeOffsetBodyBatteryValues") or {}).values()
        if isinstance(v, int | float) and v >= 0
    ]
    patch = (
        {"body_battery_high": int(max(values)), "body_battery_low": int(min(values))}
        if values
        else {}
    )
    return _calendar_date(item), patch
