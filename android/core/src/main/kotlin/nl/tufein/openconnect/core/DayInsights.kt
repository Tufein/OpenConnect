package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.hume.HumeDay
import nl.tufein.openconnect.core.oura.OuraDay
import nl.tufein.openconnect.core.whoop.WhoopDay
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/** Where a day's numbers come from. They are never mixed: a day is read from the watch or from the ring, as chosen. */
enum class InsightSource(
    /** The measures this source reports, in the order the day card lists them. */
    val metrics: List<ChartMetric>,
) {
    GARMIN(
        listOf(
            ChartMetric.READINESS, ChartMetric.SLEEP_SCORE, ChartMetric.SLEEP, ChartMetric.STEPS, ChartMetric.RESTING_HR,
            ChartMetric.BODY_BATTERY, ChartMetric.STRESS, ChartMetric.HRV, ChartMetric.SPO2, ChartMetric.RESPIRATION,
        ),
    ),
    OURA(
        listOf(
            ChartMetric.READINESS, ChartMetric.SLEEP_SCORE, ChartMetric.ACTIVITY_SCORE, ChartMetric.SLEEP, ChartMetric.STEPS,
            ChartMetric.LOWEST_HR, ChartMetric.HRV, ChartMetric.TEMPERATURE, ChartMetric.RESPIRATION, ChartMetric.SPO2,
        ),
    ),
    WHOOP(
        listOf(
            ChartMetric.READINESS, ChartMetric.STRAIN, ChartMetric.SLEEP_SCORE, ChartMetric.SLEEP, ChartMetric.RESTING_HR,
            ChartMetric.HRV, ChartMetric.RESPIRATION, ChartMetric.SPO2,
        ),
    ),

    /** The Hume Band through Health Connect: only what Health Connect has types for, so no scores. */
    HUME(
        listOf(
            ChartMetric.SLEEP, ChartMetric.STEPS, ChartMetric.RESTING_HR, ChartMetric.LOWEST_HR, ChartMetric.HRV, ChartMetric.SPO2,
        ),
    ),
}

/** An effort on one day, from the watch (or a file) or from the ring. [id] opens it in the activity screen. */
data class DayWorkout(
    val id: String,
    /** The name it was given; empty when it has none, and the screen then shows the sport. */
    val name: String,
    val sport: Sport,
    val startMs: Long,
    val durationS: Double,
    val distanceM: Double?,
    val calories: Int?,
)

/** What one source knows about one day: a number per measure that was taken, and the workouts. */
data class DayRecord(
    val date: LocalDate,
    val values: Map<ChartMetric, Double> = emptyMap(),
    val workouts: List<DayWorkout> = emptyList(),
) {
    val isEmpty: Boolean get() = values.isEmpty() && workouts.isEmpty()
}

/** The days of one source, one record per day (empty when nothing was measured), so a chart can show the gaps. */
class DaySeries(val source: InsightSource, records: List<DayRecord>) {
    private val byDate = records.associateBy { it.date }

    fun record(day: LocalDate): DayRecord? = byDate[day]

    fun value(metric: ChartMetric, day: LocalDate): Double? = byDate[day]?.values?.get(metric)

    /** [days] days ending on [end], oldest first, null where the measure is missing. */
    fun series(metric: ChartMetric, end: LocalDate, days: Int): List<Pair<LocalDate, Double?>> =
        (days - 1 downTo 0).map { back -> end.minusDays(back.toLong()).let { it to value(metric, it) } }

    /** The newest day up to and including [upTo] that has anything, or null. */
    fun latestWithData(upTo: LocalDate): LocalDate? = byDate.values.filter { !it.isEmpty && !it.date.isAfter(upTo) }.maxOfOrNull { it.date }

    val hasAnyData: Boolean get() = byDate.values.any { !it.isEmpty }
}

