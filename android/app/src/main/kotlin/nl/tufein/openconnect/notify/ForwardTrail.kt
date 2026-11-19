// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What forwarding did, in the last few minutes, one line at a time: the listener starting, a notification seen (the
 * app and what was decided, never what it says), each step of the Bluetooth connection, every kind of message the
 * watch sent. It is kept in memory only, shown on the card ("What is happening?") and can be copied, so "it does not
 * arrive" can be answered from the phone itself instead of guessed at.
 *
 * Nothing of a notification's title or text may ever be written here.
 */
class ForwardTrail(
    private val capacity: Int = 150,
    private val now: () -> Instant = { Instant.now() },
) {
    data class Line(val at: Instant, val text: String)

    private val lock = Any()
    private val lines = ArrayDeque<Line>()
    private val _lines = MutableStateFlow<List<Line>>(emptyList())

    /** The lines, oldest first. */
    val flow: StateFlow<List<Line>> = _lines.asStateFlow()

    fun add(text: String) {
        val line = Line(now(), text.take(MAX_LINE))
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > capacity) lines.removeFirst()
            _lines.value = lines.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            lines.clear()
            _lines.value = emptyList()
        }
    }

    /** The whole trail as text, newest last, with a header saying what it is, for pasting into a message. */
    fun asText(header: String, zone: ZoneId = ZoneId.systemDefault()): String = format(synchronized(lock) { lines.toList() }, header, zone)

    companion object {
        private const val MAX_LINE = 300

        /** [lines] as text, oldest first, one per line, each with the time it happened. */
        fun format(lines: List<Line>, header: String, zone: ZoneId = ZoneId.systemDefault()): String {
            val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
            return buildString {
                appendLine(header)
                for (line in lines) appendLine("${time.format(line.at)}  ${line.text}")
            }.trimEnd()
        }
    }
}
