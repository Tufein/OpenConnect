from urllib.parse import parse_qs, urlparse

import httpx
import pytest

from openconnect.garmin.oauth import (
    GarminAuthError,
    GarminOAuth,
    code_challenge,
    new_code_verifier,
)
from openconnect.storage import Database, Repository


class Clock:
    def __init__(self, now: float = 1_000_000.0):
        self.now = now

    def __call__(self) -> float:
        return self.now


def make_oauth(handler, repo=None, clock=None):
    repo = repo or Repository(Database(":memory:"))
    clock = clock or Clock()
    http = httpx.Client(transport=httpx.MockTransport(handler))
    oauth = GarminOAuth(
        client_id="cid",
        client_secret="secret",
        redirect_uri="http://localhost:8000/api/garmin/callback",
        repo=repo,
        http=http,
        clock=clock,
    )
    return oauth, repo, clock


def token_response(access="acc1", refresh="ref1", expires_in=3600):
    return httpx.Response(
        200,
        json={"access_token": access, "refresh_token": refresh, "expires_in": expires_in,
              "token_type": "Bearer"},
    )  # fmt: skip


def test_pkce_challenge_matches_rfc7636_example():
    verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    assert code_challenge(verifier) == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"


def test_code_verifier_is_url_safe_and_long_enough():
    v1, v2 = new_code_verifier(), new_code_verifier()
    assert v1 != v2
    assert 43 <= len(v1) <= 128
    assert all(c.isalnum() or c in "-._~" for c in v1)


def test_start_builds_authorize_url_with_pkce_and_state():
    oauth, _, _ = make_oauth(lambda r: token_response())
    url, state = oauth.start()
    parsed = urlparse(url)
    q = {k: v[0] for k, v in parse_qs(parsed.query).items()}
    assert f"{parsed.scheme}://{parsed.netloc}{parsed.path}" == oauth.authorize_url
    assert q["response_type"] == "code" and q["client_id"] == "cid"
    assert q["code_challenge_method"] == "S256" and q["state"] == state
    assert q["redirect_uri"] == "http://localhost:8000/api/garmin/callback"
    assert len(q["code_challenge"]) == 43


def test_complete_exchanges_code_with_verifier_and_stores_tokens():
    seen = {}

    def handler(request: httpx.Request):
        seen["url"] = str(request.url)
        seen["form"] = {k: v[0] for k, v in parse_qs(request.content.decode()).items()}
        return token_response()

    oauth, repo, clock = make_oauth(handler)
    url, state = oauth.start()
    challenge = parse_qs(urlparse(url).query)["code_challenge"][0]
    tokens = oauth.complete(code="the-code", state=state)

    assert seen["url"] == oauth.token_url
    form = seen["form"]
    assert form["grant_type"] == "authorization_code" and form["code"] == "the-code"
    assert form["client_id"] == "cid" and form["client_secret"] == "secret"
    assert code_challenge(form["code_verifier"]) == challenge
    assert tokens.access_token == "acc1" and tokens.expires_at == clock.now + 3600
    assert repo.get_tokens("garmin")["refresh_token"] == "ref1"
    assert oauth.is_connected()


def test_complete_rejects_unknown_state_and_replay():
    oauth, _, _ = make_oauth(lambda r: token_response())
    with pytest.raises(GarminAuthError, match="state"):
        oauth.complete(code="c", state="forged")
    _, state = oauth.start()
    oauth.complete(code="c", state=state)
    with pytest.raises(GarminAuthError, match="state"):
        oauth.complete(code="c", state=state)  # single use


def test_complete_rejects_expired_state():
    oauth, _, clock = make_oauth(lambda r: token_response())
    _, state = oauth.start()
    clock.now += 11 * 60
    with pytest.raises(GarminAuthError, match="expired"):
        oauth.complete(code="c", state=state)


def test_complete_surfaces_provider_errors():
    oauth, repo, _ = make_oauth(lambda r: httpx.Response(400, json={"error": "invalid_grant"}))
    _, state = oauth.start()
    with pytest.raises(GarminAuthError, match="invalid_grant"):
        oauth.complete(code="bad", state=state)
    assert not oauth.is_connected()


def test_access_token_is_reused_until_close_to_expiry_then_refreshed():
    calls = []

    def handler(request: httpx.Request):
        form = {k: v[0] for k, v in parse_qs(request.content.decode()).items()}
        calls.append(form["grant_type"])
        if form["grant_type"] == "refresh_token":
            assert form["refresh_token"] == "ref1"
            return token_response(access="acc2", refresh="ref2")
        return token_response()

    oauth, repo, clock = make_oauth(handler)
    _, state = oauth.start()
    oauth.complete("c", state)
    assert oauth.access_token() == "acc1"
    clock.now += 3000
    assert oauth.access_token() == "acc1"  # still valid
    assert calls == ["authorization_code"]
    clock.now += 600  # within the 60 s safety margin of expiry
    assert oauth.access_token() == "acc2"
    assert calls == ["authorization_code", "refresh_token"]
    assert repo.get_tokens("garmin")["refresh_token"] == "ref2"  # rotated refresh token kept


def test_force_refresh_ignores_expiry():
    oauth, _, _ = make_oauth(lambda r: token_response(access="fresh"))
    _, state = oauth.start()
    oauth.complete("c", state)
    assert oauth.access_token(force_refresh=True) == "fresh"


def test_refresh_failure_disconnects():
    state_box = {}

    def handler(request: httpx.Request):
        if b"refresh_token" in request.content and b"grant_type=refresh_token" in request.content:
            return httpx.Response(400, json={"error": "invalid_grant"})
        return token_response()

    oauth, repo, clock = make_oauth(handler)
    _, state_box["s"] = oauth.start()
    oauth.complete("c", state_box["s"])
    clock.now += 7200
    with pytest.raises(GarminAuthError):
        oauth.access_token()
    assert not oauth.is_connected()


def test_access_token_without_connection_raises():
    oauth, _, _ = make_oauth(lambda r: token_response())
    with pytest.raises(GarminAuthError, match="not connected"):
        oauth.access_token()


def test_disconnect_removes_tokens():
    oauth, repo, _ = make_oauth(lambda r: token_response())
    _, state = oauth.start()
    oauth.complete("c", state)
    oauth.disconnect()
    assert repo.get_tokens("garmin") is None and not oauth.is_connected()
