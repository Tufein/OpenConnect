package nl.tufein.openconnect.core

import java.time.LocalDate

enum class HrvStatusLevel {
    /** Your heart-rate variability is clearly below your own normal: often fatigue, stress or illness coming on. */
    LOW,

    /** Within a tenth of your normal either way. */
    BALANCED,

    /** Clearly above your normal. */
    HIGH,
}

/** [recentMs] is the average of the last week, [normalMs] the average of the four weeks before it, both in milliseconds. */
data class HrvStatus(val recentMs: Double, val normalMs: Double, val level: HrvStatusLevel) {
    /** How far the last week sits from the normal, in percent. */
    val changePct: Double
        get() = if (normalMs <= 0.0) 0.0 else (recentMs - normalMs) / normalMs * 100.0
}

/**
 * Heart-rate variability is personal: a value only means something against your own baseline. This compares the last
 * seven nights with the four weeks before them, exactly as the resting-heart-rate trend does. It is OpenConnect's own
 * reading, not Garmin's "HRV Status" (which Garmin does not expose).
 */
object HrvStatusCalculator {
    const val RECENT_DAYS = 7
    const val NORMAL_DAYS = 28
    const val MIN_RECENT = 4
    const val MIN_NORMAL = 10

    /** A tenth below or above the normal counts as a different status. */
    const val LOW_RATIO = 0.90
    const val HIGH_RATIO = 1.10

    fun compute(dailies: List<DailySummary>, today: LocalDate): HrvStatus? {
        val recentFrom = today.minusDays((RECENT_DAYS - 1).toLong())
        val normalFrom = recentFrom.minusDays(NORMAL_DAYS.toLong())
        fun values(from: LocalDate, to: LocalDate) =
            dailies.filter { it.date in from..to }.mapNotNull { it.hrvMs?.takeIf { v -> v > 0 }?.toDouble() }

        val recent = values(recentFrom, today)
        val normal = values(normalFrom, recentFrom.minusDays(1))
        if (recent.size < MIN_RECENT || normal.size < MIN_NORMAL) return null
        val r = recent.average()
        val n = normal.average()
        if (n <= 0.0) return null
        val ratio = r / n
        val level = when {
            ratio < LOW_RATIO -> HrvStatusLevel.LOW
            ratio > HIGH_RATIO -> HrvStatusLevel.HIGH
            else -> HrvStatusLevel.BALANCED
        }
        return HrvStatus(r, n, level)
    }
}
