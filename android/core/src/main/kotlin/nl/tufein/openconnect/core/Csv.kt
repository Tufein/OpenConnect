package nl.tufein.openconnect.core

import java.util.Locale

/**
 * CSV files of the person's own data, to open in a spreadsheet or keep as a backup of the numbers. Lines end with
 * CRLF and decimals use a dot, whatever language the phone is set to, so every program reads them the same way.
 * Names that start like a spreadsheet formula get a leading apostrophe so a spreadsheet never runs them.
 */
object Csv {
    private const val EOL = "\r\n"

    private val DAILY_HEADER = listOf(
        "date", "steps", "steps_goal", "distance_m", "active_kcal", "floors", "moderate_min", "vigorous_min",
        "resting_hr", "min_hr", "max_hr", "avg_stress", "max_stress", "body_battery_high", "body_battery_low",
        "hrv_ms", "spo2_avg", "spo2_min", "respiration_avg", "sleep_hours", "sleep_deep_hours", "sleep_light_hours",
        "sleep_rem_hours", "sleep_awake_hours", "sleep_score",
    )

    private val ACTIVITY_HEADER = listOf(
        "id", "source", "name", "sport", "start_utc", "duration_s", "distance_m", "elevation_gain_m", "avg_hr",
        "max_hr", "calories", "device",
    )

    /** One row per date that has a day summary or a night of sleep, oldest first. */
    fun dailies(dailies: List<DailySummary>, sleeps: List<SleepSummary>): String {
        val byDay = dailies.associateBy { it.date }
        val bySleep = sleeps.associateBy { it.date }
        val out = StringBuilder(DAILY_HEADER.joinToString(",")).append(EOL)
        for (date in (byDay.keys + bySleep.keys).sorted()) {
            val d = byDay[date]
            val s = bySleep[date]
            fun hours(seconds: Int?) = seconds?.let { String.format(Locale.ROOT, "%.2f", it / 3600.0) }
            val cells = listOf(
                date.toString(), d?.steps, d?.stepsGoal, d?.distanceM, d?.activeKcal, d?.floorsClimbed,
                d?.moderateIntensityMin, d?.vigorousIntensityMin, d?.restingHr, d?.minHr, d?.maxHr, d?.avgStress,
                d?.maxStress, d?.bodyBatteryHigh, d?.bodyBatteryLow, d?.hrvMs, d?.spo2Avg, d?.spo2Min,
                d?.respirationAvg, hours(s?.durationS), hours(s?.deepS), hours(s?.lightS), hours(s?.remS),
                hours(s?.awakeS), s?.score,
            )
            out.append(cells.joinToString(",") { cell(it) }).append(EOL)
        }
        return out.toString()
    }

    /** One row per activity in the order given; the track itself is not included (use the FIT, GPX or TCX export). */
    fun activities(activities: List<Activity>): String {
        val out = StringBuilder(ACTIVITY_HEADER.joinToString(",")).append(EOL)
        for (a in activities) {
            val cells = listOf(
                a.id, a.source, a.name, a.sport.key, a.startTime.toString(), a.durationS, a.distanceM, a.elevationGainM,
                a.avgHr, a.maxHr, a.calories, a.deviceName,
            )
            out.append(cells.joinToString(",") { cell(it) }).append(EOL)
        }
        return out.toString()
    }

    private fun cell(value: Any?): String {
        if (value == null) return ""
        if (value !is String) return value.toString()
        var text = value
        if (text.isNotEmpty() && text[0] in FORMULA_STARTS) text = "'$text"
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