/** Turns what the app stores into [DaySeries]. */
object DayRecords {
    /**
     * One record per day in [start]..[end] from the watch's days and nights, its readiness estimate and the activities of the day.
     * An activity belongs to the day it started on in [zone] (the phone's own clock).
     */
    fun garmin(
        start: LocalDate,
        end: LocalDate,
        dailies: List<DailySummary>,
        sleeps: List<SleepSummary>,
        readiness: List<Pair<LocalDate, Int?>>,
        activities: List<Activity>,
        zone: ZoneId = ZoneOffset.UTC,
    ): DaySeries {
        val daily = dailies.associateBy { it.date }
        val sleep = sleeps.associateBy { it.date }
        val ready = readiness.toMap()
        val work = activities.groupBy { it.startTime.atZone(zone).toLocalDate() }
        return DaySeries(
            InsightSource.GARMIN,
            days(start, end).map { day ->
                val d = daily[day]
                val s = sleep[day]?.takeIf { it.durationS != 0 }
                val values = LinkedHashMap<ChartMetric, Double>()
                ready[day]?.let { values[ChartMetric.READINESS] = it.toDouble() }
                sleep[day]?.score?.let { values[ChartMetric.SLEEP_SCORE] = it.toDouble() }
                s?.let { values[ChartMetric.SLEEP] = Math.rint(it.hours * 100) / 100 }
                d?.let { values[ChartMetric.STEPS] = it.steps.toDouble() }
                d?.restingHr?.takeIf { it > 0 }?.let { values[ChartMetric.RESTING_HR] = it.toDouble() }
                d?.bodyBatteryHigh?.let { values[ChartMetric.BODY_BATTERY] = it.toDouble() }
                d?.avgStress?.let { values[ChartMetric.STRESS] = it.toDouble() }
                d?.hrvMs?.takeIf { it > 0 }?.let { values[ChartMetric.HRV] = it.toDouble() }
                d?.spo2Avg?.takeIf { it > 0 }?.let { values[ChartMetric.SPO2] = it.toDouble() }
                d?.respirationAvg?.takeIf { it > 0 }?.let { values[ChartMetric.RESPIRATION] = it }
                DayRecord(
                    day,
                    values,
                    work[day].orEmpty().sortedBy { it.startTime }.map {
                        DayWorkout(it.id, it.name, it.sport, it.startTime.toEpochMilli(), it.durationS, it.distanceM.takeIf { m -> m > 0 }, it.calories)
                    },
                )
            },
        )
    }

    /** One record per day in [start]..[end] from the ring's days, each under the day Oura files it under. */
    fun oura(start: LocalDate, end: LocalDate, days: List<OuraDay>): DaySeries {
        val byDay = days.associateBy { it.date }
        return DaySeries(
            InsightSource.OURA,
            days(start, end).map { day ->
                val d = byDay[day] ?: return@map DayRecord(day)
                val values = LinkedHashMap<ChartMetric, Double>()
                d.readiness?.let { values[ChartMetric.READINESS] = it.toDouble() }
                d.sleepScore?.let { values[ChartMetric.SLEEP_SCORE] = it.toDouble() }
                d.activityScore?.let { values[ChartMetric.ACTIVITY_SCORE] = it.toDouble() }
                d.sleepHours?.takeIf { d.sleepS != 0 }?.let { values[ChartMetric.SLEEP] = Math.rint(it * 100) / 100 }
                d.steps?.let { values[ChartMetric.STEPS] = it.toDouble() }
                d.lowestHr?.takeIf { it > 0 }?.let { values[ChartMetric.LOWEST_HR] = it.toDouble() }
                d.averageHrv?.takeIf { it > 0 }?.let { values[ChartMetric.HRV] = it.toDouble() }
                d.temperatureDeviation?.let { values[ChartMetric.TEMPERATURE] = it }
                d.breathAvg?.takeIf { it > 0 }?.let { values[ChartMetric.RESPIRATION] = it }
                d.spo2Avg?.takeIf { it > 0 }?.let { values[ChartMetric.SPO2] = it }
                DayRecord(
                    day,
                    values,
                    d.workouts.sortedBy { it.startMs }.map {
                        DayWorkout(RingWorkouts.ID_PREFIX + it.id, it.name, RingWorkouts.sport(it.activity), it.startMs, it.durationS.toDouble(), it.distanceM?.takeIf { m -> m > 0 }, it.calories)
                    },
                )
            },
        )
    }

