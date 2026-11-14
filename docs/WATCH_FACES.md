# OpenConnect watch faces

The `watch/` folder holds a Garmin Connect IQ watch face in Monkey C, in three designs that follow the app: **Clean**
(a ring around the big time), **Dashboard** (four tiles on black) and **Retro** (a segment bar on indigo). The app's
*Settings, Watch face preview* shows each one with your numbers and gives the command that builds it.

## What is and is not possible

- A face runs **on the watch** and shows what the watch itself knows: Body Battery, heart rate, stress, steps and floors.
  It needs no phone.
- There is **no Sleep field**: Garmin's API gives a face no length of last night's sleep.
- OpenConnect's own readiness score cannot go to the watch without Garmin's phone app, so it is not on the face.
- The phone **cannot switch the active face**, and a face has no menu on the watch. You pick the face on the watch
  (hold the menu button, *Watch Face*). Colour and fields are fixed **when the face is built**, one `.prg` per variant,
  **unless the face comes from the Connect IQ store**: then design, colour and the four fields are settings in Garmin's
  Connect IQ app (since v0.24.0, see below).
- The app copies a `.prg` to `GARMIN/Apps` over USB (*Settings, Watch face*, since v0.18.0).

## Build one

You need three things, once:

1. **The Connect IQ SDK and your watch's device files.** Garmin's SDK Manager downloads both and asks for your Garmin
   login. The SDK alone is not enough: `monkeyc` refuses a device id whose files are not downloaded. In the SDK Manager
   pick the device that is yours (the app offers `fenix843mm`, `fenix847mm`, `fenix8pro47mm`, `fenix8solar47mm` and `fenix8solar51mm`; the Solar ones have a different screen than the AMOLED ones, and the preview is drawn for the AMOLED).
2. **A developer key:** `watch/build.sh --make-key` creates `watch/developer_key.der`. Keep it; faces built with another
   key do not replace each other.
3. **`bash`, `openssl` and Java.**

Then copy the command from the app (*Copy build command*), or write it yourself:

```
watch/build.sh --device fenix847mm --design retro --color pink --fields steps,battery,heart,stress
```

- `--design`: `clean`, `dashboard` or `retro`.
- `--type`: `watchface` (default) or `widget`. A widget has one look, so `--design` is ignored for it.
- `--color`: `mint`, `blue`, `amber`, `pink`, `green` or a hex colour such as `12ab34`.
- `--all-colors`: one file per colour (mint, blue, amber, pink, green) instead of `--color`.
- `--fields`: four different ones of `battery`, `heart`, `stress`, `steps`, `floors`. The first is the big one.
- `--sdk` or `CIQ_SDK`: the SDK folder, when it is not under `~/.Garmin/ConnectIQ/Sdks`.
- `--key` or `CIQ_KEY`: another developer key.
- `--dry-run`: only write the generated files into `watch/gen/` (used by the tests).

The result is `watch/out/openconnect-<design>-<COLOUR HEX>-<device>.prg`, for example `openconnect-retro-FF5FA2-fenix847mm.prg`. The app shows this name for the face you chose, so you pick the right file when you install it. Every design and colour gets its own app id, so
several variants can sit on the watch side by side.

## The widget and its glance (v0.23.0)

`watch/build.sh --type widget --device fenix847mm --color mint` builds a second app, `openconnect-widget-<COLOUR>-<device>.prg`:

- **Glance:** in the glance list (hold the up button, or swipe from the watch face) it shows `OPENCONNECT` and your first
  three fields on one line, for example `Battery 72  HR 52  Stress 28`.
- **Widget:** open it from the glance for one screen: the first field as a ring, all four fields as rows, and a line of
  the Body Battery of the last 12 hours.
- **Sleep is not in it.** A Connect IQ app on the watch cannot read how long you slept (there is no API for it), so the
  roadmap's sleep screen is not possible. The fields are the same watch-native ones as the faces: `battery`, `heart`,
  `stress`, `steps`, `floors`.
- A glance has very little memory. `Config`, `Data` and the glance class carry the `(:glance)` annotation for that; if
  the glance shows nothing on the watch, send the message and it gets simpler.

## For the Connect IQ store (v0.24.0)

`watch/build.sh --package` (with `--type watchface` or `--type widget`) packs one `.iq` file instead of a `.prg`: every
fenix 8 model in one file (`--products` changes the list), one fixed app id per app, and settings. The face reads
`Application.Properties` through `source/Prefs.mc`: `Design`, `Colour` and `Field1` to `Field4`, with the values the
package was built with as defaults (`resources/settings` and `resources/properties` are written into `gen/`). A value
that is missing or not valid falls back to the built-in `Config`, so the screen is never empty. English and Dutch names
are in `resources/strings` and `resources-nld`.

`watch/check-store.sh` checks everything that does not need Garmin's login. The whole route, with what only the owner
can do, is in [CONNECT_IQ_STORE.md](CONNECT_IQ_STORE.md).

## How it works

- `build.sh` writes `gen/source/Config.mc` (design, colour, fields), `gen/resources/strings/strings.xml` and
  `gen/manifest.xml` from the template `manifest.xml`, then runs `monkeyc` with `monkey.jungle`.
- `source/Data.mc` reads the values (`ActivityMonitor`, `Activity`, `SensorHistory`, `UserProfile`); a missing value is
  a dash. `source/Faces.mc` draws the three designs with fractions of the screen size, so 416 and 454 px displays fit.
  `source/FaceView.mc` picks the design and shows only the time while an AMOLED wrist is down (burn-in protection).
- The Python tests run `build.sh --dry-run` against a copy of the folder (`tests/test_watch_build.py`, `tests/test_watch_store.py`).

## What has not been checked

The widget and glance (`OpenConnectWidgetApp.mc`, `WidgetView.mc`, `OpenConnectGlance.mc`) are in the same position. The Monkey C has **not been compiled or run**: the device files need a Garmin login, which the build environment does
not have. The names it uses (`getBodyBatteryHistory`, `currentHeartRate`, `restingHeartRate`, `floorsClimbed`, `drawArc`
and the font and justify constants) were checked against the Connect IQ 9.3.0 API list, but a compile on your machine
can still find mistakes. If `monkeyc` reports one, send the message and it is fixed in the next version. The look on the
real watch also differs from the preview: the watch has no pixel font, so Retro uses blocks and colours instead.
