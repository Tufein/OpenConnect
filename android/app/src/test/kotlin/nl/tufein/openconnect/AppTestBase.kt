package nl.tufein.openconnect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.i18n.AppLocale
import org.junit.After
import org.junit.Rule
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

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

    /**
     * Starts the app on an empty database. The first-start questions count as answered (Garmin on, Oura off) unless
     * [onboarded] is false; [oura] switches the Oura Ring on as if it were chosen there. [appLanguage] is the app's own
     * language ("system" follows the phone, which the tests pin to English); a test that checks a translation passes
     * "nl", "de" or "fr".
     */
    protected fun launchFresh(
        onboarded: Boolean = true,
        oura: Boolean = false,
        appLanguage: String = AppLocale.SYSTEM,
        prepare: (OpenConnectApp) -> Unit = {},
    ) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("openconnect.db")
        resetLocales()
        val language = AppLocale.normalize(appLanguage)
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("onboarded", onboarded)
            .putBoolean("use_oura", oura)
            .putString("app_language", language)
            .commit()
        // ApplicationProvider already created the Application (and ran its own start-up) before the preferences above
        // were written, so the chosen language is handed to AppCompat here again.
        AppLocale.apply(language)
        resetStaticViewModelFactory()
        prepare(ctx as OpenConnectApp)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /**
     * Robolectric reuses the sandbox between tests, so a language picked by one test would otherwise leak into the
     * next: AppCompat's per-app locales and the JVM defaults are put back to English before every launch.
     */
    private fun resetLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        Locale.setDefault(Locale.ENGLISH)
        LocaleList.setDefault(LocaleList.forLanguageTags("en"))
    }

    @After
    fun close() {
        if (::scenario.isInitialized) scenario.close()
        resetLocales()
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
        // The navigation bar is composed a moment after the app opens; waiting here keeps a slow start from failing a test.
        rule.waitUntil(15_000) { rule.onAllNodes(tappable).fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodes(tappable).fetchSemanticsNodes().size <= 1) return rule.onNode(tappable)
        return rule.onNode(tappable and androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab))
    }

    protected fun hasText(text: String, substring: Boolean): Boolean =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    /**
     * A cold start (bundled fonts, first composition) is slow on a shared CI runner: four runs timed out at 15 s and passed on
     * the next try, so the wait is 45 s. A screen that is really missing still fails, just later.
     *
     * A lazy list only composes what is on screen. With Compose 1.13 (PR #94) "Load demo data" on Home and a few cards
     * on other screens sat just below the composed part, and 16 tests waited 45 s for a text that was there one swipe
     * down. So a text that is not composed yet is also looked for in the main list, as a person would scroll to it.
     */
    protected fun waitForText(text: String, substring: Boolean = false) {
        try {
            rule.waitUntil(45_000) { hasText(text, substring) || scrolledInto(text, substring) }
        } catch (e: Throwable) {
            println("--- screen when waiting for \"$text\" ---\n" + rule.onRoot().printToString(maxDepth = 100))
            throw e
        }
    }

    /**
     * Since v0.43.0 the look settings (dark or light, style, letters, colours, app icon) sit in a section of Settings that
     * is folded away. This opens it when it is closed and leaves it alone when it is open; call it on the Settings tab.
     */
    protected fun openAppearance() {
        waitForText("Appearance")
        if (!hasText("Colours", substring = false)) clickText("Appearance")
        waitForText("Colours")
    }

    /** The main vertical list, not a small horizontal row that happens to be first in the tree. */
    private fun mainScrollable(): SemanticsNodeInteraction {
        val nodes = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        val index = nodes.indices.maxByOrNull { nodes[it].boundsInRoot.height } ?: 0
        return rule.onAllNodes(hasScrollAction())[index]
    }

    /** Back to the top of a list; a setup screen can leave the next screen scrolled to its old position. */
    protected fun scrollToTop() {
        mainScrollable().performScrollToIndex(0)
    }

    /** A chip in a fixed header has no scroll parent; click its semantics instead of trying to scroll to it. */
    protected fun clickChip(text: String) {
        rule.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
    }

    /**
     * Scroll the main list until [text] has been composed. The screen's data can still be loading when this is called
     * (the Journal heads-up appears once the days are read), so it keeps looking for as long as [waitForText] does.
     */
    protected fun scrollToText(text: String, substring: Boolean = false) {
        waitForText(text, substring)
    }

    /** True when the main list could be scrolled to a node with [text]; false when there is no list or no such node yet. */
    private fun scrolledInto(text: String, substring: Boolean): Boolean =
        runCatching {
            mainScrollable().performScrollToNode(androidx.compose.ui.test.hasText(text, substring))
            hasText(text, substring)
        }.getOrDefault(false)

    /** Click a tagged tile/card through the nearest click action, even when the tag is hidden in the merged tree. */
    protected fun clickTag(tag: String) {
        var node: androidx.compose.ui.semantics.SemanticsNode? =
            rule.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode()
        while (node != null) {
            if (node.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)) {
                node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke()
                return
            }
            node = node.parent
        }
        throw AssertionError("no click action above test tag $tag")
    }

    /** Lists compose lazily, so an item below the fold is not in the tree until it has been scrolled to. */
    protected fun clickText(text: String) {
        if (!hasText(text, substring = false)) {
            mainScrollable().performScrollToNode(hasText(text))
        }
        // A tile or button can keep its click on a parent while its text is a child without an OnClick action: walk up
        // and invoke the nearest click action, so a tile behind the navigation bar is clicked through semantics too.
        var node: androidx.compose.ui.semantics.SemanticsNode? = rule.onNodeWithText(text).fetchSemanticsNode()
        while (node != null) {
            if (node.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)) {
                node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke()
                return
            }
            node = node.parent
        }
        // No click action anywhere above the label: tap the label itself.
        rule.onNodeWithText(text).performScrollTo().performClick()
    }
}
