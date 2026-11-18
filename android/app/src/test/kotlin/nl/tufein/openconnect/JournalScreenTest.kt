package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.JournalBook
import nl.tufein.openconnect.core.JournalTag
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import nl.tufein.openconnect.watch.OpenedWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** v0.36.0 on the JVM: the journal card on Home, the Journal tab and the heads-up when the body is off for days. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class JournalScreenTest : AppTestBase() {
    private val today = LocalDate.now()

    private fun launchWith(seed: (AppContainer) -> Unit = {}) = launchFresh { app ->
        val container = AppContainer(
            SqliteRepository(app),
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            FakeUsb { OpenedWatch(nl.tufein.openconnect.core.watch.InMemoryWatchSource("W", emptyMap())) { } },
            FakeBle(),
        )
        app.containerOverride = container
        seed(container)
    }

    private fun settings() = androidx.test.core.app.ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings

    @Test
    fun aDayIsMarkedOnHomeAndTheJournalTabCountsIt() {
        launchWith()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        scrollToText("Alcohol")
        shot("70-journal-card")
        clickText("Alcohol")
        rule.waitUntil(5_000) { JournalBook.decode(settings().journal).tags(today) == setOf(JournalTag.ALCOHOL) }
        // Yesterday is a separate day, and tapping the chip again switches the mark off.
        clickText("Yesterday")
        clickText("Late meal")
        rule.waitUntil(5_000) { JournalBook.decode(settings().journal).tags(today.minusDays(1)) == setOf(JournalTag.LATE_MEAL) }
        assertEquals(2, JournalBook.decode(settings().journal).days.size)
        clickText("Late meal")
        rule.waitUntil(5_000) { JournalBook.decode(settings().journal).days.size == 1 }

        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Journal").performClick()
        waitForText("What the journal says")
        waitForText("Days with something marked: 1")
        waitForText("Nothing to compare yet", substring = true)
        scrollToText("Not medical advice", substring = true)
    }

    @Test
    fun aTagThatGoesWithWorseNightsShowsUpOnTheJournalTab() {
        launchWith { container ->
            // Every third day is marked; the night after a marked day has a lower HRV.
            val marked = (0..19 step 3).map { today.minusDays(20L - it) }
            container.settings.journal = JournalBook(marked.associateWith { setOf(JournalTag.ALCOHOL) }).encode()
            for (k in 0..20) {
                val day = today.minusDays(20L - k)
                val after = day.minusDays(1) in marked
                container.repository.upsertDaily(DailySummary(date = day, steps = 8000, restingHr = 52, hrvMs = if (after) 40 else 60))
            }
        }
        waitForText("Readiness")
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Journal").performClick()
        waitForText("What the journal says")
        waitForText("Alcohol")
        waitForText("40 ms against 60 ms")
        shot("71-journal-tab")
    }

    @Test
    fun aHeadsUpShowsWhenRestingHeartRateAndHrvAreOffForDays() {
        launchWith { container ->
            for (k in 0..30) {
                val day = today.minusDays(30L - k)
                val off = k >= 29 - 1 // the last three days
                container.repository.upsertDaily(
                    DailySummary(date = day, steps = 8000, restingHr = if (off) 60 else 50, hrvMs = if (off) 40 else 60),
                )
            }
        }
        waitForText("Heads-up")
        waitForText("have been off for days", substring = true)
        shot("72-heads-up")
    }

    @Test
    fun noHeadsUpWhenTheBodyIsAsUsual() {
        launchWith { container ->
            for (k in 0..30) {
                container.repository.upsertDaily(DailySummary(date = today.minusDays(30L - k), steps = 8000, restingHr = 50, hrvMs = 60))
            }
        }
        waitForText("Readiness")
        assertTrue(!hasText("Heads-up", substring = false))
    }
}
