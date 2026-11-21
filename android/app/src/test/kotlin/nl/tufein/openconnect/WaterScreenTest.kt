package nl.tufein.openconnect

import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.1.0: the Water card adds to today's total and keeps it, which is the only place the app has water. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class WaterScreenTest : AppTestBase() {
    private fun openHealthWithDemo() {
        launchFresh()
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        tab("Health").performClick()
        waitForText("Water")
    }

    @Test
    fun tapsAddUpForTodayAndAreStored() {
        openHealthWithDemo()
        waitForText("Nothing logged today.")
        clickText("+500 ml")
        waitForText("0.5 L today")
        clickText("+500 ml")
        waitForText("1.0 L today")
        val stored = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.repository
        assertEquals(1000, stored.listWater(LocalDate.now(), LocalDate.now()).single().millilitres)
    }
}
