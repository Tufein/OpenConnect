package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** What a trophy counts in, so the screen can write "4.9 of 5 km". */
enum class TrophyUnit { KM, METRES, DAYS, STEPS, SPORTS, MINUTES, ACTIVITIES, WEEKS }

/** Every trophy there is, in the order the screen shows them, with the target it is won at. */
enum class TrophyKey(val target: Double, val unit: TrophyUnit) {
    FIRST_ACTIVITY(1.0, TrophyUnit.ACTIVITIES),
    RUN_5K(5.0, TrophyUnit.KM),
    RUN_10K(10.0, TrophyUnit.KM),
    RUN_HALF(21.0975, TrophyUnit.KM),
    RIDE_50K(50.0, TrophyUnit.KM),
    DISTANCE_100(100.0, TrophyUnit.KM),
    DISTANCE_500(500.0, TrophyUnit.KM),
    DISTANCE_1000(1000.0, TrophyUnit.KM),
    CLIMB_EVEREST(8849.0, TrophyUnit.METRES),
    STEP_STREAK_7(7.0, TrophyUnit.DAYS),
    STEP_STREAK_30(30.0, TrophyUnit.DAYS),
    STEPS_MILLION(1_000_000.0, TrophyUnit.STEPS),
    SLEEP_STREAK_7(7.0, TrophyUnit.DAYS),
    VARIETY_3(3.0, TrophyUnit.SPORTS),
    WEEK_150(150.0, TrophyUnit.MINUTES),
    EARLY_BIRD(5.0, TrophyUnit.ACTIVITIES),
    CONSISTENT_4(4.0, TrophyUnit.WEEKS),
}

/**
 * [current] is how far along the person is, in the key's unit (it can pass the target); [unlockedOn] is the day the
 * target was first reached, when it was.
 */
data class Trophy(val key: TrophyKey, val current: Double, val unlockedOn: LocalDate?) {
    val target: Double get() = key.target
    val unlocked: Boolean get() = current >= key.target
    val progress: Double get() = if (key.target <= 0) 1.0 else minOf(1.0, current / key.target)
}

/** Level 1 starts at 0 xp, level 2 at 100, level 3 at 400, level 4 at 900: each level is longer than the last. */
data class PlayerLevel(val level: Int, val xp: Int, val xpIntoLevel: Int, val xpForNext: Int) {
    companion object {
        fun of(xp: Int): PlayerLevel {
            var level = 1
            while (xp >= 100 * level * level) level++
            val start = 100 * (level - 1) * (level - 1)
            return PlayerLevel(level, xp, xp - start, 100 * (2 * level - 1))
        }
    }
}

data class Achievements(val trophies: List<Trophy>, val player: PlayerLevel)

/**
 * Works out the trophies and the level from what is stored. Nothing is kept: it is recomputed whenever the screen
 * opens, so deleting or importing data always shows the truth. Days are the phone's own clock ([zone]).
 */
object AchievementsCalculator {
    private const val DEFAULT_STEP_GOAL = 10_000
    private val FIRST_DAY = LocalDate.of(1970, 1, 1)
    private val LAST_DAY = LocalDate.of(2100, 12, 31)
    private const val EARLY_MINUTE_OF_DAY = 6 * 60 + 30

