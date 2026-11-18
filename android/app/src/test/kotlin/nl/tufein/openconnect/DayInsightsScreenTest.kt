package nl.tufein.openconnect

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import nl.tufein.openconnect.ui.Fmt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v0.44.0 on the JVM: the Day tab of Insights (one day read against your own days before it, from the watch or the ring,
 * chosen in dropdowns) and the dates under the charts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class DayInsightsScreenTest : AppTestBase() {
    private fun loadDemo() {
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
    }

    private fun openDay() {
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        tab("Day").performClick()
        waitForText("Tap a measure to see it as a chart.")
    }

    /** An entry of an open dropdown list; the chips and the bottom bar carry other roles, so the same word is not mistaken for it. */
    private fun option(text: String) =
        rule.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    /** The closed field of a dropdown, which shows the chosen entry. */
    private fun field(text: String) =
        rule.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    private fun choose(current: String, wanted: String) {
        field(current).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(wanted) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes().isNotEmpty() }
        option(wanted).performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun aDayIsReadAgainstYourOwnUsualFromTheWatch() {
        launchFresh()
        loadDemo()
        openDay()
        // The newest day with data is the one on show; demo data runs up to today.
        waitForText(Fmt.longDate(LocalDate.now(ZoneOffset.UTC)))
        // With only the watch in use there is nothing to choose between, so there is no source dropdown.
        assertFalse(hasText("Source", substring = false))
        assertTrue(hasText("measures in a good range", substring = true))
        for (name in listOf("Readiness", "Sleep", "Steps", "Resting heart rate", "Body Battery", "Stress")) {
            assertTrue(hasText(name, substring = false), "a row or field for $name")
        }
        assertTrue(hasText("usual", substring = true), "each reading is held against the usual")
        // The ring's own scores are not the watch's.
        assertFalse(hasText("Activity score", substring = false))
        shot("44-day-garmin")
        // The chart under the readings has dates under it and the chosen day picked.
        val chosen = LocalDate.now(ZoneOffset.UTC)
        scrollToText(Fmt.shortDate(chosen.minusDays(29)))
        waitForText(Fmt.shortDate(chosen.minusDays(29)))
        assertTrue(hasText(Fmt.shortDate(chosen), substring = false), "the last date of the axis")
        assertTrue(hasText(Fmt.shortDate(chosen) + " · ", substring = true), "the chosen day is picked in the chart")
        shot("44-day-garmin-chart")
    }

    @Test
    fun aMeasureTappedInTheListBecomesTheChart() {
        launchFresh()
        loadDemo()
        openDay()
        // Readiness is the first reading, so it is the chart at first; the field shows it.
        assertTrue(hasText("Readiness", substring = false))
        clickText("Resting heart rate")
        scrollToText("Resting heart rate, 30 days up to this day")
        waitForText("Resting heart rate, 30 days up to this day")
        assertTrue(hasText("Your heart rate at rest.", substring = true))
        shot("45-day-chosen-measure")
    }

    @Test
    fun theDayCanBeSteppedAndPickedFromAList() {
        launchFresh()
        loadDemo()
        openDay()
        val today = LocalDate.now(ZoneOffset.UTC)
        waitForText(Fmt.longDate(today))
        rule.onNodeWithContentDescription("Day after").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Day before").assertIsEnabled().performClick()
        waitForText(Fmt.longDate(today.minusDays(1)))
        rule.onNodeWithContentDescription("Day after").assertIsEnabled().performClick()
        waitForText(Fmt.longDate(today))
        // The list of days: pick one from the middle of it.
        choose(Fmt.dayLabel(today), Fmt.dayLabel(today.minusDays(5)))
        waitForText(Fmt.longDate(today.minusDays(5)))
        shot("46-day-picked")
    }

    @Test
    fun theSourceIsChosenBetweenGarminAndOura() {
        launchFresh(oura = true)
        loadDemo()
        openDay()
        waitForText("measures in a good range", substring = true)
        // Both devices are in use, so the source dropdown is there, on the watch first.
        assertTrue(hasText("Source", substring = false))
        assertTrue(hasText("Body Battery", substring = false), "the watch has Body Battery")
        assertFalse(hasText("Activity score", substring = false))
        choose("Garmin", "Oura")
        waitForText("Activity score")
        // The ring has no Body Battery and no stress; the day card says what it lacks only for measures it has.
        assertFalse(hasText("Body Battery", substring = false))
        assertTrue(hasText("Lowest heart rate", substring = false), "the ring's lowest heart rate")
        shot("47-day-oura")
        choose("Oura", "Garmin")
        waitForText("Body Battery")
        assertFalse(hasText("Activity score", substring = false))
    }

    @Test
    fun aDayWithNothingSaysSo() {
        launchFresh()
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        tab("Day").performClick()
        waitForText("Nothing from Garmin on this day.")
        assertTrue(hasText("Pick another day, or sync first.", substring = false))
        shot("48-day-empty")
    }

    @Test
    fun datesStandUnderTheHealthCharts() {
        launchFresh()
        loadDemo()
        tab("Health").performClick()
        val today = LocalDate.now(ZoneOffset.UTC)
        waitForText(Fmt.shortDate(today.minusDays(29)))
        assertTrue(hasText(Fmt.shortDate(today), substring = false))
        shot("49-health-dates")
    }
}
