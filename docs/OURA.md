# Oura Ring

Since v0.16.0 OpenConnect can read an Oura ring through Oura's API (v2). **Not tried against the real Oura service yet**:
the code follows Oura's published OpenAPI description (version 1.43) and is tested against a fake Oura.

## What you need

- An **active Oura Membership** (Oura requires it for API access with a Gen3 ring or Oura Ring 4).
- An **Oura application of your own**. Oura switched personal access tokens off in December 2025, so every app logs in
  with OAuth2. Making one takes a minute:
  1. Sign in at <https://cloud.ouraring.com/oauth/applications> and create a new application.
  2. Enter `openconnect://oura` as its redirect address (exactly that; the app shows it too).
  3. Copy the client ID and client secret into *Settings, Oura Ring* in OpenConnect.

## The login

*Log in at Oura* opens your browser at Oura's consent page. The app asks for `daily workout spo2 personal` and uses
PKCE (S256) and a random state. After you allow it, Oura sends the browser to `openconnect://oura?code=…`, which
opens OpenConnect again; it checks the state and trades the code for tokens. If the browser does not come back, copy the
address it ends on and paste it in the app.

The client secret, the access token and the refresh token are kept encrypted (Android Keystore), like the AI and Garmin
keys, and are only sent to `api.ouraring.com`. Oura's refresh tokens work once: every refresh stores the new pair.

## What is read

| Oura collection | Goes into |
| --- | --- |
| `daily_activity` | steps, active calories, walking distance, intensity minutes (medium and high activity time) |
| `sleep` (the longest `long_sleep`/`sleep` of each day) | the night (total, deep, light, REM, awake), resting heart rate, HRV, breathing |
| `daily_sleep` | the sleep score |
| `daily_spo2` | average blood oxygen |
| `workout` | activities (`oura-<id>`), skipped when the watch has one starting within 2 minutes |

Oura only **fills gaps**: a value your watch gave is never overwritten. The switch *Use the ring's sleep instead of the
watch's* lets the ring's night win. Oura's readiness and stress scores are not copied; OpenConnect works out its own.

## When it syncs

When the app comes to the front, every 10 minutes while it is open, and about every 15 minutes in the background (the
shortest period Android allows; it may be later in battery saver or Doze). An automatic sync reads the last 2 days, or
7 days after a pause of 12 hours or more; *Sync now* and the first sync after the login read 30 days.
