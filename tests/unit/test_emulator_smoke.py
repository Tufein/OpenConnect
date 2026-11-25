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


# --- a lost emulator is not an app failure ---------------------------------------------------
#
# On 2026-11-24 and 25 the release smoke died five times in a row with a Python traceback: the
# emulator process went away in the middle of a swipe, `adb wait-for-device` timed out and the
# exception left the script without a message, a screenshot or the app's log. The release was
# made to ignore the smoke instead. Now a timeout is a named condition (DeviceLost) with its own
# exit status, so the workflow log says "the emulator was lost" and the release can retry once on
# a fresh emulator, while a crash of the app still fails at once.


def test_an_adb_call_that_times_out_is_a_lost_device_not_a_traceback(monkeypatch):
    def hang(command, **kwargs):
        raise smoke.subprocess.TimeoutExpired(command, kwargs.get("timeout", 1))

    monkeypatch.setattr(smoke.subprocess, "run", hang)
    with pytest.raises(smoke.DeviceLost, match="adb wait-for-device did not answer within 30 s"):
        smoke.adb("wait-for-device", timeout=30)


def test_a_lost_device_is_a_kind_of_smoke_failure():
    assert issubclass(smoke.DeviceLost, smoke.Smoke)


def test_a_lost_emulator_ends_the_run_with_its_own_message_and_exit_status(
    monkeypatch, tmp_path, capsys
):
    def gone(*args, **kwargs):
        raise smoke.DeviceLost("adb shell input swipe did not answer within 30 s")

    monkeypatch.setattr(smoke, "adb", gone)

    def no_adb(*args, **kwargs):
        raise smoke.DeviceLost("gone")

    monkeypatch.setattr(smoke.subprocess, "run", no_adb)
    assert smoke.main("app.apk", str(tmp_path)) == smoke.EXIT_EMULATOR_LOST == 3
    out = capsys.readouterr().out
    assert "THE EMULATOR WAS LOST" in out and "logcat.txt" in out
    # From v1.11.0 to v1.13.0 the emulator was lost at the same swipe every time, with an emulator
    # and a runner image that had walked v1.10.0 the same day: the message may not blame the runner.
    assert "not a failure of the app" not in out


def test_an_app_failure_keeps_exit_status_one(monkeypatch, tmp_path, capsys):
    def broken(*args, **kwargs):
        raise smoke.Smoke("the app is not running any more")

    monkeypatch.setattr(smoke, "adb", broken)

    def no_adb(*args, **kwargs):
        raise OSError("no adb here")

    monkeypatch.setattr(smoke.subprocess, "run", no_adb)
    assert smoke.main("app.apk", str(tmp_path)) == 1
    assert "SMOKE TEST FAILED: the app is not running any more" in capsys.readouterr().out


# When the emulator is lost, adb can no longer be asked for the device log afterwards: by then the
# device is gone. So the whole log is streamed to OUT/logcat.txt from the moment the app is
# installed, and what the device said up to its last second survives in the uploaded artifact.


class FakeLogcat:
    """Stands in for `adb logcat` streaming: writes one line and remembers how it was stopped."""

    def __init__(self, command, stdout=None, stderr=None):
        self.command = command
        self.stopped = False
        stdout.write(b"11-25 12:44:01.000  1234  1234 I ActivityManager: Start proc nl.tufein\n")
        FakeLogcat.last = self

    def poll(self):
        return 0 if self.stopped else None

    def terminate(self):
        self.stopped = True

    def wait(self, timeout=None):
        return 0

    def kill(self):
        self.stopped = True


def _lost_at_install(*args, **kwargs):
    if args and args[0] == "install":
        raise smoke.DeviceLost("adb install did not answer within 300 s")
    return ""


def test_the_device_log_is_streamed_to_a_file_and_survives_a_lost_emulator(monkeypatch, tmp_path):
    monkeypatch.setattr(smoke, "adb", _lost_at_install)
    monkeypatch.setattr(smoke.subprocess, "Popen", FakeLogcat)
    assert smoke.main("app.apk", str(tmp_path)) == smoke.EXIT_EMULATOR_LOST
    recorded = FakeLogcat.last
    assert recorded.command[:2] == ["adb", "logcat"]
    assert recorded.stopped, "the recording is stopped when the walk ends, also when it failed"
    assert "Start proc" in (tmp_path / "logcat.txt").read_text()


def test_without_adb_the_recording_is_skipped_and_the_walk_still_reports(monkeypatch, tmp_path):
    def no_adb(*args, **kwargs):
        raise OSError("no adb here")

    monkeypatch.setattr(smoke, "adb", _lost_at_install)
    monkeypatch.setattr(smoke.subprocess, "Popen", no_adb)
    assert smoke.main("app.apk", str(tmp_path)) == smoke.EXIT_EMULATOR_LOST