    /**
     * One record per day in [start]..[end] from the WHOOP band's days. Recovery stands in for the readiness of the other
     * sources and sleep performance for their sleep score, because those are the numbers a band's morning is about.
     */
    fun whoop(start: LocalDate, end: LocalDate, days: List<WhoopDay>): DaySeries {
        val byDay = days.associateBy { it.date }
        return DaySeries(
            InsightSource.WHOOP,
            days(start, end).map { day ->
                val d = byDay[day] ?: return@map DayRecord(day)
                val values = LinkedHashMap<ChartMetric, Double>()
                d.recovery?.let { values[ChartMetric.READINESS] = it.toDouble() }
                d.strain?.let { values[ChartMetric.STRAIN] = it }
                d.sleepPerformance?.let { values[ChartMetric.SLEEP_SCORE] = it.toDouble() }
                d.sleepHours?.takeIf { d.sleepS != 0 }?.let { values[ChartMetric.SLEEP] = Math.rint(it * 100) / 100 }
                d.restingHr?.takeIf { it > 0 }?.let { values[ChartMetric.RESTING_HR] = it.toDouble() }
                d.hrv?.takeIf { it > 0 }?.let { values[ChartMetric.HRV] = it }
                d.respiration?.takeIf { it > 0 }?.let { values[ChartMetric.RESPIRATION] = it }
                d.spo2?.takeIf { it > 0 }?.let { values[ChartMetric.SPO2] = it }
                DayRecord(
                    day,
                    values,
                    d.workouts.sortedBy { it.startMs }.map {
                        DayWorkout(BandSource.WHOOP.idPrefix + it.id, it.name, RingWorkouts.sport(it.activity), it.startMs, it.durationS.toDouble(), it.distanceM?.takeIf { m -> m > 0 }, it.calories)
                    },
                )
            },
        )
    }

    /**
     * One record per day in [start]..[end] from the Hume Band's days. Health Connect carries no readiness or sleep score, so
     * none is made up: the card shows the night, steps, heart rate, HRV and blood oxygen the band wrote.
     */
    fun hume(start: LocalDate, end: LocalDate, days: List<HumeDay>): DaySeries {
        val byDay = days.associateBy { it.date }
        return DaySeries(
            InsightSource.HUME,
            days(start, end).map { day ->
                val d = byDay[day] ?: return@map DayRecord(day)
                val values = LinkedHashMap<ChartMetric, Double>()
                d.sleepHours?.takeIf { d.sleepS != 0 }?.let { values[ChartMetric.SLEEP] = Math.rint(it * 100) / 100 }
                d.steps?.let { values[ChartMetric.STEPS] = it.toDouble() }
                d.restingHr?.takeIf { it > 0 }?.let { values[ChartMetric.RESTING_HR] = it.toDouble() }
                d.lowestHr?.takeIf { it > 0 }?.let { values[ChartMetric.LOWEST_HR] = it.toDouble() }
                d.hrv?.takeIf { it > 0 }?.let { values[ChartMetric.HRV] = it }
                d.spo2?.takeIf { it > 0 }?.let { values[ChartMetric.SPO2] = it }
                DayRecord(
                    day,
                    values,
                    d.workouts.sortedBy { it.startMs }.map {
                        DayWorkout(BandSource.HUME.idPrefix + it.id, it.name, RingWorkouts.sport(it.activity), it.startMs, it.durationS.toDouble(), it.distanceM?.takeIf { m -> m > 0 }, it.calories)
                    },
                )
            },
        )
    }

    private fun days(start: LocalDate, end: LocalDate): List<LocalDate> =
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
}

enum class Extreme { HIGHEST, LOWEST }

/**
 * One measure on one day. [usual] is the person's own average of the 28 days before (null with fewer than three readings);
 * [changePct] and [better] are the day against it.
 */
