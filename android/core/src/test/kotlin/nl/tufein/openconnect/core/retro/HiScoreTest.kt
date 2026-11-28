// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.retro

import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.BestEfforts
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.TrackPoint
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** v1.17.0, retro part 3: a hi-score table per distance with initials, like the cabinet in the corner of the arcade. */
class HiScoreTest {
    @Test
    fun `initials are three capital letters, anything else is dropped`() {
        assertEquals("SVB", Initials.clean("svb"))
        assertEquals("AB", Initials.clean("a-b 1"))
        assertEquals("XYZ", Initials.clean("xyzw"))
        assertEquals("", Initials.clean(null))
        assertEquals("", Initials.clean("ÉÀ!"), "only A to Z, so the pixel font can draw every letter")
    }

    @Test
    fun `a table with no initials shows AAA, as an arcade machine does`() {
        assertEquals("AAA", Initials.shown(""))
        assertEquals("ABA", Initials.shown("ab"))
        assertEquals("SVB", Initials.shown("SVB"))
    }

    @Test
    fun `the arrows step through the alphabet and wrap around`() {
        assertEquals("BAA", Initials.step("", slot = 0, delta = 1))
        assertEquals("AAZ", Initials.step("AAA", slot = 2, delta = -1))
        assertEquals("SVA", Initials.step("SVZ", slot = 2, delta = 1))
        assertEquals("SVB", Initials.step("SVB", slot = 3, delta = 1), "a slot that does not exist changes nothing")
    }

    @Test
    fun `places read like a cabinet`() {
        assertEquals(listOf("1ST", "2ND", "3RD", "4TH", "5TH"), (1..5).map(Initials::rank))
        assertEquals(listOf("11TH", "12TH", "13TH", "21ST", "22ND"), listOf(11, 12, 13, 21, 22).map(Initials::rank))
    }

    /** A straight run at a steady pace: one point every 10 s. */
    private fun run(id: String, start: String, metres: Double, secondsPerKm: Double): Activity {
        val t0 = Instant.parse(start)
        val step = 10.0
        val points = (0..(metres / 1000 * secondsPerKm / step).toInt()).map { i ->
            val s = i * step
            TrackPoint(time = t0.plusSeconds(s.toLong()), distanceM = s / secondsPerKm * 1000)
        }
        return Activity(
            id = id, source = "test", name = id, sport = Sport.RUNNING, startTime = t0,
            durationS = metres / 1000 * secondsPerKm, distanceM = metres, track = points,
        )
    }

    @Test
    fun `the table keeps the five fastest runs per distance, fastest first, one entry per run`() {
        val repo = InMemoryRepository()
        val paces = listOf(330.0, 300.0, 360.0, 290.0, 345.0, 310.0, 400.0)
        paces.forEachIndexed { i, pace -> repo.upsertActivity(run("r$i", "2026-11-0${i + 1}T07:00:00Z", 5200.0, pace)) }
        val table = BestEfforts.highScores(repo)
        val five = table.getValue(5000.0)
        assertEquals(5, five.size)
        assertEquals(listOf("r3", "r1", "r5", "r0", "r4"), five.map { it.activityId })
        assertTrue(five.zipWithNext().all { (a, b) -> a.seconds <= b.seconds })
        assertEquals(five.size, five.map { it.activityId }.toSet().size, "one run never holds two places")
        assertEquals(listOf(1000.0, 5000.0), table.keys.toList(), "only distances someone ran, in the usual order")
    }

    @Test
    fun `personal bests are the top of the table`() {
        val repo = InMemoryRepository()
        listOf(330.0, 300.0, 360.0).forEachIndexed { i, pace -> repo.upsertActivity(run("r$i", "2026-11-0${i + 1}T07:00:00Z", 10_500.0, pace)) }
        val table = BestEfforts.highScores(repo)
        assertEquals(table.values.map { it.first() }, BestEfforts.personalBests(repo))
        assertEquals(listOf(1000.0, 5000.0, 10_000.0), BestEfforts.personalBests(repo).map { it.distanceM })
        assertEquals("r1", BestEfforts.personalBests(repo).last().activityId)
    }
}
