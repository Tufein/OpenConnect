package nl.tufein.openconnect.core.hume

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.tufein.openconnect.core.BandWorkout
import java.time.LocalDate

/** A workout that the Hume app wrote to Health Connect. Kept apart from the watch's activities. */
@Serializable
data class HumeWorkout(
    /** The id Health Connect gave the session. */
    override val id: String,
    /** The kind of exercise as a plain key such as "running" or "strengthTraining". */
    override val activity: String,
    override val startMs: Long,
    override val endMs: Long,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    override val calories: Int? = null,
    override val distanceM: Double? = null,
    /** The title the Hume app gave the session, when it gave one. */
    val title: String? = null,
) : BandWorkout {
    override val name: String
        get() = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: activity.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ').trim().lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * One day as the Hume Band reported it through Health Connect. Hume's own scores do not reach Health Connect, so this is
 * only what Health Connect has types for: steps, energy, heart rate, nights, heart rate variability and blood oxygen.
 * It is never mixed into the watch's days, nights or activities.
 *
 * The day is the date on the phone's clock: the night belongs to the morning it ended on, everything else to the day it
 * was measured on.
 */
@Serializable
data class HumeDay(
    /** ISO date. */
    val day: String,
    val steps: Int? = null,
    /** Active energy of the day in kilocalories. */
    val activeKcal: Int? = null,
    /** The resting heart rate the band reported for the day. */
    val restingHr: Int? = null,
    /** The lowest heart rate of the night. */
    val lowestHr: Int? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    /** Time asleep in seconds: light, deep and REM together, without the time awake. */
    val sleepS: Int? = null,
    val deepS: Int? = null,
    val lightS: Int? = null,
    val remS: Int? = null,
    val awakeS: Int? = null,
    /** HRV as RMSSD in milliseconds, the mean of the night's readings. */
    val hrv: Double? = null,
    /** Blood oxygen in percent, the mean of the day's readings. */
    val spo2: Double? = null,
    val workouts: List<HumeWorkout> = emptyList(),
) {
    val date: LocalDate get() = LocalDate.parse(day)
    val sleepHours: Double? get() = sleepS?.let { it / 3600.0 }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun fromJson(text: String): HumeDay = JSON.decodeFromString(serializer(), text)
    }
}

/** Made-up Hume days to look around the Hume tab with, the same every time for the same [today]. */
object HumeDemo {
    fun days(today: LocalDate, count: Int = 30): List<HumeDay> = (0 until count).map { back ->
        val date = today.minusDays(back.toLong())
        val n = date.toEpochDay().toInt()
        val wave = ((n * 29) % 11) - 5 // -5..5, steady from day to day
        val sleep = 25_800 + wave * 780
        val walk = if (n % 4 == 0) {
            val start = date.atTime(12, 15).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            listOf(HumeWorkout("demo-$date", "walking", start, start + 38 * 60_000L, 104, 126, 150, 3_100.0))
        } else {
            emptyList()
        }
        HumeDay(
            day = date.toString(),
            steps = 8_400 + wave * 600,
            activeKcal = 520 + wave * 30,
            restingHr = 55 - wave / 2,
            lowestHr = 47 - wave / 3,
            avgHr = 71 + wave / 2,
            maxHr = 138 + wave * 2,
            sleepS = sleep,
            deepS = sleep / 6,
            lightS = sleep * 8 / 15,
            remS = sleep * 3 / 10,
            awakeS = 1_500,
            hrv = 58.0 + wave * 2,
            spo2 = 96.2,
            workouts = walk,
        )
    }.reversed()
}
