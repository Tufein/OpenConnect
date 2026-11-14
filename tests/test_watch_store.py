"""The Connect IQ store route (v0.24.0): package build, settings, check script. No Garmin tools."""

import os
import re
import shutil
import subprocess
import tomllib
from pathlib import Path
from xml.etree import ElementTree

import pytest

ROOT = Path(__file__).resolve().parent.parent
WATCH = ROOT / "watch"
VERSION = tomllib.loads((ROOT / "pyproject.toml").read_text())["project"]["version"]
FENIX8 = ["fenix843mm", "fenix847mm", "fenix8pro47mm", "fenix8solar47mm", "fenix8solar51mm"]


@pytest.fixture()
def tree(tmp_path):
    """A copy of the watch folder next to pyproject.toml, so build.sh finds the version."""
    shutil.copytree(WATCH, tmp_path / "watch", ignore=shutil.ignore_patterns("gen", "out"))
    shutil.copy(ROOT / "pyproject.toml", tmp_path / "pyproject.toml")
    return tmp_path / "watch"


def package(tree, *args):
    return subprocess.run(
        ["bash", str(tree / "build.sh"), "--package", "--dry-run", *args],
        capture_output=True,
        text=True,
    )


def manifest_of(tree):
    return (tree / "gen" / "manifest.xml").read_text()


def test_a_package_names_the_iq_file_after_the_version_and_lists_every_fenix_8(tree):
    done = package(tree, "--type", "watchface")
    assert done.returncode == 0, done.stderr
    assert f"file: openconnect-watchface-{VERSION}.iq" in done.stdout
    manifest = manifest_of(tree)
    assert re.findall(r'<iq:product id="([^"]+)"', manifest) == FENIX8
    assert f'version="{VERSION}"' in manifest
    assert 'type="watchface"' in manifest
    assert "<iq:language>nld</iq:language>" in manifest
    assert "__" not in manifest


def test_the_widget_package_is_its_own_app(tree):
    done = package(tree, "--type", "widget")
    assert done.returncode == 0, done.stderr
    assert f"file: openconnect-widget-{VERSION}.iq" in done.stdout
    assert 'entry="OpenConnectWidgetApp"' in manifest_of(tree)
    assert 'type="widget"' in manifest_of(tree)


def test_the_store_app_ids_never_change(tree):
    # Garmin keeps the id of the first upload and refuses another later: if this fails, it changed.
    ids = {}
    for kind in ("watchface", "widget"):
        assert package(tree, "--type", kind, "--color", "pink", "--design", "retro").returncode == 0
        ids[kind] = re.search(r'application [^>]*id="([0-9a-f]{32})"', manifest_of(tree)).group(1)
    assert ids == {"watchface": "3ad12a80549c56a2f8d2ccad2c56f139", "widget": ids["widget"]}
    assert len(set(ids.values())) == 2
    # Colour, design and version do not decide the id of a store package.
    assert package(tree, "--type", "watchface", "--color", "mint").returncode == 0
    assert ids["watchface"] in manifest_of(tree)


def test_products_can_be_chosen_and_must_be_sane(tree):
    assert package(tree, "--products", "fenix847mm,fr970").returncode == 0
    assert re.findall(r'<iq:product id="([^"]+)"', manifest_of(tree)) == ["fenix847mm", "fr970"]
    for bad in ("fenix847mm,fenix847mm", "fenix8; rm -rf /"):
        done = package(tree, "--products", bad)
        assert done.returncode == 2, bad
        assert "build.sh:" in done.stderr


def test_a_package_has_no_all_colors_mode(tree):
    done = package(tree, "--all-colors")
    assert done.returncode == 2
    assert "--package" in done.stderr


def properties(tree):
    root = ElementTree.parse(tree / "gen" / "resources" / "properties" / "properties.xml").getroot()
    return {p.get("id"): (p.get("type"), p.text) for p in root.findall("property")}


def settings(tree):
    root = ElementTree.parse(tree / "gen" / "resources" / "settings" / "settings.xml").getroot()
    return {
        s.get("propertyKey").removeprefix("@Properties."): [
            e.get("value") for e in s.iter("listEntry")
        ]
        for s in root.findall("setting")
    }


def test_the_face_can_be_set_up_in_garmins_app(tree):
    assert (
        package(
            tree,
            "--type",
            "watchface",
            "--design",
            "retro",
            "--color",
            "pink",
            "--fields",
            "steps,battery,heart,stress",
        ).returncode
        == 0
    )
    props = properties(tree)
    assert props == {
        "Design": ("string", "retro"),
        "Colour": ("number", str(0xFF5FA2)),
        "Field1": ("string", "steps"),
        "Field2": ("string", "battery"),
        "Field3": ("string", "heart"),
        "Field4": ("string", "stress"),
    }
    lists = settings(tree)
    assert set(lists) == set(props)
    # Every default is one of the choices Garmin's app offers, so the setting shows a value.
    for key, (_, default) in props.items():
        assert default in lists[key], key
    assert lists["Design"] == ["clean", "dashboard", "retro"]
    assert lists["Field1"] == ["battery", "heart", "stress", "steps", "floors"]


