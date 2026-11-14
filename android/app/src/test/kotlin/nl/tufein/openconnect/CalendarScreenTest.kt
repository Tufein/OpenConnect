package nl.tufein.openconnect

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.Streaks
import nl.tufein.openconnect.ui.dayTag
import nl.tufein.openconnect.ui.monthTitle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.assertTrue

/** v0.29.0 on the JVM: the month calendar with the streaks above it, paging, and a day's activities. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class CalendarScreenTest : AppTestBase() {
    private val now = YearMonth.now(ZoneOffset.UTC)

    private fun repo() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.repository

    private fun openCalendar() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Calendar").performClick()
        waitForText(monthTitle(now))
    }

    /** Presses "previous month" until [target] is shown, waiting for each month in turn. */
    private fun pageBackTo(target: YearMonth) {
        var shown = now
        while (shown > target) {
            rule.onNodeWithContentDescription("Previous month").performClick()
            shown = shown.minusMonths(1)
            waitForText(monthTitle(shown))
        }
    }

    @Test
    fun theCurrentMonthShowsTheStreaksAndPagingStopsAtTheFirstMonthWithAnActivity() {
        openCalendar()
        waitForText("Current streak")
        waitForText("Longest streak")
        waitForText("Weeks in a row")
        shot("59-calendar")

        // This is the newest month, so there is no way forward.
        rule.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        val first = Streaks.firstMonth(repo().listActivities(limit = 10_000))!!
        pageBackTo(first)
        rule.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        if (first < now) rule.onNodeWithContentDescription("Next month").assertIsEnabled()
    }

    @Test
    fun aDayShowsItsActivitiesAndOpensOne() {
        openCalendar()
        val latest = repo().listActivities(limit = 1).single()
        val day = latest.startTime.atZone(ZoneOffset.UTC).toLocalDate()
        pageBackTo(YearMonth.from(day))
        waitForText("Tap a day to see its activities.")

        rule.onNodeWithTag(dayTag(day)).performClick()
        waitForText(latest.name)
        assertTrue(hasText("km", substring = true))
        shot("60-calendar-day")

        // A day without an activity says so.
        val view = Streaks.month(repo().listActivities(limit = 10_000), YearMonth.from(day))
        val rest = (1..view.month.lengthOfMonth()).map { view.month.atDay(it) }.firstOrNull { it !in view.days }
        if (rest != null) {
            rule.onNodeWithTag(dayTag(rest)).performClick()
            waitForText("No activity on this day.")
            rule.onNodeWithTag(dayTag(day)).performClick()
            waitForText(latest.name)
        }

        // The activity of the day opens its page.
        rule.onNodeWithText(latest.name).performClick()
        waitForText("Distance")
    }

    @Test
    fun todayIsInTheGrid() {
        openCalendar()
        rule.onNodeWithTag(dayTag(LocalDate.now(ZoneOffset.UTC))).assertIsEnabled()
    }
}
