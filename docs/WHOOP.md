# WHOOP band

Since v0.49.0 OpenConnect can read a WHOOP band through WHOOP's developer API (v2), built the same way as the Oura Ring
link ([OURA.md](OURA.md)): its own tab, shown only to someone who switched the WHOOP band on (at the first start, or later
under *Settings, Your devices*), and its data **kept apart** from the watch's and the ring's. **Not tried against the real
WHOOP service yet**: the code follows WHOOP's published developer documentation and is tested against a fake WHOOP.

## What you need

- A **WHOOP membership** with a band (4.0 or 5.0/MG). WHOOP lets members use the developer platform for their own data.
- A **WHOOP application of your own**. Making one takes a few minutes:
  1. Sign in at <https://developer-dashboard.whoop.com>. WHOOP asks for a team first; make one for yourself.
  2. Create an application and tick the scopes `read:recovery`, `read:cycles`, `read:workout` and `read:sleep`.
  3. Enter `openconnect://whoop` as a redirect URL (exactly that; the app shows it too).
  4. Copy the client ID and client secret into the **WHOOP** tab in OpenConnect.

WHOOP may cap the number of members an application that has not been approved for general use can serve; for one person's own
data that should be no obstacle (not checked).

## The login

*Log in at WHOOP* opens your browser at WHOOP's consent page
(`https://api.prod.whoop.com/oauth/oauth2/auth`). The app asks for `offline read:recovery read:cycles read:workout
read:sleep` and sends a random state of 22 characters (WHOOP wants at least eight). `offline` is what makes WHOOP hand out a
refresh token, so the login lasts. WHOOP does not document PKCE, so the app does not send it. After you allow it, WHOOP
sends the browser to `openconnect://whoop?code=…`, which opens OpenConnect again; it checks the state and trades the code
for tokens. If the browser does not come back, copy the address it ends on and paste it in the app.

The client secret, the access token and the refresh token are kept encrypted (Android Keystore), like the other keys, and
are only sent to `api.prod.whoop.com`. **WHOOP's refresh tokens work once**: every refresh stores the new pair, and the
refresh is serialised so that two refreshes at once cannot end the login.

## What is read

| WHOOP endpoint | Shown on the WHOOP tab as |
| --- | --- |
| `GET /v2/recovery` | recovery score (0 to 100), resting heart rate, HRV (rMSSD), blood oxygen, skin temperature |
| `GET /v2/cycle` | day strain (0 to 21), energy (kJ shown as kcal), average and highest heart rate, steps if the cycle has them |
| `GET /v2/activity/sleep` | last night: asleep, deep (slow wave), light, REM, awake, sleep need, performance, consistency, efficiency, breathing |
| `GET /v2/activity/workout` | the band's workouts: sport, length, strain, heart rate, energy, distance |

Everything goes into its own table (`whoop_days`, one row per day) and is **never mixed** into the watch's days, nights or
activities, so nothing is counted twice and the Health, Home and Insights screens stay the watch's. The tab shows the
latest day's recovery, strain and sleep (tap one to jump to its chart), last night, 30-day charts you can read by touch,
and the last ten workouts. The Day tab of Insights can show a WHOOP day as a source of its own, WHOOP's recovery standing
in for readiness and its sleep performance for the sleep score. Backups include the band's days. Demo data has a month of
fake WHOOP days.

**Workouts** are listed among the activities with a mark for the band, and are never stored as activities: totals,
trophies and records stay the watch's. A workout the watch recorded too is recognised (same sport, overlapping time) and
listed once.

### How a day is cut

WHOOP does not work in calendar days but in *cycles* (from one wake-up to the next) and nights. OpenConnect labels a day
with **the date the main night ended on**, on the clock of the place it was recorded in (WHOOP's `timezone_offset`), and takes
that night's recovery and cycle by `cycle_id`. Naps are skipped. A workout belongs to the date it started on. That mirrors
how Oura and Garmin label a night. It is an assumption, pinned by tests, and it has not been checked against a real account.

A record WHOOP has not scored yet (`score_state` other than `SCORED`, for instance `PENDING_SCORE` during the first hours)
is left out until a later sync finds it scored.

## When it syncs

Only while the WHOOP band is switched on. When the app comes to the front, every 10 minutes while it is open, and about
every 15 minutes in the background (the shortest period Android allows; it may be later in battery saver or Doze). An
automatic sync reads the last 2 days, or 7 days after a pause of 12 hours or more; *Sync now* and the first sync after the
login read 30 days. A sync writes only days that changed. WHOOP allows 100 requests a minute and 10,000 a day and returns
25 records a page; a sync of 30 days needs about two pages per kind of data. When WHOOP answers 429, the app waits for as long
as WHOOP asks (`Retry-After`, or the time until `X-RateLimit-Reset`; half an hour when it does not say, never less than a
minute) before it tries again, and says so on the tab.

## Not verified

- No WHOOP account, band or application was available, so **nothing was run against api.prod.whoop.com**. The tests use a fake
  that answers with JSON shaped like WHOOP's documentation.
- Field names and the `/v2` paths come from WHOOP's documentation; WHOOP has changed versions before (v1 was retired),
  so a rename is the first thing to suspect if a sync fails with "unexpected answer".
- The day cut above, WHOOP's exact scope names and whether WHOOP wants PKCE or a `client_secret_post` token request are the
  open questions. The tab shows the exact problem (login, forbidden scopes, rate limit, network, server) and the Sync log
  under *Settings* keeps the last attempts.
