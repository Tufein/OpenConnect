package nl.tufein.openconnect.core

import java.time.ZoneOffset

enum class ActivitySort { NEWEST, OLDEST, DISTANCE, DURATION }

/**
 * Filters and orders an activity list. Every word of [text] has to match the name, the sport (as stored: "running")
 * or the date written as 2026-05-02, so "run 2026-04" finds the runs of April. A blank text keeps everything.
 */
fun List<Activity>.search(text: String, sort: ActivitySort): List<Activity> {
    val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val matching = if (words.isEmpty()) {
        this
    } else {
        filter { a ->
            val haystacks = listOf(a.name.lowercase(), a.sport.key, a.startTime.atZone(ZoneOffset.UTC).toLocalDate().toString())
            words.all { word -> haystacks.any { word in it } }
        }
    }
    return when (sort) {
        ActivitySort.NEWEST -> matching.sortedByDescending { it.startTime }
        ActivitySort.OLDEST -> matching.sortedBy { it.startTime }
        ActivitySort.DISTANCE -> matching.sortedByDescending { it.distanceM }
        ActivitySort.DURATION -> matching.sortedByDescending { it.durationS }
    }
}
