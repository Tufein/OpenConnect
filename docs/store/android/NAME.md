# The name: facts and a decision list for Stefan

**This file does not choose a name.** `OpenConnect (working title)` in the store metadata is a placeholder, not a
proposal. The decision is Stefan's, and it must be made before the first Play or F-Droid upload.

## Why this is a blocker

“OpenConnect” is the established name of a **VPN client**, not a fitness app. It is not a rare or ambiguous word in the
Android ecosystem:

| Fact | Detail | Source / date |
|---|---|---|
| The OpenConnect VPN client | Cross-platform multi-protocol SSL VPN client (Cisco AnyConnect, GlobalProtect, Pulse, FortiGate, F5, Array, Juniper). Original author David Woodhouse; first release 2009; LGPL-2.1. | <https://www.infradead.org/openconnect/>, Wikipedia, observed 2026-11-25 |
| Android VPN app on Google Play | App titled exactly **“OpenConnect”**, package `com.github.digitalsoftwaresolutions.openconnect`, developer Innovative Software Technology SPC, **1M+ downloads**, contains ads and in-app purchases, VPN client. | Play Store listing, observed 2026-11-25 |
| Android VPN app on F-Droid | App titled **“OpenConnect”**, package `net.openconnect_vpn.android`; “Android UI for OpenConnect VPN client”; GPLv2; built and signed by F-Droid. | <https://f-droid.org/en/packages/net.openconnect_vpn.android>, observed 2026-11-25 |
| A separate company name | USPTO records show a mark “OPENCONNECT SYSTEMS” (registration 1718746, serial 73814189), unrelated to this project. The live status and the classes it covers are **not verified**. | USPTO Assignment Center, observed 2026-11-25 |
| Another “Open Connect” | Netflix’s Open Connect content-delivery network uses the name in a different field. | Wikipedia disambiguation |
| This project | An Android fitness/health dashboard, package `nl.tufein.openconnect`, AGPL-3.0-or-later. It is not the VPN client and shares no code with it. | this repository |

**Consequences.**

- Play and F-Droid search will show the VPN app for “OpenConnect”; users looking for a VPN may install this app, and
  users looking for a fitness app may skip it. The titles are identical today.
- The Play VPN listing has 1M+ downloads and ads. A new health app with the same name inherits that association.
- A trademark complaint or a Play impersonation review is conceivable. The official OpenConnect project is not
  affiliated with this app, and this app must not imply that it is.
- The current store title “OpenConnect (working title)” is not shippable: a store listing with a parenthetical working
  title looks unfinished and still contains the conflicting word.
- The package id `nl.tufein.openconnect` is less visible than the title, but it is permanent once the first store
  release is published. It can stay even if the display name changes.

## What is verified, and what is not

- ✅ The app's package, version, licence and the absence of a VPN implementation are verified in this repository.
- ✅ The Play and F-Droid listings above were observed on 2026-11-25; store listings change, so re-check before deciding.
- ⚠️ **Trademark status is unverified.** This document is not a legal opinion. The USPTO record is a company-name
  record, not proof that the word “OpenConnect” is free or that it is registered for software.
- ⚠️ **Play/F-Droid enforcement is unverified.** Neither store has been asked about this app's name; no upload has
  been made.
- ⚠️ **The final name's availability is unverified.** Any candidate must be searched on Play, F-Droid, Connect IQ,
  GitHub, the app stores in the target countries, USPTO/EUIPO/BOIP/DPMA and the relevant domains/handles.

## Decision list (for Stefan)

Answer these in order; record the date and the answer in this file.

1. **Do we keep “OpenConnect” as the display name at all?**
   - [ ] Keep it, with a clear “not the VPN client” disclaimer and accept the search confusion.
   - [ ] Qualify it with a distinctive word (placeholder: `OpenConnect <qualifier>`).
   - [ ] Replace the display name entirely.
   - This is a product/legal decision, not an engineering one. The repository can implement any of the three.

2. **Do we change the package id `nl.tufein.openconnect` too?**
   - [ ] Keep it. Only the display name and store listings change. Existing GitHub installs keep updating; no data
     migration; same signing key.
   - [ ] Change it. This must happen **before** the first Play/F-Droid upload. It affects the OAuth redirect
     (`openconnect://oura`), the GitHub FileProvider authority, the app id in docs/tests, and every future update;
     existing GitHub installs cannot update over the new id and need an export/import.
   - Changing it after the first store release means a new listing and a visible migration; avoid that.

3. **What happens to the Connect IQ names?**
   - [ ] Keep “OpenConnect Watch Face” / “OpenConnect Widget” as the Garmin listing names.
   - [ ] Rename the display names while keeping the fixed Connect IQ app ids.
   - The Connect IQ app ids are fixed at first upload and must not change; the display names can change in the store
     listing. Align this with the Android name so the project does not have two identities.

4. **What is the legal check?**
   - [ ] Search the final name in the relevant trademark registers and ask a lawyer if the app will be distributed
     commercially or in the EU/US.
   - [ ] Check Play/F-Droid/Connect IQ/GitHub/domain/social handles for the final name before printing it on the icon or
     feature graphic.
   - [ ] Decide whether to add a “not affiliated with the OpenConnect VPN project” line to the store listing and About
     screen. The current draft descriptions already say it; keep or remove it with the final name.

5. **How do we communicate the change?**
   - [ ] If only the display name changes: a normal release note and store listing update; existing installs update
     normally (same applicationId and signing key).
   - [ ] If the package id changes: publish an export/backup guide, a migration release, and a clear “uninstall the old
     app only after exporting” warning.
   - [ ] Tell Connect IQ users separately if the watch listings are renamed.

6. **Who decides and when?**
   - Owner: **Stefan**.
   - Deadline: **before the first Play Console app creation and before the first F-Droid merge request**.
   - No store upload, no public listing and no “working title” release until this file records the decision.

## Constraints that do not decide the name

- The app is AGPL-3.0-or-later; the VPN client is LGPL-2.1. Different code, different licences; there is no licence
  conflict, only a name conflict.
- The Python server in this repository is not the VPN server `ocserv`; it is the optional self-hosted OpenConnect data
  server from v0.1.0. The Android app does not need it.
- The package id can keep the word `openconnect` after a display-name change; users rarely see it, and changing it is
  the expensive option. Do not let the package id force the display name.

## Current state

- Store metadata title: `OpenConnect (working title)` / localized equivalents. **Placeholder.**
- Android app label: `OpenConnect` (`res/values*/strings.xml`). **Not changed in this lane.**
- Connect IQ listings: “OpenConnect Watch Face” and “OpenConnect Widget”. **Not changed in this lane.**
- Decision recorded: **none yet.**
