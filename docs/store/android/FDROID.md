# F-Droid preparation (draft)

**Status: draft only.** No metadata has been submitted to F-Droid and no F-Droid build exists. This document prepares a
recipe for the existing `store` flavor; it does not claim that Cercis is available on F-Droid.

The `store` flavor is the one to build: it has no in-app updater, no `REQUEST_INSTALL_PACKAGES` permission and no
FileProvider. The `github` flavor must never be sent to F-Droid.

## What F-Droid needs

- A free-software licence for the whole app: **AGPL-3.0-or-later** (`LICENSE`, `NOTICE`). ✅ Verified (repo).
- Source that builds on F-Droid's buildserver with no prebuilt proprietary dependencies. The app uses AndroidX,
  Kotlin, kotlinx.serialization/coroutines (Apache-2.0) and fonts under the OFL; there is no Google Play Services,
  Firebase, ad SDK or crash reporter. ✅ Verified (repo; `android/app/build.gradle.kts`).
- A unique application ID: `nl.tufein.openconnect`. ✅ Verified (repo).
- A tag or full commit hash, a version name/code and a Gradle task. 🚫 The `v1.13.1` tag does not exist yet.
- Listing text, icon, screenshots and a feature graphic. Text and icon exist; feature graphic and screenshots are
  blockers (see `fastlane/metadata/android/IMAGE_TODO.md`).
- A final name: **Cercis** (decided 2026-11-25, see [NAME.md](NAME.md)). The old “OpenConnect (working title)” is no
  longer used. F-Droid already lists an OpenConnect VPN client (`net.openconnect_vpn.android`); the name is different
  now, but legal clearance of Cercis is still open.

## Recipe (draft)

This is the intended `metadata/nl.tufein.openconnect.yml` for an `fdroiddata` merge request. It is **not** committed to
this repository as a live F-Droid file because F-Droid metadata belongs in the `fdroiddata` repository; keep this copy
as the source of truth for the submitter.

```yaml
Categories:
  - Sports & Health
License: AGPL-3.0-or-later
AuthorName: Tufein and Cercis contributors
SourceCode: https://github.com/Tufein/OpenConnect
IssueTracker: https://github.com/Tufein/OpenConnect/issues
Changelog: https://github.com/Tufein/OpenConnect/blob/main/CHANGELOG.md
RepoType: git
Repo: https://github.com/Tufein/OpenConnect.git

Builds:
  - versionName: 1.13.1
    versionCode: 94
    commit: d36ceecadfea2d5dad6bdd2738f32fa6747dc047   # DRAFT: HEAD when written; replace with the v1.13.1 release commit
    subdir: android
    gradle:
      - store
    output: app/build/outputs/apk/store/release/app-store-release.apk
    gradleprops: openconnect.minify=false

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.13.1
CurrentVersionCode: 94
```

Notes on the fields:

- `gradle: - store` is the F-Droid spelling for the `store` build flavor. F-Droid then runs
  **`assembleStoreRelease`** (its Gradle build is `assemble<Flavor>Release`). Do not write
  `assembleStoreRelease` in the `gradle:` list; that would be treated as a flavor name. The task this project uses
  locally is `:app:bundleStoreRelease` for the AAB and `:app:assembleStoreRelease` for the APK; F-Droid builds the APK.
