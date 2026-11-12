from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

GARMIN_MODES = ("mock", "live")
AI_PROVIDERS = ("auto", "anthropic", "template")
AI_EFFORTS = ("low", "medium", "high", "xhigh", "max")


class ConfigError(ValueError):
    """Invalid or incomplete configuration."""


def _choice(env: Mapping[str, str], key: str, allowed: tuple[str, ...], default: str) -> str:
    value = (env.get(key) or default).lower()
    if value not in allowed:
        raise ConfigError(f"{key} must be one of {', '.join(allowed)} (got {value!r})")
    return value


@dataclass(frozen=True)
class Settings:
    db_path: Path = Path("data/openconnect.db")
    host: str = "127.0.0.1"
    port: int = 8000
    base_url: str = "http://localhost:8000"
    language: str = "en"
    garmin_mode: str = "mock"
    garmin_client_id: str | None = None
    garmin_client_secret: str | None = None
    garmin_authorize_url: str | None = None
    garmin_token_url: str | None = None
    garmin_api_base_url: str | None = None
    webhook_token: str | None = None
    ai_provider: str = "auto"
    ai_model: str = "claude-opus-5-5"
    ai_effort: str = "medium"
    anthropic_api_key: str | None = None
    extra_hosts: tuple[str, ...] = ()

    @property
    def allowed_hosts(self) -> list[str]:
        """Host headers we answer to; anything else is refused (DNS-rebinding defence)."""
        hosts = {"localhost", "127.0.0.1", "[::1]", "::1", *self.extra_hosts}
        if urlparse(self.base_url).hostname:
            hosts.add(urlparse(self.base_url).hostname)  # type: ignore[arg-type]
        return sorted(hosts)

    @property
    def redirect_uri(self) -> str:
        return f"{self.base_url}/api/garmin/callback"

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> Settings:
        env = os.environ if env is None else env

        def get(key: str) -> str | None:
            return env.get(key) or None

        try:
            port = int(env.get("OPENCONNECT_PORT") or 8000)
        except ValueError as exc:
            raise ConfigError("OPENCONNECT_PORT must be an integer") from exc

        settings = cls(
            db_path=Path(env.get("OPENCONNECT_DB") or "data/openconnect.db"),
            host=env.get("OPENCONNECT_HOST") or "127.0.0.1",
            port=port,
            base_url=(env.get("OPENCONNECT_BASE_URL") or f"http://localhost:{port}").rstrip("/"),
            language=(env.get("OPENCONNECT_LANGUAGE") or "en").lower(),
            garmin_mode=_choice(env, "OPENCONNECT_GARMIN_MODE", GARMIN_MODES, "mock"),
            garmin_client_id=get("GARMIN_CLIENT_ID"),
            garmin_client_secret=get("GARMIN_CLIENT_SECRET"),
            garmin_authorize_url=get("GARMIN_AUTHORIZE_URL"),
            garmin_token_url=get("GARMIN_TOKEN_URL"),
            garmin_api_base_url=get("GARMIN_API_BASE_URL"),
            webhook_token=get("OPENCONNECT_WEBHOOK_TOKEN"),
            ai_provider=_choice(env, "OPENCONNECT_AI_PROVIDER", AI_PROVIDERS, "auto"),
            ai_model=env.get("OPENCONNECT_AI_MODEL") or "claude-opus-5-5",
            ai_effort=_choice(env, "OPENCONNECT_AI_EFFORT", AI_EFFORTS, "medium"),
            anthropic_api_key=get("ANTHROPIC_API_KEY"),
            extra_hosts=tuple(
                h.strip()
                for h in (env.get("OPENCONNECT_ALLOWED_HOSTS") or "").split(",")
                if h.strip()
            ),
        )
        if settings.garmin_mode == "live" and not (
            settings.garmin_client_id and settings.garmin_client_secret
        ):
            raise ConfigError(
                "OPENCONNECT_GARMIN_MODE=live requires GARMIN_CLIENT_ID and GARMIN_CLIENT_SECRET"
            )
        return settings
