package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset

/**
 * One year in numbers, the "hi-score card": [longestRun] and [longestRide] are in kilometres, [biggestClimb] in metres of climbing.
 * [previousDistanceKm] is the year before, for the change.
 */
data class YearScore(
    val year: Int,
    val activities: Int,
    val distanceKm: Double,
    val hours: Double,
    val activeDays: Int,
    val longestStreakDays: Int,
    val longestRun: RecordEntry?,
    val longestRide: RecordEntry?,
    val biggestClimb: RecordEntry?,
    val busiestMonth: Month?,
    val steps: Long,
    val bestStepsDay: Pair<LocalDate, Int>?,
    val previousDistanceKm: Double,
) {
    /** Percent more or less distance than the year before, null when there was none. */
    val distanceChangePct: Double? get() = if (previousDistanceKm > 0) (distanceKm - previousDistanceKm) / previousDistanceKm * 100 else null
}

object YearScoreCalculator {
    private fun day(a: Activity) = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()

    /** The years with an activity or a day of steps, newest first. */
    fun years(activities: List<Activity>, dailies: List<DailySummary>): List<Int> =
        (activities.map { day(it).year } + dailies.filter { it.steps > 0 }.map { it.date.year }).distinct().sortedDescending()

    /** The score of [year], or null when that year has no activity and no steps. */
    fun compute(activities: List<Activity>, dailies: List<DailySummary>, year: Int): YearScore? {
        val mine = activities.filter { day(it).year == year }
        val steps = dailies.filter { it.date.year == year && it.steps > 0 }
        if (mine.isEmpty() && steps.isEmpty()) return null

        fun entry(a: Activity, value: Double) = RecordEntry(value, a.id, a.name, day(a))
        fun longest(sport: Sport) = mine.filter { it.sport == sport && it.distanceM > 0 }.maxByOrNull { it.distanceM }?.let { entry(it, it.distanceM / 1000.0) }

        val days = mine.map(::day).distinct().sorted()
        var streak = 0
        var longestStreak = 0
        var previous: LocalDate? = null
        for (d in days) {
            streak = if (previous != null && previous.plusDays(1) == d) streak + 1 else 1
            longestStreak = maxOf(longestStreak, streak)
            previous = d
        }
        val busiest = mine.groupBy { day(it).month }.maxByOrNull { (_, list) -> list.sumOf { it.distanceM } }?.takeIf { (_, l) -> l.sumOf { it.distanceM } > 0 }?.key
        val bestDay = steps.maxByOrNull { it.steps }

        return YearScore(
            year = year,
            activities = mine.size,
            distanceKm = mine.sumOf { it.distanceM } / 1000.0,
            hours = mine.sumOf { it.durationS } / 3600.0,
            activeDays = days.size,
            longestStreakDays = longestStreak,
            longestRun = longest(Sport.RUNNING),
            longestRide = longest(Sport.CYCLING),
            biggestClimb = mine.filter { it.elevationGainM > 0 }.maxByOrNull { it.elevationGainM }?.let { entry(it, it.elevationGainM) },
            busiestMonth = busiest,
            steps = steps.sumOf { it.steps.toLong() },
            bestStepsDay = bestDay?.let { it.date to it.steps },
            previousDistanceKm = activities.filter { day(it).year == year - 1 }.sumOf { it.distanceM } / 1000.0,
        )
    }
}
