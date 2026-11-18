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
    READINESS,
    SLEEP_SCORE,

    /** The Oura Ring's own activity score (v0.44.0; only the ring has one). */
    ACTIVITY_SCORE,

    /** The lowest heart rate of the night as the ring measures it; not the same as the watch's resting heart rate. */
    LOWEST_HR,

    /** How far the skin was from its normal temperature during the night, in degrees Celsius. */
    TEMPERATURE,

    /** The WHOOP band's day strain on its 0 to 21 scale (v0.49.0): how hard the day was, which is neither good nor bad. */
    STRAIN,
    ;

    /** Whether a rising line is good news; null when neither direction is better (breathing rate). */
    val higherIsBetter: Boolean?
        get() = when (this) {
            STEPS, SLEEP, BODY_BATTERY, HRV, SPO2, READINESS, SLEEP_SCORE, ACTIVITY_SCORE -> true
            RESTING_HR, STRESS, LOWEST_HR -> false
            RESPIRATION, TEMPERATURE, STRAIN -> null
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
            // A temperature difference sits around zero, so a percentage of it says nothing: no trend for it.
            if (values.size >= 6 && metric != ChartMetric.TEMPERATURE) {
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
            ChartMetric.READINESS -> pick(avg >= 70, avg >= 40)
            ChartMetric.SLEEP_SCORE -> pick(avg >= 80, avg >= 60)
            ChartMetric.ACTIVITY_SCORE -> pick(avg >= 70, avg >= 50)
            ChartMetric.LOWEST_HR -> pick(avg <= 55, avg <= 70)
            ChartMetric.TEMPERATURE -> pick(abs(avg) <= 0.5, abs(avg) <= 1.0)
            // WHOOP's own bands: up to 9 light, 10 to 13 moderate, 14 to 17 high, 18 and over all out. A week that sits in the first three is balanced.
            ChartMetric.STRAIN -> pick(avg in 6.0..17.0, avg in 4.0..18.5)
        }

        /**
         * How one day looks, with the same limits as the period average. Heart rate variability is personal, so it is held against
         * [usual] (the person's own average of the days before) and gets no verdict without one.
         */
        fun verdictOfDay(metric: ChartMetric, value: Double, usual: Double?): Verdict? = when (metric) {
            ChartMetric.HRV -> usual?.let { verdictOf(metric, it, value) }
            // One hard day is not a bad day: strain is only judged over a period.
            ChartMetric.STRAIN -> null
            else -> verdictOf(metric, value, value)
        }

        private fun pick(good: Boolean, fair: Boolean) = if (good) Verdict.GOOD else if (fair) Verdict.FAIR else Verdict.WATCH
    }
}
