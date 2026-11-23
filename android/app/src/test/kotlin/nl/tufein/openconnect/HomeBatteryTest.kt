package nl.tufein.openconnect

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * The front page carries the charge of the watch and the ring, so a device that is nearly empty is noticed before a
 * sync. The numbers only exist after a sync has reported them; no real Bluetooth or Oura account is involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class HomeBatteryTest : AppTestBase() {
    @Test
    fun theFrontPageShowsTheChargeOfTheWatchAndTheRing() {
        launchFresh(oura = true, prepare = { app ->
            with(app.container.settings) {
                watchBattery = 87
                watchBatteryAt = System.currentTimeMillis() - 5 * 60_000
                ringBattery = 64
                ringBatteryAt = System.currentTimeMillis() - 120 * 60_000
            }
        })

        waitForText("Battery")
        waitForText("87%")
        waitForText("64%")
        // the ring last reported two hours ago; the watch five minutes ago
        assertTrue(hasText("updated", substring = true))
        shot("17-home-batteries")
    }
}
