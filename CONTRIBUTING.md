# Contributing to OpenConnect

OpenConnect is a self-hosted Garmin Connect alternative: a Python server (FastAPI + SQLite) and a
Companion Android app in the same repository, developed test first.

## Get oriented

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — layers, the security model, what is deliberately simple.
- [docs/FEATURES.md](docs/FEATURES.md) — the Garmin function inventory and what the Developer API exposes.
- [docs/SYNC.md](docs/SYNC.md), [docs/DESIGN.md](docs/DESIGN.md), [docs/releases/](docs/releases) —
  per-version notes; every release gets one of these files.

## Server: setup and checks

```sh
python -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"
ruff check src tests
ruff format --check src tests
pytest --cov=openconnect --cov-report=term-missing --cov-fail-under=85
```

CI runs exactly these on Python 3.11, 3.12 and 3.13, then builds a wheel, smoke-tests the CLI
(`openconnect sync --days 14`) and a Docker image. Coverage may not drop below 85%.

The project is written test first: add the failing test for a behaviour before its implementation,
and keep the mock Garmin source (`GARMIN_MODES = ("mock", "live")`) usable — demos and tests run
against it without credentials.

## Android app

The app lives in `android/` with its own workflow (`android.yml`). Build and test from that
directory; sync, backup and Health Connect paths have their own test suites, and anything touching a
real watch is verified manually and recorded in the release notes.

## Releases

One tag drives both artifacts: it must equal `v<version>` in `pyproject.toml` **and** `versionName`
in `android/app/build.gradle.kts`. `release.yml` verifies this, builds the signed APK, checks the
signer (`android/scripts/verify-apk-signer.sh`) and publishes checksums.

## Pull requests

State which Garmin mode your change affects (mock, OAuth live, Personal API in the app) and
which checks you ran. Health data is personal: never attach a real database, FIT file or token to an
issue — use the mock Garmin source (`OPENCONNECT_GARMIN_MODE=mock`, the default) or a synthetic
fixture.
