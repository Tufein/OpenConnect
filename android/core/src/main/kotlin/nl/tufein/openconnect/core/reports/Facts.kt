package nl.tufein.openconnect.core.reports

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.ReadinessCalculator
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.SportTotals
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.core.WeeklySummary
import nl.tufein.openconnect.core.haversineM
import nl.tufein.openconnect.core.weekStart
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The structured facts reports are written from. Reports use these only, never raw rows, which keeps
 * prompts small, makes the input hashable (so unchanged input is never re-billed) and limits what
 * leaves the phone.
 */
sealed interface ReportFacts

@Serializable
data class HrHalves(
    @SerialName("first_half_avg") val firstHalfAvg: Int,
    @SerialName("second_half_avg") val secondHalfAvg: Int,
)

@Serializable
data class PreviousSameSport(
    val count: Int,
    @SerialName("avg_distance_km") val avgDistanceKm: Double,
    @SerialName("avg_pace_min_per_km") val avgPaceMinPerKm: String?,
)

@Serializable
data class ActivityContext(
    @SerialName("is_longest_of_sport") val isLongestOfSport: Boolean,
    @SerialName("previous_same_sport") val previousSameSport: PreviousSameSport? = null,
)

@Serializable
data class ActivityFacts(
    val name: String,
    val sport: String,
    val date: String,
    @SerialName("duration_min") val durationMin: Double,
    @SerialName("distance_km") val distanceKm: Double,
    @SerialName("pace_min_per_km") val paceMinPerKm: String?,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double?,
    @SerialName("avg_hr") val avgHr: Int?,
    @SerialName("max_hr") val maxHr: Int?,
    @SerialName("elevation_gain_m") val elevationGainM: Int,
    val calories: Int?,
    val device: String?,
    @SerialName("splits_s_per_km") val splitsSPerKm: List<Int>,
    val hr: HrHalves?,
    val context: ActivityContext,
) : ReportFacts

@Serializable
data class WeekTotals(
    @SerialName("activity_count") val activityCount: Int,
    @SerialName("total_distance_m") val totalDistanceM: Double,
    @SerialName("total_duration_s") val totalDurationS: Double,
    @SerialName("total_elevation_m") val totalElevationM: Double,
    @SerialName("total_calories") val totalCalories: Int,
    @SerialName("by_sport") val bySport: Map<String, SportTotals>,
    @SerialName("days_with_data") val daysWithData: Int,
    @SerialName("total_steps") val totalSteps: Int,
    @SerialName("avg_steps") val avgSteps: Double?,
    @SerialName("goal_days") val goalDays: Int,
    @SerialName("intensity_minutes") val intensityMinutes: Int,
    @SerialName("avg_resting_hr") val avgRestingHr: Double?,
    @SerialName("avg_stress") val avgStress: Double?,
    @SerialName("avg_body_battery_high") val avgBodyBatteryHigh: Double?,
    @SerialName("avg_sleep_hours") val avgSleepHours: Double?,
    @SerialName("avg_sleep_score") val avgSleepScore: Double?,
)

@Serializable
data class WeekActivity(
    val name: String,
    val sport: String,
    val date: String,
    @SerialName("distance_km") val distanceKm: Double,
    @SerialName("duration_min") val durationMin: Double,
    @SerialName("avg_hr") val avgHr: Int?,
)

@Serializable
data class WeeklyFacts(
    @SerialName("week_start") val weekStart: String,
    @SerialName("week_end") val weekEnd: String,
    @SerialName("this_week") val thisWeek: WeekTotals,
    @SerialName("previous_week") val previousWeek: WeekTotals,
    val activities: List<WeekActivity>,
) : ReportFacts

/** A month or a year: the same totals as a week, against the period before it. [kind] is "month" or "year". */
@Serializable
data class PeriodFacts(
    val kind: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("this_period") val thisPeriod: WeekTotals,
    @SerialName("previous_period") val previousPeriod: WeekTotals,
    /** The longest activities of the period (by duration), at most [PERIOD_ACTIVITIES]. */
    val activities: List<WeekActivity>,
) : ReportFacts

