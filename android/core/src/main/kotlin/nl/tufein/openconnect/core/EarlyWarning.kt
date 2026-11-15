package nl.tufein.openconnect.core

import java.time.LocalDate

enum class WarningLevel {
    /** Two odd days in a row on one signal. */
    MILD,

    /** Both signals off, or three odd days in a row. */
    STRONG,
}

/**
 * [days] odd days in a row; [restingHrUp] is how many beats the latest odd day's resting heart rate is above your normal
 * and [hrvDownPct] how many percent its HRV is below it (each null when that day has no value or it is not odd).
 */
data class BodyWarning(val level: WarningLevel, val days: Int, val restingHrUp: Double?, val hrvDownPct: Double?)

/**
 * A heads-up when resting heart rate is up or HRV is down for a few days in a row compared with your own last four weeks, which
 * often goes with getting ill or with too little rest (Oura calls it Symptom Radar, WHOOP the Health Monitor). It is a
 * rule of thumb from two numbers, not a diagnosis and not medical advice.
 */
object EarlyWarning {
    const val BASELINE_DAYS = 28
    const val RECENT_DAYS = 3
    const val MIN_BASELINE = 7

    /** Resting heart rate this many beats above normal, or HRV this share below it, makes a day odd. */
    const val RESTING_HR_UP = 4.0
    const val HRV_DOWN = 0.20

    fun compute(dailies: List<DailySummary>, today: LocalDate): BodyWarning? {
        val recentFrom = today.minusDays((RECENT_DAYS - 1).toLong())
        val baseFrom = recentFrom.minusDays(BASELINE_DAYS.toLong())
        val base = dailies.filter { it.date >= baseFrom && it.date < recentFrom }
        val normalHr = mean(base.mapNotNull { it.restingHr?.takeIf { v -> v > 0 }?.toDouble() })
        val normalHrv = mean(base.mapNotNull { it.hrvMs?.takeIf { v -> v > 0 }?.toDouble() })
        if (normalHr == null && normalHrv == null) return null

        val recent = dailies.filter { it.date in recentFrom..today }.sortedByDescending { it.date }
        var days = 0
        var latestHr: Double? = null
        var latestHrv: Double? = null
        var bothOnLatest = false
        for (d in recent) {
            val up = d.restingHr?.takeIf { it > 0 && normalHr != null }?.let { it - normalHr!! }?.takeIf { it >= RESTING_HR_UP }
            val down = d.hrvMs?.takeIf { it > 0 && normalHrv != null }?.let { 1 - it / normalHrv!! }?.takeIf { it >= HRV_DOWN }
            if (up == null && down == null) break
            if (days == 0) {
                latestHr = up
                latestHrv = down?.times(100)
                bothOnLatest = up != null && down != null
            }
            days++
        }
        if (days < 2) return null
        val level = if (days >= RECENT_DAYS || bothOnLatest) WarningLevel.STRONG else WarningLevel.MILD
        return BodyWarning(level, days, latestHr, latestHrv)
    }

    private fun mean(values: List<Double>): Double? = if (values.size < MIN_BASELINE) null else values.average()
}
