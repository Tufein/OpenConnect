"""Garmin Personal API source: an owner's key, a 24-hour token, then read-only GETs.

The answers are not documented in full, so the mapping follows Android's tolerant rule: every plain
value under a dated object is collected by its own name and by ``parent.name``. Values are looked up
by the names observed with a real account. A different layout gives fewer values, not a crash.
Everything is returned in Garmin-shaped summaries so all sources share one ingest path.
"""

from __future__ import annotations

import base64
import json
import re
import time
from collections.abc import Callable, Mapping
from datetime import date, datetime, timedelta
from typing import Any

import httpx

from openconnect.garmin.client import GarminAPIError, GarminRateLimitError
from openconnect.services.sync import utcnow

BASE_URL = "https://personal.connectapi.garmin.com"
TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token"
RENEW_BEFORE_S = 10 * 60
ASSUMED_TOKEN_S = 12 * 60 * 60
DATE_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})")
TOKEN_KEYS = ("access_token", "accessToken", "token", "bearer_token")

# (id, path, chunk in days, query template). Verified against a real account on 2026-11-12.
PLANS = (
    ("dailySummaries", "/usersummary-service/v2/dailysummaries", 365, True),
    ("steps", "/usersummary-service/stats/steps/daily/{start}/{end}", 28, False),
    ("stress", "/usersummary-service/stats/stress/daily/{start}/{end}", 28, False),
    ("hrv", "/hrv-service/hrv/daily/{start}/{end}", 28, False),
    ("sleep", "/sleep-service/sleep/dailySleepSummariesByDate", 31, True),
    ("bodyBattery", "/wellness-service/wellness/bodyBattery/reports/daily", 31, True),
)


class PersonalAPIError(GarminAPIError):
    """The key was refused, or Garmin's answer could not be understood."""


def _jwt_expiry(token: str) -> float | None:
    try:
        payload = token.split(".")[1]
        padded = payload + "=" * (-len(payload) % 4)
        claims = json.loads(base64.urlsafe_b64decode(padded))
        return float(claims["exp"])
    except (IndexError, KeyError, TypeError, ValueError, UnicodeDecodeError):
        return None


def _token_from_body(body: str) -> str | None:
    text = body.strip()
    if not text:
        return None
    if text.startswith("{"):
        try:
            data = json.loads(text)
        except ValueError:
            return None
        if isinstance(data, dict):
            for key in TOKEN_KEYS:
                value = data.get(key)
                if isinstance(value, str) and value.strip():
                    return value.strip()
        return None
    token = text.strip('"')
    return token if re.fullmatch(r"[A-Za-z0-9._~+/=-]{16,}", token) else None


