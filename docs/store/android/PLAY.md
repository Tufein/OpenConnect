# Google Play preparation (draft)

**Status: draft only.** Nothing in this document has been uploaded to Play Console, and there is no Play listing. This
is preparation, not a claim that Cercis is available on Google Play.

**How to read the marks**

- `✅ Verified (repo)` — checked in this repository (source, manifest, build file or script). It does **not** mean the
  Play Console accepted it.
- `⚠️ UNVERIFIED` — not yet confirmed against the live Play Console, current Google policy, a real device or a lawyer.
- `🚫 Blocker` — must be resolved before an upload.

**Facts from the repository**

| Item | Value | Source |
|---|---|---|
| Package name | `nl.tufein.openconnect` | `android/app/build.gradle.kts` |
| Version | `1.13.1` (versionCode `94`) | `android/app/build.gradle.kts` |
| Minimum Android | 9 (API 28) | `android/app/build.gradle.kts` |
| Target Android | 16 (API 36) | `android/app/build.gradle.kts` |
| Compile SDK | 37 | `android/app/build.gradle.kts` |
| Store AAB task | `:app:bundleStoreRelease` | `android/app/build.gradle.kts` |
| Store APK task | `:app:assembleStoreRelease` | `android/app/build.gradle.kts` |
| Store AAB output | `android/app/build/outputs/bundle/storeRelease/app-store-release.aab` | build output |
| Store APK output | `android/app/build/outputs/apk/store/release/app-store-release.apk` | build output |
| Updater permission | absent from the store flavor | `android/app/src/github/AndroidManifest.xml`; `android/scripts/verify-store-aab.sh` |
| FileProvider | absent from the store flavor | same |
| Languages in the app | English, Dutch, German, French | `android/app/src/main/res/values*/strings.xml` |
| Licences | AGPL-3.0-or-later; fonts OFL; photo Unsplash | `NOTICE`, `CREDITS.md`, `licenses/` |

## 0. The name: decided, legal clearance still open

The display name is **Cercis**, chosen on 2026-11-25 after the availability check in [NAME.md](NAME.md). Cercis is a
fitness and health dashboard, not the OpenConnect VPN client; the fastlane descriptions keep that disclaimer. The old
`OpenConnect` title collided with a well-known VPN client on Play (1M+ downloads) and F-Droid.

- [x] ✅ Final display name decided: **Cercis** (2026-11-25), see [NAME.md](NAME.md).
- [x] ✅ `applicationId` stays `nl.tufein.openconnect`; the OAuth scheme (`openconnect://oura`) and asset names stay too.
- [ ] 🚫 Legal clearance: the availability check found no exact Play/F-Droid app, but no trademark search in
  USPTO/EUIPO/BOIP/DPMA and no lawyer review has been done. Do not upload until this is closed. This document does not
  give legal advice.

## 1. Developer account and app creation

- [ ] ⚠️ Play Console developer account exists, identity verification is complete, and the account can publish health
  apps.
- [ ] ⚠️ Account type is known. A **personal** account created after 13 November 2023 must run a closed test with at
  least **12 testers who opted in for 14 continuous days** before it can apply for production access; organization
  accounts are exempt. Confirm the current rule in Play Console.
- [ ] ⚠️ Accept the Developer Program Policies, US export laws and the Health Connect terms.
- [ ] ⚠️ Create the app: default language `en-US`, app name = final name, package `nl.tufein.openconnect`, app (not
  game), free, category **Health & Fitness** (subcategory to be chosen).
- [ ] ⚠️ Decide the countries/regions and whether the app is listed on all of them. Health data and AI providers may
  have regional constraints.
- [ ] ⚠️ Decide the contact email and website. A real support address is needed; the GitHub issues page can be the
  website, but a dedicated page is better.

## 2. Store listing

Copy the text from `fastlane/metadata/android/<locale>/`. The character counts below are from the files in this tree.

| Field | en-US | nl-NL | de-DE | fr-FR | Limit | Status |
|---|---:|---:|---:|---:|---:|---|
| `title.txt` | 6 | 6 | 6 | 6 | 30 | ✅ Verified (repo) |
| `short_description.txt` | 60 | 67 | 59 | 67 | 80 | ✅ Verified (repo) |
| `full_description.txt` | 2642 | 2802 | 2940 | 3211 | 4000 | ✅ Verified (repo) |
| `changelogs/94.txt` | 268 | 285 | 290 | 328 | 500 | ✅ Verified (repo) |
| `changelogs/93.txt` (previous build) | 416 | 437 | 446 | 435 | 500 | ✅ historical |

