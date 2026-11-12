from __future__ import annotations

import contextlib

import pytest

from openconnect.storage import Database


@pytest.fixture(autouse=True)
def _close_databases(monkeypatch):
    """Close every SQLite database a test opened, so no connection leaks between tests."""
    opened: list[Database] = []
    original = Database.__init__

    def tracking(self, *args, **kwargs):
        original(self, *args, **kwargs)
        opened.append(self)

    monkeypatch.setattr(Database, "__init__", tracking)
    yield
    for db in opened:
        with contextlib.suppress(Exception):
            db.close()
