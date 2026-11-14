package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NudgesShoesTest {
    private val monday = LocalDate.parse("2026-11-09")
    private val goals = WeeklyGoals(0, 0, 0)
    private val week = WeeklySummary(weekStart = monday, weekEnd = monday.plusDays(6), activityCount = 0, totalDistanceM = 0.0, intensityMinutes = 0)
    private val pegasus = ShoeUsage(Shoe("s1", "Pegasus", Sport.RUNNING, retireKm = 800), 812.4, 90, null, ShoeStatus.REPLACE)

    private fun due(hour: Int, worn: List<ShoeUsage>, sent: Set<String> = emptySet()) =
        Nudges.due(monday.plusDays(2), hour, goals, week, week, null, sent, worn)

    @Test
    fun aWornPairIsANudgeFromTheMorningWithItsNameAndKilometres() {
        val n = due(9, listOf(pegasus)).single()
        assertEquals(NudgeKind.SHOES_WORN, n.kind)
        assertEquals("Pegasus", n.goal)
        assertEquals(listOf(812), n.numbers)
        assertTrue(due(7, listOf(pegasus)).isEmpty(), "not in the middle of the night")
        assertTrue(due(9, emptyList()).isEmpty())
    }

    @Test
    fun theSameLimitIsOnlySentOnceButANewLimitIsNewNews() {
        val first = due(9, listOf(pegasus)).single()
        assertTrue(due(9, listOf(pegasus), sent = setOf(first.key)).isEmpty())
        val raised = pegasus.copy(shoe = pegasus.shoe.copy(retireKm = 850))
        assertEquals(1, due(9, listOf(raised), sent = setOf(first.key)).size)
    }
}
