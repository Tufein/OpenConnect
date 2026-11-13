package nl.tufein.openconnect.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/** The race a plan builds towards. [peakKm] is the weekly distance of the biggest week at three runs a week. */
@Serializable
enum class PlanGoal(val km: Double, val peakKm: Double, val startFloorKm: Double) {
    FIVE_K(5.0, 24.0, 8.0),
    TEN_K(10.0, 38.0, 12.0),
}

/**
 * What the user chose. This is all that is stored: the plan itself is rebuilt from it, always the same way, so a plan
 * never goes stale or needs a migration. [start] must be a Monday (the builder moves it back if it is not).
 */
@Serializable
data class PlanConfig(
    val goal: PlanGoal,
    val runsPerWeek: Int = 3,
    val weeks: Int = 8,
    val start: String,
    val baselineKm: Double = 0.0,
) {
    fun coerced() = copy(
        runsPerWeek = runsPerWeek.coerceIn(2, 4),
        weeks = weeks.coerceIn(6, 12),
        baselineKm = baselineKm.coerceIn(0.0, 200.0),
    )

    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        fun decode(text: String): PlanConfig? = try {
            JSON.decodeFromString(serializer(), text).also { LocalDate.parse(it.start) }.coerced()
        } catch (_: Exception) {
            null
        }
    }
}

enum class SessionType { EASY, LONG, TEMPO, INTERVALS, RACE }

data class PlannedSession(val date: LocalDate, val type: SessionType, val distanceKm: Double)

data class PlanWeek(
    val number: Int,
    val start: LocalDate,
    val volumeKm: Double,
    val recovery: Boolean,
    val race: Boolean,
    val sessions: List<PlannedSession>,
) {
    val end: LocalDate get() = start.plusDays(6)
}

data class TrainingPlan(val config: PlanConfig, val weeks: List<PlanWeek>) {
    val start: LocalDate get() = weeks.first().start
    val raceDate: LocalDate get() = weeks.last().sessions.last().date
}

enum class WeekState { UPCOMING, CURRENT, DONE, BEHIND }

data class WeekProgress(val week: PlanWeek, val doneKm: Double, val doneRuns: Int, val state: WeekState)

data class PlanProgress(
    val weeks: List<WeekProgress>,
    /** The week that holds today, or null before the start and after the race. */
    val current: WeekProgress?,
    /** The first planned run from today on that has not been run yet. */
    val next: PlannedSession?,
    val finished: Boolean,
    val notStarted: Boolean,
)

/**
 * A simple 5 K or 10 K plan from the weekly distance you run now. These are rules of thumb used by many beginner plans,
 * not medical advice: the weekly distance grows by at most 10% a week, every fourth week is lighter, the last week
 * tapers and ends with the race. It does not know your health, injuries or pace.
 */
object TrainingPlans {
    private const val GROWTH = 1.10
    private const val RECOVERY = 0.75
    private const val TAPER = 0.60
    private const val DONE_SHARE = 0.8

    fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun nextMonday(day: LocalDate): LocalDate = day.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    /** The average weekly running distance over the 28 days before [today], in km. */
    fun baselineKm(repo: Repository, today: LocalDate): Double {
        val from = today.minusDays(28).atStartOfDay().toInstant(ZoneOffset.UTC)
        val to = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val metres = repo.listActivities(sport = Sport.RUNNING, start = from, end = to, limit = 500).sumOf { it.distanceM ?: 0.0 }
        return metres / 1000.0 / 4.0
    }

    fun build(input: PlanConfig): TrainingPlan {
        val config = input.coerced()
        val goal = config.goal
        val runs = config.runsPerWeek
        val scale = runs / 3.0
        val peak = goal.peakKm * scale
        val start = minOf(maxOf(config.baselineKm, goal.startFloorKm * scale), peak * 0.85)
        val first = mondayOf(LocalDate.parse(config.start))
        val weeks = ArrayList<PlanWeek>()
        var build = start
        for (n in 1..config.weeks) {
            val monday = first.plusWeeks((n - 1).toLong())
            val race = n == config.weeks
            val recovery = !race && n % 4 == 0 && n != config.weeks - 1
            val volume = when {
                race -> build * TAPER
                recovery -> build * RECOVERY
                else -> { build = if (n == 1) start else minOf(peak, build * GROWTH); build }
            }
            weeks += PlanWeek(n, monday, round(volume), recovery, race, sessions(config, n, monday, volume, race, recovery))
        }
        return TrainingPlan(config, weeks)
    }

    private fun sessions(config: PlanConfig, n: Int, monday: LocalDate, volume: Double, race: Boolean, recovery: Boolean): List<PlannedSession> {
        val runs = config.runsPerWeek
        val days = when (runs) {
            2 -> listOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
            3 -> listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
            else -> listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)
        }.map { monday.with(TemporalAdjusters.nextOrSame(it)) }
        if (race) {
            val easy = maxOf(2.0, (volume - config.goal.km) / (runs - 1))
            return days.dropLast(1).map { PlannedSession(it, SessionType.EASY, round(easy)) } + PlannedSession(days.last(), SessionType.RACE, config.goal.km)
        }
        val quality = if (recovery) SessionType.EASY else if (n % 2 == 1) SessionType.TEMPO else SessionType.INTERVALS
        val longCap = if (config.goal == PlanGoal.FIVE_K) 8.0 else 16.0
        val shape: List<Pair<SessionType, Double>> = when (runs) {
            2 -> listOf(SessionType.EASY to 0.45, SessionType.LONG to 0.55)
            3 -> listOf(SessionType.EASY to 0.30, quality to 0.30, SessionType.LONG to 0.40)
            else -> listOf(SessionType.EASY to 0.20, quality to 0.25, SessionType.EASY to 0.20, SessionType.LONG to 0.35)
        }
        return shape.mapIndexed { i, (type, share) ->
            val km = share * volume
            PlannedSession(days[i], type, round(if (type == SessionType.LONG) minOf(km, longCap) else km))
        }
    }

    /** Distances go in halves of a kilometre, never below 2 km. */
    private fun round(km: Double) = maxOf(2.0, Math.round(km * 2) / 2.0)

    /** How far the plan has got, given the runs recorded (any list; only those inside the plan count). */
    fun progress(plan: TrainingPlan, runs: List<Activity>, today: LocalDate): PlanProgress {
        val mine = runs.filter { it.sport == Sport.RUNNING }.map { it.startTime.day() to (it.distanceM ?: 0.0) }
        val weeks = plan.weeks.map { w ->
            val inWeek = mine.filter { it.first >= w.start && it.first <= w.end }
            val km = inWeek.sumOf { it.second } / 1000.0
            val state = when {
                today < w.start -> WeekState.UPCOMING
                today <= w.end -> WeekState.CURRENT
                km >= DONE_SHARE * w.volumeKm -> WeekState.DONE
                else -> WeekState.BEHIND
            }
            WeekProgress(w, km, inWeek.size, state)
        }
        val ranDays = mine.map { it.first }.toSet()
        val next = plan.weeks.flatMap { it.sessions }.firstOrNull { it.date >= today && it.date !in ranDays }
        return PlanProgress(
            weeks = weeks,
            current = weeks.firstOrNull { it.state == WeekState.CURRENT },
            next = next,
            finished = today > plan.weeks.last().end,
            notStarted = today < plan.start,
        )
    }

    private fun Instant.day(): LocalDate = atZone(ZoneOffset.UTC).toLocalDate()
}
