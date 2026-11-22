package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A workout the person typed in because no device recorded it: an hour of yoga at home, a swim in a pool without a
 * watch, a walk the phone counted but never wrote down.
 *
 * The numbers are the person's own, so they are checked here and nowhere else. A workout that does not pass [problem]
 * is never stored.
 */
data class ManualActivity(
    val sport: Sport = Sport.OTHER,
    val date: LocalDate,
    val minutes: Int,
    val distanceKm: Double = 0.0,
    val avgHr: Int? = null,
    val name: String = "",
) {
    /** What stops this from being stored, or null when it can be saved. */
    val problem: Problem?
        get() = when {
            minutes < 1 || minutes > MAX_MINUTES -> Problem.MINUTES
            distanceKm < 0.0 || distanceKm > MAX_KM -> Problem.DISTANCE
            avgHr != null && (avgHr < MIN_HR || avgHr > MAX_HR) -> Problem.HEART_RATE
            else -> null
        }

    /**
     * The activity as the rest of the app sees it. Midday keeps the date the same even on a night when the clocks
     * change, and leaves the time of day open instead of pretending to know it.
     */
    fun toActivity(zone: ZoneId, id: String): Activity {
        require(problem == null) { "a manual activity needs numbers that add up" }
        return Activity(
            id = id,
            source = SOURCE,
            name = name.trim(),
            sport = sport,
            startTime = date.atTime(12, 0).atZone(zone).toInstant(),
            durationS = minutes * 60.0,
            distanceM = distanceKm * 1000.0,
            avgHr = avgHr,
        )
    }

    enum class Problem { MINUTES, DISTANCE, HEART_RATE }

    companion object {
        /** The `source` of every activity that was typed in. */
        const val SOURCE = "manual"

        const val MAX_MINUTES = 1440
        const val MAX_KM = 1000.0
        const val MIN_HR = 30
        const val MAX_HR = 250

        fun id(now: Instant): String = "manual-${now.toEpochMilli()}"
    }
}
