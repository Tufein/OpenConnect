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
| v0.11.0 | Archive on the watch, background Bluetooth (both optional) |
| v0.12.0 | Widget, optional notifications |

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

## v0.11.0: more from the watch ✅ (partly)

6. **Bluetooth in the background.** ✅ Built as an optional WorkManager job (about every three hours). Not a foreground
   service that reconnects the moment the watch is in range. ❓ Needs a real watch to tune.
7. **Archive files on the watch** after reading, like Garmin Connect does. ✅ Built as an optional switch. ❓ Real watch.
8. **The newer Garmin file sync** (the protobuf one) for watches that no longer use the classic directory sync. ❓ Real watch.
9. **Live heart rate as a foreground service**, so a workout keeps recording with the screen off.

## v0.12.0: on your phone ✅ (widget and notifications)

10. **Home-screen widget** with readiness, today's steps and the week's activity goal.
12. **Notifications:** a nudge when a goal is close, a weekly summary on Monday, a note when load jumps.

## v0.13.0: Health Connect ✅

11. **Health Connect** as one more source (and as an output, so other apps see OpenConnect's activities).

## v0.14.0: more insight ✅

13. **Best efforts** from tracks (fastest 1K, 5K, 10K) and a lap table.
14. **Monthly and yearly reports.**

## v0.15.0: plan and protect ✅

15. **Training plans:** a simple 5K or 10K plan built from your goals and load.
16. **App lock** (biometrics) for the health data.

## v0.16.0: Oura, one AI, livelier screens ✅

17. **Oura Ring** as a source (OAuth2 login with the owner's own Oura application; days, nights and workouts).
18. **One AI at a time:** offline, Claude or Gemini; only the chosen one is shown and used.
19. **Sync on opening and every 10 minutes** while the app is open; background jobs every 15 minutes.
20. **Tiles that open their chart**, and charts that can be read by touch.

## v0.17.0: choose your devices ✅

21. **First start asks which devices you use** (Garmin, Oura Ring, both or none); only those show up. Changeable under
    *Settings, Your devices*.
22. **Oura gets its own tab**, with its own scores, nights, charts and workouts, kept apart from the watch's data.

## v0.18.0: themes and watch faces ✅

23. **Seven colour themes** (Arcade, Game Boy, Amber terminal, Synthwave, Ocean, Sunset, Wallpaper) and **three styles**
    (Sport, Round, Pixel), on top of dark, light or follow the phone.
24. **Watch face over USB:** copy a `.prg` to `GARMIN/Apps`; choose it on the watch. Switching the active face from the
    phone is not possible over any open protocol.

## v0.19.0: three designs ✅

25. **Clean Pro, Dashboard Dark and Retro Pro**, picked in Settings or at the first start. Each lays out Home its own
    way, draws cards and charts in its own style and brings its own colours, which stay changeable.

## v0.20.0: watch face preview ✅

26. **Round preview of the watch face** in Settings (Clean, Dashboard, Retro; five colours; four fields), with your numbers.

## v0.21.0: watch faces (source) ✅

27. **Three watch faces as Connect IQ source** (`watch/`) with `build.sh`; the app shows and copies the build command.
    Not compiled yet: the device files need the owner's Garmin login.

## Later 💡

- Wear OS or a watch face is out of scope; the project is about the phone side.

## Needs the owner ❓

- A real **fenix 8** test of the Bluetooth sync. Everything in v0.11.0 depends on its first result.
- Live checks of **Claude and Gemini** with the owner's own keys, entered in the app.
- A real **Oura** login with the owner's own Oura application and membership.

## How releases work

Each version is a pull request, then a GitHub release with the signed APK. Nothing ships without CI green and the
owner's go-ahead. Features that cannot be verified without hardware are marked as such in their release notes.
