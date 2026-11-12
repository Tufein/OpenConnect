package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.DailySummary
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * What a watch records over the day, one number at a time. The `STEPS_*`, `DISTANCE_*`, `ACTIVE_KCAL_*`,
 * `ASCENT` and `*_MIN` metrics are counters that the watch adds up from local midnight, so a day's value is
 * the largest reading of that day; the others are plain readings.
 */
enum class Metric(val code: Int) {
    HEART_RATE(1),
    RESTING_HR(2),
    STRESS(3),
    BODY_BATTERY(4),
    RESPIRATION(5),
    SPO2(6),
    HRV(7),
    HRV_LAST_NIGHT(8),
    STEPS_WALKING(20),
    STEPS_RUNNING(21),
    DISTANCE_WALKING(22),
    DISTANCE_RUNNING(23),
    ACTIVE_KCAL_WALKING(24),
    ACTIVE_KCAL_RUNNING(25),
    ASCENT(26),
    MODERATE_MIN(27),
    VIGOROUS_MIN(28),
    ;

    companion object {
        fun fromCode(code: Int): Metric? = entries.firstOrNull { it.code == code }
    }
}

/** One reading. [day] is the calendar day on the watch's own clock, which is when its counters reset. */
data class MetricSample(
    val metric: Metric,
    val time: Instant,
    val day: LocalDate,
    val value: Double,
)

/** Turns one day of watch readings into the daily summary the dashboard shows. */
object DailyAggregator {
    /** Garmin counts a floor as about 3 metres (10 feet) of ascent. */
    private const val METRES_PER_FLOOR = 3.048

    /**
     * Fields without any reading keep the value of [existing], so a partial sync never wipes out what an
     * earlier one (or another source) already knew about the day.
     */
    fun summarize(date: LocalDate, samples: List<MetricSample>, existing: DailySummary? = null): DailySummary {
        val byMetric = samples.filter { it.day == date }.groupBy { it.metric }

        fun values(metric: Metric): List<Double> = byMetric[metric].orEmpty().map { it.value }

        fun counter(vararg metrics: Metric): Double? {
            val maxima = metrics.mapNotNull { values(it).maxOrNull() }
            return if (maxima.isEmpty()) null else maxima.sum()
        }

        val base = existing ?: DailySummary(date = date)
        val steps = counter(Metric.STEPS_WALKING, Metric.STEPS_RUNNING)
        val distance = counter(Metric.DISTANCE_WALKING, Metric.DISTANCE_RUNNING)
        val kcal = counter(Metric.ACTIVE_KCAL_WALKING, Metric.ACTIVE_KCAL_RUNNING)
        val ascent = counter(Metric.ASCENT)
        val moderate = counter(Metric.MODERATE_MIN)
        val vigorous = counter(Metric.VIGOROUS_MIN)
        val hr = values(Metric.HEART_RATE)
        val stress = values(Metric.STRESS)
        val battery = values(Metric.BODY_BATTERY)
        val spo2 = values(Metric.SPO2)
        val resp = values(Metric.RESPIRATION)
        val resting = byMetric[Metric.RESTING_HR].orEmpty().maxByOrNull { it.time }?.value
        val hrv = byMetric[Metric.HRV_LAST_NIGHT].orEmpty().maxByOrNull { it.time }?.value
            ?: values(Metric.HRV).takeIf { it.isNotEmpty() }?.average()

        return base.copy(
            steps = steps?.roundToInt() ?: base.steps,
            distanceM = distance ?: base.distanceM,
            activeKcal = kcal?.roundToInt() ?: base.activeKcal,
            floorsClimbed = ascent?.let { (it / METRES_PER_FLOOR).roundToInt() } ?: base.floorsClimbed,
            moderateIntensityMin = moderate?.roundToInt() ?: base.moderateIntensityMin,
            vigorousIntensityMin = vigorous?.roundToInt() ?: base.vigorousIntensityMin,
            restingHr = resting?.roundToInt() ?: base.restingHr,
            minHr = hr.minOrNull()?.roundToInt() ?: base.minHr,
            maxHr = hr.maxOrNull()?.roundToInt() ?: base.maxHr,
            avgStress = stress.takeIf { it.isNotEmpty() }?.average()?.let { Math.rint(it).toInt() } ?: base.avgStress,
            maxStress = stress.maxOrNull()?.roundToInt() ?: base.maxStress,
            bodyBatteryHigh = battery.maxOrNull()?.roundToInt() ?: base.bodyBatteryHigh,
            bodyBatteryLow = battery.minOrNull()?.roundToInt() ?: base.bodyBatteryLow,
            hrvMs = hrv?.roundToInt() ?: base.hrvMs,
            spo2Avg = spo2.takeIf { it.isNotEmpty() }?.average()?.let { Math.rint(it).toInt() } ?: base.spo2Avg,
            spo2Min = spo2.minOrNull()?.roundToInt() ?: base.spo2Min,
            respirationAvg = resp.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it * 10) / 10.0 } ?: base.respirationAvg,
        )
    }
}
