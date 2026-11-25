"""Build CHANGELOG.md from docs/releases/vX.Y.Z.md.

Each release note starts with `## OpenConnect/Cercis vX.Y.Z: title` and a first paragraph. The
changelog keeps those two, newest first, with a link to the full note. Run
`python tools/changelog.py` after adding a release note; a test fails when CHANGELOG.md is out
of date, so a release cannot forget it.
"""

import re
import sys
from pathlib import Path

HEADER = (
    "# Changelog\n\n"
    "Generated from [docs/releases](docs/releases) by `python tools/changelog.py`; "
    "do not edit by hand. Each entry is the title and the first paragraph of that release's "
    "notes (the notes are in Dutch from v0.43.0).\n"
)
LIMIT = 400


def _version_key(path: Path):
    """Sort key: a final release is above its candidates, so 1.0.0 > 1.0.0-rc.2 > rc.1 > 0.58.0."""
    match = re.match(r"v(\d+)\.(\d+)\.(\d+)(?:-rc\.?(\d+))?$", path.stem)
    if not match:
        return tuple(int(part) for part in re.findall(r"\d+", path.stem)) + (1, 0)
    major, minor, patch, candidate = match.groups()
    final = candidate is None
    return (int(major), int(minor), int(patch), 1 if final else 0, 0 if final else int(candidate))


def _entry(path: Path) -> str:
    lines = path.read_text(encoding="utf-8").splitlines()
    title = re.sub(r"^#+\s*(?:OpenConnect|Cercis)\s+", "", lines[0]).strip()
    paragraph: list[str] = []
    for line in lines[1:]:
        if not line.strip():
            if paragraph:
                break
            continue
        if line.startswith("#") and not paragraph:
            break
        paragraph.append(line.strip())
    text = " ".join(paragraph)
    if len(text) > LIMIT:
        text = text[:LIMIT].rsplit(" ", 1)[0].rstrip(",;:.") + " …"
    entry = f"## {title}\n\n"
    if text:
        entry += f"{text}\n\n"
    return entry + f"[Release notes](docs/releases/{path.name})\n"


def build(releases: Path) -> str:
    notes = sorted(releases.glob("v*.md"), key=_version_key, reverse=True)
    return HEADER + "".join("\n" + _entry(note) for note in notes)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    (root / "CHANGELOG.md").write_text(build(root / "docs" / "releases"), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
