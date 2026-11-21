package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HrvStatusTest {
    private val today = LocalDate.of(2026, 11, 21)

    private fun days(recent: Int?, normal: Int?, recentDays: Int = 7, normalDays: Int = 28): List<DailySummary> {
        val list = ArrayList<DailySummary>()
        for (i in 0 until recentDays) {
            if (recent != null) list += DailySummary(date = today.minusDays(i.toLong()), hrvMs = recent)
        }
        for (i in recentDays until recentDays + normalDays) {
            if (normal != null) list += DailySummary(date = today.minusDays(i.toLong()), hrvMs = normal)
        }
        return list
    }

    @Test
    fun `a week within a tenth of the normal is balanced`() {
        val status = HrvStatusCalculator.compute(days(recent = 52, normal = 50), today)!!
        assertEquals(HrvStatusLevel.BALANCED, status.level)
        assertEquals(52.0, status.recentMs)
        assertEquals(50.0, status.normalMs)
        assertEquals(4.0, status.changePct)
    }

    @Test
    fun `a week clearly below the normal is low`() {
        val status = HrvStatusCalculator.compute(days(recent = 40, normal = 55), today)!!
        assertEquals(HrvStatusLevel.LOW, status.level)
    }

    @Test
    fun `a week clearly above the normal is high`() {
        val status = HrvStatusCalculator.compute(days(recent = 70, normal = 55), today)!!
        assertEquals(HrvStatusLevel.HIGH, status.level)
    }

    @Test
    fun `too few nights gives no status at all`() {
        val recent3 = (0 until 3).map { DailySummary(date = today.minusDays(it.toLong()), hrvMs = 50) }
        val normalFrom10 = (10 until 40).map { DailySummary(date = today.minusDays(it.toLong()), hrvMs = 50) }
        assertNull(HrvStatusCalculator.compute(recent3 + normalFrom10, today))

        val normal9 = (10 until 19).map { DailySummary(date = today.minusDays(it.toLong()), hrvMs = 50) }
        val recent7 = (0 until 7).map { DailySummary(date = today.minusDays(it.toLong()), hrvMs = 50) }
        assertNull(HrvStatusCalculator.compute(recent7 + normal9, today))

        assertNull(HrvStatusCalculator.compute(days(recent = null, normal = 50), today))
        assertNull(HrvStatusCalculator.compute(days(recent = 50, normal = null), today))
    }

    @Test
    fun `missing and zero nights do not count`() {
        val list = days(recent = 50, normal = 50).toMutableList()
        list += DailySummary(date = today.minusDays(1), hrvMs = 0)
        list += DailySummary(date = today.minusDays(2), hrvMs = null)
        val status = HrvStatusCalculator.compute(list, today)!!
        assertEquals(50.0, status.recentMs)
    }
}
