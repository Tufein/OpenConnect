package nl.tufein.openconnect.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** v0.57.0: what a screen reader says about a chart, worked out from the same numbers the chart draws. */
class ChartSummaryTest {
    @Test
    fun `a chart with nothing in it has no summary`() {
        assertNull(ChartSummary.of(emptyList()))
        assertNull(ChartSummary.of(listOf(null, null)))
    }

    @Test
    fun `the summary has the count, the lowest, the highest and the latest value`() {
        val s = ChartSummary.of(listOf(5.0, 9.0, 2.0, 7.0))!!
        assertEquals(4, s.count)
        assertEquals(2.0, s.lowest)
        assertEquals(9.0, s.highest)
        assertEquals(7.0, s.latest)
        assertEquals(3, s.latestIndex)
    }

    @Test
    fun `missing days are not counted and the latest is the last day that has a value`() {
        val s = ChartSummary.of(listOf(null, 4.0, null, 6.0, null))!!
        assertEquals(2, s.count)
        assertEquals(4.0, s.lowest)
        assertEquals(6.0, s.highest)
        assertEquals(6.0, s.latest)
        assertEquals(3, s.latestIndex)
    }

    @Test
    fun `one value is its own lowest, highest and latest`() {
        val s = ChartSummary.of(listOf(3.5))!!
        assertEquals(1, s.count)
        assertEquals(3.5, s.lowest)
        assertEquals(3.5, s.highest)
        assertEquals(3.5, s.latest)
    }
}
