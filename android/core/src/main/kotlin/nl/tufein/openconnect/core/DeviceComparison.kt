package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.math.abs

/** Whether the devices' numbers for a measure lie close together or far apart. */
enum class Agreement { CLOSE, APART }

/** What one device measured. */
data class DeviceValue(val source: InsightSource, val value: Double)

/** One measure of one day as every device that has it saw it: [spread] is the largest gap between two devices. */
data class ComparedMeasure(val metric: ChartMetric, val values: List<DeviceValue>, val spread: Double, val agreement: Agreement)

/**
 * The same day as each of the person's devices measured it, side by side (v0.51.0). With a watch, a ring and a band the
 * vendors' apps each show only their own number; this shows the numbers next to each other and says whether the gap is small.
 *
 * Only measures that every device counts the same way are compared: time asleep, steps, resting heart rate and blood
 * oxygen. HRV, readiness, sleep scores and breathing are each maker's own recipe, so a gap there says nothing. The limits
 * below are rules of thumb pinned by tests, not a medical judgement.
 */
object DeviceComparison {
    val METRICS = listOf(ChartMetric.SLEEP, ChartMetric.STEPS, ChartMetric.RESTING_HR, ChartMetric.SPO2)

    /** Hours of sleep that two devices may differ by and still be "close". */
    const val SLEEP_HOURS = 0.5

    /** Beats a minute. */
    const val RESTING_HR_BPM = 3.0

    /** Percentage points of blood oxygen. */
    const val SPO2_POINTS = 2.0

    /** Fraction of the larger step count. */
    const val STEPS_FRACTION = 0.10

    private const val EPS = 1e-9

    private fun close(metric: ChartMetric, spread: Double, largest: Double): Boolean = spread <= EPS + when (metric) {
        ChartMetric.SLEEP -> SLEEP_HOURS
        ChartMetric.RESTING_HR -> RESTING_HR_BPM
        ChartMetric.SPO2 -> SPO2_POINTS
        ChartMetric.STEPS -> largest * STEPS_FRACTION
        else -> 0.0
    }

    /** The measures of [day] that at least two of [sets] have, in the order of [METRICS]. */
    fun of(day: LocalDate, sets: List<DaySeries>): List<ComparedMeasure> = METRICS.mapNotNull { metric ->
        val values = sets.mapNotNull { s ->
            s.value(metric, day)?.takeIf { !it.isNaN() && it > 0 }?.let { DeviceValue(s.source, it) }
        }
        if (values.size < 2) return@mapNotNull null
        val high = values.maxOf { it.value }
        val spread = high - values.minOf { it.value }
        ComparedMeasure(metric, values, abs(spread), if (close(metric, spread, high)) Agreement.CLOSE else Agreement.APART)
    }
}
