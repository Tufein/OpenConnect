package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.formats.FitReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Everything one watch file (Monitor, Sleep, HSA, ...) says, in our terms. */
class WatchFileData(
    val samples: List<MetricSample>,
    /** The night this file describes, when it is a sleep file. */
    val sleep: SleepSummary?,
    /** The watch's offset from UTC in seconds when the file states it, otherwise null (the fallback zone was used). */
    val utcOffsetSeconds: Int?,
)

/**
 * Reads the all-day health files of a Garmin watch (`GARMIN/Monitor`, `Sleep`, `HSA`).
 *
 * Message numbers, field numbers, scales and units are those of Garmin's public FIT profile (SDK 21.221);
 * the test files were written with Garmin's own encoder. Messages this reader does not know are skipped,
 * so newer firmware that adds messages does not break it.
 */
object WatchFit {
    // global message numbers
    private const val MONITORING = 55
    private const val MONITORING_INFO = 103
    private const val MONITORING_HR_DATA = 211
    private const val STRESS_LEVEL = 227
    private const val SPO2_DATA = 269
    private const val SLEEP_LEVEL = 275
    private const val RESPIRATION_RATE = 297
    private const val HSA_STRESS_DATA = 306
    private const val HSA_HEART_RATE_DATA = 308
    private const val HSA_BODY_BATTERY_DATA = 314
    private const val SLEEP_ASSESSMENT = 346
    private const val HRV_STATUS_SUMMARY = 370
    private const val HRV_VALUE = 371

    private const val TS = FitReader.FIELD_TIMESTAMP

    private val WANTED: Map<Int, Set<Int>> = mapOf(
        MONITORING_INFO to setOf(TS, 0),
        // distance, cycles, activity_type, local_timestamp, active_calories, heart_rate, ascent, moderate/vigorous minutes
        MONITORING to setOf(2, 3, 5, 11, 19, 27, 31, 33, 34),
        MONITORING_HR_DATA to setOf(0, 1),
        STRESS_LEVEL to setOf(0, 1),
        SPO2_DATA to setOf(0),
        SLEEP_LEVEL to setOf(0),
        RESPIRATION_RATE to setOf(0),
        HSA_STRESS_DATA to setOf(0, 1),
        HSA_HEART_RATE_DATA to setOf(0, 2),
        HSA_BODY_BATTERY_DATA to setOf(0, 1),
        SLEEP_ASSESSMENT to setOf(6),
        HRV_STATUS_SUMMARY to setOf(1),
        HRV_VALUE to setOf(0),
    )

    // monitoring.activity_type values
    private const val ACTIVITY_RUNNING = 1L
    private const val ACTIVITY_WALKING = 6L

    // sleep_level values
    private const val SLEEP_AWAKE = 1L
    private const val SLEEP_LIGHT = 2L
    private const val SLEEP_DEEP = 3L
    private const val SLEEP_REM = 4L

    private class Raw(val metric: Metric, val ts: Long, val value: Double)

    private fun valid(metric: Metric, value: Double): Boolean = when (metric) {
        Metric.HEART_RATE, Metric.RESTING_HR -> value in 1.0..250.0
        Metric.STRESS, Metric.BODY_BATTERY -> value in 0.0..100.0
        Metric.SPO2 -> value in 1.0..100.0
        Metric.RESPIRATION, Metric.HRV, Metric.HRV_LAST_NIGHT -> value > 0.0
        else -> value >= 0.0
    }

