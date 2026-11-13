package nl.tufein.openconnect.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun weekStart(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())

@Serializable
data class SportTotals(
    val count: Int = 0,
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("duration_s") val durationS: Double = 0.0,
)

data class WeeklySummary(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val activityCount: Int = 0,
    val totalDistanceM: Double = 0.0,
    val totalDurationS: Double = 0.0,
    val totalElevationM: Double = 0.0,
    val totalCalories: Int = 0,
    val bySport: Map<String, SportTotals> = emptyMap(),
    val daysWithData: Int = 0,
    val totalSteps: Int = 0,
    val avgSteps: Double? = null,
    val goalDays: Int = 0,
    val intensityMinutes: Int = 0,
    val avgRestingHr: Double? = null,
    val avgStress: Double? = null,
    val avgBodyBatteryHigh: Double? = null,
    val avgSleepHours: Double? = null,
    val avgSleepScore: Double? = null,
)

data class TrendRow(
    val date: LocalDate,
    val steps: Int?,
    val restingHr: Int?,
    val avgStress: Int?,
    val bodyBatteryHigh: Int?,
    val sleepHours: Double?,
    val sleepScore: Int?,
    val distanceM: Double,
)

data class Overview(
    val today: DailySummary?,
    val thisWeek: WeeklySummary,
    val last7: WeeklySummary,
    val previous7: WeeklySummary,
    val stepsChangePct: Double?,
    val trend: List<TrendRow>,
    val recentActivities: List<Activity>,
)

private fun mean(values: List<Number>): Double? = if (values.isEmpty()) null else values.sumOf { it.toDouble() } / values.size

private fun dayStart(day: LocalDate): Instant = day.atStartOfDay().toInstant(ZoneOffset.UTC)

class StatsService(private val repo: Repository, private val clock: () -> Instant = Instant::now) {
    private fun activitiesBetween(start: LocalDate, end: LocalDate): List<Activity> =
        repo.listActivities(start = dayStart(start), end = dayStart(end.plusDays(1)), limit = 10_000)

    /** Totals and averages for an inclusive date range (used for weeks and rolling windows). */
    fun summary(start: LocalDate, end: LocalDate): WeeklySummary {
        val activities = activitiesBetween(start, end)
        val bySport = LinkedHashMap<String, SportTotals>()
        for (a in activities) {
            val totals = bySport[a.sport.key] ?: SportTotals()
            bySport[a.sport.key] = SportTotals(totals.count + 1, totals.distanceM + a.distanceM, totals.durationS + a.durationS)
        }
        val dailies = repo.listDaily(start, end)
        val sleeps = repo.listSleep(start, end)
        return WeeklySummary(
            weekStart = start,
            weekEnd = end,
            activityCount = activities.size,
            totalDistanceM = activities.sumOf { it.distanceM },
            totalDurationS = activities.sumOf { it.durationS },
            totalElevationM = activities.sumOf { it.elevationGainM },
            totalCalories = activities.sumOf { it.calories ?: 0 },
            bySport = bySport,
            daysWithData = dailies.size,
            totalSteps = dailies.sumOf { it.steps },
            avgSteps = mean(dailies.map { it.steps }),
            goalDays = dailies.count { it.steps >= (it.stepsGoal ?: 10_000) },
            intensityMinutes = dailies.sumOf { it.intensityMinutes },
            avgRestingHr = mean(dailies.mapNotNull { it.restingHr }.filter { it != 0 }),
            avgStress = mean(dailies.mapNotNull { it.avgStress }),
            avgBodyBatteryHigh = mean(dailies.mapNotNull { it.bodyBatteryHigh }),
            avgSleepHours = mean(sleeps.filter { it.durationS != 0 }.map { it.hours }),
            avgSleepScore = mean(sleeps.mapNotNull { it.score }),
        )
    }

    fun weeklySummary(anyDayInWeek: LocalDate): WeeklySummary {
        val monday = weekStart(anyDayInWeek)
        return summary(monday, monday.plusDays(6))
    }

