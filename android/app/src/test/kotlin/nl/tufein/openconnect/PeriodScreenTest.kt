package nl.tufein.openconnect

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** v0.14.0 on the JVM: the week / month / year switch in Insights and the best efforts card, with demo data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class PeriodScreenTest : AppTestBase() {
    @Test
    fun monthAndYearReportsAndBestEfforts() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Month").performClick()
        // The week title also contains the year, so wait for the month name.
        waitForText(LocalDate.now().month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + LocalDate.now().year)
        shot("30-insights-month")
        rule.onNodeWithText("Year").performClick()
        waitForText(LocalDate.now().year.toString())
        shot("31-insights-year")
        // "Week" is also the Insights tab, which sits above the chips.
        rule.onAllNodesWithText("Week").onLast().performClick()
        waitForText("Week of", substring = true)
        scrollToText("Best efforts")
        shot("32-best-efforts")
    }
}
