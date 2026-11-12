# OpenConnect for Android

A standalone Android app (`android/`). It needs **no server**: storage, FIT/GPX/TCX handling, the
dashboard and the reports all run on the phone, and your data stays in the app's private storage.
The Python server from v0.1.0 is unchanged and stays available for people who prefer to self-host.

![OpenConnect on Android](img/android.png)

*These images are rendered on a computer by the app's own UI tests (Robolectric), not captured on a
phone, so fonts and spacing on a real device can differ slightly.*

## What the app does (v0.2.0)

| Screen | Contents |
|--------|----------|
| **Home** | Today's steps, resting heart rate, Body Battery and stress; last 7 days; 30-day steps chart; recent activities |
| **Activities** | List with a sport filter, import button; detail with route map, heart-rate and elevation charts, kilometre splits, AI report, export as FIT, GPX or TCX, delete |
| **Health** | Steps, sleep, resting heart rate and Body Battery trends |
| **Reports** | Week picker with totals, an AI report for the week, personal records per sport |
| **Settings** | Import files, load or delete all data, report provider (automatic, offline, Claude), report language, API key, model |

English and Dutch, a phosphor-green dark theme and a light theme that follows the system setting.

### Where the data comes from

1. **Demo data**: about 45 days of Garmin-like activities, sleep and daily health, generated on the phone.
2. **File import**: pick FIT, GPX or TCX files from the phone, or share or open a file with OpenConnect.
   Importing the same file again does not create a duplicate (the activity id is derived from the content).
3. **Garmin account sync: not implemented yet.** See [Garmin](#garmin) below.

### AI reports and privacy

- By default reports are written **offline by rules**; nothing leaves the phone.
- With your own Anthropic API key (Settings), reports are written by Claude (`claude-opus-5-5` by
  default, the model name is editable). The request carries a short JSON summary of the numbers and
  the activity name, **never the GPS track**. The key is kept in `EncryptedSharedPreferences`
  (Android Keystore). If secure storage is unavailable the key is not stored at all.
- The only permission is `INTERNET`, used for that one call. Backups and device transfer are off
  (`allowBackup=false` plus data-extraction rules that exclude everything).
- Text from imported files (activity names) only travels inside the data block of the prompt.

## How it is built

```
android/
  core/   pure Kotlin/JVM, no Android dependency, JUnit tests
    Domain, Repository (+ in-memory and track codec), Stats, Demo
    formats/  FIT (encoder + decoder + CRC), GPX, TCX, safe XML parsing
    reports/  facts, prompts, template + Anthropic providers, cached ReportService
  app/    Jetpack Compose UI, SQLite storage, settings, view model
```

- **Core ported from the server.** The Kotlin FIT, GPX and TCX exporters produce files that are
  byte-identical to the Python server's (checked against fixtures the server generated), and the Kotlin
  importer reads the server's files back to the same activity.
- **Storage.** SQLite with four tables. Tracks are stored compressed, one blob per activity, and
  list queries never load them.
- **Safe parsing.** XML with a DOCTYPE or ENTITY is refused (no XXE), FIT files are CRC-checked, and
  imports are capped at 25 MB per file.
- **Reports.** `ReportService` caches a report by a hash of the input facts, language, provider and
  model, so unchanged data is not regenerated or billed twice.

## Build it

Requirements: JDK 17 or newer and the Android SDK (platform 35, build-tools 35). Set `ANDROID_HOME`.

```bash
cd android
./gradlew :core:test                 # core unit tests
./gradlew :app:testDebugUnitTest     # app tests, incl. UI smoke tests with screenshots in app/build/screenshots
./gradlew :app:lintDebug
./gradlew :app:assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

CI runs the same on every pull request and attaches the APK. Release builds attach the APK to the
GitHub release (`openconnect-vX.Y.Z.apk` with a `.sha256`).

## Install it (sideload)

1. Download `openconnect-vX.Y.Z.apk` from the [releases page](https://github.com/Tufein/OpenConnect/releases).
2. On the phone, allow installing from the browser or file manager when Android asks.
3. Open the APK. Needs Android 8.0 or newer.

### About the signing key

The APK is signed with a key that is **committed to this repository on purpose** (`android/app/openconnect-sideload.jks`).
It is not a secret. It exists so every build has the same signature, which lets a new version install over
the old one and keep your data. The consequence: anyone could build an APK with this key, so only install
APKs from this repository's releases and check the `.sha256`. For a real distribution (for example the
Play Store) use your own key: set `OPENCONNECT_KEYSTORE`, `OPENCONNECT_KEYSTORE_PASSWORD`,
`OPENCONNECT_KEY_ALIAS` and `OPENCONNECT_KEY_PASSWORD` in the build environment and the build uses it
instead. Keep that key and its passwords out of the repository.

## Garmin

The app cannot reuse the server's Garmin integration: Garmin's business-tier APIs push data to a server
endpoint and use a client secret, which cannot live inside an APK. What fits a standalone app is Garmin's
**Personal API** (your own data, your own key, pulled by the app), which needs Garmin's approval.
That is planned and **not implemented in v0.2.0**. Until then the way in is file import from your watch,
Garmin Connect's own export, or any app that writes FIT, GPX or TCX. Reading from Android Health Connect
is another candidate source and has not been looked into yet.

## What has and has not been verified

| Verified by automated tests | Not verified (needs a real phone or credentials) |
|-----------------------------|--------------------------------------------------|
| Core logic, formats and reports (63 core tests) | Behaviour on real devices and Android versions other than the emulated API 34 |
| SQLite storage on the JVM (Robolectric) | File pickers, share and open-with intents, the save dialog |
| The real app driven through every tab: demo data, activity detail, offline reports, English dark and Dutch light | Android Keystore encryption of the API key (the JVM tests have no Keystore) |
| Release APK builds, is signed (v2) and is not debuggable | Live Claude calls from the phone (no key was available) |
| Lint is clean | Garmin sync (not built) |

## Roadmap

Garmin Personal API pull, Health Connect as a source, a home-screen widget, laps and zoomable charts,
achievements (they exist in the server but not yet in the app), and a signed Play Store build.
