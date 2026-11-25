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
| v1.4.0 | 9.9 MB |
| v1.12.0 and v1.13.0 | 12.56 MB (12,562,016 bytes), 20 KB under the old 12 MiB limit |
| v1.13.3 (Compose 1.13) | 13.10 MB (13,097,301 bytes in the pull request build), 96 percent of the 13 MiB budget |

The earlier statement in the roadmap that the APK was 11 MB was wrong; it had never been measured. The budget is now
**13 MiB** (13,631,488 bytes), kept in one place, `android/scripts/check_apk_size.py`. Both the *Release* workflow and the
*Android* workflow (on every push and pull request) run it, so a change that makes the APK too big is found when it is made,
not at release time. It prints where the bytes are, writes that to the run summary, warns from 95 percent and fails above
the budget. A test (`tests/unit/test_apk_size.py`) keeps this page and the script on the same number.

Where the 12.56 MB of v1.13.0 are: **10.7 MB is code** (`classes.dex` 8.7 MB and `classes2.dex` 2.1 MB, stored
uncompressed, which is what the Android Gradle Plugin does for minSdk 28 and newer), 1.15 MB is `resources.arsc` (the
strings of four languages and the layouts), 0.48 MB is `res/` (five fonts of about 0.3 MB, a 44 KB photo, nine-patches) and
0.04 MB is the native `libandroidx.graphics.path.so` for four ABIs. The code grew by about 2.6 MB over the eight releases
from v1.4.0 to v1.12.0 (the editorial look, the privacy register, plan adaptation, two more languages), roughly 0.3 MB a
release, so 13 MiB is about three releases of room, not a permanent answer.

v1.13.3 used most of that room in one go: Compose 1.13 (with material3 1.5) added about 0.54 MB, almost all of it code
(`classes.dex` 8.4 MB and `classes2.dex` 2.8 MB, 11.26 MB together). That leaves about 0.53 MB, so the check now warns on
every build, and the next release that adds a feature of any size will need one of the two ways below first.

Two ways to get a lot back when it is needed, neither taken yet because they cannot be tried on a real phone from here:
compress the dex files in the APK (`useLegacyPackaging` for dex: the APK would be about 6 MB smaller, at the price of the
phone decompressing them when the app is installed), or let R8 optimise instead of only shrinking (it would also rename,
which makes a stack trace in an issue unreadable unless the mapping file is kept).

R8 shrinks only (it removes unused code and resources, it does not rename or optimise: `proguard-rules.pro`), so a stack
trace in an issue still reads. The risk of shrinking is a class that is only reached by name (Work manager, JSON
serializers, the Keystore library) being removed. The libraries bring their own keep rules and the project's own rules are in
`android/app/proguard-rules.pro`, but the only proof is to run the shrunk APK:

- **Emulator smoke test** (`android/scripts/emulator_smoke.py`, workflow *Emulator smoke*): starts the **shrunk** release APK and
  the plain one on an Android 14 emulator, answers the first-start screen, loads demo data, opens Home, Activities, Health,
  Insights, Settings and About, saves a fake AI key (through the real Keystore), stops the app, starts it again and checks the
  key is still there. It fails on a crash, a dead process or a screen that does not show up. The same walk runs in
  `release.yml` on the signed APK **before** a release is published, and a red walk stops the release (one second try on
  a fresh emulator). Its screenshots, the app's log, the whole device log (`logcat.txt`, streamed during the walk, so it
  survives an emulator that dies) and, in the *Emulator smoke* workflow, the runner's memory and kernel log are kept as a
  build artifact. The emulator draws with `-gpu swangle_indirect`: with the emulator-runner's default
  (`swiftshader_indirect`) the emulator process vanished at the first swipe on Home from v1.11.0 on, while the same APK
  walked to the end on `swangle_indirect` and `guest` (measured 2026-11-25). *Emulator smoke* can walk published APKs on
  request (inputs `published` and `gpu`), to tell an app regression from a runner problem.
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
