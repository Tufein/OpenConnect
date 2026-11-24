package nl.tufein.openconnect.ui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The key stored in Settings maps to the card on the front page; an unknown key is always the safe calorie default. */
class HomeMetricTest {
    @Test
    fun `every known key maps back to its card and nothing else does`() {
        for (metric in HomeMetric.entries) assertEquals(metric, HomeMetric.of(metric.key))
        assertEquals(HomeMetric.CALORIES, HomeMetric.of(null))
        assertEquals(HomeMetric.CALORIES, HomeMetric.of(""))
        assertEquals(HomeMetric.CALORIES, HomeMetric.of("blood_oxygen"))
        assertEquals(HomeMetric.CALORIES, HomeMetric.of("CALORIES"))
    }

    @Test
    fun `the keys are unique and the default is one of them`() {
        val keys = HomeMetric.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(HomeMetric.DEFAULT in keys)
        assertEquals(HomeMetric.CALORIES, HomeMetric.of(HomeMetric.DEFAULT))
    }
}
