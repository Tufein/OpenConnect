package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.math.min

enum class SeasonPhase {
    /** Easy volume: runs at an easy pace, time on your feet. */
    BASE,

    /** More volume and the first harder sessions: tempo, hills, intervals. */
    BUILD,

    /** The biggest weeks and race-specific work. */
    PEAK,

    /** Less volume, a little speed, fresh legs for the day. */
    TAPER,
}

/** [weeks] weeks starting on [start]. */
data class SeasonBlock(val phase: SeasonPhase, val start: LocalDate, val weeks: Int) {
    val end: LocalDate get() = start.plusDays(weeks * 7L - 1)
}

/** The phases from now to the race. [current] is the block that holds today, null before the plan starts. */
data class SeasonPlan(val goal: RaceGoal, val weeksToRace: Int, val blocks: List<SeasonBlock>, val current: SeasonBlock?)

/**
 * The classic way a season is cut up (Intervals.icu and TrainingPeaks call it periodisation), worked out backwards from the race:
 * a taper at the end, a peak before it, a build before that, and base for all the weeks before. The lengths of taper, peak and build
 * depend on the distance and are counted from the race, so a phase begins on the same week however often the plan is worked out. The
 * weeks are counted from today, a part of a week counts as a week, and a race more than a year away only plans the last 52 weeks.
 * When there are too few weeks the phases at the front fall away first. A rule of thumb, not a coach.
 */
object SeasonPlanner {
    const val MAX_WEEKS = 52
    fun taperWeeks(distance: RaceDistance) = when (distance) {
        RaceDistance.K5, RaceDistance.K10 -> 1
        RaceDistance.HALF -> 2
        RaceDistance.MARATHON -> 3
    }

    fun peakWeeks(distance: RaceDistance) = when (distance) {
        RaceDistance.K5, RaceDistance.K10 -> 2
        RaceDistance.HALF, RaceDistance.MARATHON -> 3
    }

    fun buildWeeks(distance: RaceDistance) = when (distance) {
        RaceDistance.K5, RaceDistance.K10 -> 3
        RaceDistance.HALF, RaceDistance.MARATHON -> 4
    }

    fun plan(goal: RaceGoal, today: LocalDate): SeasonPlan? {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, goal.date)
        if (days <= 0) return null
        val weeksToRace = ((days + 6) / 7).toInt()
        val total = min(weeksToRace, MAX_WEEKS)

        val taper = min(taperWeeks(goal.distance), total)
        val peak = min(peakWeeks(goal.distance), total - taper)
        val build = min(buildWeeks(goal.distance), total - taper - peak)
        val base = total - taper - peak - build

        var start = today.plusWeeks((weeksToRace - total).toLong())
        val blocks = ArrayList<SeasonBlock>()
        for ((phase, weeks) in listOf(SeasonPhase.BASE to base, SeasonPhase.BUILD to build, SeasonPhase.PEAK to peak, SeasonPhase.TAPER to taper)) {
            if (weeks <= 0) continue
            blocks += SeasonBlock(phase, start, weeks)
            start = start.plusWeeks(weeks.toLong())
        }
        val current = blocks.firstOrNull { today >= it.start && today <= it.end }
        return SeasonPlan(goal, weeksToRace, blocks, current)
    }

    /** A range of weekly kilometres for [phase] around [usualWeeklyKm], to the nearest half kilometre; null when there is no usual volume. */
    fun weeklyKm(phase: SeasonPhase, usualWeeklyKm: Double?): Pair<Double, Double>? {
        if (usualWeeklyKm == null || usualWeeklyKm <= 0.0) return null
        val (low, high) = when (phase) {
            SeasonPhase.BASE -> 0.95 to 1.05
            SeasonPhase.BUILD -> 1.05 to 1.20
            SeasonPhase.PEAK -> 1.20 to 1.30
            SeasonPhase.TAPER -> 0.50 to 0.70
        }
        fun half(x: Double) = Math.round(x * 2) / 2.0
        return half(usualWeeklyKm * low) to half(usualWeeklyKm * high)
    }
}
