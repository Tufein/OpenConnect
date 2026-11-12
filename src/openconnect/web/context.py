from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from typing import Any

import httpx

from openconnect.ai.provider import ReportProvider, build_provider
from openconnect.ai.reports import ReportService
from openconnect.config import Settings
from openconnect.garmin.client import GarminSource, HttpGarminSource
from openconnect.garmin.fake import FakeGarminSource
from openconnect.garmin.oauth import GarminOAuth
from openconnect.services.dashboard import DashboardService
from openconnect.services.sync import SyncService, utcnow
from openconnect.storage import Database, Repository


@dataclass
class AppContext:
    settings: Settings
    repo: Repository
    source: GarminSource
    oauth: GarminOAuth | None
    sync: SyncService
    dashboard: DashboardService
    reports: ReportService
    provider: ReportProvider
    clock: Callable[[], datetime]

    @property
    def mode(self) -> str:
        return self.settings.garmin_mode

    @property
    def garmin_connected(self) -> bool:
        return True if self.oauth is None else self.oauth.is_connected()


def build_context(
    settings: Settings,
    *,
    clock: Callable[[], datetime] = utcnow,
    source: GarminSource | None = None,
    provider: ReportProvider | None = None,
    oauth_http: httpx.Client | None = None,
    anthropic_client: Any | None = None,
) -> AppContext:
    repo = Repository(Database(settings.db_path))
    oauth: GarminOAuth | None = None
    if settings.garmin_mode == "live":
        urls: dict[str, str] = {}
        if settings.garmin_authorize_url:
            urls["authorize_url"] = settings.garmin_authorize_url
        if settings.garmin_token_url:
            urls["token_url"] = settings.garmin_token_url
        oauth = GarminOAuth(
            settings.garmin_client_id or "",
            settings.garmin_client_secret or "",
            settings.redirect_uri,
            repo,
            http=oauth_http,
            **urls,
        )
        if source is None:
            extra = (
                {"base_url": settings.garmin_api_base_url} if settings.garmin_api_base_url else {}
            )
            source = HttpGarminSource(oauth.access_token, **extra)
    elif source is None:
        source = FakeGarminSource(today=clock)

    provider = provider or build_provider(settings, client=anthropic_client)
    dashboard = DashboardService(repo, clock=clock)
    return AppContext(
        settings=settings,
        repo=repo,
        source=source,
        oauth=oauth,
        sync=SyncService(repo, source, clock=clock),
        dashboard=dashboard,
        reports=ReportService(repo, provider, dashboard, clock=clock, language=settings.language),
        provider=provider,
        clock=clock,
    )
