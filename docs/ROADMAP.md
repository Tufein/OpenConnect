# OpenConnect roadmap

Where OpenConnect is, and where it goes next. Written 2026-11-13 after v0.8.0. Items are ordered by value for one person
tracking their own Garmin data, and by how well they can be tested without a watch in hand.

**Legend:** ✅ shipped · 🚧 in this release · 🔜 next · 💡 later · ❓ needs something only the owner can supply

## Shipped

| Version | What |
|---------|------|
| v0.1.0 | Python server, FIT/GPX/TCX import and export, dashboard, AI reports |
| v0.2.0 | Standalone Android app |
| v0.3.0 | Watch over USB, live heart rate over Bluetooth |
| v0.4.0 | Garmin account via the Personal API, folder import, Bluetooth reconnect |
| v0.5.0 | New look, background sync, Gemini and Claude, coach, readiness, trophies |
| v0.6.0 | Bluetooth sync of a Garmin watch (ported from Gadgetbridge), AGPL-3.0 |
| v0.7.0 | Charts explain themselves, living next to Garmin Connect |
| v0.8.0 | Scores on Health, sporty look |
| v0.9.0 | Weekly goals, training load, roadmap |
| v0.10.0 | Backup and restore, sync log, data sources |

## v0.9.0: training, with goals ✅

1. **Weekly goals.** Intensity minutes, distance and activities per week, with defaults from the usual recommendations,
   set in Settings and shown on Home as race-HUD bars. A goal of 0 is off.
2. **Training load.** This week's load against your own four-week average, six weekly bars and a plain verdict (light,
   steady, heavy, big jump). It is OpenConnect's own estimate from duration and heart rate, not Garmin's Training Load.

## v0.10.0: trust the data ✅

3. **Backup and restore.** Export everything to one file and bring it back, so a new phone or a reinstall loses nothing.
4. **Sync log.** A screen that lists the last syncs from every route (account, USB, folder, Bluetooth) with what each
   added, and what went wrong. Makes the first real-watch tests easy to report.
5. **Data sources view.** Activities counted per source with their period, plus the health totals. (The switch to prefer
   one source when two disagree is not built; the dedupe by start time already keeps the first copy.)

## v0.11.0: more from the watch 🚧

6. **Bluetooth in the background.** ✅ Built as an optional WorkManager job (about every three hours). Not a foreground
   service that reconnects the moment the watch is in range. ❓ Needs a real watch to tune.
7. **Archive files on the watch** after reading, like Garmin Connect does. ✅ Built as an optional switch. ❓ Real watch.
8. **The newer Garmin file sync** (the protobuf one) for watches that no longer use the classic directory sync. ❓ Real watch.
9. **Live heart rate as a foreground service**, so a workout keeps recording with the screen off.

## v0.12.0: on your phone 💡

10. **Home-screen widget** with readiness and today's steps.
11. **Health Connect** as one more source (and as an output, so other apps see OpenConnect's activities).
12. **Notifications:** a nudge when a goal is close, a weekly summary on Monday, a note when load jumps.

## Later 💡

- Best efforts from tracks (fastest 1K, 5K, 10K) and a lap table.
- Monthly and yearly reports.
- Training plans: a simple 5K or 10K plan built from your goals and load.
- Wear OS or a watch face is out of scope; the project is about the phone side.
- Optional app lock (biometrics) for the health data.

## Needs the owner ❓

- A real **fenix 8** test of the Bluetooth sync. Everything in v0.11.0 depends on its first result.
- Live checks of **Claude and Gemini** with the owner's own keys, entered in the app.

## How releases work

Each version is a pull request, then a GitHub release with the signed APK. Nothing ships without CI green and the
owner's go-ahead. Features that cannot be verified without hardware are marked as such in their release notes.
