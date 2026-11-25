#!/usr/bin/env python3
"""Starts a built APK on a running Android emulator and walks the app, so a release that does not even start (or that crashes
on the first screens, or that lost something to R8) is caught before anyone installs it.

    emulator_smoke.py APK OUT_DIR

What it does, on a real Android (an emulator in CI), not on the JVM:
  1. removes any previous copy, installs the APK and starts it cold, answers the first-start screen,
  2. loads the demo data and opens Home, Activities, Health, Insights, Settings and the About screen,
  3. saves a fake AI key (this goes through the real Android Keystore and EncryptedSharedPreferences),
  4. stops the app, starts it again and checks the key is still saved (read back from the Keystore),
  5. fails if the app's process died or the crash log mentions the app at any point.
Screenshots of every step, the app's log and the whole device log (logcat.txt, streamed while the walk runs) are
written to OUT_DIR. Only the Python standard library and adb are needed.
"""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "nl.tufein.openconnect"
FAKE_KEY = "sk-ant-api03-" + "A" * 60
# The app is called Cercis since v1.13.1 and OpenConnect before. Both are accepted, so a published APK from before the
# rename can still be walked (emulator.yml, input `published`) to compare it with today's.
WELCOME = ("Welcome to Cercis", "Welcome to OpenConnect")
ABOUT = ("About Cercis", "About OpenConnect")


class Smoke(Exception):
    pass


class DeviceLost(Smoke):
    """The emulator stopped answering. That can be the runner, but not always: from v1.11.0 to v1.13.0 the emulator's
    swiftshader_indirect renderer died at the first swipe on Home every time (the walks now use swangle_indirect, see
    release.yml). It has its own exit status, the release workflow tries once more on a fresh emulator, and the device
    log streamed to OUT/logcat.txt shows what the device said up to its last second."""


EXIT_EMULATOR_LOST = 3


def adb(*args, timeout=90, check=True):
    try:
        done = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        raise DeviceLost(f"adb {' '.join(args)} did not answer within {timeout} s") from None
    out = done.stdout.decode("utf-8", errors="replace")
    if check and done.returncode != 0:
        raise Smoke(f"adb {' '.join(args)} failed: {done.stderr.decode('utf-8', errors='replace')[:500]}")
    return out


class DeviceLog:
    """Streams the whole device log to OUT/logcat.txt while the walk runs. Asking for the log afterwards (`logcat -d`) is
    impossible once the emulator is gone, which is exactly when it is needed."""

    def __init__(self, out):
        self.path = os.path.join(out, "logcat.txt")
        self.file = None
        self.process = None

    def start(self):
        self.file = open(self.path, "wb")
        try:
            self.process = subprocess.Popen(
                ["adb", "logcat", "-v", "threadtime", "-b", "main,system,crash,events"],
                stdout=self.file,
                stderr=subprocess.STDOUT,
            )
        except OSError as problem:
            self.file.write(f"could not start adb logcat: {problem}\n".encode())

    def stop(self):
        if self.process is not None and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
        if self.file is not None:
            self.file.close()


class Node:
    def __init__(self, element):
        self.text = element.get("text", "")
        self.desc = element.get("content-desc", "")
        self.cls = element.get("class", "")
        # A button that is switched off says so; a node that does not say anything counts as enabled.
        self.enabled = element.get("enabled", "true") != "false"
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", element.get("bounds", "[0,0][0,0]"))
        self.left, self.top, self.right, self.bottom = (int(v) for v in m.groups()) if m else (0, 0, 0, 0)

    @property
    def centre(self):
        return (self.left + self.right) // 2, (self.top + self.bottom) // 2

    def __repr__(self):
        return f"{self.text or self.desc!r}@{self.centre}"


def screen():
    """The nodes on screen now. uiautomator sometimes cannot get an idle state, so it is tried a few times."""
    for _ in range(6):
        # Remove the old dump first: if the device is briefly offline, cat must not return the previous screen.
        adb("shell", "rm", "-f", "/sdcard/ui.xml", check=False)
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        start = xml.find("<?xml")
        if start >= 0:
            try:
                return [Node(e) for e in ET.fromstring(xml[start:]).iter("node")]
            except ET.ParseError:
                pass
        time.sleep(1)
    raise Smoke("could not read the screen")


def labels(nodes):
    return [n.text or n.desc for n in nodes if (n.text or n.desc)]


def match(node, text, contains):
    for value in (node.text, node.desc):
        if value and (text in value if contains else value == text):
            return True
    return False


def find(nodes, text, contains=False):
    found = [n for n in nodes if match(n, text, contains)]
    # The tab bar is at the bottom: when a word is both a tab and a heading, the lowest one is meant.
    return max(found, key=lambda n: n.top) if found else None


