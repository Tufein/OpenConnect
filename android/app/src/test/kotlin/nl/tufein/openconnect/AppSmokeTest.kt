package nl.tufein.openconnect

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Drives the real MainActivity on the JVM (Robolectric): empty state, load demo data, every tab, an
 * activity detail and both report flows. Screenshots go to `app/build/screenshots` so the layout can be
 * looked at without a phone. This does not replace trying the APK on a device (file pickers, Keystore,
 * real fonts and gestures).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class AppSmokeTest : AppTestBase() {
    @Before
    fun launch() = launchFresh()

    @Test
    fun emptyStateThenDemoDataThenEveryScreen() {
        waitForText("Load demo data")
        shot("01-home-empty")

        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        shot("02-home")

        tab("Activities").performClick()
        rule.waitForIdle()
        shot("03-activities")

        rule.onAllNodesWithText("Easy Run")[0].performClick()
        waitForText("Distance")
        shot("04-activity-detail")
        clickText("Write report")
        waitForText("Source:", substring = true)
        shot("04b-activity-report")
        rule.runOnUiThread { scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
        rule.waitForIdle()

        tab("Health").performClick()
        rule.waitForIdle()
        shot("05-health")

        tab("Insights").performClick()
        rule.waitForIdle()
        shot("06-reports")
        clickText("Write report")
        waitForText("Source:", substring = true)
        shot("06b-weekly-report")

        tab("Trophies").performClick()
        waitForText("trophies won", substring = true)
        shot("06c-trophies")

        tab("Coach").performClick()
        waitForText("The coach needs an AI", substring = true)
        shot("06d-coach-needs-key")

        tab("Settings").performClick()
        rule.waitForIdle()
        shot("07-settings")

        // The repository is real: demo data landed in SQLite.
        val repo = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.repository
        assertTrue(repo.countActivities() > 10)
    }

    @Test
    fun healthChartsSayWhatTheyMean() {
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        tab("Health").performClick()
        rule.waitForIdle()

        // the scores come first, each as a tile, then their 30-day charts
        waitForText("Scores")
        assertTrue(hasText("Sleep score", substring = false))
        assertTrue(hasText("Readiness is OpenConnect's own estimate", substring = true))
        shot("34-health-scores")
        scrollToText("Readiness, 30 days")
        scrollToText("OpenConnect's own 0 to 100 estimate", substring = true)
        shot("35-health-readiness-chart")
        scrollToText("Sleep score, 30 days")
        scrollToText("The sleep score your watch gives each night", substring = true)

        // steps: the period, a verdict, the numbers with their days, the goal count and what the chart is
        scrollToText("days reached 10,000 steps", substring = true)
        assertTrue(hasText("days reached 10,000 steps", substring = true))
        assertTrue(hasText("Steps counted by your watch each day", substring = true))
        assertTrue(hasText("General guide, not medical advice.", substring = false))
        shot("30-health-steps-explained")

        scrollToText("Your heart rate at rest", substring = true)
        shot("31-health-resting-hr-explained")
        scrollToText("stress score from heart rate variability", substring = true)
        assertTrue(hasText("up to 25 is rest", substring = true))
        shot("32-health-stress-explained")
    }

    @Test
    fun settingsShowBackupDataSourcesAndTheSyncLog() {
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }

        tab("Settings").performClick()
        rule.waitForIdle()
        scrollToText("Notifications")
        assertTrue(hasText("Send me these notifications", substring = false))
        shot("41-settings-notifications")
        scrollToText("Back up and restore")
        assertTrue(hasText("Back up", substring = false))
        assertTrue(hasText("Restore", substring = false))
        scrollToText("Where your data comes from")
        scrollToText("activities", substring = true)
        assertTrue(hasText("Demo", substring = true))
        shot("39-settings-sources")
        scrollToText("Sync log")
        assertTrue(hasText("No syncs yet", substring = true))
        shot("40-settings-synclog")
    }

    @Test
    fun homeShowsGoalsAndTrainingLoadAndGoalsCanBeChangedInSettings() {
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }

        scrollToText("Goals this week")
        assertTrue(hasText("goals reached", substring = true))
        shot("36-home-goals")
        scrollToText("Training load")
        assertTrue(hasText("Not Garmin's Training Load", substring = true))
        shot("37-home-training-load")

        tab("Settings").performClick()
        rule.waitForIdle()
        scrollToText("Weekly goals")
        assertTrue(hasText("Intensity minutes", substring = false))
        assertTrue(hasText("150 min", substring = false))
        rule.onAllNodesWithText("+")[0].performClick() // intensity minutes: 150 -> 160
        waitForText("160 min")
        shot("38-settings-goals")
        tab("Home").performClick()
        scrollToText("Goals this week")
        scrollToText("of 160 min", substring = true)
    }

    @Test
    fun theWatchScreenExplainsHowToLiveNextToGarminConnect() {
        waitForText("Connect watch")
        rule.onNodeWithText("Connect watch").performClick()
        waitForText("Sync a Garmin watch over Bluetooth")
        scrollToText("one phone app at a time", substring = true)
        assertTrue(hasText("Using Garmin Connect too?", substring = false))
        shot("33-watch-next-to-garmin-connect")
    }

    @Test
    @Config(qualifiers = "nl-w411dp-h891dp-notnight-xxhdpi")
    fun dutchLightThemeFitsTheNavigationBar() {
        waitForText("Demodata laden")
        rule.onNodeWithText("Demodata laden").performClick()
        rule.waitUntil(15_000) { !hasText("Demodata laden", substring = false) }
        shot("nl-light-home")
        tab("Activiteiten").performClick()
        rule.waitForIdle()
        shot("nl-light-activities")
        tab("Inzichten").performClick()
        rule.waitForIdle()
        shot("nl-light-insights")
        tab("Trofeeën").performClick()
        waitForText("trofeeën behaald", substring = true)
        shot("nl-light-trophies")
        tab("Instellingen").performClick()
        rule.waitForIdle()
        shot("nl-light-settings")
    }
}
