package nl.tufein.openconnect

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.3.0: a workout typed on the Activities screen lands in the list and in the database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class ManualActivityScreenTest : AppTestBase() {
    private fun openActivitiesWithDemo() {
        launchFresh()
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        tab("Activities").performClick()
        waitForText("Import")
    }

    private fun typeInto(label: String, text: String) {
        val field = rule.onAllNodes(hasText(label) and hasSetTextAction()).fetchSemanticsNodes()
        assertTrue(field.isNotEmpty(), "no field labelled $label")
        rule.onAllNodes(hasText(label) and hasSetTextAction())[0].performTextInput(text)
        rule.waitUntil(5_000) { hasText(text, substring = true) }
    }

    @Test
    fun aTypedWorkoutIsStoredWithItsOwnSource() {
        openActivitiesWithDemo()
        clickText("Add")
        waitForText("Add a workout")
        typeInto("Minutes", "45")
        typeInto("Kilometres (optional)", "8,5")
        clickChip("Save")
        // The demo list already contains a run, so "Running ·" is not proof that the typed workout was saved.
        val stored = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.repository
        rule.waitUntil(10_000) { stored.listActivities().any { it.source == "manual" } }
        waitForText("Running ·", substring = true)

        val typed = stored.listActivities().firstOrNull { it.source == "manual" }
        assertTrue(typed != null, "a manual activity should be stored")
        assertEquals(45 * 60.0, typed.durationS)
        assertEquals(8500.0, typed.distanceM)
        assertEquals(LocalDate.now(), typed.startTime.atZone(ZoneId.systemDefault()).toLocalDate())
    }

    @Test
    fun withoutMinutesTheFormRefusesToSave() {
        openActivitiesWithDemo()
        clickText("Add")
        waitForText("Add a workout")
        clickChip("Save")
        waitForText("Fill in a duration from 1 to 1440 minutes.")
        val stored = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.repository
        assertTrue(stored.listActivities().none { it.source == "manual" }, "nothing is stored without a duration")
    }
}
