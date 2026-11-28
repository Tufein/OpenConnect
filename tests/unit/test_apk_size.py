"""The APK size budget is checked by a script that the release and Android workflows both call.

The release workflow had its own copy of "12 MiB" in a shell line. v1.12.0 and v1.13.0 came out
at 12,562,016 bytes, 20 KB below it, so the next added string could have stopped a release, and
a pull request could only find out at release time. One script now holds the number, prints where
the bytes are (the two dex files are most of it), warns from 95 percent and fails above the budget.
"""

import importlib.util
import re
import zipfile
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "android" / "scripts" / "check_apk_size.py"
spec = importlib.util.spec_from_file_location("check_apk_size", SCRIPT)
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)

MIB = 1024 * 1024
V1_13_11_APK_BYTES = 13_156_021  # the published openconnect-v1.13.11.apk
# The release build of v1.14.0 with compressed dex (local build, debug signature).
V1_14_0_APK_BYTES = 6_439_365


def make_apk(path: Path, **sizes: int) -> Path:
    """A zip with stored entries of the given sizes (the keys name the entries, see [names])."""
    names = {
        "classes_dex": "classes.dex",
        "classes2_dex": "classes2.dex",
        "resources_arsc": "resources.arsc",
        "res_font": "res/aa.ttf",
        "lib_so": "lib/arm64-v8a/libx.so",
        "other": "META-INF/x.txt",
    }
    with zipfile.ZipFile(path, "w", zipfile.ZIP_STORED) as apk:
        for key, size in sizes.items():
            apk.writestr(names[key], b"\0" * size)
    return path


def test_the_budget_leaves_room_above_the_latest_release():
    assert tool.BUDGET_BYTES == 8 * MIB
    headroom = tool.BUDGET_BYTES - V1_14_0_APK_BYTES
    assert headroom >= 1024 * 1024, "less than a MiB of headroom is a few features from failing"


def test_an_apk_without_compressed_dex_no_longer_fits():
    # v1.13.11 stored its dex uncompressed; losing the compression must fail the build
    assert tool.BUDGET_BYTES < V1_13_11_APK_BYTES


def test_the_build_compresses_the_dex_files():
    gradle = (ROOT / "android" / "app" / "build.gradle.kts").read_text()
    assert "dex.useLegacyPackaging = true" in gradle


def test_the_docs_state_the_same_budget_as_the_script():
    docs = (ROOT / "docs" / "PERFORMANCE.md").read_text()
    assert re.search(r"\*\*8 MiB\*\*", docs), "docs/PERFORMANCE.md must say the budget is 8 MiB"
    assert "12 MB**" not in docs, "the old 12 MB limit is still in docs/PERFORMANCE.md"


def test_an_apk_inside_the_budget_passes_and_reports_the_headroom(tmp_path, capsys):
    apk = make_apk(tmp_path / "a.apk", classes_dex=2000, resources_arsc=500)
    assert tool.main([str(apk)]) == 0
    out = capsys.readouterr().out
    assert "headroom" in out and "classes.dex" in out and "resources.arsc" in out


def test_an_apk_over_the_budget_fails(tmp_path, capsys):
    apk = make_apk(tmp_path / "big.apk", classes_dex=3000)
    assert tool.main([str(apk), "--budget", "2000"]) == 1
    assert "larger than" in capsys.readouterr().out


def test_above_ninety_five_percent_it_passes_with_a_warning(tmp_path, capsys):
    apk = make_apk(tmp_path / "near.apk", classes_dex=1000)
    size = apk.stat().st_size
    assert tool.main([str(apk), "--budget", str(int(size / 0.97))]) == 0
    assert "::warning::" in capsys.readouterr().out


def test_below_ninety_five_percent_there_is_no_warning(tmp_path, capsys):
    apk = make_apk(tmp_path / "small.apk", classes_dex=1000)
    assert tool.main([str(apk), "--budget", str(MIB)]) == 0
    assert "::warning::" not in capsys.readouterr().out


def test_the_report_groups_the_entries_by_kind(tmp_path):
    sizes = dict(
        classes_dex=400, classes2_dex=300, resources_arsc=200, res_font=100, lib_so=50, other=10
    )
    apk = make_apk(tmp_path / "g.apk", **sizes)
    groups = tool.groups(apk)
    assert groups["code (dex)"] == 700
    assert groups["resources.arsc"] == 200
    assert groups["res/ (fonts, images, layouts)"] == 100
    assert groups["lib/ (native)"] == 50
    assert groups["everything else"] == 10


def test_a_missing_file_is_an_error_not_a_pass(tmp_path, capsys):
    assert tool.main([str(tmp_path / "nope.apk")]) == 2
    assert "not found" in capsys.readouterr().out


@pytest.mark.parametrize("workflow", ["release.yml", "android.yml"])
def test_both_workflows_call_the_script_instead_of_their_own_number(workflow):
    text = (ROOT / ".github" / "workflows" / workflow).read_text()
    assert "check_apk_size.py" in text, f"{workflow} must run android/scripts/check_apk_size.py"
    assert "12582912" not in text, f"{workflow} still has its own 12 MiB number"