    fun compute(repo: Repository, zone: ZoneId): Achievements {
        val activities = repo.listActivities(limit = 100_000).sortedBy { it.startTime }
        val dailies = repo.listDaily(FIRST_DAY, LAST_DAY).sortedBy { it.date }
        val sleeps = repo.listSleep(FIRST_DAY, LAST_DAY).sortedBy { it.date }
        fun Activity.day() = startTime.atZone(zone).toLocalDate()

        val found = LinkedHashMap<TrophyKey, Trophy>()
        fun put(key: TrophyKey, current: Double, on: LocalDate?) {
            found[key] = Trophy(key, current, if (current >= key.target) on else null)
        }

        put(TrophyKey.FIRST_ACTIVITY, activities.size.toDouble(), activities.firstOrNull()?.day())

        fun best(key: TrophyKey, sport: Sport) {
            val ofSport = activities.filter { it.sport == sport }
            put(
                key,
                (ofSport.maxOfOrNull { it.distanceM } ?: 0.0) / 1000,
                ofSport.firstOrNull { it.distanceM / 1000 >= key.target }?.day(),
            )
        }
        best(TrophyKey.RUN_5K, Sport.RUNNING)
        best(TrophyKey.RUN_10K, Sport.RUNNING)
        best(TrophyKey.RUN_HALF, Sport.RUNNING)
        best(TrophyKey.RIDE_50K, Sport.CYCLING)

        val totalKm = activities.sumOf { it.distanceM } / 1000
        for (key in listOf(TrophyKey.DISTANCE_100, TrophyKey.DISTANCE_500, TrophyKey.DISTANCE_1000)) {
            put(key, totalKm, reached(activities.map { it.day() to it.distanceM / 1000 }, key.target))
        }
        put(
            TrophyKey.CLIMB_EVEREST,
            activities.sumOf { it.elevationGainM },
            reached(activities.map { it.day() to it.elevationGainM }, TrophyKey.CLIMB_EVEREST.target),
        )

        val goodStepDays = dailies.map { it.date to (it.steps >= (it.stepsGoal ?: DEFAULT_STEP_GOAL)) }
        for (key in listOf(TrophyKey.STEP_STREAK_7, TrophyKey.STEP_STREAK_30)) {
            val streak = longestStreak(goodStepDays, key.target.toInt())
            put(key, streak.longest.toDouble(), streak.reachedOn)
        }
        val totalSteps = dailies.sumOf { it.steps }.toDouble()
        put(TrophyKey.STEPS_MILLION, totalSteps, reached(dailies.map { it.date to it.steps.toDouble() }, TrophyKey.STEPS_MILLION.target))

        val goodNights = sleeps.filter { it.durationS > 0 }.map { it.date to (it.durationS >= 7 * 3600) }
        val sleepStreak = longestStreak(goodNights, TrophyKey.SLEEP_STREAK_7.target.toInt())
        put(TrophyKey.SLEEP_STREAK_7, sleepStreak.longest.toDouble(), sleepStreak.reachedOn)

        val seen = LinkedHashSet<Sport>()
        var varietyOn: LocalDate? = null
        for (a in activities) {
            if (seen.add(a.sport) && seen.size == TrophyKey.VARIETY_3.target.toInt()) varietyOn = a.day()
        }
        put(TrophyKey.VARIETY_3, seen.size.toDouble(), varietyOn)

        val weeks = dailies.groupBy { weekStart(it.date) }
        var bestWeek = 0
        var weekOn: LocalDate? = null
        for ((_, days) in weeks) {
            var running = 0
            for (d in days) {
                running += d.intensityMinutes
                if (running >= TrophyKey.WEEK_150.target && (weekOn == null || d.date < weekOn)) weekOn = d.date
            }
            bestWeek = maxOf(bestWeek, running)
        }
        put(TrophyKey.WEEK_150, bestWeek.toDouble(), weekOn)

        val early = activities.filter {
            val time = it.startTime.atZone(zone).toLocalTime()
            time.hour * 60 + time.minute < EARLY_MINUTE_OF_DAY
        }
        put(TrophyKey.EARLY_BIRD, early.size.toDouble(), early.getOrNull(TrophyKey.EARLY_BIRD.target.toInt() - 1)?.day())

        val byWeek = activities.groupBy { weekStart(it.day()) }.toSortedMap()
        var weekStreak = 0
        var longestWeeks = 0
        var consistentOn: LocalDate? = null
        var previousWeek: LocalDate? = null
        for ((monday, list) in byWeek) {
            if (list.size < 3) {
                weekStreak = 0
                previousWeek = null
                continue
            }
            weekStreak = if (previousWeek != null && previousWeek.plusDays(7) == monday) weekStreak + 1 else 1
            previousWeek = monday
            longestWeeks = maxOf(longestWeeks, weekStreak)
            if (weekStreak == TrophyKey.CONSISTENT_4.target.toInt() && consistentOn == null) consistentOn = list[2].day()
        }
        put(TrophyKey.CONSISTENT_4, longestWeeks.toDouble(), consistentOn)

        val trophies = TrophyKey.entries.map { found.getValue(it) }
        val xp = (totalKm * 10 + totalSteps / 500.0 + activities.size * 5 + trophies.count { it.unlocked } * 100).roundToInt()
        return Achievements(trophies, PlayerLevel.of(xp))
    }

    /** The first day the running total of [values] (in the order given) reaches [target]. */
    private fun reached(values: List<Pair<LocalDate, Double>>, target: Double): LocalDate? {
        var sum = 0.0
        for ((day, value) in values) {
            sum += value
            if (sum >= target) return day
        }
        return null
    }

    private class Streak(val longest: Int, val reachedOn: LocalDate?)

    /** The longest run of consecutive days that are "good" (a day with no data breaks it), and when it reached [target]. */
    private fun longestStreak(days: List<Pair<LocalDate, Boolean>>, target: Int): Streak {
        var current = 0
        var longest = 0
        var reachedOn: LocalDate? = null
        var previous: LocalDate? = null
        for ((day, good) in days) {
            current = when {
                !good -> 0
                previous != null && previous.plusDays(1) == day -> current + 1
                else -> 1
            }
            previous = day
            longest = maxOf(longest, current)
            if (current >= target && reachedOn == null) reachedOn = day
        }
        return Streak(longest, reachedOn)
    }
}