data class DayReading(
    val metric: ChartMetric,
    val value: Double,
    val usual: Double?,
    /** Percent above (positive) or below the usual; null without a usual, and for temperature, which sits around zero. */
    val changePct: Double?,
    /** True when the day is clearly (5% or more) on the good side of the usual, false on the bad side, null when similar or neither side is better. */
    val better: Boolean?,
    /** The day against the common range of the measure; null when it cannot be judged (HRV without a usual). */
    val verdict: Verdict?,
    /** Set when the day is above, or below, every one of the days before it in the last four weeks. */
    val extreme: Extreme?,
)

/** A whole day as one source saw it. */
data class DayInsight(
    val date: LocalDate,
    val source: InsightSource,
    val readings: List<DayReading>,
    /** The measures of the source that have no number for this day. */
    val missing: List<ChartMetric>,
    val workouts: List<DayWorkout>,
) {
    val isEmpty: Boolean get() = readings.isEmpty() && workouts.isEmpty()
    val judged: Int get() = readings.count { it.verdict != null }
    val good: Int get() = readings.count { it.verdict == Verdict.GOOD }
}

/** Reads one day against the person's own days before it: the "insights per day". */
object DayInsights {
    /** How many days before the chosen one make the person's usual, and the window for "highest in four weeks". */
    const val BASELINE_DAYS = 28
    const val MIN_BASELINE = 3
    const val MIN_FOR_EXTREME = 7

    /** Days drawn in the chart under the readings, ending on the chosen day. */
    const val CHART_DAYS = 30

    /** Days on offer in the day list, newest first. */
    const val CHOICES = 60

    /** How far back the data has to reach so the oldest day on offer still has its four weeks before it and a full chart ending on it. */
    val HISTORY_DAYS: Int = CHOICES - 1 + maxOf(BASELINE_DAYS, CHART_DAYS - 1)

    private const val CLEARLY_DIFFERENT_PCT = 5.0

    fun of(set: DaySeries, day: LocalDate): DayInsight {
        val readings = ArrayList<DayReading>()
        val missing = ArrayList<ChartMetric>()
        for (metric in set.source.metrics) {
            val value = set.value(metric, day)
            if (value == null || value.isNaN()) {
                missing += metric
                continue
            }
            val before = (1..BASELINE_DAYS).mapNotNull { set.value(metric, day.minusDays(it.toLong()))?.takeIf { v -> !v.isNaN() } }
            val usual = before.takeIf { it.size >= MIN_BASELINE }?.average()
            val pct = if (metric == ChartMetric.TEMPERATURE) null else usual?.takeIf { it != 0.0 }?.let { (value - it) / abs(it) * 100 }
            val better = pct?.takeIf { abs(it) >= CLEARLY_DIFFERENT_PCT }?.let { p -> metric.higherIsBetter?.let { (p > 0) == it } }
            val extreme = when {
                before.size + 1 < MIN_FOR_EXTREME -> null
                value > before.max() -> Extreme.HIGHEST
                value < before.min() -> Extreme.LOWEST
                else -> null
            }
            readings += DayReading(metric, value, usual, pct, better, ChartInsight.verdictOfDay(metric, value, usual), extreme)
        }
        return DayInsight(day, set.source, readings, missing, set.record(day)?.workouts.orEmpty())
    }

    /** The days to pick from, newest first, from [today] back. */
    fun choices(today: LocalDate, count: Int = CHOICES): List<LocalDate> = (0 until count).map { today.minusDays(it.toLong()) }

    /**
     * The day to show: [wanted] when it is one of the [choices], otherwise the newest day with data (a ring or a watch that has
     * not synced today still shows yesterday), and today when there is nothing at all.
     */
    fun pick(set: DaySeries, today: LocalDate, wanted: LocalDate?): LocalDate {
        if (wanted != null && !wanted.isAfter(today) && wanted.isAfter(today.minusDays(CHOICES.toLong()))) return wanted
        return set.latestWithData(today) ?: today
    }

    /** The day [by] days from [day], kept inside the days on offer. */
    fun step(day: LocalDate, by: Int, today: LocalDate): LocalDate =
        day.plusDays(by.toLong()).let { if (it.isAfter(today)) today else if (!it.isAfter(today.minusDays(CHOICES.toLong()))) today.minusDays((CHOICES - 1).toLong()) else it }
}
