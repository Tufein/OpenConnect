# Store images: what exists and what is still missing

This file is intentionally a checklist, not a set of placeholders. Do not add mock-ups, fake phone frames, upscaled
UI-test renders or invented branding: Play and F-Droid reviewers can tell, and the project should show the real app.

## Present

- `fastlane/metadata/android/en-US/images/icon.png` — copied byte-for-byte from `docs/img/logo.png` (the project's
  existing 512 x 512 launcher mark; 8-bit RGBA PNG). No new artwork was drawn.
  Check before upload: 512 x 512 px, PNG, at most 1 MiB, no text. This is a source asset, not a Play Console render.

## Missing (blockers for a store upload)

1. **Feature graphic — `fastlane/metadata/android/en-US/images/featureGraphic.png`**
   - Required size: **1024 x 500 px**, PNG or JPEG, no transparency recommended, no important text near the edges.
   - Why it is not here: the repository has a square 512 x 512 mark (`docs/img/logo.png` / `logo.svg`) but no
     banner-shaped artwork. Making a 1024 x 500 banner means choosing a composition, background, type and crop; that
     is a design decision, not a mechanical resize. It would invent branding, so it is left to Stefan/design.
   - Do not use `docs/img/android.png` (an old UI-test render) or any file in `android/app/build/screenshots/`
     (Robolectric UI-test captures) as the feature graphic.

2. **Phone screenshots — `fastlane/metadata/android/en-US/images/phoneScreenshots/`**
   - Required: 2–8 real screenshots per device type (Play's current upload form decides the exact count and size).
   - Capture them on a real phone or a clean emulator running the **store** build, not from JVM/Robolectric tests.
   - The exact scene list and commands are in
     `en-US/images/phoneScreenshots/README.md` (this file and that directory share the same fastlane tree).
   - No screenshots are committed here: an empty directory would be mistaken for a finished listing.

3. **Optional tablet screenshots** — only if Stefan decides to list tablet support. Capture 7-inch and 10-inch sets
   from the same store build; do not stretch phone screenshots.

## Acceptance

- [ ] `featureGraphic.png` is 1024 x 500 and readable at small size.
- [ ] 2–8 phone screenshots, all from the store build, with demo data and no personal information.
- [ ] No screenshot shows the GitHub updater, a real API key, a real Garmin/Oura account or a real health record.
- [ ] Icon, feature graphic and screenshots pass the current Play Console preview.
- [ ] Remove this file only when all three items above are true (or keep it as a record).
