package nl.tufein.openconnect

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import nl.tufein.openconnect.core.DailySummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertTrue

/**
 * The four score cards on Health are rings, one shape and one size each. Tapping a ring opens that score's chart;
 * a score the watch has not measured leaves an empty ring and a dash instead of crashing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class HealthScoresTest : AppTestBase() {
    private val rings = listOf(
        "score-ring-readiness" to "Cercis's own 0 to 100 estimate",
        "score-ring-sleep" to "The sleep score your watch gives each night",
        "score-ring-battery" to "Garmin's energy estimate",
        "score-ring-stress" to "stress score from heart rate variability",
    )

    private fun loadDemo() {
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitForIdle()
    }

    @Test
    fun theFourScoresAreRingsAndOpenTheirOwnChart() {
        launchFresh()
        loadDemo()
        tab("Health").performClick()
        waitForText("Scores")

        for (label in listOf("Readiness", "Sleep score", "Body Battery", "Stress")) {
            assertTrue(hasText(label, substring = false), "the $label score card is on screen")
        }

        for ((tag, chart) in rings) {
            scrollToTop()
            val ring = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val spoken = ring.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            assertTrue(spoken != null && Regex("""\d+ out of 100""").containsMatchIn(spoken), "$tag says its score: $spoken")
            assertTrue(ring.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null, "$tag carries progress semantics")

            clickTag(tag)
            scrollToText(chart, substring = true)
            assertTrue(hasText(chart, substring = true), "$tag opens the chart that says \"$chart\"")
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp-night-xhdpi", application = OpenConnectApp::class)
    fun theRingsFitOnANarrowPhone() {
        launchFresh()
        loadDemo()
        tab("Health").performClick()
        waitForText("Scores")

        val screenWidth = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        for ((tag, _) in rings) {
            val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0f && bounds.right <= screenWidth, "$tag stays inside the 320 dp screen: $bounds")
        }
        shot("34b-health-scores-320")
    }

    @Test
    fun theRetroStyleKeepsTheSameFourRings() {
        launchFresh { app -> app.container.settings.style = "retro" }
        loadDemo()
        tab("Health").performClick()
        waitForText("Scores")

        for ((tag, _) in rings) {
            rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        }
        shot("34c-health-scores-retro")
    }

    @Test
    fun aMissingScoreIsAnEmptyRingWithADash() {
        launchFresh { app ->
            app.container.repository.upsertDaily(DailySummary(date = LocalDate.now(ZoneOffset.UTC), steps = 1_234))
        }
        tab("Health").performClick()
        waitForText("Scores")

        for ((tag, _) in rings) {
            val ring = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                ring.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == null,
                "$tag has no progress when there is no score",
            )
        }
        val dashes = rule.onAllNodesWithText("–").fetchSemanticsNodes()
        assertTrue(dashes.size >= 4, "each empty score shows a dash, found ${dashes.size}")
    }
}
