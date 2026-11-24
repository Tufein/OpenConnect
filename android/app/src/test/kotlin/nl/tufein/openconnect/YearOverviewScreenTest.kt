package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/** v1.9.3 on the JVM: twelve months of steps, sleep, resting heart rate and trainings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class YearOverviewScreenTest : AppTestBase() {
    @Test
    fun theYearTabShowsTwelveMonthsOfStepsSleepHeartRateAndTrainings() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)

        clickChip("Year")
        val year = LocalDate.now(ZoneOffset.UTC).year
        waitForText("YEAR $year")
        scrollToText("Steps in total")
        scrollToText("Steps per month")
        scrollToText("Trainings per month")
        shot("79-year-overview")
    }
}