    fun parse(data: ByteArray, fallbackZone: ZoneId = ZoneOffset.UTC): WatchFileData {
        val raws = ArrayList<Raw>()
        val sleepLevels = ArrayList<Pair<Long, Long>>()
        var sleepScore: Int? = null
        var offset: Int? = null

        fun add(metric: Metric, ts: Long?, value: Double?) {
            if (ts == null || ts < FitReader.MIN_DATE_TIME || value == null || !valid(metric, value)) return
            raws.add(Raw(metric, ts, value))
        }

        /** Spread an array field over its processing interval: element i happened i intervals after the timestamp. */
        fun addArray(metric: Metric, ts: Long?, interval: Long?, values: LongArray?) {
            if (ts == null || interval == null || interval <= 0 || values == null) return
            values.forEachIndexed { i, v ->
                if (v != FitReader.INVALID) add(metric, ts + i * interval, v.toDouble())
            }
        }

        FitReader.read(data, wanted = { WANTED[it] }) { global, v ->
            val ts = v[TS]
            when (global) {
                MONITORING_INFO -> {
                    val local = v[0]
                    if (ts != null && local != null) offset = (local - ts).toInt()
                }
                MONITORING -> {
                    val local = v[11]
                    if (ts != null && local != null) offset = (local - ts).toInt()
                    val type = v[5]
                    val cycles = v[3]
                    // the profile's "steps" subfield of cycles: one cycle is one step for walking and running
                    when (type) {
                        ACTIVITY_WALKING -> {
                            add(Metric.STEPS_WALKING, ts, cycles?.toDouble())
                            add(Metric.DISTANCE_WALKING, ts, v[2]?.let { it / 100.0 })
                            add(Metric.ACTIVE_KCAL_WALKING, ts, v[19]?.toDouble())
                        }
                        ACTIVITY_RUNNING -> {
                            add(Metric.STEPS_RUNNING, ts, cycles?.toDouble())
                            add(Metric.DISTANCE_RUNNING, ts, v[2]?.let { it / 100.0 })
                            add(Metric.ACTIVE_KCAL_RUNNING, ts, v[19]?.toDouble())
                        }
                    }
                    add(Metric.HEART_RATE, ts, v[27]?.toDouble())
                    add(Metric.ASCENT, ts, v[31]?.let { it / 1000.0 })
                    add(Metric.MODERATE_MIN, ts, v[33]?.toDouble())
                    add(Metric.VIGOROUS_MIN, ts, v[34]?.toDouble())
                }
                MONITORING_HR_DATA -> add(Metric.RESTING_HR, ts, (v[1] ?: v[0])?.toDouble())
                STRESS_LEVEL -> add(Metric.STRESS, v[1], v[0]?.toDouble())
                SPO2_DATA -> add(Metric.SPO2, ts, v[0]?.toDouble())
                RESPIRATION_RATE -> add(Metric.RESPIRATION, ts, v[0]?.let { it / 100.0 })
                HRV_VALUE -> add(Metric.HRV, ts, v[0]?.let { it / 128.0 })
                HRV_STATUS_SUMMARY -> add(Metric.HRV_LAST_NIGHT, ts, v[1]?.let { it / 128.0 })
                SLEEP_LEVEL -> {
                    val level = v[0]
                    if (ts != null && ts >= FitReader.MIN_DATE_TIME && level != null) sleepLevels.add(ts to level)
                }
                SLEEP_ASSESSMENT -> sleepScore = v[6]?.toInt()
                HSA_BODY_BATTERY_DATA -> addArray(Metric.BODY_BATTERY, ts, v[0], v.array(1))
                HSA_STRESS_DATA -> addArray(Metric.STRESS, ts, v[0], v.array(1))
                HSA_HEART_RATE_DATA -> addArray(Metric.HEART_RATE, ts, v[0], v.array(2))
            }
        }

        val zoneAt: (Instant) -> ZoneOffset = { instant ->
            offset?.let { ZoneOffset.ofTotalSeconds(it) } ?: fallbackZone.rules.getOffset(instant)
        }
        fun instant(ts: Long) = Instant.ofEpochSecond(ts + FitReader.FIT_EPOCH_OFFSET)
        fun dayOf(ts: Long): LocalDate = instant(ts).atOffset(zoneAt(instant(ts))).toLocalDate()

        return WatchFileData(
            samples = raws.map { MetricSample(it.metric, instant(it.ts), dayOf(it.ts), it.value) },
            sleep = sleepSummary(sleepLevels, sleepScore, ::dayOf),
            utcOffsetSeconds = offset,
        )
    }

    /** Each level lasts until the next level change; the last message only marks the moment of waking up. */
    private fun sleepSummary(levels: List<Pair<Long, Long>>, score: Int?, dayOf: (Long) -> LocalDate): SleepSummary? {
        if (levels.size < 2) return null
        val sorted = levels.sortedBy { it.first }
        var deep = 0L
        var light = 0L
        var rem = 0L
        var awake = 0L
        for ((current, next) in sorted.zipWithNext()) {
            val seconds = next.first - current.first
            when (current.second) {
                SLEEP_DEEP -> deep += seconds
                SLEEP_LIGHT -> light += seconds
                SLEEP_REM -> rem += seconds
                SLEEP_AWAKE -> awake += seconds
            }
        }
        if (deep + light + rem == 0L) return null
        return SleepSummary(
            date = dayOf(sorted.last().first),
            durationS = (deep + light + rem).toInt(),
            deepS = deep.toInt(),
            lightS = light.toInt(),
            remS = rem.toInt(),
            awakeS = awake.toInt(),
            score = score,
        )
    }
}
