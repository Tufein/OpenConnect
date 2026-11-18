package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.42.0 on the JVM: the step goal week card on Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class StepWeekScreenTest : AppTestBase() {
    @Test
    fun homeShowsHowManyDaysReachedTheStepGoal() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        scrollToText("Step goal this week")
        scrollToText("Reached on", substring = true)
        scrollToText("busiest day", substring = true)
        shot("83-step-week")
    }
}
