package nl.tufein.openconnect.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * What an export keeps next to its activity files (v1.4.0, milestone M14): the names people gave their activities,
 * and the shape of the JSON files this version cannot read yet.
 *
 * - **Strava**: `activities.csv` has a row per activity with its `Activity Name` and the `Filename` of the file in
 *   `activities/`.
 * - **Garmin**: `summarizedActivities.json` has an entry per activity with its `name` and its start time. Garmin does not
 *   publish the layout of its export, so this is read tolerantly (any object with a text `name` and a start time under a
 *   known key, wherever it sits in the file) and matched on start time, not on file names.
 * - **Shapes**: the daily files (`UDSFile_*.json`, `*_sleepData.json`, and so on) are not read in this version because
 *   their layout is not documented. Their file name pattern and key names are written down, never a value and never an
 *   address, so a real export can show what they look like.
 */
object ArchiveMeta {
    const val NAME_MAX = 120
    const val MAX_KEYS = 60

    private val KEY = Regex("^[A-Za-z][A-Za-z0-9_]{0,39}$")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val CONTROL_OR_SPACE = Regex("[\\p{Cntrl}\\s]+")
    private val DIGITS = Regex("[0-9]+")
    private val START_KEYS = listOf("startTimeGmt", "startTimeGMT", "beginTimestamp")

    /** An activity name with control characters turned into spaces, trimmed and cut; null when nothing is left. */
    fun cleanName(raw: String?): String? {
        val cleaned = raw?.replace(CONTROL_OR_SPACE, " ")?.trim().orEmpty().take(NAME_MAX).trim()
        return cleaned.ifEmpty { null }
    }

    /** Strava's `activities.csv` as the last part of each `Filename` (lower case) to the activity's name. */
    fun stravaNames(csv: String): Map<String, String> {
        val rows = SimpleCsv.parse(csv)
        val header = rows.firstOrNull()?.map { it.trim().lowercase() } ?: return emptyMap()
        val nameAt = header.indexOf("activity name")
        val fileAt = header.indexOf("filename")
        if (nameAt < 0 || fileAt < 0) return emptyMap()
        val out = HashMap<String, String>()
        for (row in rows.drop(1)) {
            val file = row.getOrNull(fileAt)?.trim().orEmpty()
            val name = cleanName(row.getOrNull(nameAt)) ?: continue
            if (file.isEmpty()) continue
            out[file.substringAfterLast('/').lowercase()] = name
        }
        return out
    }

    class GarminName(val epochSec: Long, val name: String)

    /**
     * Names and start times from `summarizedActivities.json`, wherever they sit. Throws [IllegalArgumentException] when
     * the text is not JSON.
     */
    fun garminNames(json: String): List<GarminName> {
        val out = ArrayList<GarminName>()
        fun walk(e: JsonElement, depth: Int) {
            if (depth > 6) return
            when (e) {
                is JsonArray -> e.forEach { walk(it, depth + 1) }
                is JsonObject -> {
                    val name = (e["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    val start = START_KEYS.firstNotNullOfOrNull { key -> (e[key] as? JsonPrimitive)?.let(::epochSeconds) }
                    cleanName(name)?.let { clean -> if (start != null) out.add(GarminName(start, clean)) }
                    e.values.forEach { walk(it, depth + 1) }
                }
                else -> Unit
            }
        }
        walk(Json.parseToJsonElement(json), 0)
        return out
    }

    private fun epochSeconds(p: JsonPrimitive): Long? {
        val number = p.doubleOrNull
        if (number != null) {
            val v = number.toLong()
            return if (kotlin.math.abs(v) > 100_000_000_000L) v / 1000 else v
        }
        if (!p.isString) return null
        val text = p.content.trim()
        return runCatching { Instant.parse(text).epochSecond }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC).epochSecond }.getOrNull()
    }

    /** `UDSFile_2020-01-01_2020-04-10.json` becomes `UDSFile_#-#-#_#-#-#.json`; an address in the name is blanked. */
    fun shapePattern(entryName: String): String = redact(entryName.substringAfterLast('/')).replace(DIGITS, "#")

    /** Anything that looks like an e-mail address is replaced by an ellipsis. */
    fun redact(text: String): String = EMAIL.replace(text, "…")

    /**
     * The key names that appear in the start of a JSON file, in order of first appearance. A string counts as a key when a
     * colon follows it; values are never looked at, and a "key" that is not a plain name (a date, a number) is data and is left out.
     */
    fun shapeKeys(head: ByteArray): List<String> {
        val text = String(head, Charsets.UTF_8)
        val keys = LinkedHashSet<String>()
        var i = 0
        while (i < text.length && keys.size < MAX_KEYS) {
            if (text[i] != '"') {
                i++
                continue
            }
            val sb = StringBuilder()
            i++
            var closed = false
            while (i < text.length) {
                val c = text[i]
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == '"') {
                    closed = true
                    i++
                    break
                }
                if (sb.length <= 64) sb.append(c)
                i++
            }
            if (!closed) break
            var j = i
            while (j < text.length && text[j].isWhitespace()) j++
            if (j < text.length && text[j] == ':' && KEY.matches(sb)) keys.add(sb.toString())
        }
        return keys.toList()
    }
}

/** A small CSV reader (quotes, doubled quotes, newlines inside quotes, a byte-order mark): the files of an export, not a general parser. */
object SimpleCsv {
    fun parse(text: String): List<List<String>> {
        val s = text.removePrefix("﻿")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endRow() {
            row.add(cell.toString())
            cell.setLength(0)
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = ArrayList()
        }
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    ',' -> {
                        row.add(cell.toString())
                        cell.setLength(0)
                    }
                    '\r' -> Unit
                    '\n' -> endRow()
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
