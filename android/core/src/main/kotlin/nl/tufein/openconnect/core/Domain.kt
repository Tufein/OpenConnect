package nl.tufein.openconnect.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Core domain models. Storage, file formats and report facts all convert to and from these. */
enum class Sport(val key: String) {
    RUNNING("running"),
    CYCLING("cycling"),
    SWIMMING("swimming"),
    WALKING("walking"),
    HIKING("hiking"),
    STRENGTH("strength"),
    YOGA("yoga"),
    OTHER("other"),
    ;

    companion object {
        fun fromKey(key: String?): Sport = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

data class TrackPoint(
    val time: Instant,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Metres above sea level. */
    val ele: Double? = null,
    /** Beats per minute. */
    val hr: Int? = null,
    val cadence: Int? = null,
    /** Watts. */
    val power: Int? = null,
    /** Cumulative distance in metres, when the source records it. */
    val distanceM: Double? = null,
) {
    val hasPosition: Boolean get() = lat != null && lon != null
}

data class Activity(
    val id: String,
    /** Where it came from: "import", "demo", "garmin", ... */
    val source: String = "import",
    val externalId: String? = null,
    val name: String = "",
    val sport: Sport = Sport.OTHER,
    val startTime: Instant,
    val durationS: Double = 0.0,
    val distanceM: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val calories: Int? = null,
    val deviceName: String? = null,
    val track: List<TrackPoint> = emptyList(),
) {
    val avgSpeedMps: Double?
        get() = if (durationS <= 0 || distanceM <= 0) null else distanceM / durationS

    val paceSPerKm: Double?
        get() = avgSpeedMps?.let { 1000.0 / it }

    val hasGps: Boolean get() = track.any { it.hasPosition }
}

/** One calendar day of all-day health data. */
data class DailySummary(
    val date: LocalDate,
    val steps: Int = 0,
    val stepsGoal: Int? = null,
    val distanceM: Double = 0.0,
    val activeKcal: Int = 0,
    val floorsClimbed: Int = 0,
    val moderateIntensityMin: Int = 0,
    val vigorousIntensityMin: Int = 0,
    val restingHr: Int? = null,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    val avgStress: Int? = null,
    val maxStress: Int? = null,
    val bodyBatteryHigh: Int? = null,
    val bodyBatteryLow: Int? = null,
) {
    /** Garmin counts vigorous minutes double towards the weekly goal. */
    val intensityMinutes: Int get() = moderateIntensityMin + 2 * vigorousIntensityMin
}

data class SleepSummary(
    /** The calendar day the sleep ends on. */
    val date: LocalDate,
    val durationS: Int = 0,
    val deepS: Int = 0,
    val lightS: Int = 0,
    val remS: Int = 0,
    val awakeS: Int = 0,
    val score: Int? = null,
) {
    val hours: Double get() = durationS / 3600.0
}

/** An AI report kept so unchanged input is not regenerated (and re-billed). */
data class StoredReport(
    val kind: String,
    val subject: String,
    val text: String,
    val provider: String,
    val model: String?,
    val factsHash: String,
    val createdAt: Instant,
)

fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radius = 6_371_008.8
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dPhi = p2 - p1
    val dLambda = Math.toRadians(lon2 - lon1)
    val a = sin(dPhi / 2).pow(2) + cos(p1) * cos(p2) * sin(dLambda / 2).pow(2)
    return 2 * radius * asin(sqrt(a))
}

/** Total ascent with a small hysteresis so GPS or barometer noise is not counted. */
fun elevationGain(elevations: List<Double>, thresholdM: Double = 1.0): Double {
    if (elevations.isEmpty()) return 0.0
    var gain = 0.0
    var reference = elevations.first()
    for (ele in elevations.drop(1)) {
        if (ele - reference >= thresholdM) {
            gain += ele - reference
            reference = ele
        } else if (reference - ele >= thresholdM) {
            reference = ele
        }
    }
    return gain
}

/** Fill derived summary metrics from the track when they are missing or zero. */
fun finalizeActivity(activity: Activity): Activity {
    val track = activity.track
    if (track.isEmpty()) return activity

    val duration = if (activity.durationS <= 0 && track.size > 1) {
        Duration.between(track.first().time, track.last().time).toMillis() / 1000.0
    } else {
        activity.durationS
    }

    val distance = if (activity.distanceM <= 0) {
        val cumulative = track.mapNotNull { it.distanceM }
        if (cumulative.isNotEmpty()) {
            cumulative.max()
        } else {
            track.filter { it.hasPosition }.zipWithNext().sumOf { (a, b) ->
                haversineM(a.lat!!, a.lon!!, b.lat!!, b.lon!!)
            }
        }
    } else {
        activity.distanceM
    }

    val gain = if (activity.elevationGainM <= 0) {
        elevationGain(track.mapNotNull { it.ele })
    } else {
        activity.elevationGainM
    }

    val hrs = track.mapNotNull { it.hr }.filter { it != 0 }
    val avgHr = activity.avgHr ?: if (hrs.isNotEmpty()) pyRound(hrs.sum().toDouble() / hrs.size) else null
    val maxHr = activity.maxHr ?: if (hrs.isNotEmpty()) hrs.max() else null

    return activity.copy(
        durationS = duration,
        distanceM = distance,
        elevationGainM = gain,
        avgHr = avgHr,
        maxHr = maxHr,
    )
}

/** Round half to even, like Python's `round`, so the app and the server agree on borderline values. */
fun pyRound(value: Double): Int = Math.rint(value).toInt()

internal fun clampInt(value: Double, lo: Int, hi: Int): Int = max(lo, minOf(hi, value.roundToInt()))
