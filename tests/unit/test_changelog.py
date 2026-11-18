"""CHANGELOG.md is generated from docs/releases/, and a release cannot forget to update it."""

import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def _tool():
    spec = importlib.util.spec_from_file_location("changelog_tool", ROOT / "tools" / "changelog.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_entries_are_newest_first_with_title_and_first_paragraph(tmp_path):
    (tmp_path / "v0.9.0.md").write_text(
        "## OpenConnect v0.9.0: nine\n\nNine is here.\n\n### Install\n\nDownload it.\n"
    )
    (tmp_path / "v0.10.0.md").write_text(
        "## OpenConnect v0.10.0: ten\n\nTen is\nhere on two lines.\n"
    )
    text = _tool().build(tmp_path)
    assert text.index("v0.10.0") < text.index("v0.9.0"), "0.10.0 is newer than 0.9.0"
    assert "## v0.10.0: ten" in text and "Ten is here on two lines." in text
    assert "Nine is here." in text and "Download it." not in text, "only the first paragraph"
    assert "(docs/releases/v0.10.0.md)" in text


def test_a_long_first_paragraph_is_cut_at_a_word(tmp_path):
    (tmp_path / "v1.0.0.md").write_text("## OpenConnect v1.0.0: long\n\n" + "word " * 200 + "\n")
    text = _tool().build(tmp_path)
    paragraph = [line for line in text.splitlines() if line.startswith("word")][0]
    assert len(paragraph) < 450 and paragraph.endswith("…")


def test_the_checked_in_changelog_is_up_to_date():
    """Run `python tools/changelog.py` after adding docs/releases/vX.Y.Z.md."""
    expected = _tool().build(ROOT / "docs" / "releases")
    assert (ROOT / "CHANGELOG.md").read_text() == expected, (
        "CHANGELOG.md is stale: run python tools/changelog.py"
    )


def test_every_release_note_is_in_the_changelog():
    text = (ROOT / "CHANGELOG.md").read_text()
    for note in (ROOT / "docs" / "releases").glob("v*.md"):
        assert f"## {note.stem}:" in text, f"{note.name} is missing from CHANGELOG.md"