def test_the_widget_has_no_design_setting(tree):
    assert package(tree, "--type", "widget").returncode == 0
    assert "Design" not in properties(tree)
    assert "Design" not in settings(tree)
    assert {"Colour", "Field1", "Field2", "Field3", "Field4"} <= set(settings(tree))


def test_the_colour_choices_match_the_apps_palette(tree):
    assert package(tree, "--type", "widget").returncode == 0
    # WatchColor in the Android app: mint, blue, amber, pink, green.
    assert settings(tree)["Colour"] == [
        str(int(h, 16)) for h in ("5CF2C0", "7AA7FF", "FFB000", "FF5FA2", "9BBC0F")
    ]


def string_ids(path):
    return {s.get("id") for s in ElementTree.parse(path).getroot().findall("string")}


def test_english_and_dutch_have_the_same_settings_strings():
    english = string_ids(WATCH / "resources" / "strings" / "settings_strings.xml")
    dutch = string_ids(WATCH / "resources-nld" / "strings" / "settings_strings.xml")
    assert english == dutch
    assert {"DesignTitle", "ColourTitle", "Field1Title", "FieldFloors", "ColourMint"} <= english


def test_only_prefs_reads_the_built_in_config():
    # Everything else asks Prefs, which reads Garmin's settings first and falls back to Config.
    users = [
        p.name
        for p in (WATCH / "source").glob("*.mc")
        if re.search(r"\bConfig\.[A-Z]+\b", p.read_text())
    ]
    assert users == ["Prefs.mc"]


def test_the_check_script_passes_on_the_repository():
    done = subprocess.run(
        ["bash", str(WATCH / "check-store.sh")],
        capture_output=True,
        text=True,
        env={**os.environ, "CIQ_SDK": os.environ.get("CIQ_SDK", "")},
    )
    assert done.returncode == 0, done.stdout + done.stderr
    assert "All checks passed." in done.stdout
    assert "FAIL" not in done.stdout


def test_the_check_script_notices_a_wrong_version(tmp_path):
    root = tmp_path / "repo"
    shutil.copytree(ROOT / "watch", root / "watch", ignore=shutil.ignore_patterns("gen", "out"))
    shutil.copytree(ROOT / "docs", root / "docs", ignore=shutil.ignore_patterns("img", "releases"))
    (root / "android" / "app").mkdir(parents=True)
    shutil.copytree(
        ROOT / "android" / "core" / "src" / "main",
        root / "android" / "core" / "src" / "main",
        ignore=shutil.ignore_patterns("res"),
    )
    (root / "pyproject.toml").write_text(f'[project]\nversion = "{VERSION}"\n')
    (root / "android" / "app" / "build.gradle.kts").write_text('versionName = "0.0.1"\n')
    done = subprocess.run(
        ["bash", str(root / "watch" / "check-store.sh")], capture_output=True, text=True
    )
    assert done.returncode == 1
    assert "FAIL: android versionName (0.0.1) differs" in done.stdout


def test_the_listing_texts_exist_in_both_languages_and_stay_honest():
    english = (ROOT / "docs" / "store" / "listing-en.md").read_text()
    dutch = (ROOT / "docs" / "store" / "listing-nl.md").read_text()
    for text in (english, dutch):
        assert "OpenConnect Watch Face" in text and "OpenConnect Widget" in text
        assert "github.com/Tufein/OpenConnect" in text
        assert "Garmin" in text and "AGPL-3.0-or-later" in text
    # Same structure: the same number of headings and bold labels.
    assert english.count("\n## ") == dutch.count("\n## ")
    assert len(re.findall(r"^\*\*[^*]+:\*\*", english, re.M)) == len(
        re.findall(r"^\*\*[^*]+:\*\*", dutch, re.M)
    )
    # What the face cannot do is said, not promised.
    assert "no sleep" in english.lower() and "geen slaap" in dutch.lower()


@pytest.mark.skipif(
    not (os.environ.get("CIQ_SDK") and shutil.which("xmllint")),
    reason="needs the Connect IQ SDK (CIQ_SDK) and xmllint",
)
def test_the_resources_validate_against_garmins_schema(tree):
    xsd = Path(os.environ["CIQ_SDK"]) / "bin" / "resources.xsd"
    for kind in ("watchface", "widget"):
        assert package(tree, "--type", kind).returncode == 0
        for rel in (
            "resources/strings/strings.xml",
            "resources/properties/properties.xml",
            "resources/settings/settings.xml",
        ):
            done = subprocess.run(
                ["xmllint", "--noout", "--schema", str(xsd), str(tree / "gen" / rel)],
                capture_output=True,
                text=True,
            )
            assert done.returncode == 0, f"{kind} {rel}: {done.stderr}"
