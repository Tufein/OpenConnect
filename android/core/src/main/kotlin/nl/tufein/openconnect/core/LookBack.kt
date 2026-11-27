package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** How many activities, how far and how long: the three figures the look-back screen counts. */
data class LookBackTotals(val count: Int = 0, val distanceM: Double = 0.0, val durationS: Double = 0.0) {
    operator fun plus(other: LookBackTotals) = LookBackTotals(count + other.count, distanceM + other.distanceM, durationS + other.durationS)

    companion object {
        fun of(activities: Iterable<Activity>): LookBackTotals {
            var count = 0
            var distance = 0.0
            var duration = 0.0
            for (a in activities) {
                count++
                distance += a.distanceM
                duration += a.durationS
            }
            return LookBackTotals(count, distance, duration)
        }
    }
}

/** What a cell, a difference or the heaviest month is measured in. */
enum class LookBackMetric {
    DISTANCE,
    TIME,
    COUNT,
    ;

    fun value(totals: LookBackTotals): Double = when (this) {
        DISTANCE -> totals.distanceM
        TIME -> totals.durationS
        COUNT -> totals.count.toDouble()
    }
}

/**
 * This year up to today against last year up to the same day, and the whole of last year. The two lines are running
 * totals at the end of each month: [thisYearLine] stops after the month of [asOf] (a null is "not yet"), while
 * [previousYearLine] always has twelve points and ends on [previousFull].
 */
data class YearComparison(
    val year: Int,
    val previousYear: Int,
    /** The last day counted this year: today. */
    val asOf: LocalDate,
    /** The sport the figures are limited to; null means all sports together. */
    val sport: Sport?,
    val thisYear: LookBackTotals,
    /** Last year up to the same date as [asOf] (the 28th of February when [asOf] is a leap day). */
    val previousToDate: LookBackTotals,
    val previousFull: LookBackTotals,
    val thisYearLine: List<LookBackTotals?>,
    val previousYearLine: List<LookBackTotals>,
) {
    val distanceDiffM: Double get() = thisYear.distanceM - previousToDate.distanceM
    val durationDiffS: Double get() = thisYear.durationS - previousToDate.durationS
    val countDiff: Int get() = thisYear.count - previousToDate.count

    /** The change against last year to the same day in whole percent, or null when last year had nothing to compare with. */
    fun percent(metric: LookBackMetric): Int? {
        val base = metric.value(previousToDate)
        if (base <= 0) return null
        return ((metric.value(thisYear) - base) / base * 100).roundToInt()
    }
}

/** One year of the month grid: twelve months January to December and their total. */
data class MonthTableYear(val year: Int, val months: List<LookBackTotals>, val total: LookBackTotals)

data class HeaviestMonth(val year: Int, val month: Month, val totals: LookBackTotals)

/** Every month of every year from the first activity to the last, newest year first, empty years in between included. */
data class MonthTable(val sport: Sport?, val years: List<MonthTableYear>) {
    /** The month with the highest figure on [metric] over all years; a tie goes to the newest month. Null when nothing was counted. */
    fun heaviest(metric: LookBackMetric): HeaviestMonth? {
        var best: HeaviestMonth? = null
        var bestValue = 0.0
        for (row in years) { // newest first, and inside a year December first, so a strict > keeps the newest on a tie
            for (index in 11 downTo 0) {
                val value = metric.value(row.months[index])
                if (value > bestValue) {
                    bestValue = value
                    best = HeaviestMonth(row.year, Month.of(index + 1), row.months[index])
                }
            }
        }
        return best
    }
}

data class YearSport(val sport: Sport, val totals: LookBackTotals)

/** A week from Monday, counted over the activities of one year only (a week over New Year is cut in two). */
data class WeekTotals(val weekStart: LocalDate, val totals: LookBackTotals)

/**
 * One year across all sports. [quietWeeks] are the weeks without any activity between the first and the last week of
 * the year; in the running year only finished weeks count, so the week that has just begun is never "quiet".
 */
data class YearSummary(
    val year: Int,
    val totals: LookBackTotals,
    /** Longest time first. */
    val sports: List<YearSport>,
    val busiestWeek: WeekTotals,
    val quietWeeks: Int,
)

