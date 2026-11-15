package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeasonPlanTest {
    private val today = LocalDate.parse("2026-01-05") // a Monday

    private fun goal(weeksOut: Long, distance: RaceDistance = RaceDistance.MARATHON, extraDays: Long = 0) =
        RaceGoal(today.plusWeeks(weeksOut).plusDays(extraDays), distance, distance.defaultSeconds)

    private fun shape(plan: SeasonPlan) = plan.blocks.map { it.phase to it.weeks }

    @Test
    fun `no plan for a race today or in the past`() {
        assertNull(SeasonPlanner.plan(RaceGoal(today, RaceDistance.K10, 3600), today))
        assertNull(SeasonPlanner.plan(RaceGoal(today.minusDays(3), RaceDistance.K10, 3600), today))
    }

    @Test
    fun `a race next week is only the taper`() {
        val plan = SeasonPlanner.plan(goal(1, RaceDistance.K10), today)!!
        assertEquals(1, plan.weeksToRace)
        assertEquals(listOf(SeasonPhase.TAPER to 1), shape(plan))
    }

    @Test
    fun `twelve weeks for a marathon give two base, four build, three peak and three taper`() {
        val plan = SeasonPlanner.plan(goal(12), today)!!
        assertEquals(12, plan.weeksToRace)
        assertEquals(listOf(SeasonPhase.BASE to 2, SeasonPhase.BUILD to 4, SeasonPhase.PEAK to 3, SeasonPhase.TAPER to 3), shape(plan))
    }

    @Test
    fun `twelve weeks for a 10K have a short taper and more build`() {
        val plan = SeasonPlanner.plan(goal(12, RaceDistance.K10), today)!!
        assertEquals(listOf(SeasonPhase.BASE to 6, SeasonPhase.BUILD to 3, SeasonPhase.PEAK to 2, SeasonPhase.TAPER to 1), shape(plan))
    }

    @Test
    fun `a short run in drops the phases that no longer fit, starting from the back`() {
        val plan = SeasonPlanner.plan(goal(3, RaceDistance.HALF), today)!!
        assertEquals(listOf(SeasonPhase.PEAK to 1, SeasonPhase.TAPER to 2), shape(plan))
    }

    @Test
    fun `the blocks follow each other from today and the weeks always add up`() {
        for (weeks in 1L..40L) for (d in RaceDistance.entries) {
            val plan = SeasonPlanner.plan(goal(weeks, d), today)!!
            assertEquals(weeks.toInt(), plan.blocks.sumOf { it.weeks }, "weeks $weeks $d")
            assertEquals(today, plan.blocks.first().start)
            plan.blocks.zipWithNext { a, b -> assertEquals(a.start.plusWeeks(a.weeks.toLong()), b.start) }
            assertTrue(plan.blocks.last().end >= goal(weeks, d).date.minusDays(1))
        }
    }

    @Test
    fun `days that do not fill a week count as a week`() {
        assertEquals(13, SeasonPlanner.plan(goal(12, extraDays = 1), today)!!.weeksToRace)
        assertEquals(12, SeasonPlanner.plan(goal(12), today)!!.weeksToRace)
    }

    @Test
    fun `a phase begins on the same week before the race however often the plan is worked out`() {
        val g = goal(12)
        assertEquals(SeasonPhase.BASE, SeasonPlanner.plan(g, today)!!.current?.phase)
        assertEquals(SeasonPhase.BASE, SeasonPlanner.plan(g, today.plusWeeks(1))!!.current?.phase)
        assertEquals(SeasonPhase.BUILD, SeasonPlanner.plan(g, today.plusWeeks(2))!!.current?.phase) // ten weeks to go
        assertEquals(SeasonPhase.BUILD, SeasonPlanner.plan(g, today.plusWeeks(5))!!.current?.phase)
        assertEquals(SeasonPhase.PEAK, SeasonPlanner.plan(g, today.plusWeeks(6))!!.current?.phase) // six weeks to go
        assertEquals(SeasonPhase.TAPER, SeasonPlanner.plan(g, today.plusWeeks(9))!!.current?.phase) // three weeks to go
    }

    @Test
    fun `a race far away only plans the last year and has no current block yet`() {
        val plan = SeasonPlanner.plan(goal(70), today)!!
        assertEquals(70, plan.weeksToRace)
        assertEquals(52, plan.blocks.sumOf { it.weeks })
        assertEquals(today.plusWeeks(18), plan.blocks.first().start)
        assertNull(plan.current)
        assertNotNull(SeasonPlanner.plan(goal(70), today.plusWeeks(20))!!.current)
    }

    @Test
    fun `each phase has a range of weekly kilometres around what you do now`() {
        assertEquals(38.0 to 42.0, SeasonPlanner.weeklyKm(SeasonPhase.BASE, 40.0))
        assertEquals(42.0 to 48.0, SeasonPlanner.weeklyKm(SeasonPhase.BUILD, 40.0))
        assertEquals(48.0 to 52.0, SeasonPlanner.weeklyKm(SeasonPhase.PEAK, 40.0))
        assertEquals(20.0 to 28.0, SeasonPlanner.weeklyKm(SeasonPhase.TAPER, 40.0))
        assertNull(SeasonPlanner.weeklyKm(SeasonPhase.BASE, null))
        assertNull(SeasonPlanner.weeklyKm(SeasonPhase.BASE, 0.0))
    }
}
