# Which Garmin devices OpenConnect knows

Until v1.0.0-rc.1 the app was written around one watch, the fenix 8, and only recognised other Garmin devices by a
rough name filter. From **v1.0.0-rc.2** it recognises **every Garmin family** from a catalogue
(`android/core/.../core/watch/GarminDevices.kt` and `GarminModels.kt`, pure Kotlin, covered by `GarminDevicesTest`).

**Recognising is not syncing.** That the app knows a Forerunner 965 says nothing about whether the Bluetooth sync works
with it. **Nothing here has been tried on a real watch**, the fenix 8 included. The catalogue only decides what to list,
what to call it and what to warn about.

## How a device is recognised

Four places use the same catalogue, strongest signal first:

| Where | What it looks at | What it does with the answer |
|-------|------------------|------------------------------|
| Bluetooth search (*Watch, Find watch*) | The **product number** in Garmin's manufacturer data (Bluetooth company id `0x0087`, then the number in two bytes, big end first), then the advertised **name** | Lists the device under its name, leaves out heart rate straps, lights and scales, adds a note for a model that carries a doubt. A watch that advertises no name is still found |
| After the Bluetooth handshake | The watch's own device information: product number, part number (`006-B<number>-00`), names | Names the watch for the activities it brings in; the log line says how it was recognised |
| A FIT file from a watch (USB, folder, Bluetooth) | `file_id`: manufacturer 1 (Garmin) and the product number | The activity gets the real model as its device ("Forerunner 965"), not the name of a cable or a folder |
| The name alone | Lower case, without accents, sizes (`- 47mm`), the ® mark and a leading "Garmin"; `UAT-<number>` names some watches advertise before they have a name | Same model for `vívoactive 5`, `vivoactive 5` and `Garmin VIVOACTIVE 5 - 47mm` |

A name from a family the table does not know a model of yet (say a new *Forerunner 999*) is still recognised as that
family and offered, and says so: the model is shown as the watch named itself.

## What is in the catalogue

265 models in 30 families, 407 Garmin product numbers, plus accessories recognised by name only.

| Kind | Families |
|------|----------|
| Watches | fenix, epix, Enduro, tactix, quatix, MARQ, Forerunner, Venu, vívoactive, vívomove, vívosmart, vívofit, vívosport, vívoki, Instinct, Descent, Lily, Swim, Bounce, D2, Legacy, Cirqa, Approach S |
| Bike computers | Edge |
| Golf devices | Approach G, X and Z |
| Handhelds (recognised, not offered for Bluetooth sync) | eTrex, Oregon, Rino, GPSMAP, Foretrex |
| Accessories and cameras (recognised so they are **never offered as a watch**) | HRM straps, Varia, Vector, Rally, Tacx, tempe, Index, VIRB, Edge Remote |

**Where the data comes from.** Product numbers and names are the `garmin_product` list in Garmin's FIT profile
(version 21.222): a number is a fact about a product, so only numbers and names were taken, none of Garmin's SDK code.
Regional variants (`_asia`, `_japan`, …) are folded into one model and the sizes of a model share one entry. Variants
that Garmin gives no number of their own (Pro, Sapphire, Solar, Tactical) are found by name only. Whether a model is on
Gadgetbridge's list (159 of the 265 are) was read from the device names Gadgetbridge's Garmin support accepts; the Bluetooth
sync was ported from the same project.

## What the app says about syncing, and why

Each model carries one of four answers (`BleSync`), shown as a note under the device in the search list:

| Answer | Meaning | Shown |
|--------|---------|-------|
| `LISTED` | Gadgetbridge supports exactly this model with the classic sync that OpenConnect has (156 models) | nothing |
| `NEEDS_NEWER_PROTOCOL` | Gadgetbridge switches its *new sync protocol* on for this model because it misses files without it (Venu 4, Venu X1, Cirqa Smart Band). OpenConnect only has the classic one | "…files may be missing" |
| `UNKNOWN` | Not on Gadgetbridge's list (Edge, MARQ, Approach, the older families) or the model is not in the table: it may work, nobody has said so | "…nothing was tried on this model" |
| `NOT_APPLICABLE` | Accessory, camera or handheld | not offered |

**This is a statement about what Gadgetbridge supports, not about what OpenConnect achieved.** A `LISTED` model can
still fail here; the first real sync of any watch is a trial (T7 in [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md)).

## Other routes, which do not depend on the model

- **Garmin account** (Personal API or the Connect Developer API): the activities come with the device's name as Garmin
  gives it; nothing here depends on the model.
- **USB**: every Garmin device has USB vendor id `0x091E`. Newer watches are meant to show up as MTP; if an older one
  shows up as a drive instead, read its `GARMIN` folder with *Pick a folder*. Which model does which was not checked.
- **Health Connect** and **a folder of FIT files**: no device model involved.

## Connect IQ watch faces

The Monkey C faces and the `.iq` package are not tied to the fenix 8 either: `watch/build.sh --device <id>` and
`--products <id>,<id>,…` take any Connect IQ device id. **Connect IQ ids are not FIT product numbers** (`fenix847mm`,
`fr965`, …) and are not in the catalogue: Garmin's SDK Manager shows them, and `monkeyc` refuses an id whose device
files are not downloaded. The default list is still the five fenix 8 models because those are the ids that were
checked against Garmin's SDK documentation. The faces are drawn in fractions of the screen so round screens fit;
rectangular watches (Venu Sq, older vívoactive) were not designed for. The Monkey C has never been compiled.

## Adding or fixing a model

1. Add a row to `GarminModels.kt`: the name as the watch advertises it **without the size**, the product numbers from
   the FIT profile if Garmin gave it any, `gb = true` if Gadgetbridge has a device of that name.
2. Add the name to `GarminDevicesTest`. Two models may not share a name or a number: `GarminDevices` refuses to start
   when they do, and a test says which.
3. A name that differs only by capitals is the one exception (the first *epix* advertises `EPIX`, the second
   generation `epix - 47mm`): see `byExactName`.

## Not done, on purpose

- No attempt to sync from a model the table calls `UNKNOWN`; they are offered, with a note.
- No Python server catalogue: the server gets the device name from Garmin's API, and its FIT import does not read
  `file_id` yet.
- The newer Garmin sync protocol (what the three `NEEDS_NEWER_PROTOCOL` models need) is not built.
- No ANT+ or Bluetooth Classic devices.