The counts are characters without the trailing newline. The changelogs are under 500 characters in all four locales; re-check every field in the Console after any edit.

- [x] Icon: `fastlane/metadata/android/en-US/images/icon.png` is the existing 512 x 512 project mark. ✅ Verified.
- [ ] 🚫 Feature graphic: missing; see `fastlane/metadata/android/IMAGE_TODO.md`. ⚠️ Play requires 1024 x 500.
- [ ] 🚫 Phone screenshots: missing; see `fastlane/metadata/android/en-US/images/phoneScreenshots/README.md`.
- [ ] ⚠️ Privacy policy URL: `https://github.com/Tufein/OpenConnect/blob/main/docs/PRIVACY.md`. It is public, but a
  GitHub file is editable and not a dedicated policy page. Confirm Play accepts it, or publish a stable URL
  (GitHub Pages / own domain) and update the fastlane text and this document.
- [ ] ⚠️ App access: answer that all functionality is available without an account. Garmin, Oura, Claude and Gemini
  keys are optional, user-supplied integrations; Health Connect and notification access are optional permissions.
- [ ] ⚠️ Ads: **No**. There is no ad SDK, no advertising ID and no analytics; verify again in the final APK.
- [ ] ⚠️ Content rating questionnaire: health/fitness content, no violence, sex, gambling, user-generated content or
  purchases. Proposed rating is Everyone / PEGI 3, but the questionnaire decides. Do not copy this into the Console
  without reading the questions.
- [ ] ⚠️ Target audience: decide 13+ or 18+. The app handles health data and has no social features; the choice affects
  the Families policy and the data safety form.
- [ ] ⚠️ Category, tags, contact details, website and support email.
- [ ] ⚠️ Do not claim Play availability anywhere in the listing until the app is actually live.

## 3. Data safety

These are **proposed** answers, not console values. Google's definition of “collect” is broader than “send to the
developer”: data transmitted off the device by the app can count, even when the developer never receives it. The
optional AI calls are therefore the main reason a health/fitness row can be “Yes”. Review every row against the live
form with the final build.

**Proposed top-level answer:** Yes for **Health info** and **Fitness info**, only through optional AI calls; **No** for
every other category. Tufein itself receives nothing. This is the most likely answer to be challenged in review, so
treat it as a question to confirm, not a fact.

| Data type | Collected? | Shared? | Purpose / when | Notes |
|---|---|---|---|---|
| Health info (sleep, heart rate, steps, HRV, SpO2, stress, Body Battery, weight) | ⚠️ Yes, only when an optional source or AI feature is used | ⚠️ Yes — optional AI providers (Anthropic/Google); Garmin/Oura are data sources | App functionality; only after the user adds a key/account and asks for a report, coach answer or sync | Stored on the phone; no Tufein server; AI facts exclude GPS points, routes, journal, notes and workout text (see `docs/PRIVACY.md`) |
| Fitness info (activities, workouts, splits, personal records) | ⚠️ Yes, same optional AI path | ⚠️ Yes — optional AI providers | App functionality | Same limits; FIT/GPX/TCX imports stay local |
| Location | No | No | — | GPS belongs to imported activity files and stays local; AI facts exclude GPS points/routes; Android 12+ does not ask for location. Legacy `ACCESS_*_LOCATION` is `maxSdkVersion=30` only for BLE scanning. |
| Personal info (name, email, phone, address, user IDs) | No | No | — | No account; Garmin/Oura/AI keys are credentials, stored in Keystore-backed preferences, not profile data |
| Messages (notification title/text) | No | No | — | Read transiently only when notification forwarding is on, sent to the paired watch over Bluetooth; not stored in the database or a server; at most 16 texts wait at most two minutes in memory |
| Files and documents | No | No | — | Import/export and backup are local files the user chooses; sharing one is a user action |
| App activity and in-app search | No | No | — | No analytics, no telemetry, no ad SDK |
| App info and performance (crash logs, diagnostics) | No | No | — | No crash reporting; the user can copy a diagnostics text manually |
| Device or other IDs | No | No | — | No advertising ID; no device identifier sent to Tufein |
| Financial info, contacts, calendar, photos, audio, SMS | No | No | — | Not accessed |

