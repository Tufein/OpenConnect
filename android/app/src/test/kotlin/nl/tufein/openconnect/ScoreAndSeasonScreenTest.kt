package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/** v0.39.0 on the JVM: the hi-score card of a year and the season plan under the race goal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class ScoreAndSeasonScreenTest : AppTestBase() {
    private fun loadDemo() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
    }

    @Test
    fun theHiScoreCardShowsTheYearAndCanBeShared() {
        loadDemo()
        rule.onNodeWithText("Hi-score").performClick()
        val year = LocalDate.now(ZoneOffset.UTC).year
        waitForText("HI-SCORE $year")
        scrollToText("Activities")
        scrollToText("Longest streak")
        scrollToText("Share as text")
        scrollToText("Nothing is sent anywhere", substring = true)
        shot("77-hi-score")
    }

    @Test
    fun aRaceGoalGetsASeasonPlanWithThePhaseYouAreIn() {
        loadDemo()
        rule.onNodeWithText("Plan").performClick()
        waitForText("Race goal")
        clickText("Set race goal")
        waitForText("in 84 days (12 weeks)")
        scrollToText("Season plan")
        scrollToText("12 weeks to the race")
        scrollToText("Base", substring = true)
        scrollToText("You are here", substring = true)
        shot("78-season")
    }
}
