# Oura Ring

Since v0.16.0 OpenConnect can read an Oura ring through Oura's API (v2). Since v0.17.0 the ring has **its own tab**, shown
only to someone who switched the Oura Ring on (at the first start, or later under *Settings, Your devices*), and its data
is **kept apart** from the watch's. **Not tried against the real Oura service yet**:
the code follows Oura's published OpenAPI description (version 1.43) and is tested against a fake Oura.

## What you need

- An **active Oura Membership** (Oura requires it for API access with a Gen3 ring or Oura Ring 4).
- An **Oura application of your own**. Oura switched personal access tokens off in December 2025, so every app logs in
  with OAuth2. Making one takes a minute:
  1. Sign in at <https://cloud.ouraring.com/oauth/applications> and create a new application.
  2. Enter `openconnect://oura` as its redirect address (exactly that; the app shows it too).
  3. Copy the client ID and client secret into the **Oura** tab in OpenConnect.

## The login

*Log in at Oura* opens your browser at Oura's consent page. The app asks for `daily workout spo2 personal` and uses
PKCE (S256) and a random state. After you allow it, Oura sends the browser to `openconnect://oura?code=…`, which
opens OpenConnect again; it checks the state and trades the code for tokens. If the browser does not come back, copy the
address it ends on and paste it in the app.

The client secret, the access token and the refresh token are kept encrypted (Android Keystore), like the AI and Garmin
keys, and are only sent to `api.ouraring.com`. Oura's refresh tokens work once: every refresh stores the new pair.

## What is read

| Oura collection | Shown on the Oura tab as |
| --- | --- |
| `daily_readiness` | the readiness score and the skin temperature difference |
| `daily_sleep` | the sleep score |
| `daily_activity` | the activity score, steps and active calories |
| `sleep` (the longest `long_sleep`/`sleep` of each day) | last night (asleep, deep, light, REM, awake), lowest heart rate, HRV, breathing |
| `daily_spo2` | average blood oxygen |
| `workout` | the ring's workouts |

Everything goes into its own table (`oura_days`, one row per day) and is **never mixed** into the watch's days, nights
or activities, so nothing is counted twice and the Health, Home and Insights screens stay the watch's. The tab shows the
latest day's three scores (tap one to jump to its chart), last night, 30-day charts you can read by touch, and the last
ten workouts. Backups include the ring's days.

Coming from v0.16.0, which filled the ring's numbers into empty fields of the watch's days: the update removes the
ring's workouts from the activity list. Numbers it filled into the watch's days cannot be told apart and stay.

## When it syncs

Only while the Oura Ring is switched on. When the app comes to the front, every 10 minutes while it is open, and about every 15 minutes in the background (the
shortest period Android allows; it may be later in battery saver or Doze). An automatic sync reads the last 2 days, or
7 days after a pause of 12 hours or more; *Sync now* and the first sync after the login read 30 days.
