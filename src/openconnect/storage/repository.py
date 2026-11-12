from __future__ import annotations

import json
import zlib
from datetime import date, datetime

from openconnect.domain import (
    Activity,
    DailySummary,
    GeneratedReport,
    SleepSummary,
    Sport,
    TrackPoint,
)
from openconnect.storage.db import Database

_TRACK_FIELDS = ("time", "lat", "lon", "ele", "hr", "cadence", "power", "distance_m")


def _encode_track(track: list[TrackPoint]) -> bytes | None:
    if not track:
        return None
    rows = [
        [p.time.isoformat(), p.lat, p.lon, p.ele, p.hr, p.cadence, p.power, p.distance_m]
        for p in track
    ]
    return zlib.compress(json.dumps(rows, separators=(",", ":")).encode())


def _decode_track(blob: bytes | None) -> list[TrackPoint]:
    if not blob:
        return []
    rows = json.loads(zlib.decompress(blob))
    return [TrackPoint(**dict(zip(_TRACK_FIELDS, row, strict=True))) for row in rows]


def _activity_from_row(row, include_track: bool) -> Activity:
    return Activity(
        id=row["id"],
        source=row["source"],
        external_id=row["external_id"],
        name=row["name"],
        sport=Sport(row["sport"]),
        start_time=datetime.fromisoformat(row["start_time"]),
        duration_s=row["duration_s"],
        distance_m=row["distance_m"],
        elevation_gain_m=row["elevation_gain_m"],
        avg_hr=row["avg_hr"],
        max_hr=row["max_hr"],
        calories=row["calories"],
        device_name=row["device_name"],
        track=_decode_track(row["track"]) if include_track else [],
    )


_LIST_COLUMNS = (
    "id, source, external_id, name, sport, start_time, duration_s, distance_m, "
    "elevation_gain_m, avg_hr, max_hr, calories, device_name"
)


