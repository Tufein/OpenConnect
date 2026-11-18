# Versioning

What a version number of OpenConnect promises. It starts to mean this at **1.0.0**; before that, every `0.x`
release could change anything, and several did.

## Numbers

`MAJOR.MINOR.PATCH`, the same number for the Android app (`versionName`) and the Python server (`pyproject.toml`).
`versionCode` in the app is a plain counter that only ever goes up.

| Change | Number | Example |
|--------|--------|---------|
| Repairs, text, a dependency update, no new feature | PATCH | 1.0.1 |
| A new feature or source, a new setting, a new Android permission with an explanation | MINOR | 1.1.0 |
| Something removed, a data format that cannot be read any more, a higher minimum Android | MAJOR | 2.0.0 |

A release candidate is `1.0.0-rc.1` (and `1.0.0rc1` for Python). It is published as a GitHub **pre-release**, so the
in-app updater, which asks for the latest full release, does not offer it.

## What 1.x keeps

- **Your data.** The database schema never breaks: a new version opens the database of every earlier version
  (a test per schema version proves it) and migrates it without losing anything. A table or field only disappears
  together with a migration or an export, never silently.
- **Your backups.** A backup file made by any 1.x restores in every later 1.x.
- **Your install.** Every release is signed with the same key, so an update installs over the old app and keeps its
  data. If the key ever has to change, the new key is signed in by the old one (APK signature v3 rotation), which
  works from Android 9, the minimum.
- **Your phone.** The lowest supported Android (now **Android 9**) only goes up with a MAJOR version, and the release
  notes say so first. A phone that falls off stays on its last working version.
- **Honest labels.** A source that was never tried on a real device is labelled *Experimental* in the app until it is.

## What 1.x may change

Look and feel, the order of cards, the text of explanations, the formulas behind estimates such as form, readiness and
race predictions (they are rules of thumb, not measurements), and anything marked *Experimental*.

## The Python server

It gets the same number but is not the product of 1.0: it keeps its documented limits ([SECURITY.md](../SECURITY.md))
and only gets security fixes and the same schema promise for its own database.

## Releases

How a release is made is in [ROADMAP.md](ROADMAP.md) (section 9) and the checklist for 1.0 in
[PUBLIC_RELEASE.md](PUBLIC_RELEASE.md). The release notes of every version are in [releases](releases) and, as a short
list, in [../CHANGELOG.md](../CHANGELOG.md).
