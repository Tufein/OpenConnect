# OpenConnect in the Connect IQ store

*Written 2026-11-14 for v0.24.0, from Garmin's developer pages and forum threads (sources at the end).*

## Can the app link straight to the store listing?

**Yes, but only after the app has been published**, and the address cannot be worked out beforehand:

- The address of an app in Garmin's store is `https://apps.garmin.com/apps/<store id>`. Leave the language part
  (`/en-US/`) out and the store opens in the reader's own language.
- The **store id** is a UUID that Garmin issues when the app is uploaded for the first time. It is **not** the id inside
  the app's manifest: Garmin's forum says plainly that "the Store UUID is distinct from the App UUID".
- So until the first upload there is nothing to link to. The app is ready for it: `ConnectIqStore.kt` has two empty
  constants, `WATCHFACE_ID` and `WIDGET_ID`. Paste the store id **or the whole store address** of each published app
  into them, release a new version, and *Settings, Watch face preview, Connect IQ store* shows a button per app that
  opens it. While they are empty the app says that the apps are not in the store yet.

## What is prepared in the repository

| What | Where |
|------|-------|
| Watch face and widget that can be configured in Garmin's Connect IQ app (design, colour, four fields; English and Dutch) | `watch/source/Prefs.mc`, `watch/resources/strings/`, `watch/resources-nld/` |
| A build that makes the `.iq` file the store wants (every fenix 8 model in one file, a fixed app id per app) | `watch/build.sh --package` |
| A check of everything that can be checked without Garmin's login | `watch/check-store.sh`, run by the test suite |
| The store listing texts in English and Dutch | `docs/store/listing-en.md`, `docs/store/listing-nl.md` |
| What to photograph for the listing | `docs/store/SCREENSHOTS.md` |
| The link in the app, with the empty constants | `android/core/.../watch/ConnectIqStore.kt`, the Settings card |

`watch/check-store.sh` generates both packages without compiling, then checks the manifest (version equal to the
project's, products, type, no placeholder left), that every setting has a property and a string in both languages, that
the icon is a PNG and that the listing files exist. With the Connect IQ SDK and `xmllint` on the machine it also
validates the resource files against Garmin's own `resources.xsd`.

## What only you can do

I cannot do these: they need your Garmin login and a watch.

1. **Build once on your computer.** Install Garmin's SDK Manager, download the SDK and the fenix 8 devices (it asks for
   your Garmin login; the SDK alone is not enough, see [WATCH_FACES.md](WATCH_FACES.md)). The Monkey C has never been
   compiled, so the first build can show errors: send the message and it is fixed.
2. **Make a developer key and keep it:** `watch/build.sh --make-key --package --type watchface`. Every update of the
   same app must be signed with the same key. Back it up; losing it means a new listing.
3. **Build both packages:**
   ```
   watch/build.sh --package --type watchface    # watch/out/openconnect-watchface-<version>.iq
   watch/build.sh --package --type widget       # watch/out/openconnect-widget-<version>.iq
   ```
   Optional: `--color`, `--design` and `--fields` set the defaults of the package, and `--products a,b,c` changes the
   watches in it (default: the five fenix 8 models; the faces are drawn in fractions of the screen so other round
   screens fit, but only the fenix 8 is what this was made for).
4. **Try it on your watch first** (the simulator or a USB install) and check that the settings show in Garmin's Connect
   IQ app only after the store has it.
5. **Upload** each `.iq` file at <https://apps.garmin.com/en-US/developer/upload> with your Garmin account. Once the file
   is validated, paste in the text from `docs/store/` and add screenshots (`docs/store/SCREENSHOTS.md`).
6. **Developer details.** Garmin asks first-time developers who want to be visible in the European Economic Area to
   verify who they are: a support email and a city for an individual. Only apps that ask for money (also tips) need the
   fuller trader verification, so a free app does not.
7. **Wait for Garmin's review.** Until it is approved the app is not in the store; you can preview and download it
   yourself in the meantime.
8. **Copy the store id.** Open the app in the developer dashboard (or in the Connect IQ Store app, share) and copy the
   address, then paste it into `ConnectIqStore.WATCHFACE_ID` / `WIDGET_ID` and tell me or make the release yourself.

## Rules to keep

- **The app ids never change.** `build.sh --package` derives them from a fixed text (`openconnect-store-watchface` and
  `openconnect-store-widget`), so every build has the same ones. Garmin registers the id at the first upload and refuses a
  file with another id ("The app ID contained in the manifest file differs from the ID originally registered").
- **The signing key never changes** either.
- The per-colour `.prg` builds (`build.sh` without `--package`) have other ids on purpose, so they can sit next to the
  store version on one watch. They are for people who sideload; settings in Garmin's app only work for store apps.
- Garmin may change its pages. `monkeyc -e` is what packs a `.iq` on the command line; the SDK's own help
  (`monkeyc --help`) says so ("-e, --package-app: Create an application package"). Garmin's submit page only describes
  the export wizard of the Visual Studio Code extension ("Monkey C: Export Project"), which gives the same file.

## Sources

- Garmin, [Submit an App](https://developer.garmin.com/connect-iq/submit-an-app/): export, upload, approval.
- Garmin forum, [How to link to ConnectIQ app with the new apps.garmin.com schema?](https://forums.garmin.com/developer/connect-iq/f/discussion/427745/how-to-link-to-connectiq-app-with-the-new-apps-garmin-com-schema/1996663): the `apps.garmin.com/apps/<id>` form, no language part.
- Garmin forum, [UUID not accepted](https://forums.garmin.com/developer/connect-iq/f/connect-iq-web-store/437376/uuid-not-accepted): the store id differs from the manifest id; the id check on upload.
- Garmin forum, [Trader Verification](https://forums.garmin.com/developer/connect-iq/b/news-announcements?pifragment-928=2) and the developer verification post: who has to verify.
- The Connect IQ SDK 9.3.0: `monkeyc --help`, `bin/resources.xsd`, `samples/ConfigurableWatchFace`.
