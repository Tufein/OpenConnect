package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SleepConsistencyTest {
    private val today = LocalDate.parse("2026-06-30")

    /** One night per entry, newest last, ending today; [hours] of 0 is a night without data. */
    private fun nights(vararg hours: Double): List<SleepSummary> =
        hours.mapIndexed { i, h -> SleepSummary(today.minusDays((hours.size - 1 - i).toLong()), durationS = (h * 3600).toInt()) }

    @Test
    fun `too few nights gives nothing`() {
        assertNull(SleepConsistencyCalculator.compute(emptyList(), today))
        assertNull(SleepConsistencyCalculator.compute(nights(8.0, 8.0, 8.0, 8.0, 8.0, 8.0), today)) // 6 nights
    }

    @Test
    fun `nights without data and old nights do not count`() {
        val sleeps = nights(8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 0.0) + SleepSummary(today.minusDays(20), durationS = 8 * 3600)
        assertNull(SleepConsistencyCalculator.compute(sleeps, today))
    }

    @Test
    fun `the same night every night is steady with no spread`() {
        val r = SleepConsistencyCalculator.compute(nights(7.5, 7.5, 7.5, 7.5, 7.5, 7.5, 7.5), today)!!
        assertEquals(0, r.spreadMinutes)
        assertEquals(7.5, r.averageHours, 1e-9)
        assertEquals(7.5, r.shortestHours, 1e-9)
        assertEquals(7.5, r.longestHours, 1e-9)
        assertEquals(7, r.nights)
        assertEquals(SleepRhythm.STEADY, r.rhythm)
    }

    @Test
    fun `half an hour either way is variable`() {
        // hours 7.0 and 8.0 alternating: mean 7.5, spread 30 minutes exactly, which is the edge and belongs to variable
        val r = SleepConsistencyCalculator.compute(nights(7.0, 8.0, 7.0, 8.0, 7.0, 8.0, 7.0, 8.0), today)!!
        assertEquals(30, r.spreadMinutes)
        assertEquals(SleepRhythm.VARIABLE, r.rhythm)
        assertEquals(7.0, r.shortestHours, 1e-9)
        assertEquals(8.0, r.longestHours, 1e-9)
    }

    @Test
    fun `big swings are erratic`() {
        // 5 and 9 hours alternating: spread 2 hours
        val r = SleepConsistencyCalculator.compute(nights(5.0, 9.0, 5.0, 9.0, 5.0, 9.0, 5.0, 9.0), today)!!
        assertEquals(120, r.spreadMinutes)
        assertEquals(SleepRhythm.ERRATIC, r.rhythm)
    }

    @Test
    fun `only the last fourteen days count`() {
        val old = (15..25).map { SleepSummary(today.minusDays(it.toLong()), durationS = 3 * 3600) }
        val r = SleepConsistencyCalculator.compute(old + nights(8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0), today)!!
        assertEquals(SleepRhythm.STEADY, r.rhythm)
        assertEquals(7, r.nights)
    }
}
