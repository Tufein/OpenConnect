package nl.tufein.openconnect.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The signals the readiness estimate is made of, with their weight in percent. */
enum class ReadinessPart(val weight: Int) {
    SLEEP(35),
    BODY_BATTERY(25),
    HRV(15),
    RESTING_HR(15),
    STRESS(10),
}

enum class ReadinessLevel {
    LOW,
    MODERATE,
    GOOD,
    EXCELLENT,
    ;

    companion object {
        fun of(score: Int): ReadinessLevel = when {
            score < 40 -> LOW
            score < 70 -> MODERATE
            score < 85 -> GOOD
            else -> EXCELLENT
        }
    }
}

data class ReadinessComponent(val part: ReadinessPart, val score: Int)

/**
 * How ready the body looks for the day, 0 to 100. This is OpenConnect's own estimate from numbers the watch reports:
 * it is not Garmin's Training Readiness and not medical advice.
 */
data class Readiness(val score: Int, val level: ReadinessLevel, val components: List<ReadinessComponent>) {
    val strongest: ReadinessComponent get() = components.maxBy { it.score }
    val weakest: ReadinessComponent get() = components.minBy { it.score }
}

object ReadinessCalculator {
    /** How far back "your normal" reaches, and how many days of values it takes before it means anything. */
    const val BASELINE_DAYS = 28
    const val MIN_BASELINE_DAYS = 5

    private fun clamp(value: Double) = max(0.0, min(100.0, value)).roundToInt()

    /**
     * [today] is the day to judge, [sleep] the night that ended on it, [history] earlier days for the baseline
     * (anything on or after today, or more than [BASELINE_DAYS] back, is left out). Returns null when fewer than two
     * signals are known, because one number says too little.
     */
    fun compute(today: DailySummary?, sleep: SleepSummary?, history: List<DailySummary>): Readiness? {
        val parts = ArrayList<ReadinessComponent>()

        if (sleep != null && sleep.durationS > 0) {
            val score = sleep.score?.toDouble() ?: (sleep.hours / 8.0 * 100)
            parts.add(ReadinessComponent(ReadinessPart.SLEEP, clamp(score)))
        }
        if (today != null) {
            today.bodyBatteryHigh?.let { parts.add(ReadinessComponent(ReadinessPart.BODY_BATTERY, clamp(it.toDouble()))) }

            val earlier = history.filter { it.date < today.date && it.date >= today.date.minusDays(BASELINE_DAYS.toLong()) }
            baseline(earlier.mapNotNull { it.hrvMs })?.let { normal ->
                val hrv = today.hrvMs
                if (hrv != null && hrv > 0) {
                    val ratio = hrv / normal
                    val score = if (ratio >= 1) 80 + min(20.0, (ratio - 1) * 100) else 80 - (1 - ratio) * 200
                    parts.add(ReadinessComponent(ReadinessPart.HRV, clamp(score)))
                }
            }
            baseline(earlier.mapNotNull { it.restingHr })?.let { normal ->
                val resting = today.restingHr
                if (resting != null && resting > 0) {
                    parts.add(ReadinessComponent(ReadinessPart.RESTING_HR, clamp(90 - (resting - normal) * 10)))
                }
            }
            today.avgStress?.let { parts.add(ReadinessComponent(ReadinessPart.STRESS, clamp(100 - it * 1.25))) }
        }

        if (parts.size < 2) return null
        val totalWeight = parts.sumOf { it.part.weight }
        val score = (parts.sumOf { it.score * it.part.weight }.toDouble() / totalWeight).roundToInt()
        return Readiness(score, ReadinessLevel.of(score), parts)
    }

    private fun baseline(values: List<Int>): Double? {
        val usable = values.filter { it > 0 }
        return if (usable.size < MIN_BASELINE_DAYS) null else usable.average()
    }
}
