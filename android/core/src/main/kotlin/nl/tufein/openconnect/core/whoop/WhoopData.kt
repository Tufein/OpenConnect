package nl.tufein.openconnect.core.whoop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.tufein.openconnect.core.BandWorkout
import java.time.LocalDate

/** A workout the WHOOP band saw (or that was entered in the WHOOP app). Kept apart from the watch's activities. */
@Serializable
data class WhoopWorkout(
    /** WHOOP's own id: a UUID in the v2 API. */
    override val id: String,
    /** WHOOP's name for the sport, such as "running" or "weightlifting". */
    override val activity: String,
    override val startMs: Long,
    override val endMs: Long,
    /** WHOOP's own workout strain on its 0 to 21 scale. */
    val strain: Double? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    override val calories: Int? = null,
    override val distanceM: Double? = null,
) : BandWorkout {
    override val name: String get() = activity.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }
}

/**
 * One day as the WHOOP band sees it, with WHOOP's own scores. Kept on its own and shown on the WHOOP tab: it is never mixed
 * into the watch's days, nights or activities, nor into the ring's.
 *
 * WHOOP files a night, its recovery and the strain of the cycle that follows under one cycle id; the day here is the
 * morning the night ended on, on the clock of the place it was slept in.
 */
@Serializable
data class WhoopDay(
    /** ISO date. */
    val day: String,
    /** Recovery score, 0 to 100 percent. */
    val recovery: Int? = null,
    /** Day strain on WHOOP's 0 to 21 scale. */
    val strain: Double? = null,
    val sleepPerformance: Int? = null,
    val sleepConsistency: Int? = null,
    val sleepEfficiency: Int? = null,
    /** Time asleep in seconds: light, slow-wave and REM together, without the time awake in bed. */
    val sleepS: Int? = null,
    val deepS: Int? = null,
    val lightS: Int? = null,
    val remS: Int? = null,
    val awakeS: Int? = null,
    /** How long WHOOP says the body needed: baseline plus debt plus strain, minus naps. */
    val sleepNeedS: Int? = null,
    val restingHr: Int? = null,
    /** HRV as RMSSD in milliseconds. */
    val hrv: Double? = null,
    val respiration: Double? = null,
    val spo2: Double? = null,
    /** Skin temperature in degrees Celsius (an absolute value, not a deviation). */
    val skinTempC: Double? = null,
    /** All energy of the cycle, as kilocalories (WHOOP counts kilojoules). */
    val kcal: Int? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val steps: Int? = null,
    val workouts: List<WhoopWorkout> = emptyList(),
) {
    val date: LocalDate get() = LocalDate.parse(day)
    val sleepHours: Double? get() = sleepS?.let { it / 3600.0 }
    val sleepNeedHours: Double? get() = sleepNeedS?.let { it / 3600.0 }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun fromJson(text: String): WhoopDay = JSON.decodeFromString(serializer(), text)
    }
}

/** Made-up WHOOP days to look around the WHOOP tab with, the same every time for the same [today]. */
object WhoopDemo {
    fun days(today: LocalDate, count: Int = 30): List<WhoopDay> = (0 until count).map { back ->
        val date = today.minusDays(back.toLong())
        val n = date.toEpochDay().toInt()
        val wave = ((n * 37) % 11) - 5 // -5..5, steady from day to day
        val sleep = 26_400 + wave * 840
        val run = if (n % 3 == 0) {
            val start = date.atTime(7, 0).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            listOf(WhoopWorkout("demo-$date", "running", start, start + 42 * 60_000L, 9.1 + wave / 10.0, 152, 176, 480, 7_000.0))
        } else {
            emptyList()
        }
        WhoopDay(
            day = date.toString(),
            recovery = 66 + wave * 4,
            strain = Math.rint((11.0 + wave / 3.0 + if (run.isNotEmpty()) 2.5 else 0.0) * 10) / 10,
            sleepPerformance = 86 + wave,
            sleepConsistency = 82 + wave,
            sleepEfficiency = 91 + wave / 2,
            sleepS = sleep,
            deepS = sleep / 5,
            lightS = sleep / 2,
            remS = sleep * 3 / 10,
            awakeS = 1_900,
            sleepNeedS = 28_800,
            restingHr = 52 - wave / 2,
            hrv = 62.0 + wave * 2,
            respiration = 14.8 + wave / 10.0,
            spo2 = 96.4,
            skinTempC = 33.6 + wave / 20.0,
            kcal = 2_350 + wave * 40,
            avgHr = 66 + wave / 2,
            maxHr = 160 + wave,
            workouts = run,
        )
    }.reversed()
}
