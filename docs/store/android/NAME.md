# The name: Cercis (decided 2026-11-25)

**Decision: Cercis is the product display name.** It was chosen on 2026-11-25 after the availability check below. The
package id `nl.tufein.openconnect` stays. No trademark clearance has been done, so Play and F-Droid publication remains
blocked on legal review. Historical release notes keep the old name.

## Decision record

| Item | Decision |
|---|---|
| Display name | **Cercis**, chosen 2026-11-25 |
| Basis | Availability check on Play, F-Droid, GitHub and domains (table below); **not** a trademark clearance |
| Package id | Stays `nl.tufein.openconnect`; the OAuth scheme, asset names and repository URLs stay |
| Connect IQ display names | **Cercis Watch Face** and **Cercis Widget**; the fixed app ids stay |
| Historical release notes | Keep the old display name |
| Legal clearance | **Not done**; Play/F-Droid stay blocked until it is |

## Why this was a blocker

“OpenConnect” is the established name of a **VPN client**, not a fitness app. It is not a rare or ambiguous word in the
Android ecosystem:

| Fact | Detail | Source / date |
|---|---|---|
| The OpenConnect VPN client | Cross-platform multi-protocol SSL VPN client (Cisco AnyConnect, GlobalProtect, Pulse, FortiGate, F5, Array, Juniper). Original author David Woodhouse; first release 2009; LGPL-2.1. | <https://www.infradead.org/openconnect/>, Wikipedia, observed 2026-11-25 |
| Android VPN app on Google Play | App titled exactly **“OpenConnect”**, package `com.github.digitalsoftwaresolutions.openconnect`, developer Innovative Software Technology SPC, **1M+ downloads**, contains ads and in-app purchases, VPN client. | Play Store listing, observed 2026-11-25 |
| Android VPN app on F-Droid | App titled **“OpenConnect”**, package `net.openconnect_vpn.android`; “Android UI for OpenConnect VPN client”; GPLv2; built and signed by F-Droid. | <https://f-droid.org/en/packages/net.openconnect_vpn.android>, observed 2026-11-25 |
| A separate company name | USPTO records show a mark “OPENCONNECT SYSTEMS” (registration 1718746, serial 73814189), unrelated to this project. The live status and the classes it covers are **not verified**. | USPTO Assignment Center, observed 2026-11-25 |
| Another “Open Connect” | Netflix’s Open Connect content-delivery network uses the name in a different field. | Wikipedia disambiguation |
| This project | An Android fitness/health dashboard with display name **Cercis**, package `nl.tufein.openconnect`, AGPL-3.0-or-later. It is not the VPN client and shares no code with it. | this repository |

**Consequences.**

- Play and F-Droid search showed the VPN app for “OpenConnect”; users looking for a VPN could install this app, and
  users looking for a fitness app could skip it. The old titles were identical.
- The Play VPN listing has 1M+ downloads and ads. A new health app with the same name inherited that association.
- A trademark complaint or a Play impersonation review was conceivable. The official OpenConnect project is not
  affiliated with this app, and this app must not imply that it is.
- The old store title “OpenConnect (working title)” was not shippable: a store listing with a parenthetical working
  title looks unfinished and still contains the conflicting word. The fastlane titles now say `Cercis`.
- The package id `nl.tufein.openconnect` is less visible than the title, but it is permanent once the first store
  release is published. It can stay even if the display name changes.

## What is verified, and what is not

- ✅ The app's package, version, licence and the absence of a VPN implementation are verified in this repository.
- ✅ The Play and F-Droid listings above were observed on 2026-11-25; store listings change, so re-check before printing anything.
- ⚠️ **Trademark status is unverified.** This document is not a legal opinion. The USPTO record is a company-name
  record, not proof that the word “OpenConnect” is free or that it is registered for software.
- ⚠️ **Play/F-Droid enforcement is unverified.** Neither store has been asked about this app's name; no upload has
  been made.
- ⚠️ **Cercis was checked, not cleared.** The 2026-11-25 availability check below found no exact Play/F-Droid app and
  `cercis.health` was free. Re-check Play, F-Droid, Connect IQ, GitHub, the app stores in the target countries,
  USPTO/EUIPO/BOIP/DPMA and the relevant domains/handles before printing anything.

## Availability check (2026-11-25, basis for the decision)

A quick availability check, not a trademark clearance. Play search was queried with exact quotes; F-Droid through its
search page; domains through DNS (NXDOMAIN means no A record, not necessarily that the name is registrable); GitHub
through its repository search. Re-check before printing anything.