def key_field(nodes):
    """The text field of the AI key: the EditText that carries its label. If the dump does not tie the label to the
    field, the node that is exactly the label (the lowest one: the heading above it has the same words), never a
    button such as "Save Claude key", which a tap would hit instead of the field."""
    fields = [n for n in nodes if "EditText" in n.cls and "Claude key" in (n.text + n.desc)]
    return fields[0] if fields else find(nodes, "Claude key")


class Run:
    def __init__(self, out):
        self.out = out
        self.step = 0
        os.makedirs(out, exist_ok=True)

    def shot(self, name):
        self.step += 1
        try:
            data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
        except subprocess.TimeoutExpired:
            raise DeviceLost("adb exec-out screencap did not answer within 60 s") from None
        with open(os.path.join(self.out, f"{self.step:02d}-{name}.png"), "wb") as f:
            f.write(data)

    def alive(self):
        if not adb("shell", "pidof", PKG, check=False).strip():
            raise Smoke("the app is not running any more")
        crash = adb("logcat", "-b", "crash", "-d", check=False)
        if PKG in crash or "FATAL EXCEPTION" in crash:
            raise Smoke("the crash log mentions the app:\n" + crash[-3000:])

    def wait(self, *texts, contains=False, timeout=90):
        """Waits until one of [texts] is on screen and returns its node; checks the app is alive meanwhile."""
        deadline = time.time() + timeout
        nodes = []
        while time.time() < deadline:
            self.alive()
            nodes = screen()
            for t in texts:
                node = find(nodes, t, contains)
                if node:
                    return node
            time.sleep(1)
        raise Smoke(f"never saw any of {texts}; the screen showed: {labels(nodes)[:60]}")

    def gone(self, text, timeout=90):
        deadline = time.time() + timeout
        while time.time() < deadline:
            self.alive()
            if not find(screen(), text):
                return
            time.sleep(1)
        raise Smoke(f"{text!r} did not go away")

    def tap(self, node):
        x, y = node.centre
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(1.2)

    def swipe(self, width, height, down):
        """A swipe through adb's input service. The emulator occasionally drops one input command while it is still
        booting; wait for the device and try the other input syntax before failing a release."""
        start_y, end_y = (0.7, 0.3) if down else (0.3, 0.7)
        last = None
        for args in (
            ("input", "swipe", str(width // 2), str(int(height * start_y)), str(width // 2), str(int(height * end_y)), "350"),
            ("input", "touchscreen", "swipe", str(width // 2), str(int(height * start_y)), str(width // 2), str(int(height * end_y)), "350"),
        ):
            try:
                adb("shell", *args, timeout=30)
                time.sleep(0.8)
                return
            except Smoke as problem:
                last = problem
                adb("wait-for-device", timeout=30, check=False)
                time.sleep(1)
        raise Smoke(f"could not swipe: {last}")

    def scroll_to(self, text, contains=False, tries=24):
        """Swipes until [text] is on screen and not behind the bottom bar: down the list first, and back up when the
        end of the list is reached without finding it (a screen that was left scrolled down keeps its place)."""
        size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
        width, height = int(size.group(1)), int(size.group(2))
        down, previous, turns = True, None, 0
        for _ in range(tries):
            nodes = screen()
            node = find(nodes, text, contains)
            if node and node.bottom < height * 0.86 and node.top > height * 0.08:
                return node
            seen = labels(nodes)
            if seen == previous:
                # The screen no longer changes: this is the end of the list.
                if node and node.bottom <= height:
                    # The button sits at the very bottom (the first-start screen has no bottom bar): take it there.
                    return node
                if turns >= 2:
                    break
                down, turns = not down, turns + 1
            previous = seen
            self.swipe(width, height, down)
        raise Smoke(f"could not scroll to {text!r}; the screen showed: {labels(screen())[:60]}")

    def back(self):
        adb("shell", "input", "keyevent", "4")
        time.sleep(1.2)

    def hide_keyboard(self):
        """Back closes the soft keyboard when it is up and would leave the screen when it is not (an emulator with a
        hardware keyboard has none), so look first."""
        if "mInputShown=true" in adb("shell", "dumpsys", "input_method", check=False):
            self.back()

    def start(self):
        adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")

    def log_tail(self):
        pid = adb("shell", "pidof", PKG, check=False).strip().split(" ")[0]
        log = adb("logcat", "-d", "-t", "4000", check=False)
        mine = [line for line in log.splitlines() if (pid and f" {pid} " in line) or PKG in line]
        with open(os.path.join(self.out, "app-log.txt"), "w") as f:
            f.write("\n".join(mine[-1500:]))


def save_fake_key(run, attempts=3):
    """Types the fake key into the Claude key field and presses Save. The typing goes to whatever has focus, so a
    tap that did not give the field focus leaves it empty and the Save button switched off: that is looked at
    before pressing Save, and the whole step is tried again (a screenshot of each failed try is kept) instead of
    failing a release on a lost keystroke. After [attempts] empty tries it fails and says so."""
    for attempt in range(1, attempts + 1):
        run.scroll_to("Claude key")
        field = key_field(screen())
        if field is None:
            raise Smoke("the Claude key field is not on the screen")
        print(f"Claude key field: {field.cls or 'no class'} {field!r}, try {attempt}")
        run.tap(field)
        adb("shell", "input", "text", FAKE_KEY)
        time.sleep(1)
        run.hide_keyboard()
        save = run.scroll_to("Save Claude key")
        if save.enabled:
            run.tap(save)
            return
        print(f"Try {attempt}: the key did not land in the field (Save is still switched off)")
        run.shot(f"key-not-typed-{attempt}")
    raise Smoke(f"the fake key never landed in the Claude key field in {attempts} tries (the Save button stayed off)")


def main(apk, out):
    run = Run(out)
    device_log = DeviceLog(out)
    try:
        adb("shell", "settings", "put", "global", "window_animation_scale", "0", check=False)
        adb("shell", "settings", "put", "global", "transition_animation_scale", "0", check=False)
        adb("shell", "settings", "put", "global", "animator_duration_scale", "0", check=False)
        # Each channel is walked on its own emulator, but remove any previous install first: a stale package or its
        # data must never make a github run look like a store run (or the other way around).
        adb("uninstall", PKG, check=False)
        adb("logcat", "-c", check=False)
        device_log.start()
        print(adb("install", "-r", apk, timeout=300).strip())
        # The emulator can drop the adb connection for a moment right after a large install.
        adb("wait-for-device", timeout=60, check=False)
        time.sleep(2)
        adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)

        run.start()
        first = run.wait(*WELCOME, "Load demo data", timeout=150)
        run.shot("first-screen")
        if first.text in WELCOME:
            # The Start button is under the device list, below the fold on a small screen, and a node that is
            # not on screen is not in the uiautomator dump: scroll to it first.
            run.tap(run.scroll_to("Start"))
        run.tap(run.scroll_to("Load demo data"))
        run.gone("Load demo data")
        # The editorial front page prints the section labels in capitals ("READINESS"); accept both spellings.
        run.wait("Readiness", "READINESS", contains=True)
        run.shot("home")
        print("Home with demo data: ok")

        for tab, expect, contains in (
            ("Activities", ("Easy Run", "Tempo Run", "Weekend Ride"), False),
            ("Health", ("Scores",), False),
            ("Insights", ("Week of",), True),
            ("Settings", ("Appearance",), False),
        ):
            run.tap(run.wait(tab))
            run.wait(*expect, contains=contains)
            run.shot(tab.lower())
            print(f"{tab}: ok")

        run.tap(run.scroll_to("About and licences"))
        run.wait(*ABOUT)
        run.shot("about")
        run.back()
        run.wait("About and licences")
        print("About: ok")

        # A fake key must not be sent to the AI provider to be checked: no network for this step. Airplane mode goes
        # on before the field is touched, so the system's own reaction to it cannot take the focus away afterwards.
        adb("shell", "cmd", "connectivity", "airplane-mode", "enable", check=False)
        time.sleep(2)
        save_fake_key(run)
        run.wait("Claude key saved", contains=True)
        run.shot("key-saved")
        print("Saved a key through the Keystore: ok")

        adb("shell", "am", "force-stop", PKG)
        time.sleep(2)
        run.start()
        run.wait("Home", timeout=120)
        run.tap(run.wait("Settings"))
        run.scroll_to("Claude key saved", contains=True)
        run.shot("key-after-restart")
        print("The key is still there after a restart: ok")

        run.alive()
        print("SMOKE TEST PASSED")
        return 0
    except DeviceLost as problem:
        print(
            f"SMOKE TEST FAILED: THE EMULATOR WAS LOST ({problem}). What the device said up to that moment is in "
            f"{device_log.path}."
        )
        return EXIT_EMULATOR_LOST
    except Smoke as problem:
        print(f"SMOKE TEST FAILED: {problem}")
        try:
            run.shot("failure")
        except Exception:  # the screenshot is a courtesy
            pass
        return 1
    finally:
        try:
            run.log_tail()
        except Exception:
            pass
        device_log.stop()


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1], sys.argv[2]))
