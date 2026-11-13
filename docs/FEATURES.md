# Garmin Connect feature inventory

This document lists what Garmin Connect (web and app) does, which of those functions Garmin exposes
through the official **Garmin Connect Developer Program** APIs, and where each stands in
OpenConnect.

*Researched 2026-11-12 from Garmin's developer pages and secondary reporting. Garmin's detailed
technical specifications sit behind the developer portal login, so a few details are marked
**verify** and must be confirmed against the portal spec once API credentials are granted.*

**Status legend**

| Mark | Meaning |
|------|---------|
| ✅ | Implemented in v0.1.0 and covered by tests |
| 🟡 | Partly implemented (see notes) |
| ⏳ | Possible through the API, planned |
| 🚫 | Not offered by the Developer API (or out of scope for a self-hosted single-user app) |

## 1. The Developer Program at a glance

| API | Direction | What it provides | Access |
|-----|-----------|------------------|--------|
| **Health API** | Garmin → app | All-day summaries: steps, intensity minutes, calories, heart rate, sleep, stress, Body Battery, Pulse Ox, respiration, body composition, blood pressure, epoch summaries, user metrics (VO2 max, fitness age; **verify** exact contents) | Personal, Starter, Enterprise |
| **Activity API** | Garmin → app | Activity summaries as JSON and full FIT files (GPS, HR, power, cadence, laps) for 30+ activity types; manually entered activities | Personal, Starter, Enterprise |
| **Women's Health API** | Garmin → app | Menstrual cycle and pregnancy tracking | Personal, Starter, Enterprise |
| **Training API** | app → Garmin | Publish structured workouts and training plans to the user's Garmin Connect calendar and devices | Starter, Enterprise only |
| **Courses API** | app → Garmin | Publish courses (routes) to the user's devices | Starter, Enterprise only |
| **Golf API** | Garmin → app | Golf rounds | Enterprise only |

| Tier | Delivery | History | Users | Notes |
|------|----------|---------|-------|-------|
| **Personal** | Pull only | n/a | Your own account | Create an API key on the Garmin Connect website, exchange it for access tokens. Cannot send workouts or courses. |
| **Starter** (business) | **Push only** (webhook) | No backfill | Limited (reported cap: 1,000) | Apply through Garmin's request form. |
| **Enterprise** (business) | Push or Ping/Pull | One month or more via backfill | Unlimited | Multi-project support, enhanced beat-to-beat data, support. |

* **Authentication**: OAuth 2.0 for the business tiers (authorization-code flow with PKCE, S256).
  Users pick the data types on Garmin's consent screen; there are no OAuth scopes in the request.
