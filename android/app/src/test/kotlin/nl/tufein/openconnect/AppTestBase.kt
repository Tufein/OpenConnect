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
    protected fun launchFresh(prepare: (OpenConnectApp) -> Unit = {}) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("openconnect.db")
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

    protected fun tab(label: String): SemanticsNodeInteraction = rule.onNode(hasText(label) and hasClickAction())

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
    protected fun scrollToText(text: String) {
        if (!hasText(text, substring = false)) {
            rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
        }
    }

    /** Lists compose lazily, so an item below the fold is not in the tree until it has been scrolled to. */
    protected fun clickText(text: String) {
        if (!hasText(text, substring = false)) {
            rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
        }
        rule.onNodeWithText(text).performScrollTo().performClick()
    }
}
