from __future__ import annotations

import sqlite3
import threading
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS activities (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL,
    external_id TEXT,
    name TEXT NOT NULL,
    sport TEXT NOT NULL,
    start_time TEXT NOT NULL,
    duration_s REAL NOT NULL,
    distance_m REAL NOT NULL,
    elevation_gain_m REAL NOT NULL,
    avg_hr INTEGER,
    max_hr INTEGER,
    calories INTEGER,
    device_name TEXT,
    track BLOB
);
CREATE INDEX IF NOT EXISTS idx_activities_start ON activities (start_time DESC);
CREATE INDEX IF NOT EXISTS idx_activities_sport ON activities (sport);

CREATE TABLE IF NOT EXISTS daily_summaries (date TEXT PRIMARY KEY, data TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS sleep_summaries (date TEXT PRIMARY KEY, data TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL,
    subject TEXT NOT NULL,
    text TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reports_subject ON reports (kind, subject, created_at DESC);

CREATE TABLE IF NOT EXISTS oauth_tokens (provider TEXT PRIMARY KEY, data TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""


class Database:
    """A single shared SQLite connection guarded by a lock.

    OpenConnect is a single-user, self-hosted app, so one serialized connection is plenty and keeps
    ``:memory:`` databases usable from FastAPI's worker threads in tests.
    """

    def __init__(self, path: str | Path = ":memory:") -> None:
        if str(path) != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(str(path), check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._lock = threading.RLock()
        self.migrate()

    def migrate(self) -> None:
        with self.transaction() as conn:
            conn.executescript(SCHEMA)

    @contextmanager
    def transaction(self) -> Iterator[sqlite3.Connection]:
        with self._lock:
            try:
                yield self._conn
                self._conn.commit()
            except BaseException:
                self._conn.rollback()
                raise

    def close(self) -> None:
        with self._lock:
            self._conn.close()