| Candidate | Play exact search | F-Droid | GitHub | Domains | Note |
|---|---|---|---|---|---|
| **Tufein Health** | no exact result | no app | 0 repos for the phrase | `tufein.health`, `tufeinhealth.com`, `tufeinhealth.app` NXDOMAIN | Clear, descriptive, owner namespace; close to **Tufin**; trademark not checked |
| **Tufein Pulse** | no exact result | no app | 0 repos for the phrase | `tufeinpulse.com/.app/.health` NXDOMAIN | Brandable and health-adjacent; same Tufin caveat; “Pulse” is generic |
| **Cercis** | no exact result | no app | 37 repos, none an Android health app | `cercis.health` free; `.com` registered | Redbud tree: growth/resilience; short, distinctive, obscure; trademark not checked |
| **Ownbeat** | no exact result parsed | no app | 2 repos | `ownbeat.app` free; `ownbeat.com` registered | “Own your data + heartbeat”; `.com` unavailable |
| **Alnus** | **collision**: Turkish investment app, 10,000+ installs | no app | 37 repos | `.app`/`.health` free, `.com` registered | Alder tree, but the Play collision rules it out |
| **Sorbus** | **collision**: Sorbus Arbor Consulting | no app | 63 repos | `.health` free; `.com`/`.app` registered | Rowan tree; not clean enough |
| **OpenConnect Health** | not checked | no app | phrase not checked | not checked | Still carries the VPN collision; not recommended |

**Decision and reasoning:** **Cercis** was chosen. It is short, botanical (redbud; growth and resilience), has no exact
Play/F-Droid listing in this check, and `cercis.health` is free. It is obscure and `cercis.com` is taken, so a
trademark check is still needed; this availability check is not legal clearance.

`Tufein Health` and `Tufein Pulse` were the alternatives: clearer continuity with the owner namespace and free domains,
but close to **Tufin** (network security) and longer. They were not chosen.

## Decision list (recorded)

Answer these in order; record the date and the answer in this file.

1. **Do we keep “OpenConnect” as the display name at all?**
   - [ ] Keep it, with a clear “not the VPN client” disclaimer and accept the search confusion.
   - [ ] Qualify it with a distinctive word (placeholder: `OpenConnect <qualifier>`).
   - [x] Replace the display name entirely: **Cercis**, decided 2026-11-25.
   - This was a product/legal decision, not an engineering one.

2. **Do we change the package id `nl.tufein.openconnect` too?**
   - [x] Keep it. Only the display name and store listings change. Existing GitHub installs keep updating; no data
     migration; same signing key.
   - [ ] Change it. This must happen **before** the first Play/F-Droid upload. It affects the OAuth redirect
     (`openconnect://oura`), the GitHub FileProvider authority, the app id in docs/tests, and every future update;
     existing GitHub installs cannot update over the new id and need an export/import.
   - Changing it after the first store release means a new listing and a visible migration; avoid that.

3. **What happens to the Connect IQ names?**
   - [ ] Keep “OpenConnect Watch Face” / “OpenConnect Widget” as the Garmin listing names.
   - [x] Rename the display names to **Cercis Watch Face** / **Cercis Widget** while keeping the fixed Connect IQ app ids.
   - The Connect IQ app ids are fixed at first upload and must not change; the display names can change in the store
     listing. The docs and `watch/build.sh` now use the Cercis display names.

4. **What is the legal check?**
   - [ ] Search the final name in the relevant trademark registers and ask a lawyer if the app will be distributed
     commercially or in the EU/US.
   - [ ] Check Play/F-Droid/Connect IQ/GitHub/domain/social handles for the final name before printing it on the icon or
     feature graphic.
   - [ ] Decide whether to add a “not affiliated with the OpenConnect VPN project” line to the store listing and About
     screen. The current draft descriptions keep a “not the OpenConnect VPN client” line; keep or remove it later.

5. **How do we communicate the change?**
   - [x] If only the display name changes: a normal release note and store listing update; existing installs update
     normally (same applicationId and signing key). The fastlane titles and descriptions are updated and
     `changelogs/94.txt` is added in all four locales.
   - [ ] If the package id changes: publish an export/backup guide, a migration release, and a clear “uninstall the old
     app only after exporting” warning.
   - [ ] Tell Connect IQ users separately if the watch listings are renamed.

6. **Who decides and when?**
   - Owner: **Stefan**.
   - Decision date: **2026-11-25** (display name **Cercis**; package kept).
   - Remaining deadline: **legal clearance before the first Play Console app creation and before the first F-Droid merge
     request**.
   - No store upload, no public listing and no “working title” release until legal clearance is recorded.

## Constraints that do not decide the name

- The app is AGPL-3.0-or-later; the VPN client is LGPL-2.1. Different code, different licences; there is no licence
  conflict, only a name conflict.
- The Python server in this repository is not the VPN server `ocserv`; it is the optional self-hosted Cercis data
  server from v0.1.0. The Android app does not need it.
- The package id can keep the word `openconnect` after a display-name change; users rarely see it, and changing it is
  the expensive option. Do not let the package id force the display name.

## Current state

- Store metadata title: `Cercis` in all four locales. **Done (2026-11-25) in the fastlane metadata.**
- Android app label: `Cercis` in `res/values*/strings.xml`. **Done in the Android change that accompanies this rename.**
- Connect IQ listings: “Cercis Watch Face” and “Cercis Widget”, with the fixed Connect IQ app ids. **Updated in the docs
  and `watch/build.sh`.**
- Package id: `nl.tufein.openconnect`. **Unchanged.**
- Decision recorded: **Cercis, 2026-11-25.** Legal clearance: **none yet.**
