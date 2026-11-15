package nl.tufein.openconnect.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.exp

/** One run's estimate of VO2max (ml of oxygen per kilo per minute) and the day it was run. */
data class Vo2Estimate(val activityId: String, val date: LocalDate, val vo2max: Double)

/** [current] is the median of the last five estimates of the last eight weeks; [estimates] are those of the last eight weeks, oldest first. */
data class Vo2Summary(val current: Double, val estimates: List<Vo2Estimate>)

/**
 * VO2max worked out from pace and heart rate, like Runalyze's *effective VO2max*, and race times predicted from it with
 * Daniels' and Gilbert's formulas. A run gives an estimate: the oxygen cost of its pace divided by the share of VO2max its
 * heart rate says it took (Swain: heart rate as a share of the maximum is `0.64 * share of VO2max + 0.37`). It is rough,
 * a few ml either way, and ignores heat, hills and drift. It is not Garmin's VO2 max and not medical advice.
 */
object Vo2Max {
    const val WINDOW_DAYS = 56
    const val MIN_ESTIMATES = 2
    private const val MIN_DURATION_S = 15 * 60
    private const val MIN_DISTANCE_M = 2500.0
    private const val MIN_HR_SHARE = 0.62
    private const val MAX_HR_SHARE = 0.95

    /** Oxygen cost of running at [metersPerMin] (Daniels and Gilbert). */
    fun oxygenCost(metersPerMin: Double): Double = -4.60 + 0.182258 * metersPerMin + 0.000104 * metersPerMin * metersPerMin

    /** The share of VO2max a runner can hold for [minutes] (Daniels and Gilbert): about 80 percent for hours, more for short efforts. */
    fun fractionForDuration(minutes: Double): Double = 0.8 + 0.1894393 * exp(-0.012778 * minutes) + 0.2989558 * exp(-0.1932605 * minutes)

    /** The VO2max a race result stands for (Daniels' VDOT). */
    fun fromRaceEffort(distanceM: Double, seconds: Double): Double {
        val minutes = seconds / 60.0
        return oxygenCost(distanceM / minutes) / fractionForDuration(minutes)
    }

    /** The time a runner with [vo2max] needs for [distanceM], found by halving the interval until it fits. */
    fun predictSeconds(vo2max: Double, distanceM: Double): Double {
        var lo = 3 * 60.0 // faster than this is no human pace for any distance we use
        var hi = 12 * 3600.0
        repeat(80) {
            val mid = (lo + hi) / 2
            // Slower means a lower VO2max, so if the effort of this time asks for more than we have, we need more time.
            if (fromRaceEffort(distanceM, mid) > vo2max) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /** An estimate from one run of 15 minutes and 2.5 km or more with a heart rate between 62 and 95 percent of [maxHr]. */
    fun estimateFromRun(a: Activity, maxHr: Int): Vo2Estimate? {
        val avg = a.avgHr
        if (a.sport != Sport.RUNNING || avg == null || maxHr <= 0) return null
        if (a.durationS < MIN_DURATION_S || a.distanceM < MIN_DISTANCE_M) return null
        val hrShare = avg.toDouble() / maxHr
        if (hrShare < MIN_HR_SHARE || hrShare > MAX_HR_SHARE) return null
        val metersPerMin = a.distanceM / (a.durationS / 60.0)
        val vo2Share = (hrShare - 0.37) / 0.64
        val date = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()
        return Vo2Estimate(a.id, date, oxygenCost(metersPerMin) / vo2Share)
    }

    fun summary(activities: List<Activity>, maxHr: Int, today: LocalDate): Vo2Summary? {
        val from = today.minusDays((WINDOW_DAYS - 1).toLong())
        val estimates = activities.mapNotNull { estimateFromRun(it, maxHr) }
            .filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
            .sortedBy { it.date }
        if (estimates.size < MIN_ESTIMATES) return null
        val recent = estimates.takeLast(5).map { it.vo2max }.sorted()
        val median = if (recent.size % 2 == 1) recent[recent.size / 2] else (recent[recent.size / 2 - 1] + recent[recent.size / 2]) / 2
        return Vo2Summary(median, estimates)
    }
}
