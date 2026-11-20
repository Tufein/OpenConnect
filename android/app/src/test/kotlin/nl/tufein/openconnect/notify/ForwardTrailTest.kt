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

    @Test
    fun `the same line again and again is counted once, not repeated`() {
        val t = trail(capacity = 5)
        t.add("start")
        repeat(11) { t.add("watch sent PROTOBUF_REQUEST (5043)") }
        t.add("end")
        assertEquals(3, t.flow.value.size)
        assertEquals(11, t.flow.value[1].repeat)
        val text = t.asText("header", ZoneId.of("Europe/Amsterdam"))
        assertTrue("watch sent PROTOBUF_REQUEST (5043) (x11)" in text, text)
        assertTrue("start" in text && "end" in text)
        // a line that was seen before, but not just now, is a new line
        t.add("start")
        t.add("start")
        assertEquals(listOf(1, 11, 1, 2), t.flow.value.map { it.repeat })
    }

    @Test
    fun `the watch's questions that come back every half minute keep one line each, so the notifications stay`() {
        val t = trail(capacity = 6)
        t.add("com.whatsapp: announced to the watch")
        repeat(40) {
            clock = clock.plusSeconds(30)
            t.add("watch asked over protobuf: calendar_events_service, calendar_events_request; answered: no calendar events")
            if (it % 4 == 0) t.add("watch asked over protobuf: connect_iq_http_service, raw_resource_request; answered: no network")
        }
        t.add("com.whatsapp: announced to the watch")
        val lines = t.flow.value
        assertEquals(4, lines.size, lines.toString())
        assertEquals("com.whatsapp: announced to the watch", lines.first().text, "twenty minutes of questions did not push it out")
        assertEquals(40, lines.single { "calendar" in it.text }.repeat)
        assertEquals(10, lines.single { "raw_resource" in it.text }.repeat)
        val text = t.asText("header", ZoneId.of("Europe/Amsterdam"))
        // the line stands where the question came last, and says since when it comes
        assertTrue("15:20:00  watch asked over protobuf: calendar_events_service, calendar_events_request; answered: no calendar events (x40 since 15:00:30)" in text, text)
    }
}