- `output:` is relative to `subdir` (`android/`). ✅ The path exists in a local build.
- `commit:` must be a **full commit hash**, not a branch or tag name (F-Droid's metadata reference says so). The hash
  above is the store-channel commit, not a released/tagged version. 🚫 Replace it with the final `v1.13.1` commit and create
  the tag before submitting.
- `gradleprops: openconnect.minify=false` is a reproducibility aid, not a requirement. R8 is deterministic within
  one toolchain, but F-Droid's build JDK/SDK may differ. Turning shrinking off makes the APK larger but removes a
  common source of non-reproducible bytecode. Test both ways with `fdroid build --test`; if the minimized build is
  reproducible, drop this line. ⚠️ UNVERIFIED.
- `UpdateCheckMode: Tags` requires `v1.13.1` and later `vX.Y.Z` tags. The repository currently tags releases this way
  (`v1.13.0` is the latest). ✅ Verified (repo); the `v1.13.1` tag is missing.
- Do not add `Name:` or `Summary:`: F-Droid's reference says the localized fastlane metadata in this repository should
  win. The fastlane tree at `fastlane/metadata/android/` is already in the layout F-Droid reads.
- Do not add `AllowedAPKSigningKeys`: F-Droid signs the build with its own key. See the signing section below.

## Reproducible builds

- The local AAB has fixed ZIP timestamps (entries show 1981-01-01), and R8 is used for release. That is a good start,
  not proof. F-Droid verifies reproducibility independently (`fdroid build --test` / the reproducibility badge), and
  the result is **UNVERIFIED** for this app.
- Toolchain in the repository: JDK 17, Gradle 9.6.0 (wrapper), AGP 9.5.2, compileSdk 37.1, build-tools 36. F-Droid's
  buildserver must have platform 37.1 and a compatible JDK. This is a likely **blocker**: the public F-Droid buildserver
  may lag behind compileSdk 37. Confirm before submitting; if it lags, either wait for the buildserver or lower
  compileSdk (a build change, not part of this preparation).
- Dependencies come from `google()` and `mavenCentral()`; F-Droid allows these, but it scans for binary blobs. The
  repository contains fonts, the Unsplash photo `android/app/src/main/res/drawable-nodpi/hero_editorial.jpg` and the
  launcher/vector drawables. They are source assets, not prebuilt libraries, but the Unsplash photo may trigger
  `NonFreeAssets` (see below).
- `gradle.properties`/wrapper must not contain absolute paths or local secrets. Check before submitting. ⚠️
- F-Droid builds with network access disabled after dependency fetching. The app's optional network calls happen at
  runtime, not build time; no build script downloads a service. ✅ Verified (repo).

## Same application ID, different signing key

This is the most important user-facing consequence:

- F-Droid builds `nl.tufein.openconnect` and signs it with the **F-Droid signing key** (unless an `AllowedAPKSigningKeys`
  arrangement is explicitly made; the normal path is F-Droid's key).
- The GitHub APK is signed with the project's release key. A Play build may be signed with Google's Play App Signing
  key (or the upload key, depending on the signing setup).
- Android refuses to install an update over an app signed by a different key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
  The same `applicationId` does **not** make the channels interchangeable. A user switching from GitHub to F-Droid (or
  Play) must export/back up, uninstall, install the other channel, and restore. Health Connect records written by the
  app are separate and may remain.
- F-Droid's own build may start from a debug-signed local APK because `android/app/build.gradle.kts` falls back to the
  debug signing config when `OPENCONNECT_KEYSTORE` is unset. F-Droid normally strips/re-signs the output; this must be
  confirmed on the buildserver. 🚫 Do not assume the debug signature is acceptable. A clean solution is a build that
  produces an unsigned APK for F-Droid, but that is a build-file change outside this lane.
- Never publish the F-Droid-signed APK as a GitHub release and never publish the GitHub APK to F-Droid; the signatures
  and update paths are different.

## Metadata and images

- The fastlane tree is `fastlane/metadata/android/{en-US,nl-NL,de-DE,fr-FR}` with title, short/full description and
  `changelogs/` (93 for the first store build; 94 adds the Cercis rename note). ✅ Verified (repo; character limits checked).
- The 512 x 512 icon is `fastlane/metadata/android/en-US/images/icon.png`, copied from the project's existing
  `docs/img/logo.png`. ✅
- 🚫 `featureGraphic.png` is missing (needs 1024 x 500, a design decision).
- 🚫 Phone screenshots are missing; F-Droid accepts the same fastlane screenshots. Use real store-build captures, not
  Robolectric/JVM images. See `fastlane/metadata/android/en-US/images/phoneScreenshots/README.md`.
- F-Droid can show localized descriptions; the fastlane tree already does. No separate translation work is required
  for the first submission, but F-Droid reviewers may ask for a Dutch/German/French review.

## Blockers before an F-Droid merge request

1. 🚫 **Legal clearance of the name.** The display name is **Cercis** (see [NAME.md](NAME.md)), but no trademark
   clearance has been done. F-Droid already has an OpenConnect VPN client (`net.openconnect_vpn.android`); the app title
   is no longer the same, which removes the exact collision, not the need for a legal check.
2. 🚫 **Release tag.** `v1.13.1` is not tagged. F-Droid needs a full commit hash; `UpdateCheckMode: Tags` needs the tag.
3. 🚫 **Toolchain.** Confirm the F-Droid buildserver has compileSdk/platform 37, build-tools 36, JDK 17, Gradle 9.6.0
   and AGP 9.5.2. If not, the build fails before any policy review.
4. 🚫 **Signing.** Confirm F-Droid's buildserver can produce and sign the APK from a build that currently falls back to
   the debug signing config. Do not submit until the output is understood.
5. 🚫 **Images.** Feature graphic and phone screenshots.
6. ⚠️ **Anti-features.** Decide with F-Droid reviewers whether the optional Garmin/Oura/Claude/Gemini integrations
   warrant `NonFreeNet` (the app works offline and does not depend on them) and whether the Unsplash photo warrants
   `NonFreeAssets` (its licence is not a free-culture licence). Do not hide these; either justify them in the merge
   request or replace the asset.
7. ⚠️ **Privacy policy URL.** Same issue as Play: a GitHub blob is public but mutable. F-Droid may accept it; a stable
   URL is better.
8. ⚠️ **Health Connect.** F-Droid builds the same app, but the Health Connect client library and the declaration are
   Google-side concerns. F-Droid inclusion does not replace Play's Health Connect declaration. Confirm no Google Play
   Services dependency sneaks in.
9. ⚠️ **Real-device testing.** The T1–T17 hardware trial is incomplete. F-Droid does not require it for inclusion, but
   the app should not be advertised as tested when it is not.

## Submission (not done)

1. Fork `fdroiddata`, add `metadata/nl.tufein.openconnect.yml` and the fastlane metadata.
2. Run `fdroid rewritemeta nl.tufein.openconnect`, `fdroid lint nl.tufein.openconnect` and `fdroid build --test
   nl.tufein.openconnect:94`.
3. Open a merge request with the build log, the reproducibility result, the licence/asset notes and the anti-feature
   decision.
4. Answer reviewer questions; do not publish or announce until the F-Droid index actually contains the app.

This repository does not submit to F-Droid automatically; there is no `fdroid` step in `.github/workflows/store.yml`.

## Sources

- F-Droid, [Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/) (`gradle:` flavors,
  `commit:` full hash, `output:`, `UpdateCheckMode`, anti-features).
- F-Droid, [All About Descriptions, Graphics and Screenshots](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/)
  (fastlane metadata layout).
- F-Droid package `net.openconnect_vpn.android` (the existing VPN app; observed 2026-11-25).
- [NAME.md](NAME.md) for the name collision.

_Last written: 2026-11-25, for v1.13.1 / versionCode 94. Display name decided: Cercis. No F-Droid submission has been made._
