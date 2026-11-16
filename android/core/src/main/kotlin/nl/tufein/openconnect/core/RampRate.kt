package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset

enum class RampLevel {
    /** Clearly less than you are used to: a rest week, or a drop in training. */
    LOW,

    /** Close to what you are used to: the sweet spot to keep building from. */
    STEADY,

    /** More than usual: fine now and then, but give it an easy day after. */
    HIGH,

    /** A big jump in a week: the pattern that goes with overuse injuries. */
    SPIKE,
}

/**
 * How this week compares with your own last four weeks. [ratio] is [weekLoad] divided by [usualWeekLoad], the average
 * week of the four weeks before (the "acute to chronic workload ratio" of sports science, with a week as the acute window).
 */
data class RampRate(val ratio: Double, val weekLoad: Double, val usualWeekLoad: Double, val level: RampLevel)

/** Works on the daily training load of [TrainingLoadCalculator]. A rule of thumb, not medical advice. */
object RampRateCalculator {
    const val WEEK = 7
    const val WEEKS_BEFORE = 4

    /** At least this many days of history are needed: the week plus the four weeks before it. */
    const val MIN_HISTORY_DAYS = WEEK * (WEEKS_BEFORE + 1)

    const val LOW_BELOW = 0.8
    const val HIGH_FROM = 1.3
    const val SPIKE_FROM = 1.5

    fun compute(activities: List<Activity>, today: LocalDate): RampRate? {
        fun day(a: Activity) = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()
        val known = activities.filter { !day(it).isAfter(today) }
        if (known.isEmpty()) return null
        if (known.minOf(::day).isAfter(today.minusDays((MIN_HISTORY_DAYS - 1).toLong()))) return null
        val maxHr = activities.mapNotNull { it.maxHr ?: it.avgHr }.maxOrNull() ?: 0
        val perDay = known.groupBy(::day).mapValues { (_, list) -> list.sumOf { TrainingLoadCalculator.activityLoad(it, maxHr) } }

        fun week(end: LocalDate): Double = (0 until WEEK).sumOf { perDay[end.minusDays(it.toLong())] ?: 0.0 }

        val thisWeek = week(today)
        val usual = (1..WEEKS_BEFORE).map { week(today.minusDays(WEEK * it.toLong())) }.average()
        if (usual <= 0.0) return null
        val ratio = thisWeek / usual
        val level = when {
            ratio < LOW_BELOW -> RampLevel.LOW
            ratio < HIGH_FROM -> RampLevel.STEADY
            ratio < SPIKE_FROM -> RampLevel.HIGH
            else -> RampLevel.SPIKE
        }
        return RampRate(ratio, thisWeek, usual, level)
    }
}
