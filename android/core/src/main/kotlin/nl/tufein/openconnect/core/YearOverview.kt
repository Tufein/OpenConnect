package nl.tufein.openconnect.core

import java.time.Month
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** One month of the year overview. A null figure means the month has no rows for it; a zero is measured data. */
data class MonthOverview(
    val month: Month,
    /** Steps in the month, null when the month has no daily rows. */
    val steps: Long?,
    /** Average hours per sleep row, null when the month has no sleep rows. */
    val sleepHours: Double?,
    /** Average of the positive resting heart rates, null when there are none. */
    val restingHr: Int?,
    /** Activities in the month. */
    val trainings: Int,
)

/** A calendar year in months and totals, for the year overview screen. */
data class YearOverview(
    val year: Int,
    /** Always twelve months, January through December. */
    val months: List<MonthOverview>,
) {
    /** Steps over the months that have daily rows, null when no month does. */
    val stepsTotal: Long? get() = months.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.sum()

    /** Average of the months with sleep, null when there are none. */
    val sleepHoursAvg: Double? get() = months.mapNotNull { it.sleepHours }.takeIf { it.isNotEmpty() }?.average()

    /** Average of the monthly resting heart rates, null when there are none. */
    val restingHrAvg: Int? get() = months.mapNotNull { it.restingHr }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    val trainingsTotal: Int get() = months.sumOf { it.trainings }

    val hasData: Boolean
        get() = months.any { it.steps != null || it.sleepHours != null || it.restingHr != null || it.trainings > 0 }
}

object YearOverviewCalculator {
    /** The calendar day of an activity in UTC, so the year and month do not depend on the device zone. */
    private fun day(activity: Activity) = activity.startTime.atZone(ZoneOffset.UTC).toLocalDate()

    /** The years with a daily, a sleep or an activity, newest first. */
    fun years(dailies: List<DailySummary>, sleeps: List<SleepSummary>, activities: List<Activity>): List<Int> =
        (dailies.map { it.date.year } + sleeps.map { it.date.year } + activities.map { day(it).year })
            .distinct()
            .sortedDescending()

    /** The overview of [year], or null when that year has no daily, sleep or activity. */
    fun compute(dailies: List<DailySummary>, sleeps: List<SleepSummary>, activities: List<Activity>, year: Int): YearOverview? {
        val days = dailies.filter { it.date.year == year }
        val nights = sleeps.filter { it.date.year == year }
        val mine = activities.filter { day(it).year == year }
        if (days.isEmpty() && nights.isEmpty() && mine.isEmpty()) return null

        val months = (1..12).map { monthValue ->
            val monthDays = days.filter { it.date.monthValue == monthValue }
            val monthNights = nights.filter { it.date.monthValue == monthValue }
            val rests = monthDays.mapNotNull { it.restingHr }.filter { it > 0 }
            MonthOverview(
                month = Month.of(monthValue),
                steps = if (monthDays.isEmpty()) null else monthDays.sumOf { it.steps.toLong() },
                sleepHours = if (monthNights.isEmpty()) null else monthNights.map { it.durationS / 3600.0 }.average(),
                restingHr = if (rests.isEmpty()) null else rests.average().roundToInt(),
                trainings = mine.count { day(it).monthValue == monthValue },
            )
        }
        return YearOverview(year = year, months = months)
    }
}
