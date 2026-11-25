# Install and update OpenConnect

OpenConnect is an Android app that is published as an APK file on GitHub, signed with one fixed key. There is no Google
Play or F-Droid listing yet. This page takes you from nothing to a running app, shows how to check the download, and how
to update.

**You need:** a phone with **Android 9 or newer**, about 30 MB free (the app itself is about 10 MB), and nothing else: no
account, no server. The app targets Android 16 (API 36). (A phone with Android 8 stays on v0.51.0.)

## 1. Download

1. On your phone, open the **[latest release](https://github.com/Tufein/OpenConnect/releases/latest)**.
2. Under *Assets*, tap `openconnect-vX.Y.Z.apk` to download it. Also download `openconnect-vX.Y.Z.apk.sha256` if you want to
   check it (step 2). Ignore the `.whl` and `.tar.gz` files: those are the Python server.
3. A release whose title says *Pre-release* is a candidate for the next version; use the latest one that is **not** marked so,
   unless you want to help test.

## 2. Check the download (optional, recommended)

The `.sha256` file holds the file's fingerprint. The download is intact when your phone or computer computes the same one.

- **On a computer:** put both files in one folder and run `sha256sum -c openconnect-vX.Y.Z.apk.sha256` (Linux, macOS:
  `shasum -a 256 -c …`). It must say `OK`. On Windows: `Get-FileHash .\openconnect-vX.Y.Z.apk` in PowerShell and compare the
  value with the text in the `.sha256` file.
- **On the phone:** any file-hash app will do (look for one that shows SHA-256), or the Termux app with `sha256sum`.
- **Who signed it:** every release is signed with the same key. Its certificate fingerprint is in [SECURITY.md](../SECURITY.md)
  (`06b67b82…dbdf3b7`). On a computer with the Android build tools: `apksigner verify --print-certs openconnect-vX.Y.Z.apk`
  prints the same fingerprint. The app's updater checks the checksum and the signer for you (see below).

## 3. Install

1. Open the downloaded file (from the browser's download notification or the *Files*/*Downloads* app).
2. Android says it does not allow your browser (or file manager) to install apps. This is the *Install unknown apps* switch:
   tap **Settings**, switch **Allow from this source** on for that one app, go back and tap **Install**. You can switch it
   off again afterwards; it is per app, not for the whole phone.
3. If Google Play Protect asks whether to scan the app, you can choose to scan or to install anyway: the app is not in the
   Play Store, so it has no Play reputation yet.
4. Open **OpenConnect**, answer the first-start questions (which devices you use) and tap **Load demo data** to look around
   before you connect anything.

If Android says **"App not installed"**: the most common cause is an older copy of OpenConnect that was signed with a
different key (a build someone made themselves). Uninstalling that copy first fixes it; this deletes its data, so make a
backup first if it holds anything you need.

## 4. Update

- **In the app (GitHub build):** *Settings, Updates, Check for updates*. It asks GitHub for the newest release, downloads the
  APK, checks the checksum and that it is signed with the same key as the installed app, and only then asks Android to
  install it. A release without a checksum, or signed with a different key, is refused with a message that says why. The
  store build has no updater.
- **By hand:** download the newer APK as in step 1 and open it. It installs **over** the old one and keeps your data and
  your keys. Android refuses to go back to an older version over a newer one.
- **With Obtainium** (an app that follows GitHub releases; not tried by this project): add the app with the address
  `https://github.com/Tufein/OpenConnect`. If it offers the Python files as well, set *Filter APKs by regular expression*
  to `openconnect-v.*\.apk$`, and leave pre-releases off.

## 5. The store build (not yet available)

Since v1.13.0 the app can also be built as a Google Play/F-Droid **`store` variant**, but it is **not published**: there
is no Play or F-Droid listing. It has the same app id (`nl.tufein.openconnect`), but a store build is signed with a
different key than the GitHub APK. Android therefore sees a different app: you cannot install it over the GitHub build,
or switch back, without uninstalling.

To move: *Settings, Your data, Back up*, copy the backup file, uninstall OpenConnect, install the store build, then
*Restore*. The backup never contains keys: enter your Garmin key and AI keys again. The store build has no in-app
updater, no install-packages permission and no FileProvider; the GitHub build keeps all three. Play and F-Droid remain
blocked by the name, an account and testers, real screenshots and store declarations.

## 6. Move to a new phone, or start over

Your data lives only on the phone. To move it: *Settings, Your data, Back up* on the old phone, copy the file, then *Restore*
on the new one. The backup holds activities, days, nights, the journal, shoes, goals and your plan, **not your keys**: enter
your Garmin key and AI keys again. *Export everything (ZIP)* writes plain CSV and JSON files if you want to keep a copy in a
format any program can read.

To remove everything: *Settings, Your data, Delete all data*, or uninstall the app.

## If something does not work

*Settings, About and licences, Share diagnostics* shows a report (no keys, tokens, names, health values or routes) that you
can paste into an [issue](https://github.com/Tufein/OpenConnect/issues/new/choose). The *Hardware report* form there is for
"I tried it with my watch, ring or band": it says what worked and what did not, which is how a source stops being labelled
*Experimental*.
