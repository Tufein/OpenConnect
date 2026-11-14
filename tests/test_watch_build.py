"""watch/build.sh without the Garmin tools: --dry-run writes the files and checks every choice."""

import re
import shutil
import subprocess
from pathlib import Path

import pytest

WATCH = Path(__file__).resolve().parent.parent / "watch"


@pytest.fixture()
def script(tmp_path):
    """A copy of the watch folder, so the generated files do not land in the repository."""
    target = tmp_path / "watch"
    shutil.copytree(WATCH, target, ignore=shutil.ignore_patterns("gen", "out"))
    return target / "build.sh"


def run(script, *args):
    return subprocess.run(["bash", str(script), "--dry-run", *args], capture_output=True, text=True)


def test_dry_run_writes_the_config_manifest_and_strings(script):
    done = run(
        script,
        "--device",
        "fenix847mm",
        "--design",
        "retro",
        "--color",
        "pink",
        "--fields",
        "steps,battery,heart,stress",
    )
    assert done.returncode == 0, done.stderr
    gen = script.parent / "gen"
    config = (gen / "source" / "Config.mc").read_text()
    assert 'const DESIGN = "retro";' in config
    assert "const COLOR = 0xFF5FA2;" in config
    assert 'const FIELDS = ["steps", "battery", "heart", "stress"];' in config
    manifest = (gen / "manifest.xml").read_text()
    assert '<iq:product id="fenix847mm"' in manifest
    assert re.search(r'id="[0-9a-f]{32}"', manifest)
    assert "__" not in manifest
    assert "OpenConnect Retro" in (gen / "resources" / "strings" / "strings.xml").read_text()


def test_every_variant_gets_its_own_app_id(script):
    ids = set()
    for design, color in (("clean", "blue"), ("clean", "mint"), ("dashboard", "blue")):
        assert run(script, "--design", design, "--color", color).returncode == 0
        manifest = (script.parent / "gen" / "manifest.xml").read_text()
        ids.add(re.search(r'application [^>]*id="([0-9a-f]{32})"', manifest).group(1))
    assert len(ids) == 3


@pytest.mark.parametrize(
    "args",
    [
        ("--design", "neon"),
        ("--color", "purple"),
        ("--fields", "battery,heart,stress"),
        ("--fields", "battery,battery,heart,stress"),
        ("--fields", "battery,heart,stress,sleep"),
        ("--device", "fenix8; rm -rf /"),
    ],
)
def test_bad_choices_are_refused(script, args):
    done = run(script, *args)
    assert done.returncode == 2
    assert "build.sh:" in done.stderr


def test_a_hex_colour_is_accepted(script):
    assert run(script, "--color", "12ab34").returncode == 0
    assert "0x12AB34" in (script.parent / "gen" / "source" / "Config.mc").read_text()


def test_all_colors_names_one_file_per_colour_like_the_app(script):
    done = run(script, "--device", "fenix8solar51mm", "--design", "dashboard", "--all-colors")
    assert done.returncode == 0, done.stderr
    files = [
        line.split(": ", 1)[1] for line in done.stdout.splitlines() if line.startswith("file: ")
    ]
    # The app names the file the same way (WatchFaceConfig.fileName).
    assert files == [
        f"openconnect-dashboard-{hex}-fenix8solar51mm.prg"
        for hex in ("5CF2C0", "7AA7FF", "FFB000", "FF5FA2", "9BBC0F")
    ]
