# Pull request checklist

- [ ] `pip install -e ".[dev]"` then `ruff check src tests` and `ruff format --check src tests` pass
- [ ] `pytest --cov=openconnect --cov-fail-under=85` passes (the CI floor is 85%, do not lower it)
- [ ] Test first: the failing test for the behaviour exists before its implementation
- [ ] Nothing needs the network, a Garmin account or an API key; use the mock source or fixtures
- [ ] Database change? `migrate()` only re-runs `SCHEMA` (`CREATE TABLE IF NOT EXISTS`), so a column
      or table change needs an explicit, tested step for databases that already exist
- [ ] Android-only change: built and tested from `android/` with its own workflow commands
- [ ] `docs/releases/v<next>.md` written, and `pyproject.toml` + `android/app/build.gradle.kts`
      version bumped together (one release tag drives both), then `python tools/changelog.py`
      regenerates `CHANGELOG.md` (a test fails when it is stale)
- [ ] Health data stays out of the diff: no real database, FIT/GPX/TCX file, token or AI key