class Repository:
    def __init__(self, db: Database) -> None:
        self.db = db

    # ---- activities

    def upsert_activity(self, a: Activity) -> None:
        with self.db.transaction() as conn:
            conn.execute(
                """INSERT INTO activities (id, source, external_id, name, sport, start_time,
                       duration_s, distance_m, elevation_gain_m, avg_hr, max_hr, calories,
                       device_name, track)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                   ON CONFLICT(id) DO UPDATE SET
                       source=excluded.source, external_id=excluded.external_id,
                       name=excluded.name, sport=excluded.sport, start_time=excluded.start_time,
                       duration_s=excluded.duration_s, distance_m=excluded.distance_m,
                       elevation_gain_m=excluded.elevation_gain_m, avg_hr=excluded.avg_hr,
                       max_hr=excluded.max_hr, calories=excluded.calories,
                       device_name=excluded.device_name, track=excluded.track""",
                (
                    a.id, a.source, a.external_id, a.name, a.sport.value,
                    a.start_time.isoformat(), a.duration_s, a.distance_m, a.elevation_gain_m,
                    a.avg_hr, a.max_hr, a.calories, a.device_name, _encode_track(a.track),
                ),
            )  # fmt: skip

    def get_activity(self, activity_id: str, include_track: bool = True) -> Activity | None:
        with self.db.transaction() as conn:
            row = conn.execute("SELECT * FROM activities WHERE id = ?", (activity_id,)).fetchone()
        return _activity_from_row(row, include_track) if row else None

    @staticmethod
    def _filters(sport: Sport | None, start: datetime | None, end: datetime | None):
        clauses, params = [], []
        if sport:
            clauses.append("sport = ?")
            params.append(sport.value)
        if start:
            clauses.append("start_time >= ?")
            params.append(start.isoformat())
        if end:
            clauses.append("start_time < ?")
            params.append(end.isoformat())
        where = (" WHERE " + " AND ".join(clauses)) if clauses else ""
        return where, params

    def list_activities(
        self,
        sport: Sport | None = None,
        start: datetime | None = None,
        end: datetime | None = None,
        limit: int = 50,
        offset: int = 0,
    ) -> list[Activity]:
        where, params = self._filters(sport, start, end)
        with self.db.transaction() as conn:
            rows = conn.execute(
                f"SELECT {_LIST_COLUMNS} FROM activities{where} "
                "ORDER BY start_time DESC LIMIT ? OFFSET ?",
                [*params, limit, offset],
            ).fetchall()
        return [_activity_from_row(r, include_track=False) for r in rows]

    def count_activities(
        self,
        sport: Sport | None = None,
        start: datetime | None = None,
        end: datetime | None = None,
    ) -> int:
        where, params = self._filters(sport, start, end)
        with self.db.transaction() as conn:
            return conn.execute(f"SELECT COUNT(*) FROM activities{where}", params).fetchone()[0]

    def delete_activity(self, activity_id: str) -> bool:
        with self.db.transaction() as conn:
            cur = conn.execute("DELETE FROM activities WHERE id = ?", (activity_id,))
            return cur.rowcount > 0

    def list_devices(self) -> list[dict]:
        with self.db.transaction() as conn:
            rows = conn.execute(
                "SELECT device_name AS name, COUNT(*) AS activities, MAX(start_time) AS last_used "
                "FROM activities WHERE device_name IS NOT NULL AND device_name != '' "
                "GROUP BY device_name ORDER BY activities DESC, last_used DESC"
            ).fetchall()
        return [dict(r) for r in rows]

    # ---- daily health

    def upsert_daily(self, d: DailySummary) -> None:
        with self.db.transaction() as conn:
            conn.execute(
                "INSERT INTO daily_summaries (date, data) VALUES (?, ?) "
                "ON CONFLICT(date) DO UPDATE SET data = excluded.data",
                (d.date.isoformat(), d.model_dump_json()),
            )

    def get_daily(self, day: date) -> DailySummary | None:
        with self.db.transaction() as conn:
            row = conn.execute(
                "SELECT data FROM daily_summaries WHERE date = ?", (day.isoformat(),)
            ).fetchone()
        return DailySummary.model_validate_json(row["data"]) if row else None

    def list_daily(self, start: date, end: date) -> list[DailySummary]:
        """Inclusive date range, ascending."""
        with self.db.transaction() as conn:
            rows = conn.execute(
                "SELECT data FROM daily_summaries WHERE date >= ? AND date <= ? ORDER BY date",
                (start.isoformat(), end.isoformat()),
            ).fetchall()
        return [DailySummary.model_validate_json(r["data"]) for r in rows]

    def upsert_sleep(self, s: SleepSummary) -> None:
        with self.db.transaction() as conn:
            conn.execute(
                "INSERT INTO sleep_summaries (date, data) VALUES (?, ?) "
                "ON CONFLICT(date) DO UPDATE SET data = excluded.data",
                (s.date.isoformat(), s.model_dump_json()),
            )

    def list_sleep(self, start: date, end: date) -> list[SleepSummary]:
        with self.db.transaction() as conn:
            rows = conn.execute(
                "SELECT data FROM sleep_summaries WHERE date >= ? AND date <= ? ORDER BY date",
                (start.isoformat(), end.isoformat()),
            ).fetchall()
        return [SleepSummary.model_validate_json(r["data"]) for r in rows]

    # ---- reports

    def save_report(self, report: GeneratedReport) -> GeneratedReport:
        with self.db.transaction() as conn:
            cur = conn.execute(
                "INSERT INTO reports "
                "(kind, subject, text, provider, model, facts_hash, created_at) "
                "VALUES (?,?,?,?,?,?,?)",
                (report.kind, report.subject, report.text, report.provider, report.model,
                 report.facts_hash, report.created_at.isoformat()),
            )  # fmt: skip
            return report.model_copy(update={"id": cur.lastrowid})

    def get_report(self, kind: str, subject: str) -> GeneratedReport | None:
        with self.db.transaction() as conn:
            row = conn.execute(
                "SELECT * FROM reports WHERE kind = ? AND subject = ? "
                "ORDER BY created_at DESC, id DESC LIMIT 1",
                (kind, subject),
            ).fetchone()
        if not row:
            return None
        return GeneratedReport(
            id=row["id"],
            kind=row["kind"],
            subject=row["subject"],
            text=row["text"],
            provider=row["provider"],
            model=row["model"],
            facts_hash=row["facts_hash"],
            created_at=datetime.fromisoformat(row["created_at"]),
        )

    # ---- tokens and key/value state

    def save_tokens(self, provider: str, tokens: dict) -> None:
        with self.db.transaction() as conn:
            conn.execute(
                "INSERT INTO oauth_tokens (provider, data) VALUES (?, ?) "
                "ON CONFLICT(provider) DO UPDATE SET data = excluded.data",
                (provider, json.dumps(tokens)),
            )

    def get_tokens(self, provider: str) -> dict | None:
        with self.db.transaction() as conn:
            row = conn.execute(
                "SELECT data FROM oauth_tokens WHERE provider = ?", (provider,)
            ).fetchone()
        return json.loads(row["data"]) if row else None

    def delete_tokens(self, provider: str) -> None:
        with self.db.transaction() as conn:
            conn.execute("DELETE FROM oauth_tokens WHERE provider = ?", (provider,))

    def get_state(self, key: str) -> str | None:
        with self.db.transaction() as conn:
            row = conn.execute("SELECT value FROM state WHERE key = ?", (key,)).fetchone()
        return row["value"] if row else None

    def set_state(self, key: str, value: str) -> None:
        with self.db.transaction() as conn:
            conn.execute(
                "INSERT INTO state (key, value) VALUES (?, ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (key, value),
            )