- [ ] ⚠️ “Is all data encrypted in transit?” — Yes for optional HTTPS services (Garmin, Oura, Anthropic, Google);
  Bluetooth and Health Connect are local. Confirm the exact form wording.
- [ ] ⚠️ “Can users request data deletion?” — The app has *Settings → Your data → Delete all data* and no account.
  Health Connect records the app wrote are managed in Health Connect; explain that clearing app data does not
  necessarily remove them. Confirm the answer Play expects.
- [ ] ⚠️ “Is data sold?” — No. “Used for advertising?” — No.
- [ ] ⚠️ The data safety answers must agree with the privacy policy and the Health Connect declaration. A mismatch is
  a common rejection reason.

## 4. Health Connect declaration

Health Connect is a first-class feature, not an add-on: it is how the app reads steps/sleep/weight and how it saves
an activity. The manifest declares exactly these permissions (`android/app/src/main/AndroidManifest.xml`):

| Permission | What the app does | When |
|---|---|---|
| `android.permission.health.READ_STEPS` | Reads daily steps where Cercis has no data | After the user grants it and a sync/fill runs |
| `android.permission.health.READ_SLEEP` | Reads sleep sessions/nights | Same |
| `android.permission.health.READ_WEIGHT` | Optional body weight for the Body card | Only if granted; the rest works without it |
| `android.permission.health.WRITE_EXERCISE` | Saves an activity as an exercise session | Only when the user asks to save an activity |
| `android.permission.health.WRITE_DISTANCE` | Writes the activity distance | Same |
| `android.permission.health.WRITE_TOTAL_CALORIES_BURNED` | Writes the activity calories | Same |

Suggested declaration text (edit to match the final UI):

> Cercis reads steps, sleep and optional body weight from Health Connect so the Health screen can show them
> where the app has no data of its own. It writes an exercise session, its distance and its calories only when the
> user asks to save an activity. The data is shown in the app and stored on the phone. It is not sent to Tufein, sold,
> or used for advertising, credit, insurance or employment decisions. The user can revoke each permission in Health
> Connect or Android settings and can delete the app's data in the app; records already written to Health Connect are
> deleted in Health Connect.

- [ ] ⚠️ Complete Play Console's **Health apps** declaration and the **Health Connect** permissions section for each
  permission above.
- [ ] ⚠️ Provide the privacy policy URL and the in-app rationale. The app has the
  `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` activity alias, so the system “how this app uses health data”
  entry opens the app. ✅ Verified (repo).
- [ ] 🚫 Decide the AI/Health Connect interaction before declaring. The optional AI facts can include health values
  that may have come from Health Connect. Confirm whether Google's Health Connect policy allows that user-initiated
  transfer. If it does not, exclude Health Connect-derived values from AI facts or obtain a separate, explicit
  consent. Do not declare first and change the app later.
- [ ] ⚠️ Health Connect data is not used for advertising, sold, or used for credit/insurance/employment decisions.
  Confirm the app has no such path (the code does not).
- [ ] ⚠️ Data deletion: the app can delete its local database; records already written to Health Connect must be
  deleted by the user in Health Connect. Say this truthfully in the declaration and privacy policy.
- [ ] ⚠️ Read the current Health Connect policy and the Health apps policy; both change. This document is a checklist,
  not the policy text.

## 5. Permissions and foreground services

Store build merged manifest (verified with `aapt2 dump permissions`; GitHub build differs and must not be uploaded):

