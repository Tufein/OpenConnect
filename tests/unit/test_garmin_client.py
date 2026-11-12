from datetime import UTC, datetime, timedelta
from urllib.parse import parse_qs

import httpx
import pytest

from openconnect.garmin.client import (
    GarminAPIError,
    GarminRateLimitError,
    HttpGarminSource,
)

T0 = datetime(2026, 5, 1, 0, 0, tzinfo=UTC)


class Tokens:
    def __init__(self):
        self.calls = []

    def __call__(self, force_refresh: bool = False) -> str:
        self.calls.append(force_refresh)
        return "fresh" if force_refresh else "stale"


def source_with(handler, tokens=None, kinds=None):
    http = httpx.Client(transport=httpx.MockTransport(handler))
    kwargs = {"kinds": kinds} if kinds else {}
    return HttpGarminSource(tokens or Tokens(), http=http, **kwargs)


def test_fetch_requests_each_kind_in_24h_windows_with_bearer_token():
    requests = []

    def handler(request: httpx.Request):
        requests.append(request)
        return httpx.Response(200, json=[{"summaryId": request.url.path}])

    src = source_with(handler, kinds=("dailies", "sleeps"))
    payload = src.fetch(T0, T0 + timedelta(hours=60))  # 24h + 24h + 12h

    assert set(payload) == {"dailies", "sleeps"}
    assert len(payload["dailies"]) == 3 and len(payload["sleeps"]) == 3
    assert len(requests) == 6
    first = requests[0]
    assert first.url.host == "apis.garmin.com"
    assert first.url.path == "/wellness-api/rest/dailies"
    assert first.headers["Authorization"] == "Bearer stale"
    q = {k: int(v[0]) for k, v in parse_qs(first.url.query.decode()).items()}
    assert q["uploadStartTimeInSeconds"] == int(T0.timestamp())
    assert q["uploadEndTimeInSeconds"] == int((T0 + timedelta(hours=24)).timestamp())
    last = [r for r in requests if r.url.path.endswith("dailies")][-1]
    last_q = {k: int(v[0]) for k, v in parse_qs(last.url.query.decode()).items()}
    assert last_q["uploadEndTimeInSeconds"] == int((T0 + timedelta(hours=60)).timestamp())


def test_fetch_retries_once_with_a_refreshed_token_on_401():
    seen = []

    def handler(request: httpx.Request):
        seen.append(request.headers["Authorization"])
        if request.headers["Authorization"] == "Bearer stale":
            return httpx.Response(401, json={"message": "expired"})
        return httpx.Response(200, json=[{"summaryId": "ok"}])

    tokens = Tokens()
    src = source_with(handler, tokens, kinds=("dailies",))
    payload = src.fetch(T0, T0 + timedelta(hours=1))
    assert payload["dailies"] == [{"summaryId": "ok"}]
    assert seen == ["Bearer stale", "Bearer fresh"]
    assert tokens.calls == [False, True]


def test_persistent_401_raises_api_error():
    src = source_with(lambda r: httpx.Response(401, text="nope"), kinds=("dailies",))
    with pytest.raises(GarminAPIError) as exc:
        src.fetch(T0, T0 + timedelta(hours=1))
    assert exc.value.status_code == 401


def test_rate_limit_exposes_retry_after():
    src = source_with(
        lambda r: httpx.Response(429, headers={"Retry-After": "120"}), kinds=("dailies",)
    )
    with pytest.raises(GarminRateLimitError) as exc:
        src.fetch(T0, T0 + timedelta(hours=1))
    assert exc.value.retry_after == 120


def test_forbidden_kind_is_skipped_and_reported():
    def handler(request: httpx.Request):
        if request.url.path.endswith("sleeps"):
            return httpx.Response(403, json={"message": "no permission"})
        return httpx.Response(200, json=[{"summaryId": "d"}])

    src = source_with(handler, kinds=("dailies", "sleeps"))
    payload = src.fetch(T0, T0 + timedelta(hours=1))
    assert payload["dailies"] == [{"summaryId": "d"}]
    assert payload["sleeps"] == []
    assert src.skipped == {"sleeps": "HTTP 403"}


def test_server_errors_raise_api_error_with_body_excerpt():
    src = source_with(lambda r: httpx.Response(500, text="boom" * 100), kinds=("dailies",))
    with pytest.raises(GarminAPIError) as exc:
        src.fetch(T0, T0 + timedelta(hours=1))
    assert exc.value.status_code == 500 and "boom" in str(exc.value)
    assert len(str(exc.value)) < 400


def test_non_list_responses_are_rejected():
    src = source_with(lambda r: httpx.Response(200, json={"oops": 1}), kinds=("dailies",))
    with pytest.raises(GarminAPIError, match="unexpected"):
        src.fetch(T0, T0 + timedelta(hours=1))


def test_request_backfill_chunks_into_90_day_windows():
    requests = []

    def handler(request: httpx.Request):
        requests.append(request)
        return httpx.Response(202)

    src = source_with(handler)
    n = src.request_backfill("dailies", T0, T0 + timedelta(days=200))
    assert n == 3 == len(requests)
    assert all(r.method == "POST" for r in requests)
    assert requests[0].url.path == "/wellness-api/rest/backfill/dailies"
    q = {k: int(v[0]) for k, v in parse_qs(requests[0].url.query.decode()).items()}
    assert q["summaryEndTimeInSeconds"] - q["summaryStartTimeInSeconds"] == 90 * 86400


def test_request_backfill_surfaces_errors():
    src = source_with(lambda r: httpx.Response(409, text="duplicate"))
    with pytest.raises(GarminAPIError):
        src.request_backfill("dailies", T0, T0 + timedelta(days=1))