enum class RecordKind { LONGEST_DISTANCE, LONGEST_DURATION, MOST_ELEVATION, FASTEST_PACE }

/** A record that was beaten: [value] on [date] against [previousValue] of [previousDate]. Distances in metres, time in seconds, pace in seconds per km. */
data class RecordEvent(
    val sport: Sport,
    val kind: RecordKind,
    val value: Double,
    val previousValue: Double,
    val previousDate: LocalDate,
    val date: LocalDate,
    val activityId: String,
    val name: String,
)

/** All the look-back figures for one choice of sport, worked out together from the stored activities. */
data class LookBackReport(
    /** The sports that occur, most often first. */
    val sports: List<Sport>,
    /** The sport the comparison and the grid are limited to; null is all sports. */
    val sport: Sport?,
    /** False while the sport is the default and nobody picked one. */
    val chosen: Boolean,
    val comparison: YearComparison?,
    val table: MonthTable?,
    val years: List<YearSummary>,
    val records: List<RecordEvent>,
) {
    val isEmpty: Boolean get() = sports.isEmpty()
}

/**
 * M15 "Terugblik": looking back over a long history. Everything is worked out from the activities that are stored,
 * nothing is kept, so an import or a delete always shows the truth. Days are calendar days in UTC, like the year
 * overview and the personal records, so a late run does not move between years with the phone's zone.
 */
object LookBackCalculator {
    private fun day(activity: Activity): LocalDate = activity.startTime.atZone(ZoneOffset.UTC).toLocalDate()

