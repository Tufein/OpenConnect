"""Release notes must not contradict the app.

v1.8.2, v1.9.3, v1.11.0 and v1.12.0 each told a stranger "Nodig: Android 8.0 of nieuwer" while
the app has needed Android 9 since v0.52.0 (minSdk 28), and the fixed header the release workflow
puts above every note says Android 9. A person on Android 8 who trusted the note downloaded an APK
that would not install. The minimum is read from the Gradle file, so it cannot drift again when it
is raised.
"""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NOTES = ROOT / "docs" / "releases"

# Android version for an API level, for the levels that can be a minSdk of this app.
ANDROID_FOR_API = {26: 8, 27: 8, 28: 9, 29: 10, 30: 11, 31: 12, 33: 13, 34: 14, 35: 15, 36: 16}

# "Nodig: Android 8.0 of nieuwer" and "Nodig is Android 8.0 of nieuwer" (Dutch), "Needs Android 8.0
# or newer" (English notes before v0.43.0). Only the sentence that states the app's minimum counts:
# a note may also say that one feature needs Android 13.
MINIMUM = re.compile(
    r"(?:Nodig|Needs)(?::| is)?\s+Android (\d+)(?:\.\d+)?\s+(?:of nieuwer|or newer)"
)
OLD_RANGE = re.compile(r"Android 8\s*[\u2013-]\s*1\d")


def _version(path: Path):
    match = re.match(r"v(\d+)\.(\d+)\.(\d+)", path.stem)
    return tuple(int(part) for part in match.groups())


def _minimum_android() -> int:
    gradle = (ROOT / "android" / "app" / "build.gradle.kts").read_text()
    return ANDROID_FOR_API[int(re.search(r"minSdk\s*=\s*(\d+)", gradle).group(1))]


def _lines_since_v0_52():
    """(file name, line number, text) of every line of every note from v0.52.0 on."""
    for note in sorted(NOTES.glob("v*.md")):
        if _version(note) >= (0, 52, 0):
            for number, line in enumerate(note.read_text().splitlines(), 1):
                yield note.name, number, line


def test_the_minimum_comes_from_minsdk_28():
    assert _minimum_android() == 9, "update this test and the release header when it is raised"


def test_the_release_header_names_the_real_minimum():
    header = (ROOT / ".github" / "release-header.md").read_text()
    assert f"Android {_minimum_android()} of nieuwer" in header


def test_notes_since_v0_52_name_the_real_minimum():
    """v0.52.0 raised the minimum from Android 8.0 to 9; earlier notes were right for their time."""
    minimum = _minimum_android()
    wrong = [
        f"{name}:{number} says Android {found} or newer, the app needs Android {minimum}"
        for name, number, line in _lines_since_v0_52()
        for found in MINIMUM.findall(line)
        if int(found) != minimum
    ]
    assert not wrong, "\n".join(wrong)


def test_no_note_since_v0_52_offers_the_app_to_android_8():
    """A line about trying an old phone must not name a range that starts at Android 8."""
    hits = [
        f"{name}:{number}" for name, number, line in _lines_since_v0_52() if OLD_RANGE.search(line)
    ]
    assert not hits, f"these lines name a range that starts at Android 8: {hits}"
