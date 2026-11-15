package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset

enum class ShapeVerdict {
    /** Your recent running matches what the distance asks. */
    READY,

    /** Close: a little more volume or one longer run. */
    ALMOST,

    /** On the way, with a gap to close. */
    BUILDING,

    /** Far from what the distance asks; a prediction for it is a long shot. */
    FAR,
}

/**
 * How well the last six weeks of running fit a race distance, in the spirit of Runalyze's *Marathon Shape*.
 * [volumeShare] and [longRunShare] are your average weekly kilometres and your longest run against what a typical plan for
 * that distance asks ([requiredWeeklyKm], [requiredLongRunKm]), each capped at 1.
 */
data class RaceShape(
    val distance: RaceDistance,
    val weeklyKm: Double,
    val longestRunKm: Double,
    val requiredWeeklyKm: Double,
    val requiredLongRunKm: Double,
    val volumeShare: Double,
    val longRunShare: Double,
) {
    /** 0 to 1: the mean of the two shares. */
    val shape: Double get() = (volumeShare + longRunShare) / 2

    val verdict: ShapeVerdict
        get() = when {
            shape >= 0.9 -> ShapeVerdict.READY
            shape >= 0.7 -> ShapeVerdict.ALMOST
            shape >= 0.4 -> ShapeVerdict.BUILDING
            else -> ShapeVerdict.FAR
        }
}

/** Rules of thumb from common training plans, not science; the tests pin them so they can be tuned. */
object RaceShapeCalculator {
    const val WINDOW_DAYS = 42

    fun requiredWeeklyKm(distance: RaceDistance): Double = when (distance) {
        RaceDistance.K5 -> 20.0
        RaceDistance.K10 -> 30.0
        RaceDistance.HALF -> 40.0
        RaceDistance.MARATHON -> 55.0
    }

    fun requiredLongRunKm(distance: RaceDistance): Double = when (distance) {
        RaceDistance.K5 -> 8.0
        RaceDistance.K10 -> 12.0
        RaceDistance.HALF -> 18.0
        RaceDistance.MARATHON -> 30.0
    }

    /** The shape for [distance] from the runs of the last [WINDOW_DAYS] days up to [today]; null without a run in that time. */
    fun compute(activities: List<Activity>, distance: RaceDistance, today: LocalDate): RaceShape? {
        val from = today.minusDays((WINDOW_DAYS - 1).toLong())
        val runs = activities.filter {
            val day = it.startTime.atZone(ZoneOffset.UTC).toLocalDate()
            it.sport == Sport.RUNNING && !day.isBefore(from) && !day.isAfter(today) && it.distanceM > 0
        }
        if (runs.isEmpty()) return null
        val weeklyKm = runs.sumOf { it.distanceM } / 1000.0 / (WINDOW_DAYS / 7.0)
        val longestKm = runs.maxOf { it.distanceM } / 1000.0
        val needWeekly = requiredWeeklyKm(distance)
        val needLong = requiredLongRunKm(distance)
        return RaceShape(
            distance, weeklyKm, longestKm, needWeekly, needLong,
            volumeShare = minOf(1.0, weeklyKm / needWeekly),
            longRunShare = minOf(1.0, longestKm / needLong),
        )
    }
}
