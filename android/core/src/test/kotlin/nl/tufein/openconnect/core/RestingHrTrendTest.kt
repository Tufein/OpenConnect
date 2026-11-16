package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestingHrTrendTest {
    private val today = LocalDate.parse("2026-06-30")

    private fun day(daysAgo: Long, hr: Int?) = DailySummary(date = today.minusDays(daysAgo), restingHr = hr)

    /** [recent] bpm for each of the last 7 days and [normal] bpm for each of the 28 days before. */
    private fun history(recent: Int, normal: Int): List<DailySummary> =
        (0L..6L).map { day(it, recent) } + (7L..34L).map { day(it, normal) }

    @Test
    fun `no data or too few days gives nothing`() {
        assertNull(RestingHrTrendCalculator.compute(emptyList(), today))
        assertNull(RestingHrTrendCalculator.compute((0L..2L).map { day(it, 50) } + (7L..34L).map { day(it, 50) }, today)) // 3 recent days
        assertNull(RestingHrTrendCalculator.compute((0L..6L).map { day(it, 50) } + (7L..14L).map { day(it, 50) }, today)) // 8 normal days
    }

    @Test
    fun `days without a resting heart rate do not count`() {
        val none = (0L..34L).map { day(it, null) } + (0L..6L).map { day(it, 0) }
        assertNull(RestingHrTrendCalculator.compute(none, today))
    }

    @Test
    fun `the same as usual is stable`() {
        val t = RestingHrTrendCalculator.compute(history(recent = 52, normal = 52), today)!!
        assertEquals(RestingHrDirection.STABLE, t.direction)
        assertEquals(0.0, t.difference, 1e-9)
        assertEquals(52.0, t.recent, 1e-9)
        assertEquals(52.0, t.normal, 1e-9)
    }

    @Test
    fun `two beats lower is lower and two beats higher is higher`() {
        assertEquals(RestingHrDirection.LOWER, RestingHrTrendCalculator.compute(history(recent = 50, normal = 52), today)!!.direction)
        assertEquals(RestingHrDirection.HIGHER, RestingHrTrendCalculator.compute(history(recent = 54, normal = 52), today)!!.direction)
        assertEquals(RestingHrDirection.STABLE, RestingHrTrendCalculator.compute(history(recent = 53, normal = 52), today)!!.direction)
    }

    @Test
    fun `the difference is recent minus normal`() {
        assertEquals(-4.0, RestingHrTrendCalculator.compute(history(recent = 48, normal = 52), today)!!.difference, 1e-9)
    }

    @Test
    fun `days after today and before the window are ignored`() {
        val sleepy = history(recent = 52, normal = 52) + day(-1, 90) + (36L..60L).map { day(it, 80) }
        assertEquals(RestingHrDirection.STABLE, RestingHrTrendCalculator.compute(sleepy, today)!!.direction)
    }
}
