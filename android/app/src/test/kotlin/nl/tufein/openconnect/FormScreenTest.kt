package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.34.0 on the JVM: the training advice on Home and the Form tab behind it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class FormScreenTest : AppTestBase() {
    private fun loadDemo() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
    }

    @Test
    fun homeShowsTheAdviceAndTappingItOpensTheFormTab() {
        loadDemo()
        scrollToText("Training advice")
        shot("60-advice")
        clickText("Training advice")
        waitForText("Fitness, fatigue and form")
        waitForText("Fitness")
        shot("61-form")
    }

    @Test
    fun theFormTabHasTheThreeChartsAndAnHonestNote() {
        loadDemo()
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Form").performClick()
        waitForText("Fitness, fatigue and form")
        scrollToText("Fitness (42-day average)")
        scrollToText("Fatigue (7-day average)")
        scrollToText("Form (fitness minus fatigue)")
        scrollToText("not medical advice", substring = true)
    }
}
