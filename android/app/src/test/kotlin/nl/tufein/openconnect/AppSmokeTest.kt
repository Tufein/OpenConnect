package nl.tufein.openconnect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertTrue

/**
 * Drives the real MainActivity on the JVM (Robolectric): empty state, load demo data, every tab, an
 * activity detail and both report flows. Screenshots go to `app/build/screenshots` so the layout can be
 * looked at without a phone. This does not replace trying the APK on a device (file pickers, Keystore,
 * real fonts and gestures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class AppSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val outDir = File("build/screenshots").also { it.mkdirs() }

    @Before
    fun launch() {
        // Start every test from an empty app, whatever an earlier test left in the database file.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("openconnect.db")
        resetStaticViewModelFactory()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() = scenario.close()

    /**
     * ViewModelProvider keeps the first Application it sees in a static, and Robolectric reuses the classloader
     * between tests, so a later test would otherwise get a view model bound to the previous test's database.
     * A real process has exactly one Application, so this only matters here.
     */
    private fun resetStaticViewModelFactory() {
        val field = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory::class.java.getDeclaredField("_instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        // Draw the window directly: captureToImage waits for a real frame, which the paused test looper never produces.
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun tab(label: String): SemanticsNodeInteraction = rule.onNode(hasText(label) and hasClickAction())

    private fun hasText(text: String, substring: Boolean): Boolean =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String, substring: Boolean = false) {
        try {
            rule.waitUntil(15_000) { hasText(text, substring) }
        } catch (e: Throwable) {
            println("--- screen when waiting for \"$text\" ---\n" + rule.onRoot().printToString(maxDepth = 8))
            throw e
        }
    }

    /** Lists compose lazily, so an item below the fold is not in the tree until it has been scrolled to. */
    private fun clickText(text: String) {
        if (!hasText(text, substring = false)) {
            rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
        }
        rule.onNodeWithText(text).performScrollTo().performClick()
    }

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

        tab("Reports").performClick()
        rule.waitForIdle()
        shot("06-reports")
        clickText("Write report")
        waitForText("Source:", substring = true)
        shot("06b-weekly-report")

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
        tab("Instellingen").performClick()
        rule.waitForIdle()
        shot("nl-light-settings")
    }
}
