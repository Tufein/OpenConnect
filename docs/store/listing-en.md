# Connect IQ store listing: English

Paste these into the upload form of the Connect IQ store (see [CONNECT_IQ_STORE.md](../CONNECT_IQ_STORE.md)). The form
decides which fields exist and how long they may be; shorten where it asks. Two apps are listed: the **watch face**
and the **widget** (with its glance).

## Watch face

**Name:** OpenConnect Watch Face

**Tagline:** Your Body Battery, heart rate, stress and steps on a clean, dashboard or retro face.

**Description:**

OpenConnect Watch Face puts what your watch already knows on your wrist, in three designs you pick in Garmin's Connect
IQ app:

- **Clean:** a ring around a big time.
- **Dashboard:** four tiles on black.
- **Retro:** a segment bar on indigo, for the game-console look.

Choose the accent colour (mint, blue, amber, pink or green) and four fields out of Body Battery, heart rate, stress,
steps and floors. The first field is the big one. On an AMOLED watch the face shows only the time while your wrist is
down, to protect the screen.

Everything on the face is read on the watch itself. No phone, no account and no internet connection are needed, and
nothing is sent anywhere.

There is no sleep field: Garmin gives watch apps no sleep length. OpenConnect is an independent open-source project
(AGPL-3.0-or-later) and is not made or endorsed by Garmin. Garmin and Body Battery are trademarks of Garmin Ltd.

Source code and the companion Android app: https://github.com/Tufein/OpenConnect

**Permissions, in plain words:**

- *Sensor history:* to draw Body Battery and stress, which the watch records over the day.
- *User profile:* to read your resting heart rate.

**Privacy:** the face stores nothing and sends nothing. Its settings (design, colour, fields) live in Garmin's Connect IQ
app, like those of any watch face.

**What's new (first version):** Three designs, five colours, four fields you choose in Garmin's Connect IQ app.

**Category:** Watch faces. **Price:** free. **Languages:** English, Dutch (the settings; the face itself shows numbers).

## Widget

**Name:** OpenConnect Widget

**Tagline:** A glance and a screen with your Body Battery, heart rate, stress, steps and floors.

**Description:**

OpenConnect Widget adds a glance to the glance list of your watch: your first three fields on one line, such as
*Battery 72  HR 52  Stress 28*. Open it for one screen with a ring, four rows and a line of your Body Battery over the
last 12 hours.

Colour and fields are set in Garmin's Connect IQ app (the same five colours and five fields as the watch face). All
values come from the watch itself: no phone, no account, no internet.

There is no sleep screen: Garmin gives watch apps no sleep length. OpenConnect is an independent open-source project
(AGPL-3.0-or-later) and is not made or endorsed by Garmin. Garmin and Body Battery are trademarks of Garmin Ltd.

Source code and the companion Android app: https://github.com/Tufein/OpenConnect

**Permissions:** sensor history (Body Battery and stress) and user profile (resting heart rate), as for the face.

**Privacy:** nothing is stored or sent.

**What's new (first version):** Glance and widget screen with the same colours and fields as the face.

**Category:** Widgets. **Price:** free.
