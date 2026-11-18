package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v0.51.0 on the JVM: with more than one device in use, the Day tab of Insights shows the day as each of them measured it,
 * side by side, and says whether the numbers lie close together. With one device there is nothing to compare.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class DeviceComparisonScreenTest : AppTestBase() {
    private fun loadDemo(waitFor: String = "Load demo data") {
        waitForText(waitFor)
        rule.onNodeWithText(waitFor).performClick()
        rule.waitUntil(45_000) { !hasText(waitFor, substring = false) }
    }

    private fun openDay() {
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        tab("Day").performClick()
        waitForText("Tap a measure to see it as a chart.")
    }

    @Test
    fun withAWatchAndTwoBandsTheDayIsShownSideBySide() {
        launchFresh(oura = true, whoop = true, hume = false)
        loadDemo()
        openDay()
        scrollToText("Devices compared")
        assertTrue(hasText("Devices compared", substring = false))
        // Sleep is the one measure all three have; each device is named next to its own number.
        scrollToText("Garmin", substring = true)
        assertTrue(hasText("Garmin", substring = true) && hasText("Oura", substring = true) && hasText("WHOOP", substring = true))
        assertTrue(hasText("Close:", substring = true) || hasText("Far apart:", substring = true), "a verdict on the gap")
        scrollToText("HRV and scores are each maker", substring = true)
        shot("compare-day")
    }

    @Test
    fun withOnlyTheWatchThereIsNothingToCompare() {
        launchFresh()
        loadDemo()
        openDay()
        assertFalse(hasText("Devices compared", substring = false))
    }

    @Test
    fun withTheHumeBandToo() {
        launchFresh(oura = false, whoop = false, hume = true)
        loadDemo()
        openDay()
        scrollToText("Devices compared")
        assertTrue(hasText("Hume Band", substring = true))
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-night-xxhdpi")
    fun theCardIsInDutchToo() {
        launchFresh(oura = true, whoop = true)
        loadDemo("Demodata laden")
        tab("Inzichten").performClick()
        waitForText("Week van", substring = true)
        tab("Dag").performClick()
        scrollToText("Apparaten vergeleken")
        assertTrue(hasText("Dicht bij elkaar:", substring = true) || hasText("Ver uit elkaar:", substring = true))
    }
}
