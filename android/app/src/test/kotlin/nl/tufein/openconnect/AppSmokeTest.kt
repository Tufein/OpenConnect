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
