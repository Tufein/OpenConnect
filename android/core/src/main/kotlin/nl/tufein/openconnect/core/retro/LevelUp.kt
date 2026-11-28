// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.retro

import nl.tufein.openconnect.core.Achievements
import nl.tufein.openconnect.core.TrophyKey

/**
 * What to celebrate when the trophies are worked out again after new data: one to three new trophies, or else a level
 * that went up by one or two. More at once is history arriving (demo data, an archive import, a restored backup), not a
 * workout, so it stays quiet; and the first look after opening the app has nothing to compare with.
 */
sealed class LevelUp {
    abstract val tune: Chiptune.Tune

    /** New trophies, in the order the trophy screen shows them. */
    data class Trophies(val keys: List<TrophyKey>) : LevelUp() {
        override val tune: Chiptune.Tune get() = Chiptune.RECORD
    }

    /** The level that was just reached. */
    data class Level(val level: Int) : LevelUp() {
        override val tune: Chiptune.Tune get() = Chiptune.GOAL
    }

    companion object {
        const val MAX_TROPHIES = 3
        const val MAX_LEVELS = 2

        fun between(before: Achievements?, after: Achievements): LevelUp? {
            if (before == null) return null
            val had = before.trophies.filter { it.unlocked }.map { it.key }.toSet()
            val fresh = after.trophies.filter { it.unlocked && it.key !in had }.map { it.key }.sortedBy { it.ordinal }
            if (fresh.size > MAX_TROPHIES) return null
            val levels = after.player.level - before.player.level
            if (levels > MAX_LEVELS) return null
            if (fresh.isNotEmpty()) return Trophies(fresh)
            if (levels > 0) return Level(after.player.level)
            return null
        }
    }
}
