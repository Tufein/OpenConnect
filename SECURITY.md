# Security and maintenance

OpenConnect holds your health history and, optionally, AI provider keys and Garmin or Oura logins. Treat the
database (`openconnect.db` on the server, the app's private storage on the phone) and any backup file as sensitive.
Never attach them to an issue. For a problem report use **Settings, Share diagnostics** in the app: it shows the exact
text first and leaves keys, tokens, names, health values and routes out (a test proves that with fake secrets). Read it
before you paste it into an issue, and describe the rest in words.

## Reporting a vulnerability

Use GitHub's **private vulnerability reporting** (the *Security* tab of the repository, *Report a vulnerability*)
**once the repository owner has switched it on**. No separate confidential contact is configured in this repository.
If private reporting is not available, open a short issue that says only that you have a security report and ask for a
private contact; do not describe the weakness in the issue. No response time is promised: this is a one-person project.

## Supported versions

Only the **latest release** gets fixes. From 1.0, the latest `1.x` is the supported line; the rules for what a version
number promises are in [docs/VERSIONING.md](docs/VERSIONING.md). The Android app needs **Android 9 or newer**. A phone
with Android 8 stays on v0.51.0, which will not get fixes.

## The Android app

- **Everything stays on the phone.** There is no server of ours. The app talks only to the services you switch on
  (your Garmin account, Oura, the AI provider whose key you entered, and GitHub for the updater in the GitHub build).
- **Keys and logins** (AI keys, the Garmin key, OAuth tokens) are kept in `EncryptedSharedPreferences` (Android
  Keystore), are only entered in the app, are sent only to their own service and never in a web address. If secure
  storage is unavailable, a key is not stored at all. Backups and device transfer are off (`allowBackup=false` plus
  data-extraction rules that exclude everything); the app's own backup file never contains keys.
- **What goes to an AI provider:** a short JSON summary of the numbers and the activity names, never the GPS track. Every
  address the app can send to is listed in [docs/PRIVACY.md](docs/PRIVACY.md) and enforced by a test.
- **The updater (GitHub build only)** (`REQUEST_INSTALL_PACKAGES`) downloads the APK from the repository's GitHub releases
  and checks its SHA-256 against the published checksum before it asks Android to install it. Since v0.56.0 a release
  without a checksum file is refused (before, it was accepted unchecked), and so is an APK that is not signed with the
  same key as the installed app, each with a message that says why. Android itself also refuses a different key. The
  **store build** has no updater, no install-packages permission and no FileProvider.
- **Shrunk and checked on a real Android.** Since v0.58.0 the release APK is shrunk by R8 (no renaming, no optimising), and a
  release is only published after the signed APK has been started on an emulator and walked through
  (`android/scripts/emulator_smoke.py`).
- **Release signing:** every GitHub release is signed with the same key (certificate SHA-256
  `06b67b82ec65b533343130fea378165b15ef520bc01969f47ff440242dbdf3b7`, common name *OpenConnect release*). A release is
  only published after a step in `release.yml` has checked that the APK carries it. Check a download yourself with
  `apksigner verify --print-certs`.
- **Two channels, one app id.** The store channel keeps `nl.tufein.openconnect` but a real Play/F-Droid publication is
  signed with a different key (the store itself manages or requires it). Android then sees a different signer: a store
  build cannot install over the GitHub APK, and the GitHub APK cannot install over a store build. Moving means backup →
  uninstall → install → restore; a backup never contains keys. The store build is **not published** yet; Play and F-Droid
  remain blocked (see [docs/PUBLIC_RELEASE.md](docs/PUBLIC_RELEASE.md)).
- **Not independently reviewed.** The code is test-first and open, but nobody outside the project has audited it.

## The server (for people who host it themselves)

- **Optional sign-in.** Set `OPENCONNECT_PASSWORD` to require a signed seven-day cookie for every page and API (only
  `/healthz` and static files stay public). The cookie is signed with a key derived from the password (PBKDF2), not
  with the password itself, and gets the `Secure` flag when `OPENCONNECT_BASE_URL` starts with `https://`. Wrong
  guesses are not rate limited, so choose a long password. Without the setting there is **no login**: anyone who can
  reach the HTTP port can read, sync and delete data. `docker-compose.yml` publishes `127.0.0.1:8000` only, but the
  image itself sets `OPENCONNECT_HOST=0.0.0.0`, so a plain `docker run -p 8000:8000` exposes it to your whole network.
- **The Garmin webhook is open unless configured.** `POST /api/garmin/webhook` only checks a token when
  `OPENCONNECT_WEBHOOK_TOKEN` is set (`src/openconnect/web/routes_api.py`); with an empty setting, anyone who can
  reach the port can submit data. Ping/Pull callback URLs are only fetched from the HTTPS hosts in
  `OPENCONNECT_CALLBACK_HOSTS` (default `apis.garmin.com`).
- **Paid AI calls are unthrottled.** `POST /api/reports/...?force=true` regenerates a report at provider cost, with
  no rate limit and no confirmation beyond reaching the port (and the optional sign-in).
- **Tokens and provider keys are stored in plain text** in the SQLite database (`oauth_tokens` table); the container
  runs as the non-root user `openconnect` (uid 10001) with a healthcheck.
- There is no snapshot rotation on the server, so back up the database yourself.

## Maintenance procedure

- CI runs `ruff check`, `ruff format --check` and `pytest --cov-fail-under=85` on Python 3.11 to 3.13, a wheel smoke
  test and a Docker build; the Android workflow runs the core tests, the app tests, lint and builds the APK. Run the
  same before tagging a release.
- Dependabot proposes pip, Gradle, Docker and GitHub Actions updates weekly, grouped per family. Review AI-provider SDK
  updates especially, because report generation pins beta API features.
- Release notes say which Garmin API route was used for verification (personal key in the app, OAuth push in the
  server) and what has **not** been tried on a real device, so claims stay attributable.

This documents the current design, not an audit.