    /** The sports that occur, the most frequent first; a tie goes by the order of [Sport]. */
    fun sports(activities: List<Activity>): List<Sport> =
        activities.groupingBy { it.sport }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Sport, Int>> { it.value }.thenBy { it.key.ordinal })
            .map { it.key }

    /**
     * Running when it has an activity this year or last, otherwise the most frequent sport of those two years, and all
     * sports (null) when neither year has anything.
     */
    fun defaultSport(activities: List<Activity>, today: LocalDate): Sport? {
        val recent = activities.filter { day(it).year >= today.year - 1 }
        if (recent.isEmpty()) return null
        return if (recent.any { it.sport == Sport.RUNNING }) Sport.RUNNING else sports(recent).first()
    }

    /** This year against last, or null when neither has an activity (of [sport], or of any sport when it is null). */
    fun comparison(activities: List<Activity>, today: LocalDate, sport: Sport?): YearComparison? {
        val mine = if (sport == null) activities else activities.filter { it.sport == sport }
        val year = today.year
        val thisYear = mine.filter { val d = day(it); d.year == year && d <= today }
        val previous = mine.filter { day(it).year == year - 1 }
        if (thisYear.isEmpty() && previous.isEmpty()) return null

        val previousAsOf = today.minusYears(1)
        val previousToDate = previous.filter { day(it) <= previousAsOf }

        fun runningLine(rows: List<Activity>): List<LookBackTotals> {
            val perMonth = rows.groupBy { day(it).monthValue }
            var running = LookBackTotals()
            return (1..12).map { month ->
                running += LookBackTotals.of(perMonth[month].orEmpty())
                running
            }
        }
        val thisLine = runningLine(thisYear)
        return YearComparison(
            year = year,
            previousYear = year - 1,
            asOf = today,
            sport = sport,
            thisYear = LookBackTotals.of(thisYear),
            previousToDate = LookBackTotals.of(previousToDate),
            previousFull = LookBackTotals.of(previous),
            thisYearLine = thisLine.mapIndexed { index, totals -> if (index + 1 > today.monthValue) null else totals },
            previousYearLine = runningLine(previous),
        )
    }

    /** The grid of months and years, or null when there is nothing to count. */
    fun monthTable(activities: List<Activity>, sport: Sport?): MonthTable? {
        val mine = if (sport == null) activities else activities.filter { it.sport == sport }
        if (mine.isEmpty()) return null
        val byMonth = mine.groupBy { val d = day(it); d.year * 100 + d.monthValue }
        val first = mine.minOf { day(it).year }
        val last = mine.maxOf { day(it).year }
        val rows = (last downTo first).map { year ->
            val months = (1..12).map { LookBackTotals.of(byMonth[year * 100 + it].orEmpty()) }
            MonthTableYear(year, months, months.fold(LookBackTotals()) { sum, month -> sum + month })
        }
        return MonthTable(sport, rows)
    }

    /** One summary per year with an activity, newest first, over all sports. */
    fun yearSummaries(activities: List<Activity>, today: LocalDate): List<YearSummary> =
        activities.groupBy { day(it).year }.toSortedMap(reverseOrder()).map { (year, rows) ->
            val perSport = rows.groupBy { it.sport }
                .map { (sport, ofSport) -> YearSport(sport, LookBackTotals.of(ofSport)) }
                .sortedWith(compareByDescending<YearSport> { it.totals.durationS }.thenBy { it.sport.ordinal })
            val weeks = rows.groupBy { weekStart(day(it)) }.toSortedMap().map { (start, ofWeek) -> WeekTotals(start, LookBackTotals.of(ofWeek)) }
            val busiest = weeks.maxBy { it.totals.durationS } // sorted by date, so a tie keeps the earliest week
            val firstWeek = weeks.first().weekStart
            var lastWeek = weeks.last().weekStart
            if (year == today.year) lastWeek = maxOf(lastWeek, weekStart(today).minusWeeks(1))
            val span = ChronoUnit.WEEKS.between(firstWeek, lastWeek).toInt() + 1
            YearSummary(year, LookBackTotals.of(rows), perSport, busiest, quietWeeks = span - weeks.size)
        }

    private class Standing(var value: Double, var date: LocalDate)

    /**
     * Every time a record was beaten, newest first. The records are the same four as [personalRecords] (longest
     * distance, longest time, most climbing, and the fastest average pace over at least a kilometre of running), per
     * sport. The activity that first sets a record is not an event, since there was nothing to beat; and a tie does
     * not count as beating it. The newest event of each kind is therefore the record that stands now.
     */
    fun recordTimeline(activities: List<Activity>): List<RecordEvent> {
        val standing = HashMap<Pair<Sport, RecordKind>, Standing>()
        val events = ArrayList<RecordEvent>()
        for (a in activities.sortedWith(compareBy<Activity>({ it.startTime }, { it.id }))) {
            val date = day(a)
            fun consider(kind: RecordKind, value: Double, lowerIsBetter: Boolean = false) {
                val current = standing[a.sport to kind]
                if (current == null) {
                    standing[a.sport to kind] = Standing(value, date)
                    return
                }
                val better = if (lowerIsBetter) value < current.value else value > current.value
                if (better) {
                    events += RecordEvent(a.sport, kind, value, current.value, current.date, date, a.id, a.name)
                    current.value = value
                    current.date = date
                }
            }
            if (a.distanceM > 0) consider(RecordKind.LONGEST_DISTANCE, a.distanceM)
            if (a.durationS > 0) consider(RecordKind.LONGEST_DURATION, a.durationS)
            if (a.elevationGainM > 0) consider(RecordKind.MOST_ELEVATION, a.elevationGainM)
            if (a.sport == Sport.RUNNING && a.distanceM >= MIN_PACE_DISTANCE_M) {
                a.paceSPerKm?.let { consider(RecordKind.FASTEST_PACE, it, lowerIsBetter = true) }
            }
        }
        return events.reversed()
    }

    /**
     * Everything the screen needs. Until a sport is [chosen] the default one is used; a chosen sport that no longer has
     * any activity (after a delete) falls back to the default as well, while "all sports" (null) always stays.
     */
    fun report(activities: List<Activity>, today: LocalDate, sport: Sport?, chosen: Boolean): LookBackReport {
        val sports = sports(activities)
        val keep = chosen && (sport == null || sport in sports)
        val effective = if (keep) sport else defaultSport(activities, today)
        return LookBackReport(
            sports = sports,
            sport = effective,
            chosen = keep,
            comparison = comparison(activities, today, effective),
            table = monthTable(activities, effective),
            years = yearSummaries(activities, today),
            records = recordTimeline(activities),
        )
    }
}
