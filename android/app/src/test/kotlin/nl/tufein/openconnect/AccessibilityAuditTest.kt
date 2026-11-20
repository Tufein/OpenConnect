package nl.tufein.openconnect

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import nl.tufein.openconnect.core.DailySummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * v0.57.0 (milestone M4, accessible): the checks a screen reader user and someone with big letters depend on, run over
 * the main screens with demo data, in three set-ups: a normal phone, letters at 200 percent, and a tablet width.
 *
 * - every tappable thing has a label that TalkBack can read (text or a description),
 * - every tappable thing can be hit with a thumb: at least 48 dp by 48 dp, counting the touch padding Compose adds,
 * - no text is cut off at the bottom (a text that is shortened with an ellipsis on purpose is fine: its full text is still
 *   what the screen reader reads).
 *
 * A real TalkBack run on a phone is still needed (step T14 in docs/PUBLIC_RELEASE.md); this keeps what can be checked on
 * the JVM from slipping back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class AccessibilityAuditTest : AppTestBase() {
    private fun seed(app: OpenConnectApp) {
        app.container.repository.upsertDaily(
            DailySummary(date = LocalDate.now(), steps = 2762, stepsGoal = 2970, restingHr = 63, avgStress = 38, bodyBatteryHigh = 45, bodyBatteryLow = 5),
        )
    }

    private val minTouchDp = 48f

    private fun describe(node: SemanticsNode): String {
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val role = node.config.getOrNull(SemanticsProperties.Role)
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        val near = node.parent?.children?.mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { s -> s.text } }?.take(2)
        val keys = node.config.map { it.key.name }.sorted()
        return "\"${text ?: description ?: "?"}\" role=$role tag=$tag near=$near keys=$keys size=${node.size} at ${node.boundsInRoot.left.toInt()},${node.boundsInRoot.top.toInt()}"
    }

    /** What is wrong with the screen as it is now; one line per problem. */
    private fun audit(screen: String): List<String> {
        val problems = ArrayList<String>()
        val root = rule.onRoot().fetchSemanticsNode()
        val all = ArrayList<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            all += node
            node.children.forEach(::walk)
        }
        walk(root)
        for (node in all) {
            val config = node.config
            if (config.contains(SemanticsActions.OnClick) && !config.contains(SemanticsProperties.Disabled)) {
                val hasLabel = (config.getOrNull(SemanticsProperties.Text)?.any { it.text.isNotBlank() } == true) ||
                    (config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.isNotBlank() } == true) ||
                    node.children.any { child ->
                        child.config.getOrNull(SemanticsProperties.Text)?.any { it.text.isNotBlank() } == true ||
                            child.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.isNotBlank() } == true
                    }
                if (!hasLabel) problems += "$screen: a tappable thing has no label: ${describe(node)}"
                val density = node.layoutInfo.density.density
                // The size the thing was laid out at, not the part that happens to be on screen: a list item half scrolled
                // out of view is clipped, and clipped is not small.
                val touch = node.touchBoundsInRoot
                val w = maxOf(node.size.width.toFloat(), touch.width) / density
                val h = maxOf(node.size.height.toFloat(), touch.height) / density
                if (node.size.width > 0 && node.size.height > 0 && (w < minTouchDp - 0.5f || h < minTouchDp - 0.5f)) {
                    problems += "$screen: ${describe(node)} is ${w.toInt()} x ${h.toInt()} dp, under 48 x 48"
                }
            }
            val layout = config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            if (layout != null) {
                val results = ArrayList<TextLayoutResult>()
                layout(results)
                val result = results.firstOrNull()
                if (result != null && result.didOverflowHeight && result.layoutInput.maxLines == Int.MAX_VALUE) {
                    problems += "$screen: ${describe(node)} is cut off at the bottom"
                }
            }
        }
        return problems.distinct()
    }

    /** The screen as it is, then scrolled down in steps: a list only builds what is on screen, so one look is not enough. */
    private fun auditScrolling(screen: String): List<String> {
        val problems = ArrayList<String>(audit(screen))
        repeat(10) {
            val lists = rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())
            if (lists.fetchSemanticsNodes().isEmpty()) return problems.distinct()
            lists[0].performTouchInput { swipeUp() }
            rule.waitForIdle()
            problems += audit(screen)
        }
        return problems.distinct()
    }

    private fun walkTheMainScreens(): List<String> {
        val problems = ArrayList<String>()
        waitForText(" highest", substring = true)
        problems += auditScrolling("Home")
        for (tab in listOf("Activities", "Health", "Insights", "Settings")) {
            tab(tab).performClick()
            rule.waitForIdle()
            problems += auditScrolling(tab)
        }
        clickText("About and licences")
        waitForText("About OpenConnect")
        problems += auditScrolling("About")
        return problems
    }

    @Test
    fun theMainScreensOnANormalPhoneAreAccessible() {
        launchFresh(prepare = ::seed)
        assertEquals(emptyList(), walkTheMainScreens())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
    fun theMainScreensWithLettersAtTwoHundredPercentAreAccessible() {
        RuntimeEnvironment.setFontScale(2.0f)
        launchFresh(prepare = ::seed)
        assertEquals(emptyList(), walkTheMainScreens())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w840dp-h1280dp-night-xhdpi", application = OpenConnectApp::class)
    fun theMainScreensOnATabletAreAccessible() {
        launchFresh(prepare = ::seed)
        waitForText(" highest", substring = true)
        shot("a11y-tablet-home")
        assertEquals(emptyList(), walkTheMainScreens())
    }

    /** The same walk with the whole demo loaded: every card of Home and the Health and Insights charts, and an activity. */
    private fun walkWithTheFullDemo(): List<String> {
        val problems = ArrayList<String>()
        waitForText("Load demo data")
        clickText("Load demo data") // scrolls to it first: with big letters it is below the fold
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitForIdle()
        problems += auditScrolling("Home (demo)")
        tab("Activities").performClick()
        rule.waitForIdle()
        problems += auditScrolling("Activities (demo)")
        rule.onAllNodesWithText("Easy Run")[0].performClick()
        waitForText("Distance")
        problems += auditScrolling("Activity (demo)")
        rule.runOnUiThread { scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
        rule.waitForIdle()
        for (tab in listOf("Health", "Insights")) {
            tab(tab).performClick()
            rule.waitForIdle()
            problems += auditScrolling("$tab (demo)")
        }
        return problems
    }

    @Test
    fun theScreensWithTheFullDemoAreAccessible() {
        launchFresh()
        assertEquals(emptyList(), walkWithTheFullDemo())
    }

    @Test
    fun theScreensWithTheFullDemoAndLettersAtTwoHundredPercentAreAccessible() {
        RuntimeEnvironment.setFontScale(2.0f)
        launchFresh()
        assertEquals(emptyList(), walkWithTheFullDemo())
    }

    @Test
    fun chartsAreReadAsWordsWithTheLowestHighestAndLatestValue() {
        launchFresh()
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        tab("Health").performClick()
        rule.waitForIdle()
        clickChip("Steps chart")
        scrollToText("Steps counted by your watch each day", substring = true)
        val charts = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(", lowest ", substring = true)).fetchSemanticsNodes()
        kotlin.test.assertTrue(charts.isNotEmpty(), "the charts on Health say their numbers in words")
        val spoken = charts.first().config.getOrNull(SemanticsProperties.ContentDescription)!!.first()
        kotlin.test.assertTrue("values, lowest" in spoken && "highest" in spoken && "latest" in spoken, "got: $spoken")
    }
}
