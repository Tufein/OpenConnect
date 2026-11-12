"""Sources of Garmin data.

``GarminSource.fetch`` returns a payload shaped exactly like Garmin's push notifications:
``{"dailies": [...], "sleeps": [...], "activities": [...], ...}`` so that pulled data, pushed data
(webhook) and the offline fake all flow through the same ingest code.
"""

from __future__ import annotations

from collections.abc import Callable
from datetime import datetime, timedelta
from typing import Protocol

import httpx

KINDS = ("activities", "activityDetails", "dailies", "sleeps", "stressDetails")
DEFAULT_BASE_URL = "https://apis.garmin.com"
DEFAULT_API_PREFIX = "/wellness-api/rest"
BACKFILL_MAX_DAYS = 90
PULL_WINDOW = timedelta(hours=24)


class GarminSource(Protocol):
    def fetch(self, start: datetime, end: datetime) -> dict[str, list[dict]]: ...


class GarminAPIError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


class GarminRateLimitError(GarminAPIError):
    def __init__(self, retry_after: int | None) -> None:
        super().__init__("Garmin rate limit reached", 429)
        self.retry_after = retry_after


TokenProvider = Callable[..., str]  # (force_refresh: bool = False) -> access token


class HttpGarminSource:
    """Pull client for the Garmin Health and Activity APIs (summaries by upload time)."""

    def __init__(
        self,
        token_provider: TokenProvider,
        *,
        base_url: str = DEFAULT_BASE_URL,
        api_prefix: str = DEFAULT_API_PREFIX,
        http: httpx.Client | None = None,
        kinds: tuple[str, ...] = KINDS,
    ) -> None:
        self._token = token_provider
        self._base = base_url.rstrip("/") + api_prefix
        self._http = http or httpx.Client(timeout=30.0)
        self.kinds = kinds
        self.skipped: dict[str, str] = {}

    def _request(self, method: str, path: str, params: dict[str, int]) -> httpx.Response:
        url = f"{self._base}/{path}"
        force = False
        for _attempt in range(2):
            response = self._http.request(
                method,
                url,
                params=params,
                headers={"Authorization": f"Bearer {self._token(force)}"},
            )
            if response.status_code == 401 and not force:
                force = True  # the token may have been revoked or expired early: refresh once
                continue
            break
        if response.status_code == 429:
            retry = response.headers.get("Retry-After", "")
            raise GarminRateLimitError(int(retry) if retry.isdigit() else None)
        return response

    @staticmethod
    def _error(response: httpx.Response) -> GarminAPIError:
        return GarminAPIError(
            f"Garmin API returned HTTP {response.status_code}: {response.text[:120]}",
            response.status_code,
        )

    def fetch(self, start: datetime, end: datetime) -> dict[str, list[dict]]:
        payload: dict[str, list[dict]] = {kind: [] for kind in self.kinds}
        self.skipped = {}
        for kind in self.kinds:
            cursor = start
            while cursor < end:
                window_end = min(cursor + PULL_WINDOW, end)
                response = self._request(
                    "GET",
                    kind,
                    {
                        "uploadStartTimeInSeconds": int(cursor.timestamp()),
                        "uploadEndTimeInSeconds": int(window_end.timestamp()),
                    },
                )
                if response.status_code == 403:
                    # The user did not grant this data type (or the app lacks the API).
                    self.skipped[kind] = "HTTP 403"
                    break
                if response.status_code != 200:
                    raise self._error(response)
                body = response.json()
                if not isinstance(body, list):
                    raise GarminAPIError(f"Garmin returned an unexpected shape for {kind}")
                payload[kind].extend(body)
                cursor = window_end
        return payload

    def request_backfill(self, kind: str, start: datetime, end: datetime) -> int:
        """Ask Garmin to (re)send historical data via push. Returns the number of requests made."""
        count, cursor = 0, start
        step = timedelta(days=BACKFILL_MAX_DAYS)
        while cursor < end:
            window_end = min(cursor + step, end)
            response = self._request(
                "POST",
                f"backfill/{kind}",
                {
                    "summaryStartTimeInSeconds": int(cursor.timestamp()),
                    "summaryEndTimeInSeconds": int(window_end.timestamp()),
                },
            )
            if response.status_code not in (200, 202):
                raise self._error(response)
            count += 1
            cursor = window_end
        return count
