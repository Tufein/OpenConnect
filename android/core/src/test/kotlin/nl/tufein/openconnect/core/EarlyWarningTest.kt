package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EarlyWarningTest {
    private val today = LocalDate.parse("2026-06-30")

    /** 28 normal days before the last three (HRV 60, resting 52), then [last3] oldest first; null means no data that day. */
    private fun days(last3: List<Pair<Int, Int>?>, normalDays: Int = 28): List<DailySummary> {
        val normal = (0 until normalDays).map { DailySummary(date = today.minusDays(3L + it), hrvMs = 60, restingHr = 52) }
        val recent = last3.mapIndexedNotNull { i, v ->
            v?.let { DailySummary(date = today.minusDays((2 - i).toLong()), hrvMs = it.first, restingHr = it.second) }
        }
        return normal + recent
    }

    private val ok = 60 to 52
    private val high = 60 to 57 // resting heart rate 5 up, HRV normal
    private val low = 45 to 52 // HRV 25 percent down, resting heart rate normal
    private val both = 45 to 57

    @Test
    fun `normal days give no warning`() {
        assertNull(EarlyWarning.compute(days(listOf(ok, ok, ok)), today))
    }

    @Test
    fun `one odd day is not enough`() {
        assertNull(EarlyWarning.compute(days(listOf(ok, ok, high)), today))
        assertNull(EarlyWarning.compute(days(listOf(ok, ok, both)), today))
    }

    @Test
    fun `two odd days in a row on one signal is a mild warning`() {
        val w = EarlyWarning.compute(days(listOf(ok, high, high)), today)!!
        assertEquals(WarningLevel.MILD, w.level)
        assertEquals(2, w.days)
        assertEquals(5.0, w.restingHrUp!!, 1e-9)
        val hrv = EarlyWarning.compute(days(listOf(ok, low, low)), today)!!
        assertEquals(WarningLevel.MILD, hrv.level)
        assertEquals(25.0, hrv.hrvDownPct!!, 1e-9)
    }

    @Test
    fun `both signals off, or three days in a row, is a strong warning`() {
        assertEquals(WarningLevel.STRONG, EarlyWarning.compute(days(listOf(ok, both, both)), today)!!.level)
        assertEquals(WarningLevel.STRONG, EarlyWarning.compute(days(listOf(high, high, high)), today)!!.level)
        assertEquals(3, EarlyWarning.compute(days(listOf(high, low, high)), today)!!.days)
    }

    @Test
    fun `a day back to normal ends the warning`() {
        assertNull(EarlyWarning.compute(days(listOf(high, high, ok)), today))
    }

    @Test
    fun `a day without data is skipped`() {
        val w = EarlyWarning.compute(days(listOf(high, high, null)), today)
        assertNotNull(w)
        assertEquals(2, w.days)
    }

    @Test
    fun `without a baseline there is nothing to compare with`() {
        assertNull(EarlyWarning.compute(days(listOf(both, both, both), normalDays = 6), today))
        assertNotNull(EarlyWarning.compute(days(listOf(both, both, both), normalDays = 7), today))
    }

    @Test
    fun `the odd days are kept out of the baseline`() {
        // If the last three days counted, the average would drift up and hide the change.
        val w = EarlyWarning.compute(days(listOf(both, both, both)), today)!!
        assertEquals(5.0, w.restingHrUp!!, 1e-9)
        assertEquals(25.0, w.hrvDownPct!!, 1e-9)
    }
}
