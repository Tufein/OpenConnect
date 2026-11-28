// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.retro

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 8-bit tunes are made on the phone from a list of notes, so no sound file adds to the APK. These tests pin what
 * the ear cannot check in CI: the length, the pitch, the silence of a rest, the volume and the soft edges that stop a
 * click at the start and end of every note.
 */
class ChiptuneTest {
    private val rate = Chiptune.SAMPLE_RATE

    private fun crossings(samples: ShortArray): Int =
        (1 until samples.size).count { (samples[it - 1] < 0) != (samples[it] < 0) }

    @Test
    fun `a tune lasts exactly as long as its notes`() {
        val tune = Chiptune.Tune("test", listOf(Chiptune.Note(440.0, 100), Chiptune.Note(0.0, 50), Chiptune.Note(880.0, 250)))
        assertEquals(rate * 100 / 1000 + rate * 50 / 1000 + rate * 250 / 1000, Chiptune.render(tune).size)
        assertEquals(400, tune.millis)
    }

    @Test
    fun `a note has the pitch it says`() {
        val samples = Chiptune.render(Chiptune.Tune("a", listOf(Chiptune.Note(1000.0, 200))))
        // A square wave crosses zero twice per period: 1000 Hz for 0.2 s is 200 periods, 400 crossings.
        val n = crossings(samples)
        assertTrue(abs(n - 400) <= 4, "expected about 400 zero crossings for 1000 Hz, got $n")
    }

    @Test
    fun `a rest is silent`() {
        val samples = Chiptune.render(Chiptune.Tune("rest", listOf(Chiptune.Note(0.0, 120))))
        assertTrue(samples.all { it.toInt() == 0 })
    }

    @Test
    fun `the volume stays well below the maximum and every note starts and ends softly`() {
        for (tune in Chiptune.TUNES) {
            val samples = Chiptune.render(tune)
            val loudest = samples.maxOf { abs(it.toInt()) }
            assertTrue(loudest <= (Short.MAX_VALUE * Chiptune.VOLUME).toInt() + 1, "${tune.key}: $loudest is too loud")
            assertTrue(loudest > 1000, "${tune.key}: it should be audible")
            var start = 0
            for (note in tune.notes) {
                val length = rate * note.millis / 1000
                if (length > 0) {
                    assertTrue(abs(samples[start].toInt()) < 200, "${tune.key}: a note starts with a click")
                    assertTrue(abs(samples[start + length - 1].toInt()) < 200, "${tune.key}: a note ends with a click")
                }
                start += length
            }
        }
    }

    @Test
    fun `the tunes are short and have their own keys`() {
        assertTrue(Chiptune.TUNES.isNotEmpty())
        assertEquals(Chiptune.TUNES.size, Chiptune.TUNES.map { it.key }.toSet().size)
        for (tune in Chiptune.TUNES) {
            assertTrue(tune.millis in 200..1500, "${tune.key} lasts ${tune.millis} ms")
            assertTrue(tune.notes.all { it.hz == 0.0 || it.hz in 100.0..4000.0 }, "${tune.key} stays in a pleasant range")
        }
        assertEquals(Chiptune.SYNC, Chiptune.of("sync"))
        assertEquals(Chiptune.SYNC, Chiptune.of("no such tune"), "an unknown key plays the sync tune")
    }

    @Test
    fun `note names give the usual equal-tempered pitches`() {
        assertEquals(440.0, Chiptune.pitch("A4"), 0.01)
        assertEquals(261.63, Chiptune.pitch("C4"), 0.01)
        assertEquals(1046.50, Chiptune.pitch("C6"), 0.01)
        assertEquals(466.16, Chiptune.pitch("A#4"), 0.01)
    }
}
