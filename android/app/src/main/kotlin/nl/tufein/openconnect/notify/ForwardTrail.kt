// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.tufein.openconnect.core.watch.ble.GarminProtobuf
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
    /**
     * One line; [repeat] says how many times the same line came, in a row (the watch sends many alike in a burst) or,
     * for a question the watch keeps asking, since [since].
     */
    data class Line(val at: Instant, val text: String, val repeat: Int = 1, val since: Instant? = null)

    private val lock = Any()
    private val lines = ArrayDeque<Line>()
    private val _lines = MutableStateFlow<List<Line>>(emptyList())

    /** The lines, oldest first. */
    val flow: StateFlow<List<Line>> = _lines.asStateFlow()

    fun add(text: String) {
        val line = Line(now(), text.take(MAX_LINE))
        synchronized(lock) {
            // the same line again right after itself is counted, not repeated: a burst of twenty alike would push out the rest
            val last = lines.lastOrNull()
            val earlier = if (summarised(line.text)) lines.indexOfLast { it.text == line.text } else -1
            if (earlier >= 0) {
                // a question the watch asks every half minute: one line, moved to where it came last, with how often since when
                val old = lines.removeAt(earlier)
                lines.addLast(line.copy(repeat = old.repeat + 1, since = old.since ?: old.at))
            } else if (last != null && last.text == line.text) {
                lines.removeLast()
                lines.addLast(line.copy(repeat = last.repeat + 1))
            } else {
                lines.addLast(line)
            }
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

        /**
         * Lines that come back every half minute or so (the watch's protobuf questions). Kept as one line each, or twenty
         * minutes of them pushed the notifications themselves out of the 150 lines (seen in a log from a fenix 8).
         */
        private fun summarised(text: String): Boolean = text.startsWith(GarminProtobuf.LOG_PREFIX)

        /** [lines] as text, oldest first, one per line, each with the time it happened. */
        fun format(lines: List<Line>, header: String, zone: ZoneId = ZoneId.systemDefault()): String {
            val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
            return buildString {
                appendLine(header)
                for (line in lines) {
                    val count = when {
                        line.repeat <= 1 -> ""
                        line.since != null -> " (x${line.repeat} since ${time.format(line.since)})"
                        else -> " (x${line.repeat})"
                    }
                    appendLine("${time.format(line.at)}  ${line.text}$count")
                }
            }.trimEnd()
        }
    }
}
