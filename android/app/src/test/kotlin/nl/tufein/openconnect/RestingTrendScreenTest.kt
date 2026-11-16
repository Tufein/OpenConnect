package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.42.0 on the JVM: the resting heart rate trend card on Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class RestingTrendScreenTest : AppTestBase() {
    @Test
    fun homeShowsYourRestingHeartRateAgainstYourNormal() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        scrollToText("Resting heart rate trend")
        scrollToText("your normal:", substring = true)
        scrollToText("A rule of thumb from one number", substring = true)
        shot("82-resting-trend")
    }
}
