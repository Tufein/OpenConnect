"""OAuth 2.0 authorization-code flow with PKCE (S256) for the Garmin Connect Developer Program."""

from __future__ import annotations

import base64
import contextlib
import hashlib
import json
import secrets
import time
from collections.abc import Callable
from dataclasses import asdict, dataclass
from urllib.parse import urlencode

import httpx

from openconnect.storage import Repository

PROVIDER = "garmin"
# Endpoints as published in Garmin's OAuth2 PKCE specification; both are configurable.
DEFAULT_AUTHORIZE_URL = "https://connect.garmin.com/oauth2Confirm"
DEFAULT_TOKEN_URL = "https://connectapi.garmin.com/di-oauth2-service/oauth/token"
PENDING_TTL_S = 10 * 60
EXPIRY_MARGIN_S = 60


class GarminAuthError(RuntimeError):
    pass


def new_code_verifier() -> str:
    return secrets.token_urlsafe(64)[:96]


def code_challenge(verifier: str) -> str:
    digest = hashlib.sha256(verifier.encode("ascii")).digest()
    return base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")


@dataclass
class TokenSet:
    access_token: str
    refresh_token: str
    expires_at: float
    token_type: str = "Bearer"


class GarminOAuth:
    def __init__(
        self,
        client_id: str,
        client_secret: str,
        redirect_uri: str,
        repo: Repository,
        *,
        authorize_url: str = DEFAULT_AUTHORIZE_URL,
        token_url: str = DEFAULT_TOKEN_URL,
        http: httpx.Client | None = None,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self.client_id = client_id
        self.client_secret = client_secret
        self.redirect_uri = redirect_uri
        self.repo = repo
        self.authorize_url = authorize_url
        self.token_url = token_url
        self._http = http or httpx.Client(timeout=30.0)
        self._clock = clock

    # ---- authorization

    def start(self) -> tuple[str, str]:
        """Return ``(authorize_url, state)``; the PKCE verifier is kept server-side."""
        verifier, state = new_code_verifier(), secrets.token_urlsafe(24)
        self.repo.set_state(
            f"oauth_pending:{state}",
            json.dumps({"verifier": verifier, "created": self._clock()}),
        )
        query = urlencode(
            {
                "response_type": "code",
                "client_id": self.client_id,
                "redirect_uri": self.redirect_uri,
                "code_challenge": code_challenge(verifier),
                "code_challenge_method": "S256",
                "state": state,
            }
        )
        return f"{self.authorize_url}?{query}", state

    def complete(self, code: str, state: str) -> TokenSet:
        key = f"oauth_pending:{state}"
        raw = self.repo.get_state(key)
        if raw is None:
            raise GarminAuthError("Unknown or already used OAuth state")
        self.repo.set_state(key, "")  # single use, even if the exchange below fails
        pending = json.loads(raw) if raw else None
        if not pending:
            raise GarminAuthError("Unknown or already used OAuth state")
        if self._clock() - pending["created"] > PENDING_TTL_S:
            raise GarminAuthError("OAuth state expired; start the connection again")
        return self._token_request(
            {
                "grant_type": "authorization_code",
                "code": code,
                "code_verifier": pending["verifier"],
                "redirect_uri": self.redirect_uri,
            }
        )

    # ---- tokens

    def _token_request(self, params: dict[str, str]) -> TokenSet:
        form = {"client_id": self.client_id, "client_secret": self.client_secret, **params}
        try:
            response = self._http.post(self.token_url, data=form)
        except httpx.HTTPError as exc:
            raise GarminAuthError(f"Could not reach Garmin's token endpoint: {exc}") from exc
        if response.status_code != 200:
            detail = response.text[:200]
            with contextlib.suppress(ValueError):
                detail = response.json().get("error", detail)
            raise GarminAuthError(f"Token request failed ({response.status_code}): {detail}")
        body = response.json()
        tokens = TokenSet(
            access_token=body["access_token"],
            refresh_token=body.get("refresh_token", params.get("refresh_token", "")),
            expires_at=self._clock() + float(body.get("expires_in", 3600)),
            token_type=body.get("token_type", "Bearer"),
        )
        self.repo.save_tokens(PROVIDER, asdict(tokens))
        return tokens

    def _stored(self) -> TokenSet | None:
        data = self.repo.get_tokens(PROVIDER)
        return TokenSet(**data) if data else None

    def is_connected(self) -> bool:
        return self._stored() is not None

    def access_token(self, force_refresh: bool = False) -> str:
        tokens = self._stored()
        if tokens is None:
            raise GarminAuthError("Garmin is not connected; authorize OpenConnect first")
        if not force_refresh and tokens.expires_at - self._clock() > EXPIRY_MARGIN_S:
            return tokens.access_token
        try:
            return self._token_request(
                {"grant_type": "refresh_token", "refresh_token": tokens.refresh_token}
            ).access_token
        except GarminAuthError:
            # A rejected refresh token cannot recover on its own: make the user re-authorize.
            self.disconnect()
            raise

    def disconnect(self) -> None:
        self.repo.delete_tokens(PROVIDER)
