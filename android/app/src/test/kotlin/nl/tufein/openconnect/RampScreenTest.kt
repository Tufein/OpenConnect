package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.41.0 on the JVM: the training ramp card on the Form tab. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class RampScreenTest : AppTestBase() {
    private fun loadDemo() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
    }

    @Test
    fun theFormTabShowsHowThisWeekComparesWithYourUsual() {
        loadDemo()
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Form").performClick()
        waitForText("Fitness, fatigue and form")
        scrollToText("Training ramp")
        scrollToText("Last 7 days:", substring = true)
        scrollToText("against the average week of the four weeks before", substring = true)
        shot("80-ramp")
    }
}
