package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.35.0 on the JVM: the shape for each distance and the VO2max from your runs, under Insights, Plan. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class RaceShapeScreenTest : AppTestBase() {
    @Test
    fun thePlanTabShowsTheShapeForEachDistanceAndTheVo2maxFromTheRuns() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Plan").performClick()
        scrollToText("Shape for each distance")
        scrollToText("Rules of thumb", substring = true)
        shot("62-race-shape")
        scrollToText("VO2max from your runs")
        scrollToText("Race times from this VO2max")
        scrollToText("not Garmin", substring = true)
        shot("63-vo2max")
    }
}
