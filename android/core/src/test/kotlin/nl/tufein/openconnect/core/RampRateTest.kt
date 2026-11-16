package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RampRateTest {
    private val today = LocalDate.parse("2026-06-30")

    /** A session without heart rate: a load of 2 per minute. */
    private fun session(daysAgo: Long, load: Int) = Activity(
        id = "s-$daysAgo-$load",
        startTime = today.minusDays(daysAgo).atTime(8, 0).toInstant(ZoneOffset.UTC),
        durationS = load / 2.0 * 60,
    )

    /** One session per week-day-slot: [thisWeek] load today, [before] load on the same weekday of each of the four weeks before. */
    private fun history(thisWeek: Int, before: Int, from: Long = 28): List<Activity> =
        listOf(session(0, thisWeek)) + (1..4).map { session(it * 7L, before) } + listOf(session(from + 7, 10))

    @Test
    fun `no activities or too little history gives nothing`() {
        assertNull(RampRateCalculator.compute(emptyList(), today))
        assertNull(RampRateCalculator.compute(listOf(session(0, 100), session(10, 100)), today)) // only 10 days of history
    }

    @Test
    fun `a week with nothing before it gives nothing`() {
        assertNull(RampRateCalculator.compute(listOf(session(0, 100), session(40, 100)), today)) // the four weeks before are empty
    }

    @Test
    fun `the same load as usual is steady`() {
        val r = RampRateCalculator.compute(history(thisWeek = 100, before = 100), today)!!
        assertEquals(1.0, r.ratio, 1e-9)
        assertEquals(100.0, r.weekLoad, 1e-9)
        assertEquals(100.0, r.usualWeekLoad, 1e-9)
        assertEquals(RampLevel.STEADY, r.level)
    }

    @Test
    fun `clearly less than usual is low`() {
        assertEquals(RampLevel.LOW, RampRateCalculator.compute(history(thisWeek = 50, before = 100), today)!!.level)
    }

    @Test
    fun `a bit more than usual is high`() {
        assertEquals(RampLevel.HIGH, RampRateCalculator.compute(history(thisWeek = 140, before = 100), today)!!.level)
    }

    @Test
    fun `a big jump is a spike`() {
        val r = RampRateCalculator.compute(history(thisWeek = 200, before = 100), today)!!
        assertEquals(2.0, r.ratio, 1e-9)
        assertEquals(RampLevel.SPIKE, r.level)
    }

    @Test
    fun `the edges of the levels belong to the higher level`() {
        assertEquals(RampLevel.STEADY, RampRateCalculator.compute(history(thisWeek = 80, before = 100), today)!!.level)
        assertEquals(RampLevel.HIGH, RampRateCalculator.compute(history(thisWeek = 130, before = 100), today)!!.level)
        assertEquals(RampLevel.SPIKE, RampRateCalculator.compute(history(thisWeek = 150, before = 100), today)!!.level)
    }

    @Test
    fun `activities after today are ignored`() {
        val withFuture = history(thisWeek = 100, before = 100) + session(-2, 900)
        assertNotNull(RampRateCalculator.compute(withFuture, today))
        assertEquals(1.0, RampRateCalculator.compute(withFuture, today)!!.ratio, 1e-9)
    }
}
