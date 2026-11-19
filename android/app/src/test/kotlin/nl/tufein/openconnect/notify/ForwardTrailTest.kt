// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The short memory of what forwarding did, which the card shows and the person can copy. */
class ForwardTrailTest {
    private var clock = Instant.parse("2026-11-19T14:00:00Z")

    private fun trail(capacity: Int = 150) = ForwardTrail(capacity) { clock }

    @Test
    fun `lines come out oldest first, with the time they happened`() {
        val t = trail()
        t.add("one")
        clock = clock.plusSeconds(75)
        t.add("two")
        val text = t.asText("header", ZoneId.of("Europe/Amsterdam"))
        assertEquals("header\n15:00:00  one\n15:01:15  two", text)
    }

    @Test
    fun `only the most recent lines are kept`() {
        val t = trail(capacity = 3)
        (1..5).forEach { t.add("line $it") }
        assertEquals(listOf("line 3", "line 4", "line 5"), t.flow.value.map { it.text })
    }

    @Test
    fun `a very long line is cut`() {
        val t = trail()
        t.add("x".repeat(1_000))
        assertEquals(300, t.flow.value.single().text.length)
    }

    @Test
    fun `clearing empties it`() {
        val t = trail()
        t.add("one")
        t.clear()
        assertTrue(t.flow.value.isEmpty())
        assertEquals("header", t.asText("header"))
    }

    @Test
    fun `lines from several threads are all kept`() {
        val t = trail(capacity = 1_000)
        val workers = (1..4).map { n -> Thread { repeat(100) { t.add("thread $n line $it") } } }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        assertEquals(400, t.flow.value.size)
    }
}
