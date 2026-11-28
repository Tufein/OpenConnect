// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.retro

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Short 8-bit tunes, made on the phone itself from a list of notes. A tune is a pulse wave (a square wave that is high
 * a quarter of the time, the sound of old game consoles) with a soft start and end on every note, so nothing clicks.
 * No sound files: the whole feature costs a few hundred bytes of code. The tunes are our own little arpeggios.
 */
object Chiptune {
    /** Samples per second; plenty for a pulse wave below 4 kHz and small enough to render in a millisecond. */
    const val SAMPLE_RATE = 22_050

    /** A quarter of full scale: clearly audible on a phone speaker, never harsh. */
    const val VOLUME = 0.22

    /** The part of each period the wave is high: 25 % gives the thin, bright tone of an 8-bit console. */
    private const val DUTY = 0.25
    private const val ATTACK_MS = 4
    private const val RELEASE_MS = 10

    /** One note: [hz] 0 is a rest. */
    data class Note(val hz: Double, val millis: Int)

    /** A named tune; [key] is what the settings and the tests use. */
    data class Tune(val key: String, val notes: List<Note>) {
        val millis: Int get() = notes.sumOf { it.millis }
    }

    private fun n(name: String, millis: Int) = Note(if (name == "-") 0.0 else pitch(name), millis)

    /** A sync brought something new in: a quick climb of four notes. */
    val SYNC = Tune("sync", listOf(n("C6", 60), n("E6", 60), n("G6", 60), n("C7", 180)))

    /** A new record: a short fanfare up, a breath, and the top two notes again. */
    val RECORD = Tune("record", listOf(n("G5", 70), n("C6", 70), n("E6", 70), n("G6", 140), n("-", 40), n("E6", 70), n("G6", 300)))

    /** A goal reached: down, up and a held top note. */
    val GOAL = Tune("goal", listOf(n("C6", 90), n("G5", 90), n("C6", 90), n("E6", 90), n("G6", 300)))

    val TUNES: List<Tune> = listOf(SYNC, RECORD, GOAL)

    /** The tune with [key]; an unknown key plays [SYNC], so an old setting never stays silent by mistake. */
    fun of(key: String?): Tune = TUNES.firstOrNull { it.key == key } ?: SYNC

    /** The equal-tempered pitch of a note name such as "A4" (440 Hz), "C6" or "F#5". */
    fun pitch(name: String): Double {
        val match = Regex("^([A-G])(#?)(-?\\d)$").matchEntire(name) ?: throw IllegalArgumentException("not a note: $name")
        val (letter, sharp, octave) = match.destructured
        val semitone = "C D EF G A B".indexOf(letter[0]) + (if (sharp.isEmpty()) 0 else 1)
        val midi = (octave.toInt() + 1) * 12 + semitone
        return 440.0 * 2.0.pow((midi - 69) / 12.0)
    }

    /** 16-bit mono samples at [SAMPLE_RATE], ready for Android's AudioTrack. */
    fun render(tune: Tune): ShortArray {
        val out = ShortArray(tune.notes.sumOf { SAMPLE_RATE * it.millis / 1000 })
        val peak = Short.MAX_VALUE * VOLUME
        val attack = max(1, SAMPLE_RATE * ATTACK_MS / 1000)
        val release = max(1, SAMPLE_RATE * RELEASE_MS / 1000)
        var start = 0
        for (note in tune.notes) {
            val length = SAMPLE_RATE * note.millis / 1000
            if (note.hz > 0.0) {
                val period = SAMPLE_RATE / note.hz
                for (i in 0 until length) {
                    val phase = (i % period) / period
                    val level = if (phase < DUTY) 1.0 else -1.0
                    // Linear ramps in and out: zero at the first and the last sample, so notes join without a click.
                    val envelope = min(1.0, min(i.toDouble() / attack, (length - 1 - i).toDouble() / release))
                    out[start + i] = (level * envelope * peak).toInt().toShort()
                }
            }
            start += length
        }
        return out
    }
}
