# Hume Band

Since v0.50.0 OpenConnect can show a Hume Band, with its own tab (shown only to someone who switched the Hume Band on, at
the first start or later under *Settings, Your devices*) and its data **kept apart** from the watch's and the other bands'.
It is the third band after the Oura Ring ([OURA.md](OURA.md)) and WHOOP ([WHOOP.md](WHOOP.md)), but it is built differently,
because **Hume has no public interface**: no developer program, no OAuth, no API keys. What exists is this: the Hume Health
app (Android package `com.elink.fittrackhealth.pro`, published by Hume Health Corp) writes what the band measures to
**Health Connect**, the shared health store of Android, and OpenConnect reads it from there. **Not tried with a real band
yet**: the code is tested against a fake Health Connect.

## What you need

- A phone with **Health Connect**: part of the system settings on Android 14 and newer, a free app from Google Play on Android
  13 and older.
- The **Hume Health app**, with Health Connect switched on in its settings (the Hume app must be allowed to *write*). The
  Hume app only hands data over after it has processed it, so numbers can come a while after the band measured them.

No account, no key, nothing to enter in OpenConnect. The tab asks Health Connect for permission once.

## What is read

| Health Connect record | Shown on the Hume tab as |
| --- | --- |
| `SleepSessionRecord` (with stages when the app writes them) | last night: asleep, deep, light, REM, awake |
| `StepsRecord` | steps per day (Health Connect adds the records up) |
| `HeartRateRecord` | average and highest heart rate of the day, and the **lowest of the night** |
| `RestingHeartRateRecord` | resting heart rate |
| `HeartRateVariabilityRmssdRecord` | HRV, the mean of the night's readings |
| `OxygenSaturationRecord` | blood oxygen, the mean of the day |
| `ActiveCaloriesBurnedRecord` | active energy of the day |
| `ExerciseSessionRecord` (+ distance and energy of the session) | the band's workouts |

**Hume's own scores (its health score, recovery, stress and so on) do not exist in Health Connect**, so none is shown or
invented: no readiness, no sleep score. Which of the records above the Hume app really writes is not confirmed: an
integration vendor lists steps, heart rate, sleep and active energy as written by the app; HRV, resting heart rate, blood
oxygen and exercise may or may not arrive. Whatever is missing is simply not shown. The permission dialog asks for all of them;
steps, sleep and heart rate are required, the rest is read when allowed.

Everything goes into its own table (`hume_days`, one row per day) and is **never mixed** into the watch's days, nights or
activities. The Day tab of Insights can show a Hume day as a source of its own (sleep, steps, resting and lowest heart rate,
HRV, blood oxygen). Workouts are listed among the activities with a mark for the band and are never stored as activities;
one the watch recorded too is recognised and listed once. Backups include the band's days. Demo data has a month of fake days.

### Only the Hume app's data

Health Connect holds data from every app on the phone (your watch's app, a step counter, OpenConnect's own exports).
OpenConnect asks Health Connect **which apps wrote data** in the period and reads **only the records of the Hume app**, using
Health Connect's data-origin filter, so another app's steps or heart rate are never taken for the band's. The Hume app is
recognised by its package name (`com.elink.fittrackhealth…`, or any package with "hume" in it). If the band's app has another
name, or Health Connect holds nothing from it, the tab says so and lists the apps that did write data; picking one reads that app
instead (and remembers the choice). Health Connect can hide one app's steps behind a higher-priority app's in a total, so every
kind of record (steps, sleep, heart rate, energy, exercise) is asked for on its own: the Hume app is found as soon as it wrote
any one of them.

### How a day is cut

Health Connect gives times, not days, so the days are the **phone's own calendar days**. A night belongs to the morning it
ended on (if there are two sessions that morning the one with more sleep wins); heart rate, steps, oxygen and workouts
belong to the day they were measured or started on; the lowest heart rate and the HRV are those of the night's own hours.
Pinned by tests, not checked against a real band.

## When it syncs

Only while the Hume Band is switched on and Health Connect lets OpenConnect read. When the app comes to the front, every 10
minutes while it is open, and about every 15 minutes in the background (the shortest period Android allows). An automatic sync
reads the last 2 days, or 7 days after a pause of 12 hours or more; *Sync now* and the first sync read 30 days (Health Connect
lets an app read back 30 days from the moment it was first allowed). Reading in the background needs the extra
*read in background* permission, which the dialog asks for on phones whose Health Connect has that feature; without it the
tab still syncs while the app is open. A sync writes only days that changed. An automatic sync on a phone that never allowed
OpenConnect to read stays quiet; the *Sync now* button says what is wrong.

## Not verified

- No Hume Band, Hume app or Android phone with Health Connect was available: **nothing ran against a real Health Connect**.
  The code that talks to it (`AndroidHumeGateway`) compiles against the Health Connect client library and is otherwise
  untested; everything around it is tested with a fake.
- The Android package name of the Hume app comes from app-directory listings, not from a phone. If it is wrong the tab shows
  the apps Health Connect has and lets you pick the right one.
- Which record types the Hume app writes, and how often and how late, is unknown (see above).
- Whether the *read in background* permission is handed out the way the documentation says is unknown.