const val PERIOD_ACTIVITIES = 15

@Serializable
data class CoachDay(
    val date: String,
    val steps: Int?,
    @SerialName("steps_goal") val stepsGoal: Int?,
    @SerialName("resting_hr") val restingHr: Int?,
    @SerialName("avg_stress") val avgStress: Int?,
    @SerialName("body_battery_high") val bodyBatteryHigh: Int?,
    @SerialName("body_battery_low") val bodyBatteryLow: Int?,
    @SerialName("hrv_ms") val hrvMs: Int?,
    @SerialName("sleep_hours") val sleepHours: Double?,
    @SerialName("sleep_score") val sleepScore: Int?,
)

@Serializable
data class ReadinessFacts(
    val score: Int,
    val level: String,
    val strongest: String,
    val weakest: String,
)

/** What the coach answers from: four weeks of day values, the recent activities and two week totals. */
@Serializable
data class CoachFacts(
    val today: String,
    val days: List<CoachDay>,
    val activities: List<WeekActivity>,
    @SerialName("last_7_days") val last7Days: WeekTotals,
    @SerialName("previous_7_days") val previous7Days: WeekTotals,
    val readiness: ReadinessFacts?,
) : ReportFacts

val FactsJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = true
}

fun ReportFacts.toJson(): String = when (this) {
    is ActivityFacts -> FactsJson.encodeToString(ActivityFacts.serializer(), this)
    is WeeklyFacts -> FactsJson.encodeToString(WeeklyFacts.serializer(), this)
    is PeriodFacts -> FactsJson.encodeToString(PeriodFacts.serializer(), this)
    is CoachFacts -> FactsJson.encodeToString(CoachFacts.serializer(), this)
}

private val PACE_SPORTS = setOf(Sport.RUNNING, Sport.WALKING, Sport.HIKING)
private const val MAX_SPLITS = 60

private fun round(value: Double, digits: Int): Double {
    val factor = Math.pow(10.0, digits.toDouble())
    return Math.rint(value * factor) / factor
}

