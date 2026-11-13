package nl.tufein.openconnect.core.oura

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/** A workout the ring saw (or that was entered in the Oura app). Kept apart from the watch's activities. */
@Serializable
data class OuraWorkout(
    val id: String,
    /** Oura's name for it, such as "running" or "cycling". */
    val activity: String,
    val label: String? = null,
    val startMs: Long,
    val endMs: Long,
    val calories: Int? = null,
    val distanceM: Double? = null,
) {
    val start: Instant get() = Instant.ofEpochMilli(startMs)
    val durationS: Long get() = ((endMs - startMs) / 1000).coerceAtLeast(0)
    val name: String get() = label?.takeIf { it.isNotBlank() } ?: activity.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/**
 * One day as the Oura Ring sees it, with Oura's own scores. Since v0.17.0 this is stored on its own and shown on the
 * Oura tab; it is never mixed into the watch's days, nights or activities.
 */
@Serializable
data class OuraDay(
    /** ISO date, the day Oura files it under (a night counts for the morning it ends). */
    val day: String,
    val readiness: Int? = null,
    val sleepScore: Int? = null,
    val activityScore: Int? = null,
    val steps: Int? = null,
    val activeKcal: Int? = null,
    /** Time asleep in seconds, from the main sleep of the night. */
    val sleepS: Int? = null,
    val deepS: Int? = null,
    val lightS: Int? = null,
    val remS: Int? = null,
    val awakeS: Int? = null,
    val lowestHr: Int? = null,
    val averageHrv: Int? = null,
    val breathAvg: Double? = null,
    val spo2Avg: Double? = null,
    /** How far the skin temperature was from the person's normal, in degrees Celsius. */
    val temperatureDeviation: Double? = null,
    val workouts: List<OuraWorkout> = emptyList(),
) {
    val date: LocalDate get() = LocalDate.parse(day)
    val sleepHours: Double? get() = sleepS?.let { it / 3600.0 }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun fromJson(text: String): OuraDay = JSON.decodeFromString(serializer(), text)
    }
}

/** Made-up ring days to look around the Oura tab with, the same every time for the same [today]. */
object OuraDemo {
    fun days(today: LocalDate, count: Int = 30): List<OuraDay> = (0 until count).map { back ->
        val date = today.minusDays(back.toLong())
        val n = date.toEpochDay().toInt()
        val wave = ((n * 37) % 11) - 5 // -5..5, steady from day to day
        val sleep = 25_200 + wave * 900
        val run = if (n % 3 == 0) {
            val start = date.atTime(7, 0).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            listOf(OuraWorkout("demo-$date", "running", null, start, start + 40 * 60_000L, 420, 6_800.0))
        } else {
            emptyList()
        }
        OuraDay(
            day = date.toString(),
            readiness = 78 + wave,
            sleepScore = 80 + wave,
            activityScore = 75 - wave,
            steps = 8_500 + wave * 600,
            activeKcal = 420 + wave * 20,
            sleepS = sleep,
            deepS = sleep / 5,
            lightS = sleep / 2,
            remS = sleep * 3 / 10,
            awakeS = 1_800,
            lowestHr = 48 - wave / 2,
            averageHrv = 58 + wave,
            breathAvg = 14.2 + wave / 10.0,
            spo2Avg = 96.5,
            temperatureDeviation = wave / 20.0,
            workouts = run,
        )
    }.reversed()
}