    /** One row per day in [start, end]; days without data have null values. */
    fun trendSeries(start: LocalDate, end: LocalDate): List<TrendRow> {
        val dailies = repo.listDaily(start, end).associateBy { it.date }
        val sleeps = repo.listSleep(start, end).associateBy { it.date }
        val distance = HashMap<LocalDate, Double>()
        for (a in activitiesBetween(start, end)) {
            val day = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()
            distance[day] = (distance[day] ?: 0.0) + a.distanceM
        }
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { day ->
            val d = dailies[day]
            val s = sleeps[day]
            TrendRow(
                date = day,
                steps = d?.steps,
                restingHr = d?.restingHr,
                avgStress = d?.avgStress,
                bodyBatteryHigh = d?.bodyBatteryHigh,
                sleepHours = s?.takeIf { it.durationS != 0 }?.let { Math.rint(it.hours * 100) / 100 },
                sleepScore = s?.score,
                distanceM = distance[day] ?: 0.0,
            )
        }.toList()
    }

    /**
     * The readiness estimate for every day in [start, end], each against the 28 days before it; null for a day with
     * too little data to judge. The same calculation as the readiness ring on Home, so the two always agree.
     */
    fun readinessSeries(start: LocalDate, end: LocalDate): List<Pair<LocalDate, Int?>> {
        val from = start.minusDays(ReadinessCalculator.BASELINE_DAYS.toLong())
        val dailies = repo.listDaily(from, end)
        val byDay = dailies.associateBy { it.date }
        val sleeps = repo.listSleep(start, end).associateBy { it.date }
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { day ->
            day to ReadinessCalculator.compute(byDay[day], sleeps[day], dailies.filter { it.date < day })?.score
        }.toList()
    }

    /** The sleep score the watch gave each night in [start, end]; null where there is none. */
    fun sleepScoreSeries(start: LocalDate, end: LocalDate): List<Pair<LocalDate, Double?>> {
        val sleeps = repo.listSleep(start, end).associateBy { it.date }
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { day ->
            day to sleeps[day]?.score?.toDouble()
        }.toList()
    }

    fun overview(trendDays: Int = 30): Overview {
        val today = clock().atZone(ZoneOffset.UTC).toLocalDate()
        val last7 = summary(today.minusDays(6), today)
        val previous7 = summary(today.minusDays(13), today.minusDays(7))
        val change = if (last7.avgSteps != null && previous7.avgSteps != null && previous7.avgSteps != 0.0) {
            Math.rint((last7.avgSteps - previous7.avgSteps) / previous7.avgSteps * 100 * 10) / 10
        } else {
            null
        }
        return Overview(
            today = repo.listDaily(today.minusDays(6), today).lastOrNull(),
            thisWeek = weeklySummary(today),
            last7 = last7,
            previous7 = previous7,
            stepsChangePct = change,
            trend = trendSeries(today.minusDays((trendDays - 1).toLong()), today),
            recentActivities = repo.listActivities(limit = 5),
        )
    }
}

data class RecordEntry(val value: Double, val activityId: String, val name: String, val date: LocalDate)

const val MIN_PACE_DISTANCE_M = 1000.0

/** Best activity-level results per sport: longest, most climbing and fastest running pace. */
fun personalRecords(repo: Repository): Map<Sport, Map<String, RecordEntry>> {
    fun entry(a: Activity, value: Double) =
        RecordEntry(value, a.id, a.name, a.startTime.atZone(ZoneOffset.UTC).toLocalDate())

    val result = LinkedHashMap<Sport, Map<String, RecordEntry>>()
    for ((sport, activities) in repo.listActivities(limit = 100_000).groupBy { it.sport }) {
        val entries = LinkedHashMap<String, RecordEntry>()
        listOf<Pair<String, (Activity) -> Double>>(
            "longest_distance" to { it.distanceM },
            "longest_duration" to { it.durationS },
            "most_elevation" to { it.elevationGainM },
        ).forEach { (key, selector) ->
            val best = activities.maxBy(selector)
            if (selector(best) > 0) entries[key] = entry(best, selector(best))
        }
        if (sport == Sport.RUNNING) {
            val paced = activities.filter { it.distanceM >= MIN_PACE_DISTANCE_M && it.paceSPerKm != null }
            paced.minByOrNull { it.paceSPerKm!! }?.let { entries["fastest_pace"] = entry(it, it.paceSPerKm!!) }
        }
        if (entries.isNotEmpty()) result[sport] = entries
    }
    return result
}
