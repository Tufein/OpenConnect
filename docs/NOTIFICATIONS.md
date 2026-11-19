# Notifications on the watch

Since v1.0.0-rc.3 OpenConnect can show your phone's notifications on a Garmin watch: a message from a chat app, an
e-mail, a calendar reminder. It does what Gadgetbridge and the Garmin Connect app do, over the same Bluetooth connection
OpenConnect already uses to sync the watch. The protocol is ported from
[Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) (AGPL-3.0, like OpenConnect).

> **Not yet tried on a real watch, the fenix 8 included.** The messages follow Gadgetbridge's code and are tested against
> a pretend watch in the tests (`GarminBleNotificationsTest`), not against a real Garmin. The first real notification may
> not show, or show without its text. *Settings, Watch, Notifications on the watch, Send a test to the watch* tells you in
> a few seconds whether it works on your model.

## How to switch it on

1. *Watch, Notifications on the watch*, switch **Send notifications to the watch** on.
2. Sync the watch over Bluetooth once first (see [BLUETOOTH.md](BLUETOOTH.md)), so OpenConnect knows which watch to use.
3. Give OpenConnect **notification access**. The button opens Android's own screen; switch OpenConnect on there.
   - On **Android 13 and newer**, an app installed from a file (not from a store) cannot be given notification access until
     you allow it: open the app info (the second button), tap the three dots in the top corner, choose *Allow restricted
     settings*, then go back and try again. Android requires this step; the app cannot do it for you.
4. Allow the Bluetooth permission (*Nearby devices*) when asked.
5. **Choose apps.** Nothing is forwarded until you switch an app on: only the apps you choose go to the watch.
6. *Send a test to the watch.* If it shows, you are done.

Two options: **Hide the text on the watch** (the watch then shows only the app's name and *New notification*), and **Only
while the phone screen is off** (while you look at the phone, the watch stays quiet).

## What is forwarded, and what is not

A notification goes to the watch when **all** of these hold, in this order (the first reason that applies is shown on the
card as *Last notification not sent*):

| Held back when | Why |
|----------------|-----|
| the app is not chosen | only the apps you chose |
| it comes from OpenConnect itself | the app's own notifications stay on the phone |
| it is *ongoing* (music player, timer, download, a running service) | it would buzz the watch every second |
| it is the *summary of a stack* | the single notifications of the stack are sent instead |
| the app marked it *local only* | the app asked for it to stay on the phone |
| it is a call | the watch could show it but not answer it |
| it is a quiet notification (importance below default) | no sound on the phone, no buzz on the wrist |
| Do Not Disturb hides it | what the phone hides, the watch hides |
| the phone screen is on and *Only while the screen is off* is switched on | you are looking at it |
| it has no title and no text | nothing to show |
| the same words were sent for it already | an app that posts the same notification again and again (a progress bar) buzzes once |
| there is no connection to the watch right now | it is dropped, not queued for later |
| the watch has notifications switched off | see the watch's own settings |

A notification that you swipe away on the phone disappears from the watch, and one you dismiss on the watch is swiped
away on the phone. Title, text and the time are sent; icons and pictures are not. **Answering a message or taking a call
from the watch is not supported.**

## One watch, one phone

A Garmin watch talks to **one phone app at a time**. While forwarding is on, OpenConnect holds the Bluetooth connection all
day, so the **Garmin Connect app on the same phone cannot connect to the same watch**. You can choose:

- OpenConnect is the watch's phone (this feature, plus the Bluetooth sync), or
- Garmin Connect stays the watch's phone, and OpenConnect reads your data from your Garmin account or from files, without
  notifications.

Switch forwarding off (or *Turn off* on the persistent notification) to give the connection back.

A sync you start while forwarding is on **reads through the same connection**, so nothing is opened twice. A background or
manual sync that opens a connection of its own makes forwarding wait until the sync is over, and forwarding then
reconnects by itself.

## What the app keeps running

- A **persistent notification** (*Notifications to the watch*, with a *Turn off* button) is shown while the connection is
  kept or sought. It is what stops Android from ending the app and from taking the Bluetooth connection away. It is a
  foreground service of the *connected device* type, like the live heart-rate recording. Android 13 and newer ask
  permission to show notifications; without it forwarding still works, but no notification is shown and Android may stop
  the app sooner.
- A **notification listener** (the notification access you gave) runs as long as that access is on.
- When the watch goes out of reach or switches off, the connection is tried again by itself, with longer waits (10 s, 20 s,
  40 s, up to 5 minutes). *Try now* skips the wait. A pairing problem or a missing permission is **not** retried until you
  fix it and look at the screen again.

Phone makers differ in how hard they limit apps in the background. If notifications stop arriving after a while, switch off
battery optimisation for OpenConnect in Android's settings (and see <https://dontkillmyapp.com> for the phone).

## Privacy

The text of your notifications goes **from the phone to the watch over Bluetooth and nowhere else**. The app does not store
it, does not write it in a log, does not send it to a server (there is none) and does not hand it to an AI provider. Only
the person-chosen apps' notifications are looked at. Without notification access the app sees none of them. You can take
the access back in Android at any time. The sync log never contains notification text.

## How it works (for the curious)

Garmin's GFDI protocol over the multi-link Bluetooth service, the same one the file sync uses:

| Message | Direction | Meaning |
|---------|-----------|---------|
| 5036 `NOTIFICATION_SUBSCRIPTION` | watch → phone | the watch wants notifications (or not); the phone answers *enabled* or *disabled* |
| 5033 `NOTIFICATION_UPDATE` | phone → watch | a notification was added, changed or removed: id, category, flags |
| 5034 `NOTIFICATION_CONTROL` | watch → phone | the watch asks for the attributes of a notification (title, text, date, actions), for the name of an app, or does an action (dismiss) |
| 5035 `NOTIFICATION_DATA` | phone → watch | the answer, in chunks of at most 300 bytes with a running checksum; the watch acknowledges each chunk |

Code, all tested without a phone:

- `core/watch/ble/GarminNotifications.kt`: the messages, the attribute values and the conversation (`GarminNotifications`:
  what has been announced, what the watch asked for, how a long text is cut into chunks and resent, what happens when the
  watch is not ready).
- `core/watch/NotificationRules.kt`: the decision table above, and `RepeatFilter`.
- `core/watch/ble/GarminBleSession.kt`: routes the four message types to the conversation when notifications are on, and
  tells the watch *disabled* when they are off.
- `app/.../notify/NotificationForwarder.kt`: keeps the one connection, retries, counts, and exposes the state of the card.
- `app/.../notify/PhoneNotificationListener.kt` and `StatusBarMapper.kt`: Android's side; they only describe a notification
  and hand it over.
- `app/.../watch/LiveWatchRegistry.kt`: how a sync and the notification connection share the watch.

## What was not tried

- **Anything on a real watch**, the fenix 8 included. The attribute layouts and the action label follow Gadgetbridge; whether
  a given model shows the text, the date or the dismiss button the same way is unknown.
- **The listener and the foreground service on a real phone**: Android's rules differ between phone makers and versions.
- **Starting the connection after a phone restart** while the app is not open: the listener is started by Android once
  notification access is on, and that starts the connection, but this was not seen on a phone.
- **Calls, replying from the watch, and actions other than dismiss** are not built.
