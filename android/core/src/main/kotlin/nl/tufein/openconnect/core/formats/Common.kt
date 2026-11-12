package nl.tufein.openconnect.core.formats

import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Sport
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Raised when a file cannot be parsed or an activity cannot be exported in a format. */
class FormatError(message: String, cause: Throwable? = null) : Exception(message, cause)

private val SPORT_ALIASES = mapOf(
    "running" to Sport.RUNNING,
    "run" to Sport.RUNNING,
    "trail_running" to Sport.RUNNING,
    "cycling" to Sport.CYCLING,
    "biking" to Sport.CYCLING,
    "bike" to Sport.CYCLING,
    "ride" to Sport.CYCLING,
    "swimming" to Sport.SWIMMING,
    "swim" to Sport.SWIMMING,
    "walking" to Sport.WALKING,
    "walk" to Sport.WALKING,
    "hiking" to Sport.HIKING,
    "hike" to Sport.HIKING,
    "strength" to Sport.STRENGTH,
    "strength_training" to Sport.STRENGTH,
    "yoga" to Sport.YOGA,
)

fun sportFromText(text: String?): Sport {
    if (text.isNullOrBlank()) return Sport.OTHER
    val key = text.trim().lowercase(Locale.ROOT).replace(' ', '_').replace('-', '_')
    return SPORT_ALIASES[key] ?: Sport.OTHER
}

private val ISO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

/** `2026-11-10T07:30:00Z`, the form GPX and TCX use. */
fun isoZ(instant: Instant): String = ISO_SECONDS.format(instant)

/** Python's `datetime.isoformat()` for a UTC value, so ids match the server's. */
internal fun pyIsoFormat(instant: Instant): String {
    val base = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC).format(instant)
    val micros = instant.nano / 1000
    val fraction = if (micros != 0) String.format(Locale.ROOT, ".%06d", micros) else ""
    return "$base$fraction+00:00"
}

fun parseTime(text: String): Instant {
    val value = text.trim()
    try {
        return OffsetDateTime.parse(value).toInstant()
    } catch (_: DateTimeParseException) {
        // fall through: naive timestamps are assumed to be UTC
    }
    try {
        return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
    } catch (_: DateTimeParseException) {
        throw FormatError("Invalid timestamp: '$value'")
    }
}

/** Stable id for imported files, so re-importing an activity updates it instead of cloning it. */
fun importId(activity: Activity): String {
    val fingerprint = "${pyIsoFormat(activity.startTime)}|${Math.rint(activity.durationS).toLong()}"
    val digest = MessageDigest.getInstance("SHA-1").digest(fingerprint.toByteArray(Charsets.UTF_8))
    return "import:" + digest.joinToString("") { "%02x".format(it) }.take(16)
}

fun defaultName(activity: Activity): String {
    val day = activity.startTime.atZone(ZoneOffset.UTC).toLocalDate()
    return "${activity.sport.key.replaceFirstChar { it.titlecase(Locale.ROOT) }} $day"
}

internal fun fmt(pattern: String, vararg args: Any?): String = String.format(Locale.ROOT, pattern, *args)
