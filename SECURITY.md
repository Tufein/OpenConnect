# Security and maintenance

OpenConnect holds your Garmin health history and, optionally, an AI provider key. Treat
`openconnect.db` as sensitive: it contains OAuth and refresh tokens plus every activity and daily
health record you have synced. Never attach it to an issue.

Use GitHub private vulnerability reporting **if the repository owner has enabled it**. No separate
confidential contact is configured in this repository. If private reporting is unavailable, open a
short issue asking for a private contact rather than describing the exploit.

## What this deployment trusts

- **There is no app-level login.** Anyone who can reach the HTTP port can read, sync and delete
  data. `docker-compose.yml` deliberately publishes `127.0.0.1:8000`, but the image itself sets
  `OPENCONNECT_HOST=0.0.0.0` (`Dockerfile`), so a plain `docker run -p 8000:8000` exposes the API to
  your whole network. Optional authentication is an open roadmap item.
- **The Garmin webhook is open unless configured.** `POST /garmin/webhook` only checks a token when
  `webhook_token` is set (`src/openconnect/web/routes_api.py`); with an empty setting, anyone who can
  reach the port can submit data.
- **Paid AI calls are unthrottled.** `POST /api/reports/...?force=true` regenerates a report at
  provider cost, with no rate limit and no confirmation beyond reaching the port.
- **Tokens and provider keys are stored plaintext** in the SQLite database (`oauth_tokens` table,
  `save_tokens`); the container runs as the non-root user `openconnect` (uid 10001) with a healthcheck.
- **The Android app keeps its own credentials** in app storage and has backup coverage in
  `android/` tests; the server has no equivalent snapshot rotation, so back up the database yourself.

## Maintenance procedure

- CI runs `ruff check`, `ruff format --check`, `pytest --cov-fail-under=85` on Python 3.11–3.13,
  a wheel smoke test and a Docker build; run the same suite before tagging a release.
- Dependabot proposes pip, Gradle, Docker and GitHub Actions updates weekly; review AI-provider SDK
  updates especially, because report generation pins beta API features.
- Document, in the release notes, which Garmin API tier was used for verification (Personal API in
  the app, OAuth push in the server) so claims stay attributable.

This documents the current design, not an audit. No response SLA is promised.
