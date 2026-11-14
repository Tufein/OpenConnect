package nl.tufein.openconnect

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.Gear
import nl.tufein.openconnect.core.GearBook
import nl.tufein.openconnect.core.Shoe
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.ui.Fmt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v0.28.0 on the JVM: shoes under Insights with their kilometres, and the pair of one activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class GearScreenTest : AppTestBase() {
    private fun container() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container

    private fun book() = GearBook.decode(container().settings.gear)

    private fun openShoes(prepare: (OpenConnectApp) -> Unit = {}) {
        launchFresh(prepare = prepare)
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Shoes").performClick()
        waitForText("Your shoes")
    }

    private fun addPegasus() {
        waitForText("No shoes added yet", substring = true)
        shot("55-shoes-empty")
        rule.onNode(hasSetTextAction()).performTextInput("Pegasus 40")
        clickText("Count my earlier activities too")
        clickText("Add shoes")
        waitForText("Pegasus 40")
    }

    @Test
    fun aPairIsAddedCountsEarlierRunsAndItsLimitAndRetirementCanBeChanged() {
        openShoes()
        addPegasus()
        val shoe = book().shoes.single()
        assertEquals("Pegasus 40", shoe.name)
        assertEquals(Sport.RUNNING, shoe.sport)
        assertNull(shoe.since, "earlier activities were asked for")

        // Every demo run is on it.
        val runs = container().repository.listActivities(sport = Sport.RUNNING, limit = 10_000)
        assertTrue(runs.isNotEmpty())
        val km = runs.sumOf { it.distanceM } / 1000.0
        waitForText("${Fmt.oneDecimal(km)} of 800 km")
        shot("56-shoes")

        clickText("Limit +50 km")
        waitForText("of 850 km", substring = true)
        assertEquals(850, book().shoes.single().retireKm)

        clickText("Retire")
        waitForText("Retired on", substring = true)
        assertTrue(book().shoes.single().until != null)
        clickText("Back in use")
        rule.waitUntil(15_000) { !hasText("Retired on", substring = true) }
        assertNull(book().shoes.single().until)
    }

    @Test
    fun aPairPastItsLimitIsMarkedAndAnActivityCanBePutOnNoShoesAndBack() {
        // 900 km already on a pair that is to be replaced after 800.
        val worn = GearBook(listOf(Shoe("s1", "Pegasus 40", Sport.RUNNING, startKm = 900.0)))
        openShoes { app -> app.container.settings.gear = worn.encode() }
        waitForText("Time for a new pair.", substring = true)
        waitForText("Pegasus 40 has done", substring = true)
        shot("57-shoes-replace")

        tab("Activities").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Easy Run")[0].performClick()
        waitForText("Distance")
        scrollToText("Shoes on this activity")
        waitForText("On: Pegasus 40")
        shot("58-activity-shoes")

        clickText("No shoes")
        waitForText("No shoes on this one")
        assertEquals(Gear.NONE, book().overrides.values.single())
        clickText("Follow the default")
        waitForText("On: Pegasus 40")
        assertTrue(book().overrides.isEmpty())
    }

    @Test
    fun aPairCanBeRemoved() {
        openShoes()
        addPegasus()
        clickText("Remove")
        waitForText("No shoes added yet", substring = true)
        assertTrue(book().shoes.isEmpty())
    }
}
