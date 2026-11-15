package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.37.0 on the JVM: the sleep target for tonight on Home and monotony and strain on the Form tab. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class RecoveryScreenTest : AppTestBase() {
    private fun loadDemo() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
    }

    @Test
    fun homeShowsHowLongToSleepTonight() {
        loadDemo()
        scrollToText("Sleep tonight")
        shot("73-sleep-need")
        scrollToText("Eight hours, a little more after a hard day", substring = true)
    }

    @Test
    fun theFormTabShowsMonotonyAndStrain() {
        loadDemo()
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Form").performClick()
        waitForText("Fitness, fatigue and form")
        scrollToText("Monotony and strain")
        scrollToText("Strain")
        scrollToText("Rest days")
        shot("74-monotony")
    }
}
