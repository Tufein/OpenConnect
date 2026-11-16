package nl.tufein.openconnect.core

import java.time.LocalDate

enum class RestingHrDirection {
    /** Your resting heart rate is lower than your normal: usually a sign of good recovery and fitness. */
    LOWER,

    /** Within a beat or so of your normal. */
    STABLE,

    /** Higher than your normal: often tiredness, stress, heat or the start of an illness. */
    HIGHER,
}

/** [recent] is the average of the last week and [normal] the average of the four weeks before it, both in beats per minute. */
data class RestingHrTrend(val recent: Double, val normal: Double, val direction: RestingHrDirection) {
    val difference: Double get() = recent - normal
}

/** Resting heart rate is the slowest-moving honest signal of fitness; this compares your last week with your own normal. */
object RestingHrTrendCalculator {
    const val RECENT_DAYS = 7
    const val NORMAL_DAYS = 28
    const val MIN_RECENT = 4
    const val MIN_NORMAL = 10

    /** A difference of this many beats or more, either way, counts as a change. */
    const val CHANGE_BEATS = 2.0

    fun compute(dailies: List<DailySummary>, today: LocalDate): RestingHrTrend? {
        val recentFrom = today.minusDays((RECENT_DAYS - 1).toLong())
        val normalFrom = recentFrom.minusDays(NORMAL_DAYS.toLong())
        fun values(from: LocalDate, to: LocalDate) =
            dailies.filter { it.date in from..to }.mapNotNull { it.restingHr?.takeIf { v -> v > 0 }?.toDouble() }

        val recent = values(recentFrom, today)
        val normal = values(normalFrom, recentFrom.minusDays(1))
        if (recent.size < MIN_RECENT || normal.size < MIN_NORMAL) return null
        val r = recent.average()
        val n = normal.average()
        val direction = when {
            r - n <= -CHANGE_BEATS -> RestingHrDirection.LOWER
            r - n >= CHANGE_BEATS -> RestingHrDirection.HIGHER
            else -> RestingHrDirection.STABLE
        }
        return RestingHrTrend(r, n, direction)
    }
}
