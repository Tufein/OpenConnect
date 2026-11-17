package nl.tufein.openconnect.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AxisTicksTest {
    @Test
    fun `thirty days get five labels, first and last included`() {
        assertEquals(listOf(0, 7, 15, 22, 29), AxisTicks.indices(30))
    }

    @Test
    fun `a week gets five labels too and never more than there are points`() {
        assertEquals(listOf(0, 2, 3, 5, 6), AxisTicks.indices(7))
        assertEquals(listOf(0, 1, 2), AxisTicks.indices(3))
        assertEquals(listOf(0, 1), AxisTicks.indices(2))
        assertEquals(listOf(0), AxisTicks.indices(1))
    }

    @Test
    fun `no points, no labels`() {
        assertEquals(emptyList(), AxisTicks.indices(0))
        assertEquals(emptyList(), AxisTicks.indices(-4))
        assertEquals(emptyList(), AxisTicks.indices(10, max = 0))
    }

    @Test
    fun `the labels are in order with no repeats for any length`() {
        for (count in 1..400) {
            val ticks = AxisTicks.indices(count)
            assertEquals(ticks.sorted().distinct(), ticks, "count $count")
            assertEquals(0, ticks.first())
            assertEquals(count - 1, ticks.last())
            assertTrue(ticks.size <= AxisTicks.DEFAULT_MAX)
        }
    }

    @Test
    fun `a line puts its ends on the edges, a bar sits in the middle of its slot`() {
        assertEquals(0.0, AxisTicks.fraction(0, 30, bars = false), 1e-9)
        assertEquals(1.0, AxisTicks.fraction(29, 30, bars = false), 1e-9)
        assertEquals(0.5, AxisTicks.fraction(2, 5, bars = false), 1e-9)
        assertEquals(0.1, AxisTicks.fraction(0, 5, bars = true), 1e-9)
        assertEquals(0.9, AxisTicks.fraction(4, 5, bars = true), 1e-9)
        assertEquals(0.5, AxisTicks.fraction(0, 1, bars = false), 1e-9)
    }
}
