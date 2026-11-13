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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Shared plumbing for tests that drive the real MainActivity on the JVM (Robolectric): start from an empty app,
 * wait for text, click things below the fold and write screenshots to `app/build/screenshots`.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class AppTestBase {
    @get:Rule
    val rule = createEmptyComposeRule()

    protected lateinit var scenario: ActivityScenario<MainActivity>
    private val outDir = File("build/screenshots").also { it.mkdirs() }

    /** Start the activity on an empty database. [prepare] runs first, for example to install fake USB and Bluetooth. */
    /**
     * Starts the app on an empty database. The first-start questions count as answered (Garmin on, Oura off) unless
     * [onboarded] is false; [oura] switches the Oura Ring on as if it was chosen there.
     */
    protected fun launchFresh(onboarded: Boolean = true, oura: Boolean = false, prepare: (OpenConnectApp) -> Unit = {}) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("openconnect.db")
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("onboarded", onboarded)
            .putBoolean("use_oura", oura)
            .commit()
        resetStaticViewModelFactory()
        prepare(ctx as OpenConnectApp)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() {
        if (::scenario.isInitialized) scenario.close()
    }

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

    protected fun shot(name: String) {
        rule.waitForIdle()
        // Draw the window directly: captureToImage waits for a real frame, which the paused test looper never produces.
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /**
     * A tab or chip by its label. Since v0.16.0 tiles can be tapped too (the "Activities" tile on Home), so when more than
     * one tappable node has the label, the one in the navigation bar (role Tab) is meant.
     */
    protected fun tab(label: String): SemanticsNodeInteraction {
        val tappable = hasText(label) and hasClickAction()
        if (rule.onAllNodes(tappable).fetchSemanticsNodes().size <= 1) return rule.onNode(tappable)
        return rule.onNode(tappable and androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab))
    }

    protected fun hasText(text: String, substring: Boolean): Boolean =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    protected fun waitForText(text: String, substring: Boolean = false) {
        try {
            rule.waitUntil(15_000) { hasText(text, substring) }
        } catch (e: Throwable) {
            println("--- screen when waiting for \"$text\" ---\n" + rule.onRoot().printToString(maxDepth = 8))
            throw e
        }
    }

    /** Scroll the first scrollable list until [text] has been composed. */
    protected fun scrollToText(text: String, substring: Boolean = false) {
        if (!hasText(text, substring)) {
            rule.onAllNodes(hasScrollAction())[0].performScrollToNode(androidx.compose.ui.test.hasText(text, substring))
        }
    }

    /** Lists compose lazily, so an item below the fold is not in the tree until it has been scrolled to. */
    protected fun clickText(text: String) {
        if (!hasText(text, substring = false)) {
            rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
        }
        // The button's own click action, not a touch at its centre: after scrolling, a button can sit just behind the
        // navigation bar, and a touch there would open a tab instead.
        rule.onNodeWithText(text).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
    }
}