| Permission / component | Why it exists | User-visible trigger | Play handling |
|---|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Optional Garmin, Oura and AI calls; no updater in the store build | Adding a source/key or syncing | ⚠️ Data safety section |
| `POST_NOTIFICATIONS` | Live heart-rate notification, forwarding notification, optional nudges | Android 13+ runtime prompt | ⚠️ Normal runtime permission |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keeps `LiveRecordingService` (live heart rate) and `ForwardingService` (notifications to the watch) alive with the screen off; both are `foregroundServiceType="connectedDevice"` | User starts live heart rate or forwarding | ⚠️ Play **Foreground Service Permissions** declaration; explain both services |
| `USE_BIOMETRIC` | App lock (fingerprint/face/device credential) | User turns on App lock | Normal permission |
| Health Connect read/write permissions | See section 4 | User grants each type | Health Connect declaration |
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` | Find/connect a watch or chest strap; scan is `neverForLocation` | User pairs a sensor/watch | ⚠️ Nearby devices runtime prompt |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (`maxSdkVersion=30`), `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion=30`) | Only Android 11 and older, where BLE scanning needs location | User pairs on an old phone | ⚠️ Explain legacy-only; no location collection |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | Added by WorkManager for the background sync job | Background sync (user can turn it off) | ⚠️ Confirm in merged manifest; no exact alarms |
| Notification listener service (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Forwards chosen phone notifications to the Garmin watch | User enables *Notifications on the watch* in system settings | ⚠️ Special access; prominent disclosure and privacy policy must match |
| USB host (`android.hardware.usb.host`, `required=false`) | Read a watch over a USB cable | User plugs in a watch and grants USB permission | Optional feature; not a permission |
| `REQUEST_INSTALL_PACKAGES` | **Absent in the store build**; present only in the GitHub flavor for the in-app updater | Never in the store build | 🚫 Never upload the GitHub artifact; run the verify script |
| FileProvider (`androidx.core.content.FileProvider`) | **Absent in the store build**; present only in the GitHub flavor to hand a downloaded APK to Android's installer | Never in the store build | 🚫 Same |

- [ ] ⚠️ In Play Console → App content → Foreground Service Permissions, declare both connected-device services and
  paste the user-facing justification. Keep it short: live heart rate recording with a persistent stop notification;
  notification forwarding to a paired watch while the user has it enabled.

Suggested justification text (edit to match the final UI):

> Cercis uses a connected-device foreground service only after the user starts one of two features. Live heart
> rate keeps a Bluetooth LE connection to a watch or chest strap open and shows a persistent notification with a stop
> action. Notification forwarding keeps the Bluetooth connection to the paired Garmin watch alive while the user has
> enabled forwarding; the persistent notification says so. Neither service starts on its own, and stopping the
> notification or the feature stops the service.

- [ ] ⚠️ For notification access, ensure the in-app explanation appears before the system settings screen and that the
  policy-required disclosure is visible. The app already explains it; a reviewer still has to find it.
- [x] ✅ Verify the built store artifacts:
  `bash android/scripts/verify-store-aab.sh <file.aab|file.apk>` fails on `REQUEST_INSTALL_PACKAGES` or FileProvider.
  This is also a CI gate in `.github/workflows/store.yml`. (Run against the existing store AAB/APK; both pass.)

## 6. AAB, upload key and signing

Build the store bundle (no release key is required locally; without one the build is debug-signed):

```bash
cd android
./gradlew :app:bundleStoreRelease :app:assembleStoreRelease --console=plain
bash scripts/verify-store-aab.sh app/build/outputs/bundle/storeRelease/app-store-release.aab
bash scripts/verify-store-aab.sh app/build/outputs/apk/store/release/app-store-release.apk
```

- [ ] ⚠️ Play takes the **AAB** for a new app; the APK is for local/emulator checks and for people who sideload the
  store flavor. Confirm the current Console requirement before uploading.

- [ ] ⚠️ Play App Signing: decide before the first upload. A new app normally uploads with an **upload key** and Google
  holds the **app signing key**. Create/keep the upload key outside this repository.
- [ ] ⚠️ Do **not** upload the debug-signed artifact from CI. It is for inspection only. Sign with the upload key, or
  let Play Console guide the first upload; never reuse a throw-away debug key.
- [ ] ⚠️ Cross-channel update rule: the GitHub APK is signed with the release key; the Play app may be signed with
  Google's app signing key; F-Droid signs with its own key. Android refuses an update when the signer differs. The
  same `applicationId` does **not** make the channels interchangeable. Decide whether Play uses the same key as the
  GitHub release; if not, users must export, uninstall and reinstall to switch channels. Document this in the store
  listing and the release notes.
- [ ] ⚠️ `versionCode 94` must be higher than every previous upload. AAB uploads are immutable; bump for every retry.
- [ ] ⚠️ Target API: `targetSdk 36`. Confirm Play's current target-API requirement for new apps and updates; API 36 is
  the current target in this repository, but the Console is the authority.
- [ ] ⚠️ 64-bit: the bundle contains `arm64-v8a` (plus 32-bit and x86 for emulators). Play requires a 64-bit version;
  confirm no ABI is missing in the final bundle.
- [ ] ⚠️ `bundle { language { enableSplit = false } }` keeps all app languages in one bundle; Play may still split by
  density/ABI. Do not remove this without testing the language picker.
- [ ] ⚠️ New personal developer accounts: the **12 testers / 14 days** closed-test rule in section 1 applies before
  production. Plan the tester list and keep them opted in; testers who leave reset the clock.

## 7. Screenshots and store assets

- [x] Icon 512 x 512: present, copied from `docs/img/logo.png`; check the Console preview. ✅ (file exists)
- [ ] ⚠️ The source icon is 8-bit RGBA and has transparent corners. If the Console preview shows them, flatten the
  existing pixels onto the logo's own dark background; do not redraw or add a new mark.
- [ ] 🚫 Feature graphic 1024 x 500: **missing**. See `fastlane/metadata/android/IMAGE_TODO.md`.
- [ ] 🚫 Phone screenshots: **missing**. Follow
  `fastlane/metadata/android/en-US/images/phoneScreenshots/README.md`; use the store build and demo data, never
  Robolectric/JVM captures.
- [ ] ⚠️ Tablet screenshots: optional; only if tablet support is claimed.
- [ ] ⚠️ Check the current Play image requirements in the Console (count, aspect ratio, minimum/maximum pixels, file
  size). They change; this document deliberately does not freeze a number that may be stale.
- [ ] ⚠️ No fake frames, no invented UI, no real health data, no API keys or tokens in any image.
- [ ] ⚠️ The listing must not imply Garmin/Oura/Anthropic/Google endorsement. The descriptions already include the
  Garmin disclaimer.

## 8. Privacy policy URL

Current URL: `https://github.com/Tufein/OpenConnect/blob/main/docs/PRIVACY.md`

