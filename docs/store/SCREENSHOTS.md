# Screenshots for the store listing

The store asks for screenshots of each app. The pages of Garmin's developer site that could be read for this work do not
state sizes or counts, so use whatever the upload form says and take the pictures in the Connect IQ simulator from the
device that you build for (that is how they look on the real screen).

**Take, for the watch face** (one listing, three designs, so show all three):

1. Clean, blue, default fields (Body Battery, heart rate, stress, steps), `fenix847mm`.
2. Dashboard, mint, same fields, `fenix847mm`.
3. Retro, pink, same fields, `fenix847mm`.
4. Clean on the small screen (`fenix843mm`, 416 px) to show that it fits.
5. Optional: the always-on look (only the time) with the simulator's low power mode.

**Take, for the widget:**

1. The glance in the glance list (`Battery 72  HR 52  Stress 28`).
2. The widget screen: ring, four rows and the Body Battery line.

**Draft pictures** without the simulator: the Android app draws all faces with your numbers (*Settings, Watch face
preview*); the UI test run writes screenshots of them to `app/build/screenshots`. Use them for the first look, but the
store listing should show the real thing from the simulator.

**Icon:** the launcher icon is `watch/resources/drawables/launcher_icon.png` (80 x 80 PNG). If the upload form asks for a
store icon of another size, make that from the same drawing.
