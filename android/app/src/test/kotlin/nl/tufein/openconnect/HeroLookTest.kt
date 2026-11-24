package nl.tufein.openconnect

import nl.tufein.openconnect.ui.RingWeight
import nl.tufein.openconnect.ui.heroTone
import org.junit.Assert.assertEquals
import org.junit.Test

/** v1.9.1: the photo header follows the hour unless one light is chosen, and the rings can be thin or bold. */
class HeroLookTest {
    @Test
    fun `automatic light follows the hour of the day`() {
        assertEquals("dawn", heroTone("auto", 5).key)
        assertEquals("dawn", heroTone("auto", 9).key)
        assertEquals("day", heroTone("auto", 10).key)
        assertEquals("day", heroTone("auto", 16).key)
        assertEquals("dusk", heroTone("auto", 17).key)
        assertEquals("dusk", heroTone("auto", 20).key)
        assertEquals("night", heroTone("auto", 21).key)
        assertEquals("night", heroTone("auto", 2).key)
    }

    @Test
    fun `a chosen light holds at every hour`() {
        for (hour in 0..23) {
            assertEquals("dawn", heroTone("dawn", hour).key)
            assertEquals("day", heroTone("day", hour).key)
            assertEquals("dusk", heroTone("dusk", hour).key)
            assertEquals("night", heroTone("night", hour).key)
        }
    }

    @Test
    fun `an unknown tone falls back to the automatic light`() {
        assertEquals("day", heroTone("midnight", 12).key)
        assertEquals("day", heroTone(null, 12).key)
        assertEquals("night", heroTone(null, 23).key)
    }

    @Test
    fun `the four hours have their own crop and sky`() {
        val tones = listOf("dawn", "day", "dusk", "night").map { heroTone(it, 12) }
        assertEquals(4, tones.map { it.focus }.toSet().size)
        assertEquals(4, tones.map { it.zoom }.toSet().size)
        assertEquals(4, tones.map { it.sky }.toSet().size)
    }

    @Test
    fun `rings are thin by default and unknown weights keep the thin line`() {
        assertEquals(RingWeight.THIN, RingWeight.of(null))
        assertEquals(RingWeight.THIN, RingWeight.of("huge"))
        assertEquals(RingWeight.BOLD, RingWeight.of("bold"))
        assertEquals("bold", RingWeight.BOLD.key)
        assertEquals(4.5f, RingWeight.BOLD.stroke.value)
    }
}