- [ ] ⚠️ Confirm it opens without login in a private browser window and is reachable from the Play review region.
- [ ] ⚠️ Decide whether a GitHub blob URL is acceptable long-term. A stable page you control is better; if you move it,
  update the fastlane descriptions, the app's About screen and this document.
- [ ] ⚠️ The policy must cover every optional source (Garmin, Oura, Anthropic, Google), Health Connect, notification
  access, local storage, deletion and the absence of a Tufein server. `docs/PRIVACY.md` does; re-read it against the
  final build.
- [ ] ⚠️ Health Connect requires the privacy policy to explain access, use, sharing, retention and deletion. A generic
  app privacy policy is not enough.

## 9. Release checklist (ordered)

1. [x] ✅ Final display name decided: **Cercis**; [NAME.md](NAME.md) records it (2026-11-25); title, package decision
   and Connect IQ names aligned. 🚫 Legal clearance remains open (section 0).
2. [ ] 🚫 Feature graphic and 2–8 real phone screenshots captured and checked.
3. [ ] ⚠️ Run the T1–T17 hardware trial in `docs/PUBLIC_RELEASE.md` on the store build, or record what remains untested.
4. [ ] ⚠️ Build the AAB with the upload key; run `verify-store-aab.sh` on the AAB and APK; keep the output.
5. [ ] ⚠️ Upload to an **internal** track first; install from Play on a clean phone; test first start, demo data,
   Health Connect, notifications, watch, import/export and delete-all.
6. [ ] ⚠️ Complete Data safety, Health Connect, Foreground Service Permissions, content rating, app access and ads.
7. [ ] ⚠️ Closed test with 12+ testers for 14 days if the account is a new personal account.
8. [ ] ⚠️ Apply for production; fix review findings; do not publish automatically.
9. [ ] ⚠️ Publish only with Stefan's explicit approval. This repository does not publish to Play.

## 10. Never do this

- Never upload `app-github-release.apk` or a GitHub-flavor AAB: it contains `REQUEST_INSTALL_PACKAGES` and a
  FileProvider. The store CI workflow builds and verifies the store flavor only.
- Never add a Play service account, `fastlane supply`, or any upload step to `.github/workflows/store.yml`.
- Never publish with a working title or with the old `OpenConnect` display name.
- Never claim Play/F-Droid availability before the listing is actually live.
- Never promise medical advice, diagnosis or treatment. The app shows fitness/health estimates and says so.

## Sources to re-check before upload

- Play Console Help: Data safety, Health apps, Health Connect, Foreground Service Permissions, target API, closed
  testing for new personal accounts, screenshot and graphic requirements.
- Google Play Developer Program Policies and Health Connect policy.
- `docs/PRIVACY.md`, `docs/ANDROID.md`, `android/app/src/main/AndroidManifest.xml` and
  `android/app/src/github/AndroidManifest.xml`.

_Last written: 2026-11-25, for v1.13.1 / versionCode 94. Display name decided: Cercis. No Play upload has been made._
