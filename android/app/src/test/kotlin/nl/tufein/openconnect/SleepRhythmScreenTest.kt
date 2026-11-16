package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.42.0 on the JVM: the sleep rhythm card on Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class SleepRhythmScreenTest : AppTestBase() {
    @Test
    fun homeShowsHowAlikeYourLastNightsWere() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        scrollToText("Sleep rhythm")
        scrollToText("give or take", substring = true)
        scrollToText("Bedtimes are not stored", substring = true)
        shot("81-sleep-rhythm")
    }
}
