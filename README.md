<p align="center"><img src="docs/img/logo.png" alt="Cercis logo" width="96"></p>

# Cercis

An open-source, **private alternative to Garmin Connect**: an Android app (and a self-hosted server) that keeps your
activities and daily health data on your own device, with charts, insights, a coach and reports, optionally written by
an AI with your own key. It reads a Garmin watch directly, your Garmin account with a key you make yourself, an Oura
Ring, and FIT, GPX and TCX files. Built test-first, AGPL-3.0.

> **Status: v1.14.0.** **New in v1.14.0:** a clean foundation. The APK is 6.4 MB instead of 13.3 MB because the
> code (dex files) is now compressed inside it, and the size budget went from 14 to 8 MiB. One scroll helper steadies
> the screen tests, a pre-push check catches formatting, a stale changelog and a version that differs between files,
> and the view model is split into smaller files without changing behaviour. A new, much larger roadmap (twelve
> tracks, a ladder to 2.0 and a direction for 3.0) replaces the old one, which is kept as `docs/ROADMAP-HISTORIE.md`
> ([release notes](docs/releases/v1.14.0.md)).
> **Since v1.13.13:** confirmed on real hardware: Bluetooth sync, watch battery, phone
> notifications on the watch and live heart rate work on Stefan's fenix 8 and phone, and the app now labels those two
> sources *Tested on a real device*. Fuzz tests found and fixed seven ways a damaged FIT, GPX or TCX file, a watch file
> with an impossible time zone or a Garmin answer with `NaN` could crash the import or store nonsense
> ([release notes](docs/releases/v1.13.13.md)).
> **Since v1.13.12:** **Look back** under Insights. This year against last year to the same day,
> a grid of every month of every year, each year on its own (sports, busiest week, quiet weeks) and a timeline of the
> records you broke, for one sport or all of them, worked out on the phone from the activities you have stored. On an
> empty install it can load three years of demo data to show how it looks.
> **Since v1.13.11:** the Oura tab has its own heart-rate card with the average and lowest
> heart rate of the last night. Oura measures during sleep and its API gives no live heart rate; the card says so.
> **Since v1.13.10:** the Oura tab syncs by itself when you open it and the last sync is due;
> the Sync now button stays for a sync you want immediately.
> **Since v1.13.9:** live heart rate starts by itself when Cercis opens. The app remembers the
> last sensor, or looks for one, and keeps the recording running when you switch tabs; stop it from the card or the
> notification. It is on by default and can be switched off under Watch → Live heart rate.
> **Since v1.13.8:** live heart rate has its own card on the front page: start, pick a watch, see the bpm and
> lowest/average/highest, stop and save without opening Watch first.
> **Since v1.13.7:** the live heart rate from a Garmin watch really starts: the app asks Garmin's real-time service
> only after the watch has sent its configuration, instead of during the first handshake. The Watch screen offers the
> watch next to a chest strap and the standard broadcast; beats per minute, min/avg/max and the recording work as
> before. Garmin's real-time service has no beat-to-beat intervals, so HRV stays empty for this source.
> **Since v1.13.5:** the watch’s charge no longer gets stuck on an old value: the app accepts battery reports the
> watch sends by itself, asks again when a sync starts, and waits briefly after `SYNC_COMPLETE` for a late answer.
> **Since v1.13.4:** the four scores on Health are the same quiet ring instead of separate horizontal bars, and a
> battery answer that arrives after the sync status still updates the card at once.
> **Since v1.13.3:** the screens are drawn with Jetpack Compose 1.13 (BOM 2026.11.01, compiled against Android
> 37.1; targetSdk 36 and minSdk 28 stay). With it, the screen tests hung at random on an empty Home; the cause was the
> old Compose test rule running the screens' coroutines on background threads, so the tests now use the v2 rule, which
> runs them on the main thread as a phone does. Nothing in the app was changed on purpose.
> **Since v1.13.2:** the emulator smoke test stops a release when it fails (the walk uses `swangle_indirect`), and the
> APK has a 14 MiB budget checked on every pull request.
> **Since v1.13.1:** the app is called **Cercis**, so it no longer shares its name with the OpenConnect VPN
> client. Only the display name changed: the Android package id `nl.tufein.openconnect`, the GitHub repository and the
> release asset names (`openconnect-vX.Y.Z.apk`) stay the same, and the GitHub build keeps its updater. A phone on
> v1.13.0 therefore updates to later releases normally, with the same signing key and no data migration. The two
> channels from v1.13.0 stay; the **store build is not published**: Google Play and F-Droid remain blocked until the
> name is legally cleared, and an account, testers, real screenshots and store declarations are still missing.
> Everything from v1.12.0 stays, including the privacy register and the four languages; the German and French texts
> remain machine-drafted, not native-reviewed.
> [Release notes](docs/releases/v1.13.12.md).
> It runs end to end on demo data with no account. **Only the Garmin account route has been tried live; nothing else has been tried on a real phone, watch, ring
> or band yet** (see [what is tested](#what-is-tested)). The checks that remain are listed in
> [docs/PUBLIC_RELEASE.md](docs/PUBLIC_RELEASE.md); the plan is in [docs/ROADMAP.md](docs/ROADMAP.md).
> Not affiliated with or endorsed by Garmin or Oura.

## Install the Android app

Needs **Android 9 or newer**. No server, no account, no store listing: your data stays on the phone. The full guide, with checking the
download, the "install unknown apps" switch, updating and Obtainium, is [docs/INSTALL.md](docs/INSTALL.md).

1. Open the [latest release](https://github.com/Tufein/OpenConnect/releases/latest) on your phone and download
   `openconnect-vX.Y.Z.apk` (and `openconnect-vX.Y.Z.apk.sha256` if you want to check it).
2. Open the file. Android asks once to allow installing apps from your browser or file manager; allow it.
3. Open **Cercis**, answer the first-start questions and press **Load demo data** to look around.

Check the download with `sha256sum -c openconnect-vX.Y.Z.apk.sha256`. Every GitHub release is signed with the same key, so
an update installs over the old app and keeps your data; *Settings, Updates* (GitHub build) can fetch and verify the next
one for you. The signing certificate is listed in [SECURITY.md](SECURITY.md). A phone with Android 8 stays on v0.51.0.

Then connect what you have, all optional: a Garmin watch, bike computer or golf device (any family, see
[docs/GARMIN_DEVICES.md](docs/GARMIN_DEVICES.md)) over USB or Bluetooth ([docs/WATCH.md](docs/WATCH.md),
[docs/BLUETOOTH.md](docs/BLUETOOTH.md)), your Garmin account with your own key ([docs/GARMIN.md](docs/GARMIN.md)), an
Oura Ring ([docs/OURA.md](docs/OURA.md)), Claude or Gemini for reports and the coach ([docs/AI.md](docs/AI.md)).
Everything about
the app, its limits and how to build it: [docs/ANDROID.md](docs/ANDROID.md).

## What it does

Today and insights per day, activities with maps, heart-rate zones and splits, form, readiness and training load,
race predictions and a season plan, sleep and heart-rate trends, shoes, a journal, a calendar and streaks, hi-scores,
a training plan that can go to the watch as FIT workouts, a coach chat, widgets and nudges, backup and CSV export, a
look of its own ([docs/DESIGN.md](docs/DESIGN.md)) in Dutch and English. The whole inventory, with what each number
means, is in [docs/INSIGHTS.md](docs/INSIGHTS.md) and [docs/FEATURES.md](docs/FEATURES.md); every release's story is in
the [changelog](CHANGELOG.md).

## What is tested

Everything has automated tests that run without a network, an account or a key (more than 970 of them). **Real
hardware is another matter.** The register of what has and has not been checked is in
[docs/ROADMAP.md](docs/ROADMAP.md#7-wat-nog-niet-is-gecontroleerd-register):

| Source | State |
|--------|-------|
| Garmin account with your own key (daily data, sleep, HRV, SpO2, Body Battery) | tried once against a real account |
| Garmin watch over USB, over Bluetooth, live heart rate with the screen off, workouts to the watch | **not tried on a real watch or phone** |
| Health Connect | **not tried on a real phone** |
| Oura Ring | **never run against the real service** |
| Claude and Gemini | **no live calls with a real key** |
| In-app updater (GitHub build), key vault, app lock | **not tried on a real phone** |

Release notes say what was not checked, every time.

## Privacy

The Android app has no server of ours and sends nothing anywhere you did not switch on. If you add an AI key, a short
JSON summary of your numbers and activity names (never the GPS track) goes to that provider for a report or the coach;
keys are kept in the Android Keystore. *Settings, About and licences* in the app lists every licence and says, per source,
whether it was ever tried on a real device and lists every address the app can send data to; *Share diagnostics* shows the
exact report (no keys, tokens, names, health values or routes) before anything is shared. The full statement, in English and
Dutch, is [docs/PRIVACY.md](docs/PRIVACY.md); a test fails when the code mentions an address that is not in it. More in
[docs/ANDROID.md](docs/ANDROID.md) and [SECURITY.md](SECURITY.md).

## The server (self-hosted)

The Python server is the original OpenConnect code; the package and command stay `openconnect`: FastAPI, one SQLite
file, a web dashboard, a JSON API (`/api/docs`) and a CLI. Requires Python 3.11 or newer.

```bash
git clone https://github.com/Tufein/OpenConnect.git
cd OpenConnect
python -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"

openconnect sync --days 60     # loads demo data (mock mode is the default)
openconnect serve              # http://127.0.0.1:8000
```

Or `docker compose up --build` (published on localhost only; set `OPENCONNECT_PASSWORD` before exposing it, see
[SECURITY.md](SECURITY.md)).

```bash
openconnect sync [--days N]                    # pull from Garmin (or the demo source)
openconnect activities [--limit N]             # list stored activities with their ids
openconnect export ID --format fit|gpx|tcx [-o FILE]
openconnect import run.fit ride.gpx ...        # FIT, GPX or TCX
openconnect report weekly [--week YYYY-MM-DD]  # AI (or template) report
openconnect report activity ID [--force]
openconnect serve [--host H] [--port P]
```

Settings come from environment variables, all optional (see [`.env.example`](.env.example)):

| Variable | Default | Purpose |
|----------|---------|---------|
| `OPENCONNECT_DB` | `data/openconnect.db` | SQLite file |
| `OPENCONNECT_HOST` / `OPENCONNECT_PORT` | `127.0.0.1` / `8000` | Listen address |
| `OPENCONNECT_BASE_URL` | `http://localhost:8000` | Public URL (OAuth redirect URI, Host allow-list) |
| `OPENCONNECT_ALLOWED_HOSTS` | | Extra accepted `Host` names, comma separated |
| `OPENCONNECT_GARMIN_MODE` | `mock` | `mock` (offline demo data), `personal` (your own key) or `live` (Developer API) |
| `GARMIN_PERSONAL_KEY` | | Required in `personal` mode; create it on the Garmin Connect website |
| `GARMIN_CLIENT_ID` / `GARMIN_CLIENT_SECRET` | | Required in `live` mode |
| `OPENCONNECT_WEBHOOK_TOKEN` | | Shared secret for `POST /api/garmin/webhook?token=…` |
| `OPENCONNECT_CALLBACK_HOSTS` | `apis.garmin.com` | HTTPS hosts allowed for Ping/Pull callbacks |
| `OPENCONNECT_PASSWORD` | | Enables sign-in with a signed seven-day cookie |
| `OPENCONNECT_AI_PROVIDER` | `auto` | `auto`, `anthropic` or `template` |
| `ANTHROPIC_API_KEY` | | Enables Claude-written reports in `auto` mode |
| `OPENCONNECT_AI_MODEL` / `OPENCONNECT_AI_EFFORT` | `claude-opus-5-5` / `medium` | Model and effort for reports |
| `OPENCONNECT_LANGUAGE` | `en` | Report language (`en`, `nl`; Claude supports more) |

### Garmin access

Garmin's data APIs are not open to everyone. The **personal key** route (your own data, pull only, key made on the
Garmin Connect website, no developer agreement) works in the app and the server
(`OPENCONNECT_GARMIN_MODE=personal`). The business tiers (Starter, Enterprise: OAuth 2.0 with PKCE, push or pull) are
what `live` mode implements, and need Garmin's approval. Endpoints and field names come from Garmin's public
documentation and are marked **verify** until exercised with real credentials; details in
[docs/GARMIN.md](docs/GARMIN.md) and [docs/FEATURES.md](docs/FEATURES.md). Check Garmin's Developer Program Agreement
before sending Garmin-derived data to any third party.

## Development

The project is developed test first: each layer's tests were written before its code, and nothing in the suite needs
the network, a Garmin account or an API key.

```bash
pytest                                  # Python unit + integration tests
ruff check src tests && ruff format --check src tests
cd android && ./gradlew :core:test :app:testGithubDebugUnitTest :app:testStoreDebugUnitTest :app:lintGithubDebug :app:lintStoreDebug   # the Android app
python tools/changelog.py               # after adding docs/releases/vX.Y.Z.md
```

```
src/openconnect/   the Python server: domain, garmin/, formats/, services/, ai/, web/, storage/, cli.py
android/core/      the app's pure Kotlin logic (JUnit)
android/app/       the Jetpack Compose app (Robolectric UI tests)
watch/             Connect IQ source for watch faces (never compiled)
docs/              guides, design, roadmap, release notes
```

CI (GitHub Actions) tests the server on Python 3.11 to 3.13, builds the wheel and the Docker image, and tests, lints and
builds the Android app, and starts the shrunk APK on an Android emulator and walks through it. A release is the *Release*
workflow run on `main`: it tests everything again, signs the APK, starts the signed APK on an emulator and only then
publishes it with the wheel. Versions mean what [docs/VERSIONING.md](docs/VERSIONING.md) says. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

[AGPL-3.0-or-later](LICENSE) since v0.6.0 (MIT up to v0.5.0). The Garmin Bluetooth sync is ported from
[Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) (AGPL-3.0), see [NOTICE](NOTICE).
Garmin and Garmin Connect are trademarks of Garmin Ltd.

## Documentation

[Changelog](CHANGELOG.md) · [Roadmap](docs/ROADMAP.md) · [Release checklist](docs/PUBLIC_RELEASE.md) ·
[Install](docs/INSTALL.md) · [Privacy](docs/PRIVACY.md) · [Performance](docs/PERFORMANCE.md) ·
[Versioning](docs/VERSIONING.md) · [Security](SECURITY.md) · [Contributing](CONTRIBUTING.md) ·
[Release notes](docs/releases)