* **Endpoints used by OpenConnect** (from Garmin's OAuth2 PKCE specification): authorize at
  `https://connect.garmin.com/oauth2Confirm`, token at
  `https://connectapi.garmin.com/di-oauth2-service/oauth/token`, data under
  `https://apis.garmin.com/wellness-api/rest/…`. Both OAuth URLs and the API base are configurable
  because third-party code samples disagree on the token host (**verify**).
* **Program status**: secondary reporting (the5krunner, Oct 2026) says Garmin paused new
  applications for several months and then reopened them, together with the Starter/Enterprise
  split and the Personal API. Check the developer portal for the current state before planning
  around it.
* **Data fidelity**: the same reporting says Garmin strips some undocumented fields (VO2 max,
  recovery time, performance condition) from files delivered through the API; they remain in files
  downloaded manually from Garmin Connect. OpenConnect therefore treats imported files as a
  first-class second data source.

**Practical path for a self-hosted, single-user app:** the *Personal API* fits best (no business
agreement, your own data). OpenConnect v0.1.0 implements the business-tier OAuth2 + push + pull
flow; Personal API support is the first item on the roadmap (§7).

## 2. Activities

| Garmin Connect function | API | OpenConnect | Notes |
|-------------------------|-----|-------------|-------|
| Activity list and summaries | Activity API | ✅ | Distance, time, elevation, HR, calories, device, sport |
| Activity detail (GPS, HR, cadence, power samples) | Activity API (`activityDetails`, FIT) | ✅ | JSON samples are mapped into a track |
| Route map | derived from GPS | 🟡 | Inline SVG route with start/finish markers; map tiles are not bundled (privacy, offline) |
| Charts (HR, elevation) | derived | 🟡 | Sparklines on the activity page; full interactive charts planned |
| Laps and splits | Activity API (laps in details, **verify**) | 🟡 | Kilometre splits are computed for reports; a lap table is planned |
| 30+ activity types | Activity API | 🟡 | 8 normalized types (running, cycling, swimming, walking, hiking, strength, yoga, other); the rest map to *other* |
| Manually entered activities | Activity API (`manuallyUpdatedActivities`) | ⏳ | |
| Export FIT / GPX / TCX | FIT via API; GPX/TCX are Garmin conversions | ✅ | Exported from stored data; independent FIT decoder validates the encoder in tests |
| Import FIT / GPX / TCX | n/a | ✅ | Content-sniffed, CRC-verified FIT, XXE-safe XML, idempotent re-import |
| Upload an activity *to* Garmin | none in the Developer Program | 🚫 | The Activity API is read-only |
| Edit or delete an activity in Garmin | none | 🚫 | OpenConnect can delete its own copy |
| Personal records | not exposed | 🟡 | Computed locally per sport (longest, most climbing, fastest pace); best efforts (fastest 5K) planned |
| Segments and live segments | not exposed | 🚫 | |
| Training Effect, Training Load, Recovery Time, Performance Condition | partly in FIT; may be stripped | ⏳ | Read from imported FIT files where present |

## 3. Daily health

| Garmin Connect function | API | OpenConnect | Notes |
|-------------------------|-----|-------------|-------|
| Steps, distance, floors, active calories, goal | Health API `dailies` | ✅ | |
| Intensity minutes | `dailies` | ✅ | Vigorous minutes count double, as in Garmin Connect |
| Heart rate: resting, min, max | `dailies` | ✅ | All-day HR time series planned |
| Sleep: duration, stages, score | `sleeps` | ✅ | Sleep SpO2/respiration series planned |
| Stress: average and maximum | `dailies` | ✅ | Stress time series planned |
| Body Battery | `stressDetails` | ✅ | Daily high and low; time series planned |
| HRV (nightly, status) | `hrv` | ⏳ | |
| Respiration | `allDayRespiration` | ⏳ | |
| Pulse Ox (SpO2) | `pulseox` | ⏳ | |
| Body composition (weight, body fat, BMI) | `bodyComps` | ⏳ | |
| Blood pressure | `bloodPressures` | ⏳ | |
| Skin temperature | `skinTemp` | ⏳ | |
| Health snapshot | `healthSnapshot` | ⏳ | |
| VO2 max, fitness age | `userMetrics` | ⏳ | |
| Women's health (cycle, pregnancy) | Women's Health API | ⏳ | Highly sensitive; will be opt-in |
| Training Readiness, Training Status, Endurance/Hill score, Race predictor | not exposed | 🚫 | |
| Hydration, nutrition | not exposed | 🚫 | |

## 4. Training and planning

| Garmin Connect function | API | OpenConnect | Notes |
|-------------------------|-----|-------------|-------|
| Create a workout and send it to the device | Training API (business only) | ⏳ | Needs Starter or Enterprise access |
| Training plans on the calendar | Training API | ⏳ | |
| Garmin Coach adaptive plans | not exposed | 🚫 | |
| Courses (create and send) | Courses API (business only) | ⏳ | GPX/FIT import already produces the track data |
| Events and race goals | not exposed | 🚫 | |

## 5. Dashboard, reports and insights

| Garmin Connect function | API | OpenConnect | Notes |
|-------------------------|-----|-------------|-------|
| Home dashboard with daily cards | local | ✅ | Steps, resting HR, sleep, Body Battery, stress with 30-day sparklines |
| Weekly report | local | ✅ | Totals by sport, health averages, previous-week comparison |
| Monthly/yearly reports | local | ⏳ | |
| Trends over time | local | 🟡 | 30-day trends; longer ranges planned |
| Insights (Connect+ *Active Intelligence*, paid) | not exposed | ✅ | **AI reports** per activity and per week, written by Claude (or an offline template) |
| Badges and challenges | not exposed | 🟡 | Local **achievements** computed from your data ("Player One", "Everest Climber", …); Garmin's own badges are not available |
| Custom goals | partly (`stepsGoal`) | 🟡 | Step goal is read from Garmin; custom goals planned |

## 6. Social, devices and platform

| Garmin Connect function | API | OpenConnect | Notes |
|-------------------------|-----|-------------|-------|
| Connections, groups, news feed, leaderboards | not exposed | 🚫 | Out of scope for a single-user, self-hosted app |
| LiveTrack and sharing | not exposed | 🚫 | |
| Device list, settings, software updates | not exposed | 🟡 | Device names are derived from activities; settings and updates stay in Garmin's apps |
| Connect IQ store and apps | separate Connect IQ platform | 🚫 | |
| Device → cloud sync | handled by Garmin | ✅ | OpenConnect reads from Garmin's cloud, not from the watch |
| OAuth 2.0 PKCE, token refresh | Developer Program | ✅ | Refresh tokens are rotated and stored; a rejected refresh disconnects cleanly |
| Push webhook (Starter requires it) | Developer Program | ✅ | `POST /api/garmin/webhook`, optional shared-secret token |
| Ping/Pull callbacks | Enterprise | 🟡 | Notifications are counted but not yet followed |
| Pull (by upload time, 24 h windows) | Enterprise / Personal | ✅ | Chunked, token refresh on 401, rate-limit aware |
| Backfill of history | Enterprise | 🟡 | Client method implemented and tested; no UI yet |
| Offline demo mode | n/a | ✅ | Deterministic Garmin-shaped fake data, no credentials needed |

## 7. Roadmap

1. **Personal API support** (exchange the personal API key for access tokens at
   `personal.connectapi.garmin.com`, then pull) so a single user needs no business agreement.
2. Remaining Health API data types: HRV, respiration, Pulse Ox, body composition, blood pressure,
   skin temperature, user metrics, and the time series behind stress and Body Battery.
3. Lap table, interactive charts and best efforts (fastest 1K/5K/10K) from tracks.
4. Follow Ping/Pull `callbackURL`s; backfill UI.
5. Training API (workouts and plans) and Courses API publishing for business-tier users.
6. Monthly and yearly reports, optional app-level authentication. (Custom weekly goals shipped in v0.9.0; the full plan is in [ROADMAP.md](ROADMAP.md).)

## 8. Legal and privacy notes

* Using the Developer Program requires accepting Garmin's *Connect Developer Program Agreement*;
  read it before sending Garmin data to a third party. In particular, check whether it allows the
  AI-report feature to send data summaries to an AI provider for your use case.
* Health data is special-category data under GDPR. OpenConnect stores everything locally in SQLite
  and only sends a structured summary to Claude when you press *Generate report*.
* OpenConnect is an independent project and is not affiliated with or endorsed by Garmin. Garmin
  and Garmin Connect are trademarks of Garmin Ltd.

## Sources

* Garmin Connect Developer Program overview: <https://developer.garmin.com/gc-developer-program/overview/>
* Program FAQ: <https://developer.garmin.com/gc-developer-program/enterprise/program-faq/>
* Health API: <https://developer.garmin.com/gc-developer-program/enterprise/health-api/>
* Activity API: <https://developer.garmin.com/gc-developer-program/enterprise/activity-api/>
* Training API: <https://developer.garmin.com/gc-developer-program/enterprise/training-api/>
* Courses API: <https://developer.garmin.com/gc-developer-program/enterprise/courses-api/>
* Personal API: <https://developer.garmin.com/gc-developer-program/personal/personal-api>
* OAuth 2.0 PKCE specification (PDF): <https://developerportal.garmin.com/sites/default/files/OAuth2PKCE.pdf>
* Open Wearables, Garmin integration notes: <https://openwearables.io/docs/providers/garmin-api-integration.md>
* The5krunner, "Garmin personal API gives athletes their own data back" (Oct 2026): <https://the5krunner.com/2026/10/22/garmin-personal-api/>
