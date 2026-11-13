package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalsAndLoadTest {
    private val today = LocalDate.parse("2026-06-30")

    private fun run(daysAgo: Long, minutes: Int, avgHr: Int? = null, maxHr: Int? = null) = Activity(
        id = "a-$daysAgo-$minutes-${avgHr ?: 0}",
        startTime = today.minusDays(daysAgo).atTime(8, 0).toInstant(ZoneOffset.UTC),
        durationS = minutes * 60.0,
        distanceM = minutes * 150.0,
        avgHr = avgHr,
        maxHr = maxHr,
    )

    // goals

    @Test
    fun `defaults follow the common recommendations`() {
        val g = WeeklyGoals()
        assertEquals(150, g.intensityMinutes)
        assertEquals(20, g.distanceKm)
        assertEquals(3, g.activities)
    }

    @Test
    fun `progress is measured against the week and caps the bar but not the number`() {
        val week = WeeklySummary(weekStart = today.minusDays(6), weekEnd = today, activityCount = 4, totalDistanceM = 12_000.0, intensityMinutes = 90)
        val p = WeeklyGoals(intensityMinutes = 150, distanceKm = 20, activities = 3).progress(week)
        assertEquals(0.6, p.intensity.fraction, 1e-9)
        assertFalse(p.intensity.reached)
        assertEquals(0.6, p.distance.fraction, 1e-9)
        assertEquals(12.0, p.distance.done, 1e-9)
        assertEquals(1.0, p.activities.fraction, 1e-9) // 4 of 3: the bar is full
        assertEquals(4.0, p.activities.done, 1e-9) // but the number is honest
        assertTrue(p.activities.reached)
        assertEquals(1, p.reachedCount)
    }

    @Test
    fun `a goal of zero counts as switched off and is never reached`() {
        val p = WeeklyGoals(0, 0, 0).progress(WeeklySummary(today, today, activityCount = 9))
        assertEquals(0.0, p.activities.fraction, 1e-9)
        assertFalse(p.activities.reached)
        assertEquals(0, p.reachedCount)
    }

    @Test
    fun `goals are kept inside sensible limits`() {
        val g = WeeklyGoals(intensityMinutes = 5000, distanceKm = -3, activities = 99).coerced()
        assertEquals(1000, g.intensityMinutes)
        assertEquals(0, g.distanceKm)
        assertEquals(21, g.activities)
    }

    // training load

    @Test
    fun `harder effort counts for more per minute`() {
        val easy = TrainingLoadCalculator.activityLoad(run(0, 60, avgHr = 110), maxHr = 190)
        val hard = TrainingLoadCalculator.activityLoad(run(0, 60, avgHr = 170), maxHr = 190)
        assertTrue(hard > easy * 2, "easy=$easy hard=$hard")
        assertEquals(60.0, easy, 1e-9) // under 60% of max: weight 1
        assertEquals(60 * 4.0, TrainingLoadCalculator.activityLoad(run(0, 60, avgHr = 180), maxHr = 190), 1e-9) // 90%+: weight 4
    }

    @Test
    fun `without heart rate an activity counts as steady effort`() {
        assertEquals(60 * 2.0, TrainingLoadCalculator.activityLoad(run(0, 60), maxHr = 190), 1e-9)
    }

    @Test
    fun `too little history gives no verdict`() {
        val acts = listOf(run(2, 45), run(1, 30), run(0, 30))
        assertNull(TrainingLoadCalculator.compute(acts, today))
        assertNull(TrainingLoadCalculator.compute(emptyList(), today))
    }

    @Test
    fun `a steady routine is steady and the weekly bars are oldest first`() {
        val acts = (0 until 28 step 2).map { run(it.toLong(), 40, avgHr = 140) }
        val load = assertNotNull(TrainingLoadCalculator.compute(acts, today))
        assertEquals(LoadStatus.STEADY, load.status)
        assertEquals(6, load.weeks.size)
        assertTrue(load.weeks.first().first < load.weeks.last().first)
        assertEquals(1.0, load.ratio, 0.15)
    }

    @Test
    fun `a sudden big week is heavy or worse`() {
        val base = (8 until 28 step 3).map { run(it.toLong(), 30, avgHr = 130) }
        val spike = (0 until 7).map { run(it.toLong(), 90, avgHr = 165) }
        val load = assertNotNull(TrainingLoadCalculator.compute(base + spike, today))
        assertTrue(load.status == LoadStatus.HEAVY || load.status == LoadStatus.OVERREACHING, load.status.toString())
        assertTrue(load.ratio > 1.3)
    }

    @Test
    fun `a quiet week after a busy month is light`() {
        val busy = (8 until 28).map { run(it.toLong(), 60, avgHr = 150) }
        val quiet = listOf(run(1, 20, avgHr = 120))
        val load = assertNotNull(TrainingLoadCalculator.compute(busy + quiet, today))
        assertEquals(LoadStatus.LIGHT, load.status)
    }
}
