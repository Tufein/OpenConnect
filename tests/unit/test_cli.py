import re

import pytest

from openconnect import __version__
from openconnect.cli import main


@pytest.fixture
def env(tmp_path):
    return {"OPENCONNECT_DB": str(tmp_path / "oc.db")}


def run(args, env, capsys):
    code = main(args, env=env)
    out = capsys.readouterr()
    return code, out.out, out.err


def test_version(capsys):
    with pytest.raises(SystemExit) as exit_info:
        main(["--version"], env={})
    assert exit_info.value.code == 0
    assert __version__ in capsys.readouterr().out


def test_sync_then_list_activities(env, capsys):
    code, out, _ = run(["sync", "--days", "14"], env, capsys)
    assert code == 0 and re.search(r"Synced \d+ activities, \d+ days of health data", out)
    code, out, _ = run(["activities", "--limit", "3"], env, capsys)
    lines = out.strip().splitlines()
    assert code == 0 and len(lines) == 3 and lines[0].startswith("garmin:")


def test_export_and_import_roundtrip(env, tmp_path, capsys):
    run(["sync", "--days", "14"], env, capsys)
    _, out, _ = run(["activities", "--limit", "20"], env, capsys)
    run_id = next(line.split()[0] for line in out.splitlines() if " running " in line)

    target = tmp_path / "run.gpx"
    code, out, _ = run(["export", run_id, "--format", "gpx", "-o", str(target)], env, capsys)
    assert code == 0 and target.read_bytes().startswith(b"<?xml") and str(target) in out

    code, out, _ = run(["import", str(target)], env, capsys)
    assert code == 0 and "Imported" in out and "import:" in out
    code, out, _ = run(["import", str(target)], env, capsys)
    assert "Updated" in out  # second import of the same file does not duplicate


def test_import_reports_bad_files_without_a_traceback(env, tmp_path, capsys):
    bad = tmp_path / "bad.fit"
    bad.write_bytes(b"nope")
    code, _, err = run(["import", str(bad)], env, capsys)
    assert code == 1 and "bad.fit" in err and "FIT" in err


def test_report_commands_use_the_offline_template(env, capsys):
    run(["sync", "--days", "14"], env, capsys)
    code, out, _ = run(["report", "weekly"], env, capsys)
    assert code == 0 and "Week of" in out
    _, listing, _ = run(["activities", "--limit", "1"], env, capsys)
    activity_id = listing.split()[0]
    code, out, _ = run(["report", "activity", activity_id], env, capsys)
    assert code == 0 and "Distance" in out


def test_unknown_activity_is_a_clean_error(env, capsys):
    code, _, err = run(["export", "garmin:nope", "--format", "fit"], env, capsys)
    assert code == 1 and "not found" in err.lower()
    code, _, err = run(["report", "activity", "garmin:nope"], env, capsys)
    assert code == 1 and "not found" in err.lower()


def test_invalid_configuration_exits_with_code_2(capsys):
    code, _, err = run(["sync"], {"OPENCONNECT_GARMIN_MODE": "live"}, capsys)
    assert code == 2 and "GARMIN_CLIENT_ID" in err


def test_serve_starts_uvicorn_with_the_configured_address(env, monkeypatch, capsys):
    calls = {}
    monkeypatch.setattr("uvicorn.run", lambda app, **kw: calls.update(app=app, **kw))
    code, _, _ = run(["serve", "--port", "9001"], {**env, "OPENCONNECT_HOST": "127.0.0.1"}, capsys)
    assert code == 0 and calls["host"] == "127.0.0.1" and calls["port"] == 9001
    assert calls["app"].title == "OpenConnect"
