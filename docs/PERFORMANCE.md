# Performance, size and battery

What the app is held to, how it is checked and what is **not** measured. Added in v0.58.0 (milestone M5 of
[ROADMAP.md](ROADMAP.md)).

## The budget (checked in every CI run)

`PerformanceBudgetTest` stores **five years of history** in the real SQLite repository (1,304 activities with a track
each, 444,686 track points, 1,826 days of health data) and opens every tab on it. `HeatmapBudgetTest` builds the heatmap
from five years of tracks the way the app does.

| What | Measured (CI-like machine, Robolectric) | Budget that fails the build |
|------|------------------------------------------|-----------------------------|
| Storing five years | about 3 s | not limited (it happens once, on import) |
| First start to Home, with the data in place | about 3 s | 30 s |
| Open Activities, Health, Insights, Settings | 0.15 to 0.35 s each | 8 s each |
| The map (heatmap of the newest 300 routes) | about 0.4 s | 8 s |
| Heatmap work alone: 109,000 positions | 0.15 s and 19 MB allocated | 3 s and 100 MB |

Why the budgets are so far above the measurements: these run on a shared CI machine, on the JVM, with a pretend Android.
They are there to catch a screen that becomes slow with a lot of history (a loop inside a loop, a track read for every row),
which would show as a factor of ten or more, not to promise a time on a phone. The numbers of the last run are written to
`app/build/perf/report.txt`.

What keeps it fast: lists of activities carry no track (only the one you open reads its points); the heatmap reads at most
the newest 300 routes and thins each to 500 positions, however much history there is; activity lists are capped at 10,000 rows.

## Size of the APK

| Version | APK |
|---------|-----|
| v0.55.0 and before (not shrunk) | 30.2 MB, of which 28.7 MB is code (Compose, Material, Health Connect, Kotlin) |
| v0.58.0 (shrunk with R8) | 9.8 MB |

The earlier statement in the roadmap that the APK was 11 MB was wrong; it had never been measured. The limit for the
release is now **12 MB**, and the release workflow fails when the APK is larger.

R8 shrinks only (it removes unused code and resources, it does not rename or optimise: `proguard-rules.pro`), so a stack
trace in an issue still reads. The risk of shrinking is a class that is only reached by name (Work manager, JSON
serializers, the Keystore library) being removed. The libraries bring their own keep rules and the project's own rules are in
`android/app/proguard-rules.pro`, but the only proof is to run the shrunk APK:

- **Emulator smoke test** (`android/scripts/emulator_smoke.py`, workflow *Emulator smoke*): starts the **shrunk** release APK and
  the plain one on an Android 14 emulator, answers the first-start screen, loads demo data, opens Home, Activities, Health,
  Insights, Settings and About, saves a fake AI key (through the real Keystore), stops the app, starts it again and checks the
  key is still there. It fails on a crash, a dead process or a screen that does not show up. The same walk runs in
  `release.yml` on the signed APK **before** a release is published. Its screenshots and the app's log are kept as a build
  artifact.
- It does not cover: Bluetooth, USB, Health Connect data, the live heart-rate service, a real network, biometrics. Those are
  the trials in [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md).

## What runs in the background (and how often)

| What | When | Notes |
|------|------|-------|
| Account sync (Garmin, Oura, Health Connect) | about every 15 minutes through WorkManager, when there is a connection and the battery is not low; Android decides the exact moment | only for the sources you switched on; no-op writes when nothing changed |
| Sync while the app is open | every 10 minutes, and when the app comes to the front after a minute | stops when the app is closed |
| Nudges (a reminder to move, to sleep) | WorkManager every 3 hours, only when switched on | |
| Live heart rate | a foreground service, only from the moment you press *Live heart rate* until you press stop; it shows a notification | `START_NOT_STICKY`: Android does not restart it |
| Nothing else | no wake locks of its own, no alarms, no location | |

## Not measured

- **Battery.** There is no battery measurement yet: that needs a real phone over a day. Trial **T16** in
  [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md) asks for it: the figure Android shows under *Settings, Battery, OpenConnect* after a
  day with your sources on, and after an hour of live heart rate.
- **Start-up and memory on a phone.** The numbers above are JVM numbers. A phone with 3 GB of memory and a slow storage chip
  was not tried.
- **A very large history on a phone.** Five years is the size the budget covers; a decade of one activity a day is not.
