"""Test doubles."""

from __future__ import annotations

from types import SimpleNamespace


class FakeAnthropic:
    """Stands in for ``anthropic.Anthropic``: records requests and replays canned responses."""

    def __init__(self, text: str = "Great session.", stop_reason: str = "end_turn", raises=None):
        self.calls: list[dict] = []
        self.text = text
        self.stop_reason = stop_reason
        self.raises = raises
        self.beta = SimpleNamespace(messages=SimpleNamespace(create=self._create))

    def _create(self, **kwargs):
        self.calls.append(kwargs)
        if self.raises:
            raise self.raises
        content = [
            SimpleNamespace(type="thinking", thinking="", signature="sig"),
            SimpleNamespace(type="text", text=self.text),
        ]
        if self.stop_reason == "refusal":
            content = []
        return SimpleNamespace(
            content=content,
            stop_reason=self.stop_reason,
            stop_details=SimpleNamespace(category="cyber", explanation="declined")
            if self.stop_reason == "refusal"
            else None,
            model="claude-opus-5-5",
            usage=SimpleNamespace(input_tokens=100, output_tokens=50),
        )
