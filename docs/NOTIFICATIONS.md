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

## When it does not arrive: "What is happening?"

Since v1.0.0-rc.4 the card has a button **What is happening?**. It shows every step a notification goes through, with a
mark for how far it got, so "it does not arrive" has an answer:

| Step | `[x]` means | `[!]` / `[…]` means |
|------|-------------|---------------------|
| Notification access given | Android lets OpenConnect read notifications | give it (the card says how) |
| Android started the listener | Android runs OpenConnect's listener | the access is on but the listener is not running: switch the access off and on, or restart the phone. The app also asks Android to start it again by itself. |
| Android hands over notifications | at least one notification came in since the app started | waiting for the first one |
| Bluetooth permission, Watch known | as it says | allow it / sync the watch over Bluetooth once |
| Android lets OpenConnect show its persistent notification | notifications are allowed for the app | `[~]`: Android hides the persistent notification (on Android 13 and newer without the notification permission), and ends the app sooner; the hint has a button to the notification settings |
| Connection to the watch | paired, connected and the introductions are done | the step it is on (pairing, connecting, introductions) while it works, or the error when it failed |
| Watch asked for notifications | the watch said it wants them | `[~]`: it never asked, so they are sent anyway; `[!]`: the watch said no (check the watch's own settings under Phone) |
| Notifications shown on the watch | the watch asked what the notifications say and they were delivered; the detail reads *told / confirmed by the watch / details asked / delivered* | `[!]`: the watch was told about notifications but never asked what they say (confirmed, so it is alive and ignores them) or never even confirmed (silent, the link looks dead) |

Under the steps: how many notifications each reason held back since the app started, and the last lines of a **log**. The
log records the listener starting, each notification (the **app's name** and what was decided, never its title or text),
every step of the Bluetooth connection, and every kind of message the watch sends. **Copy the log** puts all of it on the
clipboard, to paste into a message. It lives in memory only (150 lines) and is gone when the app ends.

Hints appear under the status when a step points at something to try: close the Garmin Connect app (only shown when it is
installed), switch off battery optimisation for OpenConnect after a connection that worked and dropped, or the watch's own
notification settings.

A watch that never asks for notifications: Gadgetbridge waits for the watch to send message 5036 before it announces
anything. Not every model sends it at once, or at all, so after 10 seconds of silence OpenConnect sends notifications
anyway (`[~]` on the card). A watch that does not want them ignores them, and a later *no* from the watch still switches
them off. This is a guess about models nobody has tried here; the log says which case you are in.

### A link that has gone silent (v1.0.0-rc.5)

The first log from a real watch (a fenix 8, rc.4) showed the introductions completing and the watch asking for
notifications, but the announcement of a notification (message 5033) was never answered and the watch's own periodic
messages stopped: the link was probably silent while the app still said *connected*. Nothing proves that was the cause,
but rc.5 no longer lets it go unnoticed:

- the reliable layer (MLR) gives up after six resends without an acknowledgement (about 51 seconds) and ends the session,
  so the connection is rebuilt, where it used to resend for ever;
- after 45 seconds of silence the app asks the watch something small (the supported file types);
- a Bluetooth write Android never confirms is released after 4 seconds, and three in a row end the link;
- every message from the watch is answered like Gadgetbridge does: 5042 (music control) with zero commands, anything else
  known but unhandled with *unsupported*;
- the log shows each message sent with the state of the reliable layer, the raw bytes of each announcement, when the watch
  was last heard, and the watch's own answer to an announcement.

A card that says *told 1, confirmed 0, details asked 0* means the watch is silent (switch Bluetooth off and on); *confirmed
1, details asked 0* means the watch is alive and ignores the notification (its own notification settings, or one phone per
watch). Both are inferences from the log, not measurements on every model.

### A watch that stops answering (v1.0.0-rc.6)

With rc.5 the first notification reached a fenix 8 and the later ones did not. In the rc.5 and the rc.4 logs the watch
stops sending messages about a minute after connecting, while the link still acknowledges every packet. Both times it had
sent a batch of protobuf questions (location, calendar, Connect IQ web requests and the like) right after connecting and
the same batch again exactly 60 seconds later, because the app answered only with a plain ACK. rc.6 does two things:

- every protobuf question gets the answer Gadgetbridge gives to what it does not handle: "kept" for each part but the
  last, "discarded, unknown request" for the last part or a whole question; the log names the service, never the content;
- when the watch does not answer an announcement within 5 seconds and has sent nothing at all since, the app connects
  again and sends the missed notification once more (once per notification, only when it is still on the phone and at
  most 2 minutes old), so it arrives about 20 seconds late instead of not at all.

That the watch stalls because it waits for those answers is a hypothesis; the next log shows whether the second round of
questions goes away.

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
- `app/.../notify/ForwardChecks.kt` and `ForwardTrail.kt`: the steps and the hints behind *What is happening?*, and the log.
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
