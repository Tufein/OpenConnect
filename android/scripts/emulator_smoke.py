#!/usr/bin/env python3
"""Starts a built APK on a running Android emulator and walks the app, so a release that does not even start (or that crashes
on the first screens, or that lost something to R8) is caught before anyone installs it.

    emulator_smoke.py APK OUT_DIR

What it does, on a real Android (an emulator in CI), not on the JVM:
  1. installs the APK and starts it cold, answers the first-start screen,
  2. loads the demo data and opens Home, Activities, Health, Insights, Settings and the About screen,
  3. saves a fake AI key (this goes through the real Android Keystore and EncryptedSharedPreferences),
  4. stops the app, starts it again and checks the key is still saved (read back from the Keystore),
  5. fails if the app's process died or the crash log mentions the app at any point.
Screenshots of every step and the app's log are written to OUT_DIR. Only the Python standard library and adb are needed.
"""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "nl.tufein.openconnect"
FAKE_KEY = "sk-ant-api03-" + "A" * 60


class Smoke(Exception):
    pass


def adb(*args, timeout=90, check=True):
    done = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    out = done.stdout.decode("utf-8", errors="replace")
    if check and done.returncode != 0:
        raise Smoke(f"adb {' '.join(args)} failed: {done.stderr.decode('utf-8', errors='replace')[:500]}")
    return out


class Node:
    def __init__(self, element):
        self.text = element.get("text", "")
        self.desc = element.get("content-desc", "")
        self.cls = element.get("class", "")
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


class Run:
    def __init__(self, out):
        self.out = out
        self.step = 0
        os.makedirs(out, exist_ok=True)

    def shot(self, name):
        self.step += 1
        data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
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
            start_y, end_y = (0.7, 0.3) if down else (0.3, 0.7)
            adb("shell", "input", "swipe", str(width // 2), str(int(height * start_y)), str(width // 2), str(int(height * end_y)), "350")
            time.sleep(0.8)
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


def main(apk, out):
    run = Run(out)
    try:
        adb("shell", "settings", "put", "global", "window_animation_scale", "0", check=False)
        adb("shell", "settings", "put", "global", "transition_animation_scale", "0", check=False)
        adb("shell", "settings", "put", "global", "animator_duration_scale", "0", check=False)
        adb("logcat", "-c", check=False)
        print(adb("install", "-r", apk, timeout=300).strip())
        adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)

        run.start()
        first = run.wait("Welcome to OpenConnect", "Load demo data", timeout=150)
        run.shot("first-screen")
        if first.text == "Welcome to OpenConnect":
            # The Start button is under the device list, below the fold on a small screen, and a node that is
            # not on screen is not in the uiautomator dump: scroll to it first.
            run.tap(run.scroll_to("Start"))
        run.tap(run.scroll_to("Load demo data"))
        run.gone("Load demo data")
        run.wait("Readiness", contains=True)
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
        run.wait("About OpenConnect")
        run.shot("about")
        run.back()
        run.wait("About and licences")
        print("About: ok")

        run.scroll_to("Claude key")
        field = [n for n in screen() if "EditText" in n.cls and "Claude key" in (n.text + n.desc)] or [find(screen(), "Claude key", True)]
        run.tap(field[0])
        # A fake key must not be sent to the AI provider to be checked: no network for this step.
        adb("shell", "cmd", "connectivity", "airplane-mode", "enable", check=False)
        adb("shell", "input", "text", FAKE_KEY)
        time.sleep(1)
        run.hide_keyboard()
        run.tap(run.scroll_to("Save Claude key"))
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


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1], sys.argv[2]))
