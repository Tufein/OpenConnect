"""The emulator smoke script gates every release, so its key step is tested without an emulator.

It saved a fake AI key by tapping the field and typing. A tap that did not give the field focus left
the field empty, the Save button switched off and a release failed twice on "Claude key saved" never
appearing. The step now looks at the Save button before pressing it and tries again. A fake adb
stands in for the emulator: typing only fills the field when the field has been tapped, and the
first tap can be made to miss.
"""

import importlib.util
import pathlib
import xml.etree.ElementTree as ET

import pytest

SCRIPT = pathlib.Path(__file__).resolve().parents[2] / "android" / "scripts" / "emulator_smoke.py"
spec = importlib.util.spec_from_file_location("emulator_smoke", SCRIPT)
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


def node(text="", cls="android.view.View", enabled="true", desc="", bounds="[10,300][310,350]"):
    return smoke.Node(
        ET.Element(
            "node",
            {
                "text": text,
                "class": cls,
                "enabled": enabled,
                "content-desc": desc,
                "bounds": bounds,
            },
        )
    )


class FakePhone:
    """A Settings screen with the key field and the Save button.

    The first [misses] taps on the field do nothing."""

    def __init__(self, misses=0):
        self.misses = misses
        self.focused = False
        self.typed = False
        self.taps = []
        self.shots = []
        self.saved = False

    def save_button(self):
        return node("Save Claude key", enabled="true" if self.typed else "false")

    # What the script calls on the Run object
    def scroll_to(self, text, contains=False):
        return self.save_button() if text == "Save Claude key" else node(text)

    def tap(self, target):
        is_field = "EditText" in target.cls
        self.taps.append("field" if is_field else target.text)
        if is_field:
            if self.misses > 0:
                self.misses -= 1
            else:
                self.focused = True
        if target.text == "Save Claude key":
            self.saved = True

    def hide_keyboard(self):
        pass

    def shot(self, name):
        self.shots.append(name)

    # The adb call that types
    def adb(self, *args, **kwargs):
        if args[:3] == ("shell", "input", "text") and self.focused:
            self.typed = True
        return ""

    def screen(self):
        return [
            node("Claude key", bounds="[10,100][310,140]"),
            node("Open Claude key page", cls="android.widget.Button", bounds="[10,150][310,200]"),
            node("Claude key", cls="android.widget.EditText", bounds="[10,210][310,260]"),
            self.save_button(),
        ]


@pytest.fixture
def phone(monkeypatch):
    def make(misses=0):
        fake = FakePhone(misses)
        monkeypatch.setattr(smoke, "adb", fake.adb)
        monkeypatch.setattr(smoke, "screen", fake.screen)
        monkeypatch.setattr(smoke.time, "sleep", lambda seconds: None)
        return fake

    return make


def test_the_key_goes_in_the_first_time_and_save_is_pressed(phone):
    fake = phone()
    smoke.save_fake_key(fake)
    assert fake.taps == ["field", "Save Claude key"]
    assert fake.saved and fake.shots == []


def test_a_tap_that_missed_the_field_is_tried_again(phone):
    fake = phone(misses=1)
    smoke.save_fake_key(fake)
    assert fake.taps == ["field", "field", "Save Claude key"]
    assert fake.saved
    assert fake.shots == ["key-not-typed-1"], "the failed try keeps a screenshot"


def test_a_field_that_never_takes_the_key_fails_with_a_clear_message(phone):
    fake = phone(misses=99)
    with pytest.raises(smoke.Smoke, match="never landed in the Claude key field in 3 tries"):
        smoke.save_fake_key(fake)
    assert not fake.saved, "Save is never pressed while it is switched off"
    assert len(fake.shots) == 3


def test_the_field_is_the_edit_text_and_never_the_save_button(phone):
    fake = phone()
    assert smoke.key_field(fake.screen()).cls == "android.widget.EditText"
    # A dump that does not tie the label to the field: the lowest node that is exactly the label,
    # never "Save Claude key".
    without_class = [n for n in fake.screen() if "EditText" not in n.cls]
    assert smoke.key_field(without_class).text == "Claude key"


def test_a_switched_off_button_says_so_and_a_silent_node_counts_as_on():
    assert node("Save Claude key", enabled="false").enabled is False
    assert smoke.Node(ET.Element("node", {"text": "x"})).enabled is True
