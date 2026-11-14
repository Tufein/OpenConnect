# Syncing: what runs when, what the APIs allow, and what OpenConnect does about it

Written for v0.30.0 after reading the sync code and the API documentation. A Dutch summary is at the end.

## What runs when

| Source | When it runs | What it asks |
| --- | --- | --- |
| Garmin account (Personal API) | when the app opens (if the last sync is over a minute old), every 10 minutes while it is open, about every 15 minutes in the background (WorkManager) | 6 data types for the last 7 days (one request each) plus the activity list, plus a download per new activity (at most 30 per sync) |
| Oura Ring | the same moments | 6 collections (`sleep`, `daily_sleep`, `daily_readiness`, `daily_activity`, `daily_spo2`, `workout`) for the last 2 days (7 after 12 hours without a sync, 30 for the first one) |
| Health Connect | after every sync that changed data (only when the switch is on) | writes the activities as sessions with a stable client id |
| Garmin watch over Bluetooth | every 3 hours in the background, only when switched on | the watch's file directory |

## What the APIs allow

- **Garmin Personal API**: pull only, own data only, a 24-hour bearer token minted from the key. No published rate limits and
  no way to ask "has anything changed?". Push and ping notifications and backfill belong to the Health and Activity APIs for
  Starter and Enterprise partners: they need an approved Garmin developer account and a server Garmin can call. They are not
  available to a personal key.
- **Oura API v2**: Oura's own spec mentions a limit of 5,000 requests per 5 minutes, applied per access token and per application;
  third-party notes say a `429` carries `Retry-After`. Webhooks (events `create`, `update` and `delete` for data types such as
  `sleep` and `daily_activity`) arrive about 30 seconds after the Oura phone app synced, and need a public HTTPS callback. The ring's
  data reaches the API only after the phone app uploaded it, so sleep and readiness can lag until that app is opened. Refresh tokens
  are single-use: lose the newest one and the person must log in again.
- **Health Connect** (Android's guide, "Synchronize data"): feed only what was inserted or edited since the last time, with a
  `clientRecordId`; a record is replaced only when its `clientRecordVersion` is higher; Changes tokens expire after 30 days unused;
  apps can only read in the foreground unless the person grants Background read.

## What v0.30.0 does about it

1. **A hiccup no longer ends a sync.** A dropped connection, `429`, `500`, `502`, `503` or `504` is asked again after 1.5 and then
   5 seconds (`core/Retry.kt`, `RetryPolicy.standard()`), for Garmin and Oura. A `Retry-After` up to 30 seconds is waited out; a
   longer one is handed back, because blocking a background job for minutes helps nobody. 4xx answers other than 429 are never repeated.
2. **Automatic syncs pause after a rate limit** for what the service asked (`Retry-After`), otherwise 30 minutes (never under a minute
   or over six hours). The Sync button still works. Kept in memory: a restarted app may try once more.
3. **A sync that finds nothing new writes nothing.** Garmin used to rewrite every day it was asked about and count it as updated, so
   *every* sync told the app that data changed, which reloaded the screens and sent every activity to Health Connect again.
4. **An Oura login survives a busy Oura.** A refresh that was answered `429`, `5xx` or any other 4xx than 400/401 used to count as
   "login expired" and deleted the tokens. Only a refused refresh token (400 `invalid_grant`, 401 `invalid_client`) ends it now.
5. **Health Connect only gets what is new or edited.** A ledger of what was sent (client id and a fingerprint of the content) lets the
   automatic export skip the rest; the button still sends everything. Every batch is remembered at once, so a failure halfway costs only
   the batches after it. Edited activities now carry a rising `clientRecordVersion`, so Health Connect actually replaces them.

## What is not done, and why

- **Webhooks for Oura** would make Oura sync nearly instant and remove most polling, but need an HTTPS server that is always on and a
  place to keep the app's client secret. That is a decision for the owner (see the answer in the project thread).
- **Garmin push or ping** needs Garmin's partner program. The Personal API cannot do it.
- **Fewer Garmin requests**: `steps`, `stress` and `bodyBattery` overlap with the daily summary, but the app merges both on purpose
  (the larger counter wins). Dropping one needs a live comparison with a real account first.
- **Retrying activities that were stored from their summary because the file download failed** needs a marker so activities that
  never have a file (manual entries) are not retried for ever.
- **Health Connect Changes API** for importing steps and sleep: the import is a button, reads 30 days with `aggregate` (which
  de-duplicates across apps) and is cheap; a change token would add 30-day expiry handling for little gain.

## Not verified

Nothing here ran against the real Garmin, Oura or Health Connect services or on a phone: the rules are covered by unit tests with
in-memory fakes. The retry waits and the cool-down are chosen conservatively because Garmin publishes no limits.

## Samenvatting (Nederlands)

Een kort haperende verbinding of een drukke server beëindigt een sync niet meer: er wordt na 1,5 en 5 seconden opnieuw gevraagd en
een `Retry-After` wordt gerespecteerd. Na een rate limit wachten de automatische syncs (Garmin en Oura), de knop blijft werken. Een
sync zonder nieuwe gegevens schrijft niets meer weg en laat de app dus niet meer alles herladen en opnieuw naar Health Connect sturen.
Een Oura-login wordt niet meer weggegooid omdat Oura even druk was. Health Connect krijgt alleen nieuwe of gewijzigde activiteiten.
