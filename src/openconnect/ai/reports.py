from __future__ import annotations

import hashlib
import json
from collections.abc import Callable
from datetime import date, datetime

from openconnect.ai.facts import activity_facts, weekly_facts
from openconnect.ai.provider import ReportProvider, ReportRequest
from openconnect.domain import GeneratedReport
from openconnect.services.dashboard import DashboardService, week_start
from openconnect.services.sync import utcnow
from openconnect.storage import Repository


class ReportService:
    """Generates reports on demand and stores them; unchanged data never costs a second call."""

    def __init__(
        self,
        repo: Repository,
        provider: ReportProvider,
        dashboard: DashboardService,
        clock: Callable[[], datetime] = utcnow,
        language: str = "en",
    ) -> None:
        self.repo = repo
        self.provider = provider
        self.dashboard = dashboard
        self.clock = clock
        self.language = language

    def _fingerprint(self, facts: dict) -> str:
        material = {
            "facts": facts,
            "language": self.language,
            "provider": self.provider.name,
            "model": getattr(self.provider, "model", None),
        }
        return hashlib.sha256(
            json.dumps(material, sort_keys=True, default=str).encode()
        ).hexdigest()

    def _generate(self, kind: str, subject: str, facts: dict, force: bool) -> GeneratedReport:
        digest = self._fingerprint(facts)
        cached = self.repo.get_report(kind, subject)
        if cached and cached.facts_hash == digest and not force:
            return cached
        result = self.provider.generate(ReportRequest(kind, subject, facts, self.language))
        return self.repo.save_report(
            GeneratedReport(
                kind=kind,
                subject=subject,
                text=result.text,
                provider=result.provider,
                model=result.model,
                facts_hash=digest,
                created_at=self.clock(),
            )
        )

    def activity_report(self, activity_id: str, force: bool = False) -> GeneratedReport:
        activity = self.repo.get_activity(activity_id)
        if activity is None:
            raise LookupError(f"No activity {activity_id!r}")
        return self._generate("activity", activity_id, activity_facts(self.repo, activity), force)

    def weekly_report(self, any_day: date, force: bool = False) -> GeneratedReport:
        monday = week_start(any_day)
        return self._generate(
            "weekly", monday.isoformat(), weekly_facts(self.dashboard, monday), force
        )