fun formatPace(secondsPerKm: Double?): String? {
    if (secondsPerKm == null || secondsPerKm == 0.0) return null
    val total = Math.rint(secondsPerKm).toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

internal fun cumulativeDistance(track: List<TrackPoint>): List<Double>? {
    if (track.isNotEmpty() && track.all { it.distanceM != null }) return track.map { it.distanceM!! }
    val positioned = track.filter { it.hasPosition }
    if (positioned.size < 2) return null
    val distances = ArrayList<Double>(positioned.size)
    distances.add(0.0)
    var total = 0.0
    for ((a, b) in positioned.zipWithNext()) {
        total += haversineM(a.lat!!, a.lon!!, b.lat!!, b.lon!!)
        distances.add(total)
    }
    return distances
}

/** Seconds taken for each completed kilometre, interpolating the crossing instants. */
fun kilometreSplits(track: List<TrackPoint>): List<Int> {
    val distances = cumulativeDistance(track) ?: return emptyList()
    val points = if (distances.size == track.size) track else track.filter { it.hasPosition }
    fun seconds(p: TrackPoint) = p.time.epochSecond + p.time.nano / 1e9
    val splits = ArrayList<Int>()
    var boundary = 1000.0
    var lastT = seconds(points.first())
    for (i in 1 until points.size) {
        val d0 = distances[i - 1]
        val d1 = distances[i]
        val t0 = seconds(points[i - 1])
        val t1 = seconds(points[i])
        while (d1 >= boundary && d1 > d0 && splits.size < MAX_SPLITS) {
            val crossing = t0 + (boundary - d0) / (d1 - d0) * (t1 - t0)
            splits.add(Math.rint(crossing - lastT).toInt())
            lastT = crossing
            boundary += 1000.0
        }
    }
    return splits
}

private fun hrHalves(track: List<TrackPoint>): HrHalves? {
    val withHr = track.filter { (it.hr ?: 0) != 0 }
    if (withHr.size < 4) return null
    val start = track.first().time
    val end = track.last().time
    val midpoint = start.plus(Duration.between(start, end).dividedBy(2))
    val first = withHr.filter { it.time < midpoint }.map { it.hr!! }
    val second = withHr.filter { it.time >= midpoint }.map { it.hr!! }
    if (first.isEmpty() || second.isEmpty()) return null
    return HrHalves(
        firstHalfAvg = Math.rint(first.sum().toDouble() / first.size).toInt(),
        secondHalfAvg = Math.rint(second.sum().toDouble() / second.size).toInt(),
    )
}

fun activityFacts(repo: Repository, activity: Activity): ActivityFacts {
    val previous = repo.listActivities(sport = activity.sport, end = activity.startTime, limit = 1000)
        .filter { it.id != activity.id }
    var context = ActivityContext(isLongestOfSport = false)
    if (previous.isNotEmpty()) {
        val recent = previous.take(5)
        val paces = recent.mapNotNull { it.paceSPerKm }
        context = ActivityContext(
            isLongestOfSport = activity.distanceM > previous.maxOf { it.distanceM },
            previousSameSport = PreviousSameSport(
                count = previous.size,
                avgDistanceKm = round(recent.sumOf { it.distanceM } / recent.size / 1000, 2),
                avgPaceMinPerKm = if (paces.isNotEmpty()) formatPace(paces.average()) else null,
            ),
        )
    }
    val speed = activity.avgSpeedMps
    val paced = activity.sport in PACE_SPORTS
    return ActivityFacts(
        name = activity.name,
        sport = activity.sport.key,
        date = activity.startTime.atZone(ZoneOffset.UTC).toLocalDate().toString(),
        durationMin = round(activity.durationS / 60, 1),
        distanceKm = round(activity.distanceM / 1000, 2),
        paceMinPerKm = if (paced) formatPace(activity.paceSPerKm) else null,
        avgSpeedKmh = if (speed != null && !paced) round(speed * 3.6, 1) else null,
        avgHr = activity.avgHr,
        maxHr = activity.maxHr,
        elevationGainM = Math.rint(activity.elevationGainM).toInt(),
        calories = activity.calories,
        device = activity.deviceName,
        splitsSPerKm = kilometreSplits(activity.track),
        hr = hrHalves(activity.track),
        context = context,
    )
}

private fun WeeklySummary.toTotals() = WeekTotals(
    activityCount = activityCount,
    totalDistanceM = totalDistanceM,
    totalDurationS = totalDurationS,
    totalElevationM = totalElevationM,
    totalCalories = totalCalories,
    bySport = bySport,
    daysWithData = daysWithData,
    totalSteps = totalSteps,
    avgSteps = avgSteps,
    goalDays = goalDays,
    intensityMinutes = intensityMinutes,
    avgRestingHr = avgRestingHr,
    avgStress = avgStress,
    avgBodyBatteryHigh = avgBodyBatteryHigh,
    avgSleepHours = avgSleepHours,
    avgSleepScore = avgSleepScore,
)

fun weeklyFacts(repo: Repository, stats: StatsService, anyDay: LocalDate): WeeklyFacts {
    val monday = weekStart(anyDay)
    val sunday = monday.plusDays(6)
    val thisWeek = stats.weeklySummary(monday)
    val previous = stats.weeklySummary(monday.minusDays(7))
    val lo = monday.atStartOfDay().toInstant(ZoneOffset.UTC)
    val hi = sunday.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val activities = repo.listActivities(start = lo, end = hi, limit = 10_000).sortedBy { it.startTime }.take(30)
    return WeeklyFacts(
        weekStart = monday.toString(),
        weekEnd = sunday.toString(),
        thisWeek = thisWeek.toTotals(),
        previousWeek = previous.toTotals(),
        activities = activities.map {
            WeekActivity(
                name = it.name,
                sport = it.sport.key,
                date = it.startTime.atZone(ZoneOffset.UTC).toLocalDate().toString(),
                distanceKm = round(it.distanceM / 1000, 2),
                durationMin = round(it.durationS / 60, 1),
                avgHr = it.avgHr,
            )
        },
    )
}

fun periodFacts(repo: Repository, stats: StatsService, period: ReportPeriod, anyDay: LocalDate): PeriodFacts {
    require(period != ReportPeriod.WEEK) { "weeks have their own facts" }
    val start = period.start(anyDay)
    val end = period.end(start)
    val previousStart = period.shift(start, -1)
    val lo = start.atStartOfDay().toInstant(ZoneOffset.UTC)
    val hi = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val activities = repo.listActivities(start = lo, end = hi, limit = 10_000).sortedByDescending { it.durationS }.take(PERIOD_ACTIVITIES).sortedBy { it.startTime }
    return PeriodFacts(
        kind = period.name.lowercase(),
        periodStart = start.toString(),
        periodEnd = end.toString(),
        thisPeriod = stats.summary(start, end).toTotals(),
        previousPeriod = stats.summary(previousStart, period.end(previousStart)).toTotals(),
        activities = activities.map {
            WeekActivity(
                name = it.name,
                sport = it.sport.key,
                date = it.startTime.atZone(ZoneOffset.UTC).toLocalDate().toString(),
                distanceKm = round(it.distanceM / 1000, 2),
                durationMin = round(it.durationS / 60, 1),
                avgHr = it.avgHr,
            )
        },
    )
}

/** The coach's digest: the last [COACH_DAYS] days that have data, at most [COACH_ACTIVITIES] activities, never raw rows. */
fun coachFacts(repo: Repository, stats: StatsService, today: LocalDate): CoachFacts {
    val first = today.minusDays((COACH_DAYS - 1).toLong())
    val dailies = repo.listDaily(first, today).associateBy { it.date }
    val sleeps = repo.listSleep(first, today).associateBy { it.date }
    val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.mapNotNull { date ->
        val d = dailies[date]
        val s = sleeps[date]?.takeIf { it.durationS != 0 }
        if (d == null && s == null) return@mapNotNull null
        CoachDay(
            date = date.toString(),
            steps = d?.steps,
            stepsGoal = d?.stepsGoal,
            restingHr = d?.restingHr?.takeIf { it != 0 },
            avgStress = d?.avgStress,
            bodyBatteryHigh = d?.bodyBatteryHigh,
            bodyBatteryLow = d?.bodyBatteryLow,
            hrvMs = d?.hrvMs,
            sleepHours = s?.let { round(it.hours, 2) },
            sleepScore = s?.score,
        )
    }.toList()

    val lo = first.atStartOfDay().toInstant(ZoneOffset.UTC)
    val hi = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val activities = repo.listActivities(start = lo, end = hi, limit = 10_000).sortedBy { it.startTime }.takeLast(COACH_ACTIVITIES)

    val latestDaily = dailies.values.maxByOrNull { it.date }
    val history = repo.listDaily(today.minusDays(60), today)
    val readiness = latestDaily?.let { ReadinessCalculator.compute(it, sleeps[it.date], history) }

    return CoachFacts(
        today = today.toString(),
        days = days,
        activities = activities.map {
            WeekActivity(
                name = it.name,
                sport = it.sport.key,
                date = it.startTime.atZone(ZoneOffset.UTC).toLocalDate().toString(),
                distanceKm = round(it.distanceM / 1000, 2),
                durationMin = round(it.durationS / 60, 1),
                avgHr = it.avgHr,
            )
        },
        last7Days = stats.summary(today.minusDays(6), today).toTotals(),
        previous7Days = stats.summary(today.minusDays(13), today.minusDays(7)).toTotals(),
        readiness = readiness?.let {
            ReadinessFacts(it.score, it.level.name.lowercase(), it.strongest.part.name.lowercase(), it.weakest.part.name.lowercase())
        },
    )
}

private const val COACH_DAYS = 28
private const val COACH_ACTIVITIES = 30
