from __future__ import annotations

from datetime import UTC, datetime

import httpx
import pytest
from fastapi.testclient import TestClient

from openconnect.ai.provider import TemplateReportProvider
from openconnect.config import Settings
from openconnect.web.app import create_app

NOW = datetime(2026, 5, 31, 12, 0, tzinfo=UTC)


class App:
    def __init__(self, client: TestClient, ctx):
        self.client = client
        self.ctx = ctx


@pytest.fixture
def make_app():
    def factory(provider=None, oauth_handler=None, **settings):
        base = dict(base_url="http://testserver", db_path=":memory:")
        base.update(settings)
        app = create_app(
            Settings(**base),
            clock=lambda: NOW,
            provider=provider or TemplateReportProvider(),
            oauth_http=httpx.Client(transport=httpx.MockTransport(oauth_handler))
            if oauth_handler
            else None,
        )
        return App(TestClient(app), app.state.ctx)

    return factory


@pytest.fixture
def app(make_app):
    return make_app()


@pytest.fixture
def synced(app):
    response = app.client.post("/api/sync", params={"days_back": 28})
    assert response.status_code == 200, response.text
    return app
