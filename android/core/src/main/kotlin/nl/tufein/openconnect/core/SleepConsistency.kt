package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SleepRhythm {
    /** Nights are about equally long: the body can plan for it. */
    STEADY,

    /** Some nights are clearly longer or shorter than others. */
    VARIABLE,

    /** Long and short nights swing a lot, the pattern that feels like jet lag at home. */
    ERRATIC,
}

/**
 * How alike the last nights were in length. [spreadMinutes] is the standard deviation of the night lengths, [averageHours] their
 * mean, [shortestHours] and [longestHours] the two ends.
 */
data class SleepConsistency(
    val averageHours: Double,
    val spreadMinutes: Int,
    val shortestHours: Double,
    val longestHours: Double,
    val nights: Int,
    val rhythm: SleepRhythm,
)

/** Oura and WHOOP both score "sleep consistency"; this one only has night lengths to go on, since bedtimes are not stored. */
object SleepConsistencyCalculator {
    const val WINDOW = 14
    const val MIN_NIGHTS = 7

    /** A spread under [STEADY_BELOW] minutes is steady, under [VARIABLE_BELOW] variable, from there erratic. */
    const val STEADY_BELOW = 30.0
    const val VARIABLE_BELOW = 60.0

    fun compute(sleeps: List<SleepSummary>, today: LocalDate): SleepConsistency? {
        val from = today.minusDays((WINDOW - 1).toLong())
        val hours = sleeps.filter { it.date in from..today && it.durationS > 0 }.map { it.hours }
        if (hours.size < MIN_NIGHTS) return null
        val mean = hours.average()
        val spread = sqrt(hours.sumOf { (it - mean) * (it - mean) } / hours.size) * 60
        val rhythm = when {
            spread < STEADY_BELOW -> SleepRhythm.STEADY
            spread < VARIABLE_BELOW -> SleepRhythm.VARIABLE
            else -> SleepRhythm.ERRATIC
        }
        return SleepConsistency(mean, spread.roundToInt(), hours.min(), hours.max(), hours.size, rhythm)
    }
}
