"""An offline stand-in for Garmin: deterministic, Garmin-shaped data for demos and tests."""

from __future__ import annotations

import math
import random
from collections.abc import Callable
from datetime import UTC, date, datetime, time, timedelta

# weekday (Mon=0) -> (activityType, name, device, target minutes)
_SCHEDULE = {
    1: ("RUNNING", "Tempo Run", "Forerunner 965", 38),
    2: ("STRENGTH_TRAINING", "Strength", "Forerunner 965", 40),
    3: ("RUNNING", "Easy Run", "Forerunner 965", 45),
    5: ("CYCLING", "Weekend Ride", "Edge 1050", 95),
    6: ("WALKING", "Sunday Walk", "Forerunner 965", 60),
}
_SPEED_MPS = {"RUNNING": 2.9, "CYCLING": 7.2, "WALKING": 1.4}
_HR_BASE = {"RUNNING": 152, "CYCLING": 138, "WALKING": 105, "STRENGTH_TRAINING": 118}
_CENTRE = (52.3676, 4.9041)
SAMPLE_EVERY_S = 10


class FakeGarminSource:
    def __init__(
        self,
        seed: int = 42,
        today: datetime | Callable[[], datetime] | None = None,
        days: int = 120,
    ) -> None:
        self.seed = seed
        self._today = today
        self.days = days

    @property
    def today(self) -> datetime:
        value = self._today() if callable(self._today) else self._today
        return (value or datetime.now(UTC)).astimezone(UTC)

    def _rng(self, *parts: object) -> random.Random:
        return random.Random(f"{self.seed}:" + ":".join(str(p) for p in parts))

    def _day_range(self, start: datetime, end: datetime):
        first = max(start.date(), self.today.date() - timedelta(days=self.days))
        last = min(end.date(), self.today.date())
        day = first
        while day <= last:
            yield day
            day += timedelta(days=1)

    # ---- activities

    def _activity(self, day: date) -> tuple[dict, dict] | None:
        spec = _SCHEDULE.get(day.weekday())
        if spec is None:
            return None
        sport, name, device, minutes = spec
        rng = self._rng("activity", day)
        start = datetime.combine(day, time(7, 0), UTC) + timedelta(minutes=rng.randint(0, 90))
        if start > self.today:
            return None
        duration = int((minutes + rng.randint(-6, 8)) * 60)
        n = duration // SAMPLE_EVERY_S
        speed = _SPEED_MPS.get(sport, 0.0) * rng.uniform(0.94, 1.06)
        has_gps = sport != "STRENGTH_TRAINING"
        radius = speed * duration / (2 * math.pi)
        base_hr = _HR_BASE[sport] + rng.randint(-5, 5)
        samples, distance, climb, prev_ele = [], 0.0, 0.0, None
        hrs = []
        for i in range(n + 1):
            frac = i / n
            ele = 2 + 9 * math.sin(frac * 4 * math.pi) + rng.uniform(-0.3, 0.3)
            hr = round(
                base_hr + 12 * min(1.0, frac * 6) + 5 * math.sin(frac * 9) + rng.randint(-2, 2)
            )
            hrs.append(hr)
            sample = {
                "startTimeInSeconds": int(start.timestamp()) + i * SAMPLE_EVERY_S,
                "heartRate": hr,
            }
            if has_gps:
                angle = 2 * math.pi * frac
                wobble = 1 + 0.28 * math.sin(2 * angle + day.day) + 0.12 * math.sin(5 * angle)
                lat0 = _CENTRE[0] + 0.01 * (day.weekday() - 3) / 3
                sample["latitudeInDegree"] = round(
                    lat0 + radius * wobble * math.sin(angle) / 111_320, 6
                )
                sample["longitudeInDegree"] = round(
                    _CENTRE[1]
                    + radius
                    * (wobble * math.cos(angle) - 1)
                    / (111_320 * math.cos(math.radians(lat0))),
                    6,
                )
                sample["elevationInMeters"] = round(ele, 1)
                if i:
                    distance += speed * SAMPLE_EVERY_S * rng.uniform(0.97, 1.03)
                    if prev_ele is not None and ele - prev_ele > 0.05:
                        climb += ele - prev_ele
                sample["totalDistanceInMeters"] = round(distance, 1)
                if sport == "CYCLING":
                    sample["bikeCadenceInRPM"] = 82 + rng.randint(-6, 6)
                    sample["powerInWatts"] = 190 + rng.randint(-40, 60)
                elif sport == "RUNNING":
                    sample["stepsPerMinute"] = 168 + rng.randint(-4, 6)
                prev_ele = ele
            samples.append(sample)

        activity_id = int(day.strftime("%Y%m%d")) * 10 + 1
        summary = {
            "summaryId": f"sd{activity_id}",
            "activityId": activity_id,
            "activityName": name,
            "activityType": sport,
            "startTimeInSeconds": int(start.timestamp()),
            "startTimeOffsetInSeconds": 7200,
            "durationInSeconds": duration,
            "distanceInMeters": round(distance, 1),
            "averageHeartRateInBeatsPerMinute": round(sum(hrs) / len(hrs)),
            "maxHeartRateInBeatsPerMinute": max(hrs),
            "activeKilocalories": round(duration / 60 * (11 if sport == "RUNNING" else 7)),
            "totalElevationGainInMeters": round(climb, 1),
            "deviceName": device,
        }
        details = {"summaryId": f"sd{activity_id}-detail", "summary": summary, "samples": samples}
        return summary, details

    # ---- health

    def _health(self, day: date, ran: bool) -> tuple[dict, dict, dict]:
        rng = self._rng("health", day)
        steps = 6500 + rng.randint(-1800, 2600) + (3500 if ran else 0)
        resting = 50 + rng.randint(0, 6)
        avg_stress = 22 + rng.randint(0, 25)
        stamp = {"calendarDate": day.isoformat(), "startTimeOffsetInSeconds": 7200}
        daily = {
            **stamp,
            "summaryId": f"d{day:%Y%m%d}",
            "durationInSeconds": 86400,
            "steps": steps,
            "stepsGoal": 10000,
            "distanceInMeters": round(steps * 0.76, 1),
            "activeKilocalories": steps // 22,
            "floorsClimbed": 4 + rng.randint(0, 12),
            "moderateIntensityDurationInSeconds": 60 * rng.randint(8, 35),
            "vigorousIntensityDurationInSeconds": 60 * (rng.randint(5, 20) if ran else 0),
            "minHeartRateInBeatsPerMinute": resting - rng.randint(2, 5),
            "maxHeartRateInBeatsPerMinute": 120 + rng.randint(0, 50),
            "restingHeartRateInBeatsPerMinute": resting,
            "averageStressLevel": avg_stress,
            "maxStressLevel": min(99, avg_stress + rng.randint(30, 55)),
        }
        total = int(3600 * rng.uniform(6.0, 8.6))
        deep, rem, awake = int(total * 0.17), int(total * 0.22), int(total * 0.06)
        sleep = {
            **stamp,
            "summaryId": f"s{day:%Y%m%d}",
            "durationInSeconds": total,
            "deepSleepDurationInSeconds": deep,
            "lightSleepDurationInSeconds": total - deep - rem - awake,
            "remSleepInSeconds": rem,
            "awakeDurationInSeconds": awake,
            "overallSleepScore": {"value": 55 + int(total / 3600 * 4) + rng.randint(0, 8),
                                  "qualifierKey": "GOOD"},
        }  # fmt: skip
        high, low = 85 + rng.randint(0, 14), 12 + rng.randint(0, 25)
        stress = {
            **stamp,
            "summaryId": f"st{day:%Y%m%d}",
            "timeOffsetBodyBatteryValues": {
                str(i * 3600): round(high - (high - low) * i / 7 + rng.randint(-2, 2))
                for i in range(8)
            }
            | {"0": high, "25200": low},
        }
        return daily, sleep, stress

    # ---- GarminSource

    def fetch(self, start: datetime, end: datetime) -> dict[str, list[dict]]:
        payload: dict[str, list[dict]] = {
            "activities": [],
            "activityDetails": [],
            "dailies": [],
            "sleeps": [],
            "stressDetails": [],
        }
        for day in self._day_range(start, end):
            made = self._activity(day)
            ran = False
            if made is not None:
                summary, details = made
                if start.timestamp() <= summary["startTimeInSeconds"] < end.timestamp():
                    payload["activities"].append(summary)
                    payload["activityDetails"].append(details)
                ran = summary["activityType"] == "RUNNING"
            daily, sleep, stress = self._health(day, ran)
            payload["dailies"].append(daily)
            payload["sleeps"].append(sleep)
            payload["stressDetails"].append(stress)
        return payload
