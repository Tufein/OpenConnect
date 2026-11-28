// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.retro

import nl.tufein.openconnect.core.Achievements
import nl.tufein.openconnect.core.PlayerLevel
import nl.tufein.openconnect.core.Trophy
import nl.tufein.openconnect.core.TrophyKey
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * When new data unlocks a trophy or raises the level while the app is open, the retro pack congratulates once. A big
 * jump (demo data, an archive import, a restored backup) stays quiet: nobody wants twelve fanfares for old history.
 */
class LevelUpTest {
    private val day = LocalDate.of(2026, 11, 28)

    private fun state(xp: Int, vararg unlocked: TrophyKey) = Achievements(
        TrophyKey.entries.map { key -> if (key in unlocked) Trophy(key, key.target, day) else Trophy(key, 0.0, null) },
        PlayerLevel.of(xp),
    )

    @Test
    fun `the first look after opening the app is never a level-up`() {
        assertNull(LevelUp.between(null, state(5_000, TrophyKey.FIRST_ACTIVITY, TrophyKey.RUN_5K)))
    }

    @Test
    fun `nothing changed, nothing to say`() {
        val now = state(900, TrophyKey.FIRST_ACTIVITY)
        assertNull(LevelUp.between(now, now))
    }

    @Test
    fun `one new trophy is named`() {
        val before = state(150, TrophyKey.FIRST_ACTIVITY)
        val after = state(180, TrophyKey.FIRST_ACTIVITY, TrophyKey.RUN_5K)
        assertEquals(LevelUp.Trophies(listOf(TrophyKey.RUN_5K)), LevelUp.between(before, after))
    }

    @Test
    fun `a new trophy wins over a new level in the same sync`() {
        val before = state(380, TrophyKey.FIRST_ACTIVITY)
        val after = state(420, TrophyKey.FIRST_ACTIVITY, TrophyKey.RUN_10K, TrophyKey.RUN_5K)
        assertEquals(LevelUp.Trophies(listOf(TrophyKey.RUN_5K, TrophyKey.RUN_10K)), LevelUp.between(before, after))
    }

    @Test
    fun `a level up on its own says the new level`() {
        assertEquals(LevelUp.Level(3), LevelUp.between(state(390), state(410)))
    }

    @Test
    fun `a bulk change stays quiet`() {
        val many = arrayOf(TrophyKey.FIRST_ACTIVITY, TrophyKey.RUN_5K, TrophyKey.RUN_10K, TrophyKey.VARIETY_3)
        assertNull(LevelUp.between(state(0), state(50, *many)), "four trophies at once is an import, not a workout")
        assertNull(LevelUp.between(state(0), state(1_000)), "level 1 to 4 is an import")
        assertEquals(LevelUp.Level(3), LevelUp.between(state(0), state(400)), "two levels in one go still counts")
    }

    @Test
    fun `losing data is not a level-up`() {
        assertNull(LevelUp.between(state(900, TrophyKey.FIRST_ACTIVITY), state(0)))
    }

    @Test
    fun `each kind has its own tune`() {
        assertEquals(Chiptune.RECORD, LevelUp.Trophies(listOf(TrophyKey.RUN_5K)).tune)
        assertEquals(Chiptune.GOAL, LevelUp.Level(2).tune)
    }
}
