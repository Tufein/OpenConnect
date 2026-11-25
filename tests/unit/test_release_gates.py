"""The signed APK is walked on an emulator before a release is published, and that is a gate again.

v1.11.0, v1.12.0 and v1.13.0 were published with the emulator smoke red: it had been marked
`continue-on-error` after the emulator died on the runner several times in a row. A smoke that
cannot stop a release is not a test. The gate is back, with one honest allowance: if the emulator
itself is lost, the walk is tried once more on a fresh emulator. A crash of the app fails twice.
"""

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = yaml.safe_load((ROOT / ".github" / "workflows" / "release.yml").read_text())
JOBS = WORKFLOW["jobs"]


def _needs(job: dict) -> list:
    needs = job.get("needs", [])
    return [needs] if isinstance(needs, str) else list(needs)


def test_the_release_job_waits_for_the_android_build_and_the_smoke():
    assert set(_needs(JOBS["release"])) == {"android", "smoke"}


def test_the_smoke_job_is_not_advisory():
    assert not JOBS["smoke"].get("continue-on-error"), "a job that may fail does not gate anything"


def _walk_steps():
    return [s for s in JOBS["smoke"]["steps"] if "emulator_smoke.py" in str(s.get("with", {}))]


def test_the_emulator_walks_the_signed_apk_twice_at_most():
    steps = _walk_steps()
    assert len(steps) == 2, "one walk and one retry on a fresh emulator"
    first, retry = steps
    assert first.get("continue-on-error") is True and first.get("id")
    assert not retry.get("continue-on-error"), "the second failure must fail the job"
    assert retry["if"].replace(" ", "") == f"steps.{first['id']}.outcome=='failure'"


def test_both_walks_use_the_same_emulator_and_keep_their_own_screenshots():
    first, retry = _walk_steps()
    for step in (first, retry):
        assert step["uses"].startswith("reactivecircus/android-emulator-runner@")
    assert {k: v for k, v in first["with"].items() if k != "script"} == {
        k: v for k, v in retry["with"].items() if k != "script"
    }
    assert first["with"]["script"] != retry["with"]["script"], "each walk writes to its own folder"


def test_the_screenshots_of_both_walks_are_kept_even_when_a_walk_fails():
    keep = [
        s
        for s in JOBS["smoke"]["steps"]
        if str(s.get("uses", "")).startswith("actions/upload-artifact@")
    ]
    assert keep and keep[-1]["if"] == "always()"
    assert (
        "smoke-out/" in keep[-1]["with"]["path"] and "smoke-out-retry/" in keep[-1]["with"]["path"]
    )
