package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset

enum class LoadStatus {
    /** This week was clearly lighter than your recent norm. */
    LIGHT,

    /** In line with the last four weeks. */
    STEADY,

    /** Noticeably more than usual: fine for a short block, plan rest after it. */
    HEAVY,

    /** A big jump over what the body is used to: the usual time to back off. */
    OVERREACHING,
}

/**
 * [weeks] are the last six 7-day totals, oldest first, each labelled with the day it starts. [acute] is the last 7 days,
 * [chronicWeekly] the average week of the last 28 days, and [ratio] the first divided by the second.
 */
data class TrainingLoad(
    val weeks: List<Pair<LocalDate, Double>>,
    val acute: Double,
    val chronicWeekly: Double,
    val ratio: Double,
    val status: LoadStatus,
)

/**
 * A simple training load: minutes times an effort weight from average heart rate against the highest heart rate seen,
 * then this week against your own four-week average. It is OpenConnect's own estimate, not Garmin's Training Load
 * or Training Status, and not medical advice.
 */
object TrainingLoadCalculator {
    const val MIN_HISTORY_DAYS = 14
    private const val NO_HR_WEIGHT = 2.0

    fun activityLoad(a: Activity, maxHr: Int): Double {
        val minutes = a.durationS / 60.0
        val avg = a.avgHr
        if (avg == null || maxHr <= 0) return minutes * NO_HR_WEIGHT
        val frac = avg.toDouble() / maxHr
        val weight = when {
            frac < 0.6 -> 1.0
            frac < 0.7 -> 1.5
            frac < 0.8 -> 2.0
            frac < 0.9 -> 3.0
            else -> 4.0
        }
        return minutes * weight
    }

    fun compute(activities: List<Activity>, today: LocalDate): TrainingLoad? {
        if (activities.isEmpty()) return null
        fun day(a: Activity) = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()
        val earliest = activities.minOf(::day)
        if (earliest.isAfter(today.minusDays((MIN_HISTORY_DAYS - 1).toLong()))) return null
        val maxHr = activities.mapNotNull { it.maxHr ?: it.avgHr }.maxOrNull() ?: 0
        val load = activities.map { day(it) to activityLoad(it, maxHr) }
        fun between(from: LocalDate, to: LocalDate) = load.filter { (d, _) -> !d.isBefore(from) && !d.isAfter(to) }.sumOf { it.second }

        val weeks = (5 downTo 0).map { i ->
            val end = today.minusDays(i * 7L)
            val start = end.minusDays(6)
            start to between(start, end)
        }
        val acute = weeks.last().second
        val chronicWeekly = between(today.minusDays(27), today) / 4.0
        if (chronicWeekly <= 0.0) return null
        val ratio = acute / chronicWeekly
        val status = when {
            ratio < 0.8 -> LoadStatus.LIGHT
            ratio <= 1.3 -> LoadStatus.STEADY
            ratio <= 1.5 -> LoadStatus.HEAVY
            else -> LoadStatus.OVERREACHING
        }
        return TrainingLoad(weeks, acute, chronicWeekly, ratio, status)
    }
}
