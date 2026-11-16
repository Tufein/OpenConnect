package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StepGoalWeekTest {
    private val today = LocalDate.parse("2026-06-30")

    private fun day(daysAgo: Long, steps: Int, goal: Int? = 8000) = DailySummary(date = today.minusDays(daysAgo), steps = steps, stepsGoal = goal)

    @Test
    fun `fewer than three days with steps gives nothing`() {
        assertNull(StepGoalWeekCalculator.compute(emptyList(), today))
        assertNull(StepGoalWeekCalculator.compute(listOf(day(0, 9000), day(1, 9000), day(2, 0)), today))
    }

    @Test
    fun `counts the days the goal was reached`() {
        val w = StepGoalWeekCalculator.compute(listOf(day(0, 9000), day(1, 8000), day(2, 7999), day(3, 12000), day(4, 3000)), today)!!
        assertEquals(3, w.daysReached) // 9000, 8000 (exactly the goal) and 12000
        assertEquals(5, w.daysWithData)
        assertEquals(8000, w.goal)
        assertEquals(8000, w.averageSteps) // (9000+8000+7999+12000+3000)/5 = 7999.8
        assertEquals(today.minusDays(3), w.bestDay)
        assertEquals(12000, w.bestSteps)
    }

    @Test
    fun `a day without a goal uses ten thousand`() {
        val w = StepGoalWeekCalculator.compute(listOf(day(0, 9999, goal = null), day(1, 10000, goal = null), day(2, 5000, goal = 0)), today)!!
        assertEquals(1, w.daysReached)
        assertEquals(StepGoalWeekCalculator.DEFAULT_GOAL, w.goal)
    }

    @Test
    fun `only the last seven days count`() {
        val old = (7L..20L).map { day(it, 20000) }
        val w = StepGoalWeekCalculator.compute(old + listOf(day(0, 1000), day(1, 1000), day(2, 1000)), today)!!
        assertEquals(0, w.daysReached)
        assertEquals(3, w.daysWithData)
        assertEquals(1000, w.bestSteps)
    }

    @Test
    fun `days after today are ignored`() {
        val w = StepGoalWeekCalculator.compute(listOf(day(0, 9000), day(1, 9000), day(2, 9000), day(-1, 30000)), today)!!
        assertEquals(3, w.daysWithData)
        assertEquals(9000, w.bestSteps)
    }

    @Test
    fun `the goal is the one of the latest day`() {
        val w = StepGoalWeekCalculator.compute(listOf(day(0, 9000, goal = 9000), day(1, 9000, goal = 6000), day(2, 9000, goal = 6000)), today)!!
        assertEquals(9000, w.goal)
        assertEquals(3, w.daysReached)
    }
}
