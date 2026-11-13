package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.math.abs

/** The health charts that get an explanation under them. */
enum class ChartMetric {
    STEPS,
    SLEEP,
    RESTING_HR,
    BODY_BATTERY,
    HRV,
    SPO2,
    RESPIRATION,
    STRESS,
    ;

    /** Whether a rising line is good news; null when neither direction is better (breathing rate). */
    val higherIsBetter: Boolean?
        get() = when (this) {
            STEPS, SLEEP, BODY_BATTERY, HRV, SPO2 -> true
            RESTING_HR, STRESS -> false
            RESPIRATION -> null
        }
}

/** How the period looks against common reference ranges. These are general guides, not medical advice. */
enum class Verdict { GOOD, FAIR, WATCH }

enum class Trend { UP, DOWN, STABLE }

/**
 * What a chart shows in words: the average, the best and worst day, how the last week compares with the week before,
 * and whether the level is in a common range. Computed from the same series that is drawn.
 */
data class ChartInsight(
    val metric: ChartMetric,
    val daysWithData: Int,
    val average: Double,
    val latest: Double,
    val lowest: Double,
    val lowestDate: LocalDate,
    val highest: Double,
    val highestDate: LocalDate,
    val verdict: Verdict,
    /** Last readings against the ones before; null with fewer than 6 readings. */
    val trend: Trend?,
    val changePct: Double?,
    /** True when the trend moved the good way, false the bad way, null when stable or neither way is better. */
    val improving: Boolean?,
    /** Steps only: days that reached the 10,000-step goal. */
    val goalDays: Int?,
) {
    companion object {
        const val STEP_GOAL = 10_000.0
        private const val STABLE_BELOW_PCT = 5.0

        fun of(metric: ChartMetric, series: List<Pair<LocalDate, Double?>>): ChartInsight? {
            val present = series.mapNotNull { (d, v) -> v?.takeIf { !it.isNaN() }?.let { d to it } }
            if (present.isEmpty()) return null
            val values = present.map { it.second }
            val average = values.average()
            val low = present.minBy { it.second }
            val high = present.maxBy { it.second }
            val latest = values.last()
            var trend: Trend? = null
            var change: Double? = null
            if (values.size >= 6) {
                val n = minOf(7, values.size / 2)
                val recent = values.takeLast(n).average()
                val before = values.dropLast(n).takeLast(n).average()
                if (before != 0.0) {
                    val pct = (recent - before) / abs(before) * 100
                    change = pct
                    trend = when {
                        abs(pct) < STABLE_BELOW_PCT -> Trend.STABLE
                        pct > 0 -> Trend.UP
                        else -> Trend.DOWN
                    }
                }
            }
            val improving = when (trend) {
                null, Trend.STABLE -> null
                else -> metric.higherIsBetter?.let { (trend == Trend.UP) == it }
            }
            return ChartInsight(
                metric = metric,
                daysWithData = values.size,
                average = average,
                latest = latest,
                lowest = low.second,
                lowestDate = low.first,
                highest = high.second,
                highestDate = high.first,
                verdict = verdictOf(metric, average, latest),
                trend = trend,
                changePct = change,
                improving = improving,
                goalDays = if (metric == ChartMetric.STEPS) values.count { it >= STEP_GOAL } else null,
            )
        }

        private fun verdictOf(metric: ChartMetric, avg: Double, latest: Double): Verdict = when (metric) {
            ChartMetric.STEPS -> pick(avg >= 10_000, avg >= 7_000)
            ChartMetric.SLEEP -> pick(avg in 7.0..9.0, avg >= 6.0)
            ChartMetric.RESTING_HR -> pick(avg <= 60, avg <= 80)
            ChartMetric.BODY_BATTERY -> pick(avg >= 75, avg >= 50)
            ChartMetric.HRV -> pick(latest >= avg * 0.9, latest >= avg * 0.75)
            ChartMetric.SPO2 -> pick(avg >= 95, avg >= 90)
            ChartMetric.RESPIRATION -> pick(avg in 12.0..20.0, avg in 10.0..25.0)
            ChartMetric.STRESS -> pick(avg <= 25, avg <= 50)
        }

        private fun pick(good: Boolean, fair: Boolean) = if (good) Verdict.GOOD else if (fair) Verdict.FAIR else Verdict.WATCH
    }
}
