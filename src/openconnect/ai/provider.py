"""Report providers. ``ReportProvider`` is the seam: tests and offline use run the rule-based
``TemplateReportProvider``; production uses Claude through ``AnthropicReportProvider``, which takes
an injectable client so no API key or network is needed under test."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

import anthropic

from openconnect.ai.prompts import SYSTEM_PROMPT, build_user_prompt
from openconnect.config import ConfigError, Settings


class ReportError(RuntimeError):
    """A report could not be generated (provider refused, unreachable, rate limited, ...)."""


@dataclass(frozen=True)
class ReportRequest:
    kind: str  # "activity" | "weekly"
    subject: str
    facts: dict
    language: str = "en"


@dataclass(frozen=True)
class ReportResult:
    text: str
    provider: str
    model: str | None = None


class ReportProvider(Protocol):
    name: str

    def generate(self, request: ReportRequest) -> ReportResult: ...


# --------------------------------------------------------------------------- template (offline)

_TEXT = {
    "en": {
        "activity": "{name} ({sport}, {date})",
        "distance": "Distance: {km:.1f} km in {minutes:.0f} min",
        "pace": "average pace {pace} /km",
        "speed": "average speed {kmh} km/h",
        "hr": "Heart rate: average {avg} bpm, max {max} bpm",
        "drift": "Your heart rate rose {n} bpm between the first and second half.",
        "climb": "Climbing: {m} m",
        "longest": "This was your longest {sport} so far.",
        "week": "Week of {start}",
        "count": "{n} {noun}, {km:.1f} km in {hours:.1f} h",
        "activity_one": "activity",
        "activity_many": "activities",
        "vs": "Distance vs previous week: {pct:+.0f}%",
        "steps": "Steps: {avg:.0f} per day on average, goal reached on {goal} of {days} days",
        "sleep": "Sleep: {hours:.1f} h per night on average",
        "none": "No activities recorded this week.",
    },
    "nl": {
        "activity": "{name} ({sport}, {date})",
        "distance": "Afstand: {km:.1f} km in {minutes:.0f} min",
        "pace": "gemiddeld tempo {pace} /km",
        "speed": "gemiddelde snelheid {kmh} km/u",
        "hr": "Hartslag: gemiddeld {avg} bpm, max {max} bpm",
        "drift": "Je hartslag steeg {n} bpm tussen de eerste en tweede helft.",
        "climb": "Hoogtemeters: {m} m",
        "longest": "Dit was je langste {sport} tot nu toe.",
        "week": "Week van {start}",
        "count": "{n} {noun}, {km:.1f} km in {hours:.1f} u",
        "activity_one": "activiteit",
        "activity_many": "activiteiten",
        "vs": "Afstand t.o.v. vorige week: {pct:+.0f}%",
        "steps": "Stappen: gemiddeld {avg:.0f} per dag, doel gehaald op {goal} van {days} dagen",
        "sleep": "Slaap: gemiddeld {hours:.1f} u per nacht",
        "none": "Geen activiteiten deze week.",
    },
}


class TemplateReportProvider:
    """Deterministic, rule-based reports. No network, no key; the default without an API key."""

    name = "template"

    def generate(self, request: ReportRequest) -> ReportResult:
        t = _TEXT.get(request.language, _TEXT["en"])
        build = self._activity if request.kind == "activity" else self._weekly
        return ReportResult(text=build(request.facts, t), provider=self.name, model=None)

    @staticmethod
    def _activity(f: dict, t: dict) -> str:
        lines = [f"**{t['activity'].format(name=f['name'], sport=f['sport'], date=f['date'])}**"]
        detail = t["distance"].format(km=f["distance_km"], minutes=f["duration_min"])
        if f.get("pace_min_per_km"):
            detail += ", " + t["pace"].format(pace=f["pace_min_per_km"])
        elif f.get("avg_speed_kmh"):
            detail += ", " + t["speed"].format(kmh=f["avg_speed_kmh"])
        lines.append(f"- {detail}")
        if f.get("avg_hr"):
            lines.append("- " + t["hr"].format(avg=f["avg_hr"], max=f.get("max_hr") or "?"))
        hr = f.get("hr") or {}
        if hr and hr["second_half_avg"] - hr["first_half_avg"] >= 8:
            lines.append("- " + t["drift"].format(n=hr["second_half_avg"] - hr["first_half_avg"]))
        if f.get("elevation_gain_m"):
            lines.append("- " + t["climb"].format(m=f["elevation_gain_m"]))
        if f.get("context", {}).get("is_longest_of_sport"):
            lines.append("- " + t["longest"].format(sport=f["sport"]))
        return "\n".join(lines)

    @staticmethod
    def _weekly(f: dict, t: dict) -> str:
        w, prev = f["this_week"], f["previous_week"]
        lines = [f"**{t['week'].format(start=f['week_start'])}**"]
        if not w["activity_count"]:
            lines.append(f"- {t['none']}")
        else:
            noun = t["activity_one"] if w["activity_count"] == 1 else t["activity_many"]
            lines.append(
                "- "
                + t["count"].format(
                    n=w["activity_count"],
                    noun=noun,
                    km=w["total_distance_m"] / 1000,
                    hours=w["total_duration_s"] / 3600,
                )
            )
            if prev["total_distance_m"]:
                pct = (
                    (w["total_distance_m"] - prev["total_distance_m"])
                    / prev["total_distance_m"]
                    * 100
                )
                lines.append("- " + t["vs"].format(pct=pct))
        if w["avg_steps"] is not None:
            lines.append(
                "- "
                + t["steps"].format(
                    avg=w["avg_steps"], goal=w["goal_days"], days=w["days_with_data"]
                )
            )
        if w["avg_sleep_hours"] is not None:
            lines.append("- " + t["sleep"].format(hours=w["avg_sleep_hours"]))
        return "\n".join(lines)


# --------------------------------------------------------------------------- Claude


class AnthropicReportProvider:
    name = "anthropic"
    FALLBACK_BETA = "server-side-fallback-2026-07-01"

    def __init__(
        self,
        client: Any,
        model: str = "claude-opus-5-5",
        effort: str = "medium",
        max_tokens: int = 16000,
    ) -> None:
        self.client = client
        self.model = model
        self.effort = effort
        self.max_tokens = max_tokens

    def generate(self, request: ReportRequest) -> ReportResult:
        try:
            response = self.client.beta.messages.create(
                model=self.model,
                max_tokens=self.max_tokens,
                # Safety-classifier refusals are re-run on a fallback model server-side.
                betas=[self.FALLBACK_BETA],
                fallbacks="default",
                output_config={"effort": self.effort},
                system=SYSTEM_PROMPT,
                messages=[{"role": "user", "content": build_user_prompt(request)}],
            )
        except anthropic.RateLimitError as exc:
            retry = exc.response.headers.get("retry-after")
            hint = f"; retry after {retry}s" if retry else ""
            raise ReportError(f"Anthropic rate limit reached{hint}") from exc
        except anthropic.AuthenticationError as exc:
            raise ReportError(
                "The Anthropic API key was rejected; check ANTHROPIC_API_KEY"
            ) from exc
        except anthropic.APIStatusError as exc:
            raise ReportError(f"Anthropic API error (HTTP {exc.status_code})") from exc
        except anthropic.APIConnectionError as exc:
            raise ReportError("Could not reach the Anthropic API") from exc

        if response.stop_reason == "refusal":
            category = getattr(response.stop_details, "category", None)
            raise ReportError(f"Claude declined to write this report (category: {category})")
        text = "".join(b.text for b in response.content if b.type == "text").strip()
        if not text:
            raise ReportError("Claude returned an empty report")
        return ReportResult(
            text=text, provider=self.name, model=getattr(response, "model", self.model)
        )


def build_provider(settings: Settings, client: Any | None = None) -> ReportProvider:
    choice = settings.ai_provider
    if choice == "auto":
        choice = "anthropic" if settings.anthropic_api_key else "template"
    if choice == "template":
        return TemplateReportProvider()
    if not settings.anthropic_api_key and client is None:
        raise ConfigError("OPENCONNECT_AI_PROVIDER=anthropic requires ANTHROPIC_API_KEY")
    client = client or anthropic.Anthropic(api_key=settings.anthropic_api_key)
    return AnthropicReportProvider(client, model=settings.ai_model, effort=settings.ai_effort)


__all__ = [
    "AnthropicReportProvider",
    "ReportError",
    "ReportProvider",
    "ReportRequest",
    "ReportResult",
    "TemplateReportProvider",
    "build_provider",
]
