package nl.tufein.openconnect.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.TemporalUnit

/** An activity as a calendar cell lists it. */
data class DayItem(val id: String, val name: String, val distanceM: Double)

/** What happened on one day: how many activities, how far in all, and which ones. */
data class DayTotals(val count: Int, val distanceM: Double, val items: List<DayItem>)

/**
 * One month of the calendar. [leadingBlanks] is the number of empty cells before the 1st when weeks start on Monday;
 * only days with an activity are in [days].
 */
data class MonthView(val month: YearMonth, val days: Map<LocalDate, DayTotals>, val leadingBlanks: Int) {
    val activeDays: Int get() = days.size
    val totalCount: Int get() = days.values.sumOf { it.count }
    val totalDistanceM: Double get() = days.values.sumOf { it.distanceM }
}

/** A run of days or weeks in a row, [length] long, from [start] to [end] (a week is its Monday); none is length 0. */
data class StreakRun(val length: Int, val start: LocalDate?, val end: LocalDate?) {
    companion object {
        val NONE = StreakRun(0, null, null)
    }
}

/**
 * [currentDays] and [currentWeeks] are still running: a streak is not broken until the day (or week) is over, so one that
 * ended yesterday still counts today ([todayOpen] is true then, and the person can keep it alive today).
 */
data class StreakSummary(
    val currentDays: StreakRun,
    val longestDays: StreakRun,
    val currentWeeks: StreakRun,
    val longestWeeks: StreakRun,
    val todayOpen: Boolean,
)

/** Calendar and streaks over the activities. Days are UTC days and weeks start on Monday, like everywhere else in the app. */
object Streaks {
    private fun day(a: Activity): LocalDate = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()

    private fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The month with the activities of each of its days. */
    fun month(activities: List<Activity>, month: YearMonth): MonthView {
        val byDay = activities
            .filter { YearMonth.from(day(it)) == month }
            .groupBy { day(it) }
            .mapValues { (_, list) ->
                val sorted = list.sortedBy { it.startTime }
                DayTotals(sorted.size, sorted.sumOf { it.distanceM }, sorted.map { DayItem(it.id, it.name.ifBlank { it.sport.key }, it.distanceM) })
            }
        return MonthView(month, byDay, month.atDay(1).dayOfWeek.value - 1)
    }

    /** The first month with an activity, or null without any. */
    fun firstMonth(activities: List<Activity>): YearMonth? = activities.minOfOrNull { it.startTime }?.let { YearMonth.from(it.atZone(ZoneOffset.UTC)) }

    fun compute(activities: List<Activity>, today: LocalDate): StreakSummary {
        val days = activities.map { day(it) }.toSortedSet()
        val weeks = days.map { monday(it) }.toSortedSet()
        return StreakSummary(
            currentDays = current(days, today, ChronoUnit.DAYS),
            longestDays = longest(days, ChronoUnit.DAYS),
            currentWeeks = current(weeks, monday(today), ChronoUnit.WEEKS),
            longestWeeks = longest(weeks, ChronoUnit.WEEKS),
            todayOpen = today !in days && today.minusDays(1) in days,
        )
    }

    /** The run that ends at [anchor], or at the step before it when [anchor] itself has nothing yet. */
    private fun current(set: Set<LocalDate>, anchor: LocalDate, unit: TemporalUnit): StreakRun {
        val end = when {
            anchor in set -> anchor
            anchor.minus(1, unit) in set -> anchor.minus(1, unit)
            else -> return StreakRun.NONE
        }
        var start = end
        var length = 1
        while (start.minus(1, unit) in set) {
            start = start.minus(1, unit)
            length++
        }
        return StreakRun(length, start, end)
    }

    /** The longest run; of equally long ones, the most recent. */
    private fun longest(sorted: Set<LocalDate>, unit: TemporalUnit): StreakRun {
        var best = StreakRun.NONE
        var start: LocalDate? = null
        var previous: LocalDate? = null
        var length = 0
        for (d in sorted) {
            if (previous != null && previous.plus(1, unit) == d) {
                length++
            } else {
                start = d
                length = 1
            }
            if (length >= best.length) best = StreakRun(length, start, d)
            previous = d
        }
        return best
    }
}
