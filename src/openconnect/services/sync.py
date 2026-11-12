from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from openconnect.garmin.client import GarminSource
from openconnect.garmin.ingest import IngestResult, ingest_payload
from openconnect.storage import Repository

LAST_SYNC_KEY = "last_sync"


def utcnow() -> datetime:
    return datetime.now(UTC)


@dataclass
class SyncResult:
    start: datetime
    end: datetime
    ingest: IngestResult


class SyncService:
    def __init__(
        self,
        repo: Repository,
        source: GarminSource,
        clock: Callable[[], datetime] = utcnow,
        days_back: int = 30,
    ) -> None:
        self.repo = repo
        self.source = source
        self.clock = clock
        self.days_back = days_back

    def sync(self, days_back: int | None = None) -> SyncResult:
        """Pull everything since the last successful sync (or ``days_back`` days if forced)."""
        end = self.clock()
        last = self.repo.get_state(LAST_SYNC_KEY)
        if days_back is not None:
            start = end - timedelta(days=days_back)
        elif last:
            start = datetime.fromisoformat(last)
        else:
            start = end - timedelta(days=self.days_back)
        payload = self.source.fetch(start, end)  # may raise: the cursor then stays where it was
        ingest = ingest_payload(self.repo, payload)
        self.repo.set_state(LAST_SYNC_KEY, end.isoformat())
        return SyncResult(start=start, end=end, ingest=ingest)
