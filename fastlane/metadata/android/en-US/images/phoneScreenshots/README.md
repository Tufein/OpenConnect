# Phone screenshots: capture checklist

No phone screenshots are committed in this directory. The files in `android/app/build/screenshots/` are JVM/Robolectric
UI-test captures and **must not** be used as store screenshots. Capture the real store build on a phone or a clean
emulator instead.

## Before you start

- [ ] Build and install the store flavor, not the GitHub flavor:
  `cd android && ./gradlew :app:installStoreRelease`
- [ ] Use demo data (*Settings → Your data → Load demo data*), never a real person's health records.
- [ ] Set the app language you are capturing; do not mix languages in one set.
- [ ] Use the default font size, a clean theme and no accessibility overlay or screen recorder controls.
- [ ] Remove notifications, developer overlays and the USB debugging icon from the status bar where possible.
- [ ] Check that *Settings* does **not** show an Updates card: the store build has no updater.

## Suggested set (2–8 screenshots)

Pick the strongest 5–7, in this order:

1. Home with demo data (readiness, steps, sleep, Body Battery).
2. Health scores and one trend chart.
3. An activity detail with route and charts.
4. Insights week or year overview.
5. Watch screen with watch/ring battery and a live-heart-rate card.
6. *Settings → About and licences → What leaves your phone* (privacy page).
7. An offline report or coach answer with no API key, clearly not a real AI claim.
8. Optional: the same Home screen in Dutch, German or French to show the language picker.

Do not show: a real name, a real Garmin/Oura account, an API key, a token, a notification from another app, a real
route, a real heart-rate trace or a real body weight.

## Capture

With a phone connected over ADB:

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell am force-stop nl.tufein.openconnect
adb shell monkey -p nl.tufein.openconnect -c android.intent.category.LAUNCHER 1
# Navigate to the scene, then:
adb exec-out screencap -p > 01-home.png
```

Or use the phone's own screenshot buttons; both produce a real device capture. Do not add device frames, marketing
text, drop shadows or fake status bars afterwards.

## Check and name

- [ ] PNG or JPEG, portrait 9:16 or landscape 16:9; no stretched or rotated images.
- [ ] At least 2 and at most 8 per device type (Play's current form wins; check it, do not guess).
- [ ] Short side at least 1080 px where the phone allows it; never upscale a smaller capture.
- [ ] The app UI is readable and the screenshot is not blurry or cropped mid-control.
- [ ] Name them `01-home.png`, `02-health.png`, ... so the order is obvious.
- [ ] Put the English set in `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- [ ] Add other languages under their own locale only if the whole set is in that language.
- [ ] Open every file once at 100% and check for personal data before committing.
- [ ] Update `../../../IMAGE_TODO.md` when the set is complete.

The same directory is reused by F-Droid's fastlane metadata; see `docs/store/android/FDROID.md`.
