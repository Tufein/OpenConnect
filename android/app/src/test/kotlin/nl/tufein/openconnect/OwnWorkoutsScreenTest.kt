package nl.tufein.openconnect

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.tufein.openconnect.core.WorkoutBook
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.6.0: a workout made on the Plan tab is saved with the app and can be deleted again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class OwnWorkoutsScreenTest : AppTestBase() {
    private fun openPlan() {
        launchFresh()
        waitForText("Load demo data")
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        clickChip("More")
        clickChip("Plan")
        waitForText("Your own workouts")
    }

    private fun typeInto(label: String, text: String, substring: Boolean = false) {
        val fields = rule.onAllNodes(androidx.compose.ui.test.hasText(label, substring = substring) and hasSetTextAction()).fetchSemanticsNodes()
        assertTrue(fields.isNotEmpty(), "no field labelled $label")
        rule.onAllNodes(androidx.compose.ui.test.hasText(label, substring = substring) and hasSetTextAction())[0].performTextInput(text)
    }

    private fun replaceIn(label: String, text: String, substring: Boolean = false) {
        val fields = rule.onAllNodes(androidx.compose.ui.test.hasText(label, substring = substring) and hasSetTextAction()).fetchSemanticsNodes()
        assertTrue(fields.isNotEmpty(), "no field labelled $label")
        rule.onAllNodes(androidx.compose.ui.test.hasText(label, substring = substring) and hasSetTextAction())[0].apply {
            performTextClearance()
            performTextInput(text)
        }
    }

    @Test
    fun aWorkoutIsSavedAndDeletedAgain() {
        openPlan()
        clickText("New workout")
        typeInto("Name", "Hill repeats")
        clickText("Save workout")
        waitForText("Hill repeats")
        waitForText("4 × 4 min", substring = true)

        val settings = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings
        val stored = WorkoutBook.decode(settings.workouts).workouts
        assertEquals(1, stored.size)
        assertEquals("Hill repeats", stored[0].name)
        assertEquals(4, stored[0].repeats)
        assertEquals(2220, stored[0].totalS)

        clickText("Delete")
        rule.waitUntil(15_000) { !hasText("Hill repeats", substring = true) }
        assertTrue(WorkoutBook.decode(settings.workouts).workouts.isEmpty())
    }

    @Test
    fun withoutANameItRefusesToSave() {
        openPlan()
        clickText("New workout")
        clickText("Save workout")
        waitForText("Give the workout a name.")
        val settings = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings
        assertTrue(settings.workouts.isBlank(), "nothing is stored without a name")
    }

    @Test
    fun aDistanceWorkoutIsSavedAndCanBeEditedAfterwards() {
        openPlan()
        clickText("New workout")
        typeInto("Name", "1 km repeats")
        typeInto("Distance per repeat", "1000", substring = true)
        typeInto("Pace", "300-330", substring = true)
        clickText("Save workout")
        waitForText("1 km repeats")
        waitForText("4 × 1000 m", substring = true)

        val settings = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings
        val saved = WorkoutBook.decode(settings.workouts).workouts.single()
        assertEquals(1_000, saved.fastMeters)
        assertEquals(300, saved.paceFastS)
        assertEquals(330, saved.paceSlowS)

        clickText("Edit")
        replaceIn("Repeats", "6")
        clickText("Save workout")
        waitForText("6 × 1000 m", substring = true)

        val edited = WorkoutBook.decode(settings.workouts).workouts
        assertEquals(1, edited.size, "editing replaces the workout instead of adding one")
        assertEquals(saved.id, edited[0].id)
        assertEquals(6, edited[0].repeats)
        assertEquals(1_000, edited[0].fastMeters)
    }
}
