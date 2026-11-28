#!/usr/bin/env bash
# The checks that broke CI most often after a push, run in seconds before it: ruff (lint and format), the changelog
# that must match docs/releases, and one version in pyproject.toml, the server, the API test and the Android build.
# Run it by hand (tools/prepush.sh) or let git run it on every push: git config core.hooksPath .githooks
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

fail=0
step() { printf '\n== %s\n' "$1"; }

step "ruff check"
ruff check src tests tools || fail=1

step "ruff format --check"
ruff format --check src tests tools || fail=1

step "CHANGELOG.md matches docs/releases"
python3 - <<'PY' || fail=1
import importlib.util, sys
from pathlib import Path
spec = importlib.util.spec_from_file_location("changelog", "tools/changelog.py")
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
if Path("CHANGELOG.md").read_text(encoding="utf-8") != mod.build(Path("docs/releases")):
    sys.exit("CHANGELOG.md is out of date: run python3 tools/changelog.py and commit it")
print("ok")
PY

step "one version everywhere"
python3 - <<'PY' || fail=1
import re, sys
from pathlib import Path
found = {
    "pyproject.toml": re.search(r'^version = "([^"]+)"', Path("pyproject.toml").read_text(), re.M),
    "src/openconnect/__init__.py": re.search(r'__version__ = "([^"]+)"', Path("src/openconnect/__init__.py").read_text()),
    "tests/integration/test_api.py": re.search(r'"version": "([^"]+)"', Path("tests/integration/test_api.py").read_text()),
    "android/app/build.gradle.kts": re.search(r'versionName = "([^"]+)"', Path("android/app/build.gradle.kts").read_text()),
}
versions = {name: m.group(1) if m else None for name, m in found.items()}
if len(set(versions.values())) != 1 or None in versions.values():
    sys.exit("versions differ: " + ", ".join(f"{k}={v}" for k, v in versions.items()))
version = versions["pyproject.toml"]
if not Path(f"docs/releases/v{version}.md").exists():
    sys.exit(f"docs/releases/v{version}.md is missing")
print(f"ok: {version}")
PY

if [ "$fail" -ne 0 ]; then
    printf '\nprepush: something above needs fixing before CI sees it.\n' >&2
    exit 1
fi
printf '\nprepush: all good.\n'
