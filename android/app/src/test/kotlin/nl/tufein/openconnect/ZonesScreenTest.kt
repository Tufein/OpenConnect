package nl.tufein.openconnect

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.HeartZones
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** v0.27.0 on the JVM: heart-rate zones under Insights, the maximum that can be set, and the zones of one activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class ZonesScreenTest : AppTestBase() {
    private fun settings() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings

    private fun loadDemoAndOpenZones() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Zones").performClick()
        waitForText("Maximum heart rate")
    }

    @Test
    fun theZonesShowTheMeasuredMaximumTheLimitsAndTheLastWeek() {
        loadDemoAndOpenZones()
        waitForText("The highest heart rate in your activities", substring = true)
        shot("52-zones")
        assertEquals(0, settings().maxHr)

        waitForText("Zone limits")
        scrollToText("Zone 5", substring = true)
        assertTrue(hasText("bpm and up", substring = true))
        scrollToText("Last 7 days")
        waitForText("Z1")
        scrollToText("Earlier weeks")
        shot("53-zones-weeks")
        scrollToText("Not medical advice", substring = true)
    }

    @Test
    fun theMaximumCanBeMovedAndGoesBackToTheMeasuredOne() {
        loadDemoAndOpenZones()
        clickText("+5 bpm")
        waitForText("Set by you", substring = true)
        val first = settings().maxHr
        assertTrue(first in HeartZones.MIN_MAX_HR..HeartZones.MAX_MAX_HR, "a typed-in maximum is kept: $first")
        waitForText("$first bpm")

        clickText("−5 bpm")
        rule.waitUntil(15_000) { settings().maxHr == first - 5 }
        waitForText("${first - 5} bpm")

        clickText("Use the highest measured")
        rule.waitUntil(15_000) { settings().maxHr == 0 }
        waitForText("The highest heart rate in your activities", substring = true)
    }

    @Test
    fun anActivityShowsItsTimeInTheZones() {
        loadDemoAndOpenZones()
        tab("Activities").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Easy Run")[0].performClick()
        waitForText("Distance")
        scrollToText("Time in heart-rate zones")
        waitForText("Zones from a maximum of", substring = true)
        assertTrue(hasText("Z2", substring = false))
        shot("54-activity-zones")
    }
}
