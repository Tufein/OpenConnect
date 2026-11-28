// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.retro

import java.util.Locale

/**
 * The three letters on a hi-score table (v1.17.0). Only A to Z, so every letter exists in the pixel font and the arrows
 * on the picker have a clear order to step through. Nothing chosen shows as AAA, the way an arcade cabinet starts.
 */
object Initials {
    const val LENGTH = 3
    const val DEFAULT = "AAA"

    /** Up to three capital letters; everything that is not A to Z is dropped. Empty when nothing usable is left. */
    fun clean(text: String?): String = text.orEmpty().uppercase(Locale.ROOT).filter { it in 'A'..'Z' }.take(LENGTH)

    /** What the table shows: the chosen letters, filled up with A. */
    fun shown(text: String?): String = clean(text).padEnd(LENGTH, 'A')

    /** The letter at [slot] moved [delta] places, Z wrapping to A and back, like the joystick on the name entry screen. */
    fun step(text: String?, slot: Int, delta: Int): String {
        val letters = shown(text).toCharArray()
        if (slot in 0 until LENGTH) letters[slot] = 'A' + Math.floorMod(letters[slot] - 'A' + delta, 26)
        return String(letters)
    }

    /** "1ST", "2ND", "3RD", "4TH", "11TH", "21ST": the place column of a cabinet, the same in every language. */
    fun rank(place: Int): String = place.toString() + when {
        place % 100 in 11..13 -> "TH"
        place % 10 == 1 -> "ST"
        place % 10 == 2 -> "ND"
        place % 10 == 3 -> "RD"
        else -> "TH"
    }
}
