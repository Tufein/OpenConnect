package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.DailySummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertFalse

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

    /**
     * The day title of the steps card, by its own tag. After the battery card landed on Home the steps card can sit
     * just outside the viewport, and "Today" also names today's row in the journal further down; scroll to a fact that
     * is inside the same card, then read the title from the unmerged tree (the tappable card merges its children).
     */
    /**
     * The editorial gauge only exists once the day is loaded. The live heart-rate card now sits above it, so the gauge
     * can be outside the viewport on the first frame; scroll to it while the data is still arriving instead of assuming
     * it is already composed.
     */
    private fun waitForLoadedHome() {
        val gauge = ApplicationProvider.getApplicationContext<Context>().getString(R.string.editorial_active_calories).uppercase()
        rule.waitUntil(15_000) {
            try {
                scrollToText(gauge, substring = true)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun assertDayTitle(expected: String) {
        val anchor = ApplicationProvider.getApplicationContext<Context>().getString(R.string.intensity_minutes)
        scrollToText(anchor)
        rule.onNodeWithTag("home-day", useUnmergedTree = true).assertTextEquals(expected)
    }

    @Test
    fun aRecordOfTodayCarriesNoWarning() {
        launchFresh(prepare = seed(0))
        waitForLoadedHome()
        assertDayTitle("Today")
        assertFalse(hasText("No data for today yet", substring = true), "today's numbers need no warning")
        shot("home-day-today")
    }

    @Test
    fun aRecordOfYesterdayIsCalledYesterdayAndExplained() {
        launchFresh(prepare = seed(1))
        waitForLoadedHome()
        scrollToText("No data for today yet", substring = true)
        assertDayTitle("Yesterday")
        shot("home-day-yesterday")
    }

    @Test
    fun anOlderRecordShowsItsDate() {
        launchFresh(prepare = seed(4))
        waitForLoadedHome()
        assertDayTitle(nl.tufein.openconnect.ui.Fmt.date(LocalDate.now().minusDays(4)))
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-night-xxhdpi")
    fun theNoticeIsInDutchToo() {
        launchFresh(prepare = seed(1))
        waitForLoadedHome()
        scrollToText("Nog geen gegevens van vandaag", substring = true)
        assertDayTitle("Gisteren")
    }
}
