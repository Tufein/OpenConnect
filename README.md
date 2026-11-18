<p align="center"><img src="docs/img/logo.png" alt="OpenConnect logo" width="96"></p>

# OpenConnect

An open-source, **self-hosted alternative to Garmin Connect**, built on the official
[Garmin Connect Developer Program](https://developer.garmin.com/gc-developer-program/overview/) APIs
and developed test-first. Your activities and daily health data live in one SQLite file on your own
machine, with a dashboard, FIT/GPX/TCX import and export, and optional AI-written reports by Claude or Gemini.

![OpenConnect dashboard](docs/img/dashboard.png)

> **Status: v0.51.0, an early foundation.** It runs end to end on demo data with no credentials.
> Live Garmin access needs Garmin Developer Program credentials (see [Garmin access](#garmin-access)).
> Not affiliated with or endorsed by Garmin.

## Android app

OpenConnect also comes as a **standalone Android app** (Android 8+), with no server needed: your data
stays on the phone, you import FIT, GPX or TCX files, and reports are written offline or by Claude with
your own API key. Download the APK from the [releases page](https://github.com/Tufein/OpenConnect/releases).
Since v0.3.0 it can also **read a Garmin watch directly**: over a USB cable (activities, steps, sleep, heart rate,
stress, Body Battery, SpO2, HRV) and over Bluetooth (live heart rate), with no server and no Garmin account. Since
v0.4.0 it can also **pull your own data from your Garmin account** with a personal key you make yourself (no approval,
no server of ours), and read a folder of watch files; Bluetooth reconnects by itself. The account route has been run
live against a real account; the USB and Bluetooth parts have not yet been tried on a real watch. See
**[docs/GARMIN.md](docs/GARMIN.md)** and **[docs/WATCH.md](docs/WATCH.md)** for what works and what is unverified.
More about the app, its limits and what has been verified: **[docs/ANDROID.md](docs/ANDROID.md)**.

**New in v0.51.0:** the Android app puts your devices side by side: a **Devices compared** card on the Day tab of Insights lists what the watch, the Oura Ring, WHOOP and the Hume Band each measured that day (sleep, steps, resting heart rate, blood oxygen) and says whether the numbers lie close together ([release notes](docs/releases/v0.51.0.md)). **New in v0.50.0:** the Android app shows a **Hume Band** with its own tab (sleep, steps, heart rate, HRV, blood oxygen, workouts), read from Health Connect because Hume has no public interface: no account or key, only the data the Hume app wrote, no invented scores, Hume as a source on the Insights Day tab and band workouts among the activities. Not tried with a real band, see [docs/HUME.md](docs/HUME.md) ([release notes](docs/releases/v0.50.0.md)). **New in v0.49.0:** the Android app reads a **WHOOP band** the way it reads the Oura Ring: its own tab with recovery, strain, sleep and workouts, kept apart from the watch and the ring, login with your own WHOOP application (OAuth), WHOOP as a source on the Insights Day tab, band workouts listed among the activities. Not tried against the real WHOOP service, see [docs/WHOOP.md](docs/WHOOP.md) ([release notes](docs/releases/v0.49.0.md)). **New in v0.48.0:** the Android app has an **in-app updater** under Settings: it checks GitHub for a newer
release, downloads the signed APK, verifies its SHA-256 against the published checksum and asks Android to
install it (on Android 8+ the first install needs "allow installing apps" once). **New in v0.47.0:** the remaining roadmap items: **activities via your own Garmin key** (original FIT files
included), **Ping/Pull callbacks** fetched from an HTTPS allow-list, **optional single-password sign-in**, and
**health snapshots and epoch summaries** stored as received. **New in v0.46.0:** the **server supports your own Garmin key** (`OPENCONNECT_GARMIN_MODE=personal` + `GARMIN_PERSONAL_KEY`) for daily health, sleep and the nightly vitals, without a developer agreement; weekly reports now mention HRV/SpO2/respiration; stress, Body Battery and sleep readings are kept as **through-the-day series** with native chart tooltips; **kilometre splits and best efforts** (1K/5K/10K) come from tracks; and blood pressure, skin temperature, VO2max and fitness age are read from the Health API (field names **verify**). A backfill button is available in live mode. **New in v0.45.0:** the **server** now reads **HRV, Pulse Ox, respiration and body composition** from the Garmin Health API, merges them into each day's health data, and shows them on *Health* with sparklines, a vital-signs table and a weigh-in table (two new JSON endpoints: `/api/health/vitals` and `/api/health/body-composition`). **New in v0.44.0:** an **Insights per day** tab (`Day`): pick the source (Garmin or Oura Ring), the day and the measure in dropdowns and read the day against your own last four weeks, plus **dates under every chart** ([release notes](docs/releases/v0.44.0.md)). **New in v0.43.0:** **Settings fold away** (the look settings are one section that opens when tapped), **Connect IQ** opens Garmin's own app, the **watch face is gone** from the app, and an activity is **filled in with the watch and the Oura Ring** (Body Battery and stress around it, the day, the night before, the ring's scores and its own record of the effort) ([release notes](docs/releases/v0.43.0.md)). **New in v0.42.0:** three small cards. **Sleep rhythm** on Home (how alike your last two weeks of nights were in length), a **resting heart rate trend** on Home (your last week against the 28 days before) and a **step goal week** on Home (on how many of the last seven days you reached your step goal) ([release notes](docs/releases/v0.42.0.md)). **New in v0.41.0:** a **training ramp** card under *Insights, Form*: this week's training against the average week of your last four weeks, so a big jump shows before it hurts ([release notes](docs/releases/v0.41.0.md)). **New in v0.40.0:** live heart rate **keeps recording with the screen off**: while a sensor streams, a **persistent notification** shows your heart rate and elapsed time and holds the phone's Bluetooth connection open, so a training continues with the phone in your pocket; stop it from the notification or the app and save it as usual (see **[docs/WATCH.md](docs/WATCH.md)**). **New in v0.39.0:** a **hi-score card** per year under *Insights, Hi-score* (distance, time, active days, longest streak, longest run and ride, biggest climb, steps; share one line as text) and a **season plan** under the race goal on the Plan tab (base, build, peak and taper counted back from the race, with the phase you are in and a range of kilometres a week). v0.38.0: a **map** of your own routes under *Insights, Map* (a personal heatmap on a plain grid, no map provider involved) and **same route** on an activity (how many times you did this route, your place by time and how you compare with your best and your average). v0.37.0: **monotony and strain** under *Insights, Form* (how alike the last seven days of training were, with the strain that goes with it) and a **sleep target for tonight** on Home (eight hours plus a little after a hard day and part of your sleep debt). v0.36.0: a **journal** (a card on Home to mark a day with alcohol, a late meal, late caffeine, a stressful day, travel or feeling ill; *Insights, Journal* then shows how the nights after those days compare with the others) and a **heads-up** on Home when your resting heart rate is up or your HRV is down for days in a row compared with your own last four weeks. v0.35.0: a **shape for each distance** under *Insights, Plan* (your last six weeks of running against what a typical plan asks for 5 km, 10 km, half and marathon, so a prediction for a far-off distance is flagged) and **VO2max from your runs** (an estimate from pace and heart rate with the race times it stands for). v0.34.0: the first ideas from the [app comparison](docs/APP-VERGELIJKING.md): **fitness, fatigue and form** (*Insights, Form*: the classic 42-day and 7-day picture of your training load, with a health-bar style verdict and three charts, no power meter needed) and a **training advice for today** on Home (rest, easy, as planned or a good day for a hard one, from readiness and form together). Both are OpenConnect's own estimates. v0.33.0: a choice of **style**: Retro (the pixel look), **Sleek** (round corners, thin lines, clear letters) or **Classic** (small corners, serif titles). v0.32.0: **24 colour themes** in four groups (retro screens such as Game Boy, Virtual Boy, Commodore 64 and Terminal; easy-on-the-eyes ones such as High contrast, Grey, Nord and Solarized; colourful ones), a choice between **pixel letters and clear letters** for readability, and a **choice of launcher icon** (eight). v0.31.0: a **look of its own**: the three designs and three styles are replaced by one, "Handheld", made to look like the screen of a handheld console and not like a stock Material app: pixel type, pixel-cut corners, flat panels with a hard shadow, pixel-art icons and logo, block bar charts and a block readiness bar (see **[docs/DESIGN.md](docs/DESIGN.md)**). The colours can still be changed. v0.30.0: **calmer, cheaper syncs**: a busy Garmin, Oura or Health Connect is asked again after a short wait instead of ending the sync, a `Retry-After` is honoured and automatic syncs pause after a rate limit, a sync that finds nothing new no longer makes the app reload and resend everything to Health Connect, an Oura login is no longer thrown away because Oura was busy, and Health Connect only gets new or edited activities (see **[docs/SYNC.md](docs/SYNC.md)**). v0.29.0: a **calendar** under Insights (the month as a grid with distance per day, paging, tap a day to open its activities) and **streaks** (current and longest run of days and of weeks with an activity). v0.28.0: **shoes**: a Shoes tab under Insights with the kilometres of each pair (default replace-after limit 800 km, adjustable), the newest pair of a sport that was in use on the day takes each activity, a pair can be picked per activity, and a notification says when a pair has reached its limit. v0.27.0: **heart-rate zones**: a Zones tab under Insights with your maximum heart rate (the highest measured, or set it yourself), the five zone limits, time in each zone over the last 7 days with a plain verdict (too hard, too much in the middle, all easy or a good mix), earlier weeks, and a zones card on every activity. v0.26.0: your **training plan as watch workouts**: the next 7 days of the plan are written as structured FIT workouts (warm-up, tempo or intervals with pace targets, cool-down) and copied over USB to `GARMIN/NewFiles`; not tried on a real fenix 8 yet. v0.25.0: **race predictions** (5 km, 10 km, half and full marathon from your best efforts, with Riegel's formula and an honest note on how far each one stretches) and a **race goal** with countdown, target pace and a verdict, on the Plan tab. v0.24.0: the way to the **Connect IQ store**: face, widget and glance can be set up in Garmin's Connect IQ app, `watch/build.sh --package` makes the `.iq` file, `watch/check-store.sh` checks it, the store texts are written (English and Dutch) and *Settings* can link to the store listing once it exists (see **[docs/CONNECT_IQ_STORE.md](docs/CONNECT_IQ_STORE.md)**). v0.23.0: a **glance and widget** for the watch as Connect IQ source (`build.sh --type widget`, not compiled yet) and a copy button in the app. v0.22.0: choose your fenix 8 model in the preview and get the exact file name and build command; `build.sh --all-colors` builds one face per colour. v0.21.0: the **watch faces** as Connect IQ source in `watch/` (Clean, Dashboard, Retro) with a build script; see **[docs/WATCH_FACES.md](docs/WATCH_FACES.md)**. They are not compiled yet. v0.20.0: a round **watch face preview** in *Settings* (Clean, Dashboard or Retro, five colours, four fields) with your own numbers. v0.19.0 added three designs: **three designs** to choose from under *Settings, Appearance, Design*: **Clean Pro** (calm cards,
a big readiness ring and a "My day" list), **Dashboard Dark** (tiles in two columns on black, bold bars) and **Retro
Pro** (the arcade look). A new install asks at the first start. v0.18.0 added seven colour themes, three styles and
watch faces over USB (the app no longer has the watch face since v0.43.0). The plan for what comes next is in **[docs/ROADMAP.md](docs/ROADMAP.md)**; see the
[release notes](docs/releases/v0.48.0.md).

<p><img src="docs/img/v0190-designs.png" alt="Clean Pro in light mode, Dashboard Dark, and a Clean Pro chart" width="690"></p>


**v0.8.0:** a **Scores** section on Health with four race-HUD tiles (readiness, sleep score, Body Battery, stress),
30-day charts for readiness and the watch's sleep score with the same plain-language explanation, and a new **sporty look**:
angular cut-corner cards, forward-leaning headlines, speed stripes and segmented score bars. See the
[release notes](docs/releases/v0.8.0.md).

<p>
<img src="docs/img/v080-home.png" alt="Home in the new sporty look" width="230">
<img src="docs/img/v080-health-scores.png" alt="Scores on the Health screen" width="230">
</p>

**v0.7.0:** every health chart now **says what it shows**: the period, the average with the best and worst day,
how the last week compares with the week before, whether that is in a common range, and what the number means. The app
also explains how to use it **next to Garmin Connect** and keeps a run once when it arrives from both the watch and your
Garmin account. See the [release notes](docs/releases/v0.7.0.md).

**v0.6.0:** **sync a Garmin watch straight over Bluetooth**, no cable, no account, no Garmin Connect: the app
pairs with the watch and downloads activities, steps, sleep, heart rate, stress and HRV files using Garmin's own protocol,
ported from Gadgetbridge. Written for the fenix 8 and other recent watches, **not yet tried on a real watch**. The project
is now AGPL-3.0. See **[docs/BLUETOOTH.md](docs/BLUETOOTH.md)** and the [release notes](docs/releases/v0.6.0.md).

**v0.5.0:** a new look and logo; sync that runs like Garmin Connect (when the app opens, in the background about
every 3 hours, pull to refresh, and a status pill that says when it last synced); **Gemini next to Claude**, each with
your own key entered in the app, for the reports and for a new **Coach** chat about your numbers; a readiness score, 17
trophies with a level, activity search and sorting, and CSV export. See **[docs/AI.md](docs/AI.md)**,
**[docs/INSIGHTS.md](docs/INSIGHTS.md)** and the [release notes](docs/releases/v0.5.0.md).

<p>
<img src="docs/img/v050-home.png" alt="Home with readiness, steps and the sync status" width="230">
<img src="docs/img/v050-trophies.png" alt="Trophies and level" width="230">
<img src="docs/img/v050-coach.png" alt="The AI coach" width="230">
</p>

*Rendered on a computer by the app's own UI tests, not captured on a phone.*

## What is in the box

| Area (server) | What it does |
|------|--------|
| **Activities** | List and detail, GPS route map, HR and elevation charts, kilometre splits, personal records |
| **Daily health** | Steps, intensity minutes, resting HR, sleep stages and score, stress, Body Battery |
| **Dashboard** | Today's cards with 30-day sparklines, weekly report with totals by sport and previous-week comparison |
| **Files** | Import and export **FIT, GPX and TCX** (CRC-checked FIT, safe XML parsing, idempotent re-import) |
| **AI reports** | Per activity and per week, written by Claude or Gemini, or by an offline template when no key is set |
| **Achievements** | Local badges ("Player One", "Seven-Day Streak", "Everest Climber", …) computed from your data |
| **Garmin** | OAuth 2.0 + PKCE, pull sync, push webhook, backfill client, and an offline fake Garmin for demos and tests |
| **Interfaces** | Web UI, JSON API with OpenAPI docs (`/api/docs`), CLI |

The full inventory of Garmin Connect functions, which ones the Developer API exposes, and what is
planned is in **[docs/FEATURES.md](docs/FEATURES.md)**. How it is built is in
**[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Quick start

Requires Python 3.11 or newer.

```bash
git clone https://github.com/Tufein/OpenConnect.git
cd OpenConnect
python -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"

openconnect sync --days 60     # loads demo data (mock mode is the default)
openconnect serve              # http://127.0.0.1:8000
```

Or with Docker (published on localhost only; OpenConnect has no login, see [Security](#security)):

```bash
docker compose up --build
```

### Command line

```bash
openconnect sync [--days N]                    # pull from Garmin (or the demo source)
openconnect activities [--limit N]             # list stored activities with their ids
openconnect export ID --format fit|gpx|tcx [-o FILE]
openconnect import run.fit ride.gpx ...        # FIT, GPX or TCX
openconnect report weekly [--week YYYY-MM-DD]  # AI (or template) report
openconnect report activity ID [--force]
openconnect serve [--host H] [--port P]
```

## Configuration

Everything is optional; see [`.env.example`](.env.example). Settings come from environment variables.

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

## Garmin access

Garmin's data APIs are not open to everyone. In short (details and sources in
[docs/FEATURES.md](docs/FEATURES.md)):

* **Personal API**: your own data, pull only, key created on the Garmin Connect website. Set
  `OPENCONNECT_GARMIN_MODE=personal` and `GARMIN_PERSONAL_KEY` to sync daily health, sleep, HRV,
  Pulse Ox, respiration and Body Battery, and the original FIT files of activities, without a developer
  agreement.
* **Starter / Enterprise** (business tiers): OAuth 2.0 with PKCE. Starter is push only, so register
  `https://<your-host>/api/garmin/webhook?token=<secret>` with Garmin. **This is what live mode
  implements today.**
* Register `<base-url>/api/garmin/callback` as the redirect URI, set
  `OPENCONNECT_GARMIN_MODE=live`, then use *Settings → Connect to Garmin*.

The Garmin endpoints and field names were taken from Garmin's public documentation and a few
third-party references because the full specification sits behind the developer portal login. They
are configurable and marked **verify** in the docs until they have been exercised against real
credentials.

## AI reports and privacy

Press *Generate report* on an activity or a week. OpenConnect builds a small structured summary
(distance, pace, splits, HR trend, weekly totals, comparison with previous data) and sends **only
that** to the model. Free text such as activity names travels inside a JSON data block that the
system prompt marks as data, not instructions. Reports are cached by a hash of their inputs, so
unchanged data never costs a second call. Without `ANTHROPIC_API_KEY` the built-in offline template
is used and nothing leaves your machine.

Check Garmin's Developer Program Agreement before sending Garmin-derived data to any third party.

## Security

OpenConnect has an **optional single-password login**: set `OPENCONNECT_PASSWORD` to require a signed,
seven-day cookie for every page and API (only `/healthz` and static files stay public). Without a password
the original model applies: keep it on `localhost` or a trusted network, or put a reverse proxy with
authentication in front of it. Built in: loopback bind by default, `Host` allow-list,
refusal of cross-origin POSTs, strict Content-Security-Policy, size-limited and validated uploads,
XXE-safe XML parsing, and an optional webhook secret. OAuth tokens are stored in the SQLite file in
plain text, so protect that file. More in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Development

The project is developed test first: each layer's tests were written before its code, and nothing
in the suite needs the network, a Garmin account or an API key.

```bash
pytest                                  # unit + integration tests
pytest --cov=openconnect                # with coverage
ruff check src tests && ruff format --check src tests
```

```
src/openconnect/
  domain.py        models and pure helpers
  garmin/          OAuth/PKCE, HTTP source, mapper, ingest, offline fake
  formats/         FIT, GPX, TCX
  services/        sync, dashboard, records, achievements
  ai/              facts, prompts, providers (Claude + template), report service
  web/             FastAPI app, JSON API, HTML UI, SVG charts
  storage/         SQLite repository
  cli.py           command line
tests/             unit/ and integration/
```

CI (GitHub Actions) runs lint, format check and the tests on Python 3.11 to 3.13, builds and
smoke-tests the wheel, and builds the Docker image. A second workflow tests, lints and builds the Android
app. A release (pushing a `v*` tag, or running the Release workflow on `main`) builds the APK, the wheel
and the sdist and publishes them together.

The Android project lives in `android/` (`core/` is plain Kotlin, `app/` is the Compose app); see
[docs/ANDROID.md](docs/ANDROID.md) for how to build and test it.

## Roadmap

Trying the watch routes on real devices, Health Connect as a source, Garmin Personal API sync in the Android app, Personal API support in the server, the rest of the Health API data types (HRV, Pulse Ox, respiration, body
composition, …), laps and interactive charts, Ping/Pull callbacks and backfill UI, Training and
Courses API publishing, monthly reports, and optional authentication. See
[docs/FEATURES.md](docs/FEATURES.md#7-roadmap).

## License

[AGPL-3.0-or-later](LICENSE) since v0.6.0 (MIT up to v0.5.0). The Garmin Bluetooth sync is ported from
[Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) (AGPL-3.0), see [NOTICE](NOTICE).
Garmin and Garmin Connect are trademarks of Garmin Ltd.

## Documentation

[Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Roadmap](docs/ROADMAP.md) · [Release notes](docs/releases)
