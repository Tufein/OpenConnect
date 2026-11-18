package nl.tufein.openconnect

import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.DailySummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v0.54.0: Home called the latest record "Today" even when it was yesterday's, so the numbers differed from the watch
 * and nothing said why. Now the card says which day it shows, and the Body Battery tile says its number is the day's
 * highest (the watch shows the current level).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class HomeDayLabelTest : AppTestBase() {
    private fun seed(daysAgo: Long) = { app: OpenConnectApp ->
        app.container.repository.upsertDaily(
            DailySummary(
                date = LocalDate.now().minusDays(daysAgo), steps = 2762, stepsGoal = 2970, restingHr = 63,
                avgStress = 38, bodyBatteryHigh = 45, bodyBatteryLow = 5,
            ),
        )
    }

    @Test
    fun aRecordOfTodayCarriesNoWarning() {
        launchFresh(prepare = seed(0))
        waitForText(" highest")
        scrollToText("Intensity minutes")
        assertFalse(hasText("No data for today yet", substring = true), "today's numbers need no warning")
        assertTrue(hasText("Today", substring = false), "the steps card says Today")
        shot("home-day-today")
    }

    @Test
    fun aRecordOfYesterdayIsCalledYesterdayAndExplained() {
        launchFresh(prepare = seed(1))
        waitForText(" highest")
        scrollToText("No data for today yet", substring = true)
        assertTrue(hasText("Yesterday", substring = false), "the steps card names the day")
        assertFalse(hasText("Today", substring = false), "yesterday's steps must not be called Today")
        shot("home-day-yesterday")
    }

    @Test
    fun anOlderRecordShowsItsDate() {
        launchFresh(prepare = seed(4))
        waitForText(" highest")
        scrollToText("No data for today yet", substring = true)
        val date = nl.tufein.openconnect.ui.Fmt.date(LocalDate.now().minusDays(4))
        assertTrue(hasText(date, substring = true), "the card shows $date")
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-night-xxhdpi")
    fun theNoticeIsInDutchToo() {
        launchFresh(prepare = seed(1))
        waitForText(" hoogste")
        scrollToText("Nog geen gegevens van vandaag", substring = true)
        assertTrue(hasText("Gisteren", substring = false))
    }
}