class PersonalTokenProvider:
    """Exchanges the key for a bearer token and keeps it until it is close to expiring."""

    def __init__(
        self,
        api_key: str,
        *,
        http: httpx.Client | None = None,
        base_url: str = BASE_URL,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self.api_key = api_key.strip()
        self._http = http or httpx.Client(timeout=30.0)
        self._url = base_url.rstrip("/") + TOKEN_PATH
        self._clock = clock
        self._token: tuple[str, float] | None = None

    def __call__(self, force_refresh: bool = False) -> str:
        if not self.api_key:
            raise PersonalAPIError("No Garmin personal key configured (GARMIN_PERSONAL_KEY)")
        if self._token is not None and not force_refresh:
            value, expires = self._token
            if expires - self._clock() > RENEW_BEFORE_S:
                return value
        return self.refresh()

    def refresh(self) -> str:
        response = self._http.post(
            self._url, headers={"Authorization": f"ApiKey {self.api_key}", "Accept": "*/*"}
        )
        if response.status_code in (401, 403):
            raise PersonalAPIError(
                f"Garmin did not accept the personal key (HTTP {response.status_code}). "
                "It may be wrong, deleted or expired.",
                response.status_code,
            )
        if response.status_code != 200:
            raise PersonalAPIError(
                f"Garmin token request returned HTTP {response.status_code}", response.status_code
            )
        token = _token_from_body(response.text)
        if token is None:
            raise PersonalAPIError("Garmin's token answer held no readable token")
        expiry = _jwt_expiry(token)
        expires = expiry if expiry is not None else self._clock() + ASSUMED_TOKEN_S
        self._token = (token, expires)
        return token


def _date_of(value: Any) -> date | None:
    if not isinstance(value, dict):
        return None
    for key in ("calendarDate", "date"):
        text = value.get(key)
        match = DATE_RE.match(text) if isinstance(text, str) else None
        if match:
            try:
                return date.fromisoformat(match.group(1))
            except ValueError:
                continue
    return None


def collect_days(responses: Any) -> dict[date, dict[str, Any]]:
    """Collect plain values per calendar day, keyed by name and by ``parent.name``."""
    days: dict[date, dict[str, Any]] = {}

    def visit(node: Any, day: date | None = None, parent: str | None = None) -> None:
        if isinstance(node, dict):
            day = _date_of(node) or day
            for key, value in node.items():
                if isinstance(value, bool):
                    continue
                if isinstance(value, (str, int, float)):
                    if day is not None:
                        leaves = days.setdefault(day, {})
                        leaves.setdefault(key, value)
                        if parent:
                            leaves.setdefault(f"{parent}.{key}", value)
                elif isinstance(value, (dict, list)):
                    visit(value, day, key)
        elif isinstance(node, list):
            for item in node:
                visit(item, day, parent)

    visit(responses)
    return days


def _number(leaves: Mapping[str, Any], *keys: str) -> float | None:
    for key in keys:
        value = leaves.get(key)
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            return float(value)
        if isinstance(value, str):
            try:
                return float(value)
            except ValueError:
                continue
    return None


def _reading(leaves: Mapping[str, Any], *keys: str) -> float | None:
    value = _number(leaves, *keys)
    return value if value is not None and value >= 0 else None


def _body_battery(responses: Any) -> dict[date, tuple[int, int]]:
    found: dict[date, tuple[int, int]] = {}

    def visit(node: Any) -> None:
        if isinstance(node, list):
            for item in node:
                visit(item)
        elif isinstance(node, dict):
            day, readings = _date_of(node), node.get("bodyBatteryValuesArray")
            if day is not None and isinstance(readings, list):
                descriptors = node.get("bodyBatteryValueDescriptorDTOList") or []
                position = None
                for descriptor in descriptors:
                    if (
                        isinstance(descriptor, dict)
                        and descriptor.get("bodyBatteryValueDescriptorKey") == "bodyBatteryLevel"
                    ):
                        position = descriptor.get("bodyBatteryValueDescriptorIndex")
                levels = []
                for reading in readings:
                    if not isinstance(reading, list):
                        continue
                    candidates = [reading[position]] if isinstance(position, int) else reading[1:]
                    for candidate in candidates:
                        if isinstance(candidate, (int, float)) and 0 <= candidate <= 100:
                            levels.append(int(candidate))
                            break
                if levels:
                    found.setdefault(day, (max(levels), min(levels)))
            else:
                for value in node.values():
                    if isinstance(value, (dict, list)):
                        visit(value)

    visit(responses)
    return found


def map_personal_payload(collected: Mapping[str, list[Any]]) -> dict[str, list[dict]]:
    """Turn the Personal API answers into the same payload the Health API sources produce."""
    days: dict[date, dict[str, Any]] = {}
    for responses in collected.values():
        for day, leaves in collect_days(responses).items():
            target = days.setdefault(day, {})
            for key, value in leaves.items():
                target.setdefault(key, value)

    dailies, sleeps, hrv, pulseox, respiration, battery = [], [], [], [], [], []
    for day in sorted(days):
        leaves = days[day]
        raw = {
            "calendarDate": day.isoformat(),
            "steps": _reading(leaves, "steps.value", "totalSteps", "steps"),
            "stepsGoal": _reading(leaves, "steps.goal", "stepGoal", "dailyStepGoal"),
            "distanceInMeters": _number(
                leaves, "steps.distanceInMeters", "totalDistance", "totalDistanceMeters"
            ),
            "activeKilocalories": _reading(leaves, "calories.burnedActive", "activeKilocalories"),
            "floorsClimbed": _reading(
                leaves, "floorsAscended.value", "floorsAscended", "floorsClimbed"
            ),
            "moderateIntensityDurationInSeconds": _reading(
                leaves, "intensityMinutes.moderate", "moderateIntensityMinutes"
            ),
            "vigorousIntensityDurationInSeconds": _reading(
                leaves, "intensityMinutes.vigorous", "vigorousIntensityMinutes"
            ),
            "restingHeartRateInBeatsPerMinute": _reading(
                leaves, "heartRate.restingValue", "restingHeartRate"
            ),
            "minHeartRateInBeatsPerMinute": _reading(leaves, "heartRate.minValue", "minHeartRate"),
            "maxHeartRateInBeatsPerMinute": _reading(leaves, "heartRate.maxValue", "maxHeartRate"),
            "averageStressLevel": _reading(leaves, "stress.avgLevel", "overallStressLevel"),
            "maxStressLevel": _reading(leaves, "stress.maxLevel", "maxStressLevel"),
        }
        # Personal API intensity minutes are minutes; the Health API mapper expects seconds.
        for key in ("moderateIntensityDurationInSeconds", "vigorousIntensityDurationInSeconds"):
            if raw[key] is not None:
                raw[key] = int(raw[key] * 60)
        counters = (
            "steps",
            "distanceInMeters",
            "activeKilocalories",
            "floorsClimbed",
            "moderateIntensityDurationInSeconds",
            "vigorousIntensityDurationInSeconds",
            "restingHeartRateInBeatsPerMinute",
        )
        if any(raw[key] for key in counters):
            dailies.append(raw)

        overnight = _reading(leaves, "lastNightAvg", "avgOvernightHrv")
        if overnight:
            hrv.append({"calendarDate": day.isoformat(), "lastNightAvg": overnight})

        spo2 = _reading(leaves, "pulseOx.avgValue", "averageSpO2", "averageSpo2", "avgSpo2")
        if spo2:
            pulseox.append({"calendarDate": day.isoformat(), "timeOffsetSpo2Values": {"0": spo2}})

        breathing = _number(leaves, "respiration.avgValue", "avgWakingRespirationValue")
        if breathing and breathing > 0:
            respiration.append({"calendarDate": day.isoformat(), "avgRespirationValue": breathing})

        deep = _reading(leaves, "deepSleepSeconds")
        light = _reading(leaves, "lightSleepSeconds")
        rem = _reading(leaves, "remSleepSeconds")
        asleep = _reading(leaves, "sleepTimeSeconds") or sum(
            value for value in (deep, light, rem) if value
        )
        if asleep and asleep > 0:
            score = _reading(leaves, "overall.value", "overallScore", "sleepScore")
            sleeps.append(
                {
                    "calendarDate": day.isoformat(),
                    "durationInSeconds": int(asleep),
                    "deepSleepDurationInSeconds": int(deep or 0),
                    "lightSleepDurationInSeconds": int(light or 0),
                    "remSleepInSeconds": int(rem or 0),
                    "awakeDurationInSeconds": int(_reading(leaves, "awakeSleepSeconds") or 0),
                    **({"overallSleepScore": {"value": int(score)}} if score else {}),
                }
            )

    for day, (high, low) in sorted(_body_battery(list(collected.values())).items()):
        battery.append(
            {
                "calendarDate": day.isoformat(),
                "timeOffsetBodyBatteryValues": {"0": high, "3600": low},
            }
        )

    return {
        "dailies": dailies,
        "sleeps": sleeps,
        "hrv": hrv,
        "pulseox": pulseox,
        "allDayRespiration": respiration,
        "stressDetails": battery,
        "activities": [],  # activity list and original FIT files come in a later step
    }


class PersonalApiSource:
    """GarminSource for a personal key. Missing data types are skipped, not fatal."""

    def __init__(
        self,
        api_key: str,
        *,
        http: httpx.Client | None = None,
        base_url: str = BASE_URL,
        clock: Callable[[], datetime] | None = None,
        token_provider: Callable[..., str] | None = None,
    ) -> None:
        self._clock = clock or utcnow
        self._http = http or httpx.Client(timeout=30.0)
        self._base = base_url.rstrip("/")
        self.token = token_provider or PersonalTokenProvider(
            api_key, http=self._http, base_url=base_url, clock=lambda: self._clock().timestamp()
        )
        self.skipped: dict[str, str] = {}

    def _windows(self, start: datetime, end: datetime, days: int):
        cursor = start
        step = timedelta(days=days)
        while cursor < end:
            yield cursor, min(cursor + step, end)
            cursor += step

    def fetch(self, start: datetime, end: datetime) -> dict[str, list[dict]]:
        collected: dict[str, list[Any]] = {}
        self.skipped = {}
        for kind, path, chunk_days, use_query in PLANS:
            collected[kind] = []
            for window_start, window_end in self._windows(start, end, chunk_days):
                first, last = window_start.date().isoformat(), window_end.date().isoformat()
                url = self._base + path.format(start=first, end=last)
                params = {"startDate": first, "endDate": last} if use_query else None
                response = self._http.get(
                    url,
                    params=params,
                    headers={
                        "Authorization": f"Bearer {self.token()}",
                        "Accept": "application/json",
                    },
                )
                if response.status_code == 403:
                    self.skipped[kind] = "HTTP 403"
                    break
                if response.status_code == 429:
                    retry = response.headers.get("Retry-After", "")
                    raise GarminRateLimitError(int(retry) if retry.isdigit() else None)
                if response.status_code != 200:
                    raise GarminAPIError(
                        f"Personal API returned HTTP {response.status_code} for {kind}",
                        response.status_code,
                    )
                try:
                    body = response.json()
                except ValueError as exc:
                    raise PersonalAPIError(
                        f"Personal API returned invalid JSON for {kind}"
                    ) from exc
                collected[kind].append(body)
        return map_personal_payload(collected)
