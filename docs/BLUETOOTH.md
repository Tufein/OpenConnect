# Syncing a Garmin watch over Bluetooth

Since v0.6.0 the Android app can pull the files from a Garmin watch **over Bluetooth**, with no cable, no Garmin
account and no Garmin Connect app. It speaks Garmin's own Bluetooth protocol, ported from
[Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge). Because Gadgetbridge is AGPL-3.0, OpenConnect is
AGPL-3.0 from this version on (see [NOTICE](../NOTICE)).

> **Not yet tried on a real watch.** It was written for the fenix 8 and checked against real bytes that Gadgetbridge
> recorded from an Instinct 2S, and against a simulated watch in the tests. The first real sync may well fail; the error
> text in the app says at which step.

## How to use it

1. On the watch: *Settings, Connectivity, Phone, Pair phone*.
2. In the app: *Watch, Sync a Garmin watch over Bluetooth, Find watch*. Allow the Bluetooth permission.
3. Tap your watch. Android asks to pair; accept on the phone and on the watch.
4. The app connects, does the handshake, lists the files on the watch and downloads the new ones. Next time a single
   *Sync fenix 8* button is enough; the watch is remembered until you tap *Forget this watch*.

## What is downloaded

| Watch file | Type | Goes to |
|------------|------|---------|
| Activities | 128/4 | Activities, like an imported FIT file |
| Monitoring | 128/32 | Steps, calories, heart rate, stress, Body Battery |
| Sleep | 128/49 | Sleep stages, overnight HRV |
| Health snapshots (HSA) | 128/70 | Heart rate, stress, SpO2, respiration |

Files that were already read are skipped, so a second sync downloads only what is new. The same FIT reader as the USB
route handles them.

## Next to the Garmin Connect app

Can both apps sync the watch over Bluetooth at the same time? **No.** A Garmin watch keeps one phone connection, and
on Android only one app can hold it. Gadgetbridge has the same limit. What does work together:

| Setup | Garmin Connect | OpenConnect gets its data from |
|-------|----------------|--------------------------------|
| **Garmin Connect stays the watch's app** (recommended if you want both) | Syncs as always | Your **Garmin account** (*Garmin account* screen, your own key; also in the background), or a USB cable or folder of FIT files |
| OpenConnect is the watch's app | Stops syncing until you pair it again | Bluetooth, straight from the watch |

Since v0.7.0 a run that arrives by more than one route (watch file and Garmin account) is recognised by its start time
and stored once, in whichever order they arrive.

## How it works in the code

All protocol code is plain Kotlin in `android/core/.../core/watch/ble/`, tested on the computer without Android:

| File | What it does |
|------|--------------|
| `Framing.kt` | Garmin's CRC-16 and COBS framing (every message is wrapped so a zero byte marks its end). |
| `MultiLink.kt` | The "multi-link" service `6a4e2800-…`: handle registration on handle 0, and **MLR**, a small TCP-like layer with sequence numbers, acknowledgements and resends. |
| `Gfdi.kt` | GFDI messages (`[length][type][payload][crc]`): device information, configuration, system events, the file directory and file transfer. |
| `GarminBleSession.kt` | The conversation: close old handles, register GFDI, answer the watch's hello and configuration, then list, download and finish. |
| `GarminBleWatchSource.kt` | Makes the watch look like a folder of FIT files to the existing sync. |

The Android side (`app/.../watch/AndroidGarminBleLink.kt`) does the scanning, pairing (bonding), GATT connection, MTU
request and notifications, and pushes bytes in and out of the session.

## Limits

- Not tested on real hardware yet.
- A watch is paired with one phone app at a time. **Garmin Connect on the same phone will likely stop syncing** while
  OpenConnect is paired, and the other way around.
- Files are not marked as archived on the watch, so Garmin Connect would still see them as new.
- Only the classic file sync is built, which is what Gadgetbridge uses for the fenix 8. The newer protobuf file sync,
  Bluetooth Classic (RFCOMM), notifications, weather, GPS updates and settings are not.
- The background sync job does not use Bluetooth yet; tap *Sync* on the Watch screen.
