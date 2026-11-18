package nl.tufein.openconnect.core.hume

import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.health.HealthAccess
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One part of a night: what the sleeper was doing from [start] to [end]. */
enum class HumeStageKind { AWAKE, LIGHT, DEEP, REM, ASLEEP, OUT_OF_BED }

data class HumeStage(val kind: HumeStageKind, val start: Instant, val end: Instant)

/** A night's sleep session. [stages] is empty when the app wrote only the length. */
data class HumeSleep(val start: Instant, val end: Instant, val stages: List<HumeStage> = emptyList())

/** One reading of heart rate (beats a minute), heart rate variability (milliseconds) or blood oxygen (percent). */
data class HumeSample(val time: Instant, val value: Double)

data class HumeExercise(
    val id: String,
    /** A plain key such as "running" or "strengthTraining". */
    val type: String,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val calories: Int? = null,
    val distanceM: Double? = null,
)

/**
 * What the Hume app wrote to Health Connect, as plain data. Steps and energy are totals per day (Health Connect adds up
 * the records itself and does not double count overlapping ones), the rest are the records as they are.
 */
data class HumeReadings(
    val steps: Map<LocalDate, Int> = emptyMap(),
    val activeKcal: Map<LocalDate, Int> = emptyMap(),
    val heartRate: List<HumeSample> = emptyList(),
    val restingHr: Map<LocalDate, Int> = emptyMap(),
    val hrv: List<HumeSample> = emptyList(),
    val spo2: List<HumeSample> = emptyList(),
    val sleeps: List<HumeSleep> = emptyList(),
    val exercises: List<HumeExercise> = emptyList(),
)

/**
 * The phone's Health Connect as the Hume link sees it. The real one is in the app module; tests use a fake. Errors are thrown
 * as [java.io.IOException].
 */
interface HumeGateway {
    suspend fun access(): HealthAccess

    /** What the phone's permission dialog should ask for: what the link reads, and what lets it read in the background when the phone allows that. */
    suspend fun permissionsToAsk(): Set<String>

    /** The packages of the apps that wrote anything in the period (steps, heart rate, sleep or energy). */
    suspend fun origins(from: LocalDate, to: LocalDate): Set<String>

    /** What the apps in [origins] wrote from [from] to [to] inclusive, and nothing from any other app. */
    suspend fun read(origins: Set<String>, from: LocalDate, to: LocalDate): HumeReadings
}

/** Stands in where there is no Health Connect (tests, and a phone without it): nothing is allowed and nothing is read. */
object NoHume : HumeGateway {
    override suspend fun access(): HealthAccess = HealthAccess.UNAVAILABLE

    override suspend fun permissionsToAsk(): Set<String> = emptySet()

    override suspend fun origins(from: LocalDate, to: LocalDate): Set<String> = emptySet()

    override suspend fun read(origins: Set<String>, from: LocalDate, to: LocalDate): HumeReadings = HumeReadings()
}

class HumeException(val kind: Kind, /** The apps Health Connect does hold data of, for [Kind.NO_DATA]. */ val found: Set<String> = emptySet()) : Exception(kind.name) {
    enum class Kind {
        /** Health Connect is not on this phone, or must be updated first. */
        UNAVAILABLE,

        /** It is there, but OpenConnect may not read it yet. */
        NEEDS_PERMISSION,

        /** Health Connect holds nothing from the Hume app (or from the app the person chose). */
        NO_DATA,
    }
}

/**
 * Which app wrote the band's data. Hume's Android app is published by Hume Health Corp as `com.elink.fittrackhealth.pro`
 * (its older name was FitTrack), which says nothing of "hume", so the guess knows that id and also takes any package with
 * "hume" in it. The person can pick another app, and that choice beats the guess.
 */
object HumeOrigins {
    private val KNOWN = listOf("com.elink.fittrackhealth")

    fun looksLikeHume(pkg: String): Boolean = pkg.lowercase().let { p -> "hume" in p || KNOWN.any { p.startsWith(it) } }

    /** The apps whose data is the band's: the [chosen] one when there is one, else the ones that look like Hume's. */
    fun pick(found: Set<String>, chosen: String?): Set<String> =
        if (chosen != null) found.filter { it == chosen }.toSet() else found.filter(::looksLikeHume).toSet()
}

data class HumeSyncReport(val daysUpdated: Int = 0, val nightsUpdated: Int = 0, val workoutsAdded: Int = 0, val origins: Set<String> = emptySet()) {
    val total: Int get() = daysUpdated + nightsUpdated + workoutsAdded
}

/**
 * Brings what the Hume Band wrote to Health Connect into the app's own Hume storage. Nothing is mixed into the watch's data
 * or another band's: each device keeps its own numbers, so a day is never counted twice. Only data of the band's own app
 * is read.
 */
class HumeSync(private val gateway: HumeGateway, private val repo: Repository, private val zone: ZoneId = ZoneId.systemDefault()) {
    suspend fun sync(days: Int, today: LocalDate, chosen: String? = null): HumeSyncReport {
        when (gateway.access()) {
            HealthAccess.UNAVAILABLE -> throw HumeException(HumeException.Kind.UNAVAILABLE)
            HealthAccess.NEEDS_PERMISSION -> throw HumeException(HumeException.Kind.NEEDS_PERMISSION)
            HealthAccess.READY -> Unit
        }
        val first = today.minusDays(days.toLong() - 1)
        val found = gateway.origins(first, today)
        val origins = HumeOrigins.pick(found, chosen)
        if (origins.isEmpty()) throw HumeException(HumeException.Kind.NO_DATA, found)
        val readings = gateway.read(origins, first, today)

        fun date(time: Instant): LocalDate = time.atZone(zone).toLocalDate()

        // One night per wake-up date: the one with the most sleep.
        val nights = readings.sleeps.groupBy { date(it.end) }.mapValues { (_, sessions) -> sessions.maxBy { asleepS(it) } }
        val heart = readings.heartRate.groupBy { date(it.time) }
        val oxygen = readings.spo2.groupBy { date(it.time) }
        val variability = readings.hrv.groupBy { date(it.time) }
        val workouts = readings.exercises.groupBy { date(it.start) }

        val dates = (
            nights.keys + heart.keys + oxygen.keys + variability.keys + workouts.keys + readings.steps.keys +
                readings.activeKcal.keys + readings.restingHr.keys
            ).filter { !it.isBefore(first) && !it.isAfter(today) }.sorted()

        val before = repo.listHumeDays(first, today).associateBy { it.date }
        var daysUpdated = 0
        var nightsUpdated = 0
        var added = 0
        for (date in dates) {
            val night = nights[date]
            val inNight: (HumeSample) -> Boolean = { s -> night != null && !s.time.isBefore(night.start) && !s.time.isAfter(night.end) }
            val beats = heart[date].orEmpty()
            val duringNight = beats.filter(inNight)
            val hrvOfNight = variability[date].orEmpty().let { all -> all.filter(inNight).ifEmpty { if (night == null) all else emptyList() } }
            val stages = night?.stages?.takeIf { it.isNotEmpty() }
            val day = HumeDay(
                day = date.toString(),
                steps = readings.steps[date],
                activeKcal = readings.activeKcal[date],
                restingHr = readings.restingHr[date],
                lowestHr = duringNight.minOfOrNull { it.value }?.let { Math.round(it).toInt() },
                avgHr = beats.takeIf { it.isNotEmpty() }?.let { Math.round(it.map { s -> s.value }.average()).toInt() },
                maxHr = beats.maxOfOrNull { it.value }?.let { Math.round(it).toInt() },
                sleepS = night?.let { asleepS(it) }?.takeIf { it > 0 },
                deepS = stages?.let { seconds(it, HumeStageKind.DEEP) },
                lightS = stages?.let { seconds(it, HumeStageKind.LIGHT) },
                remS = stages?.let { seconds(it, HumeStageKind.REM) },
                awakeS = stages?.let { seconds(it, HumeStageKind.AWAKE) },
                hrv = hrvOfNight.filter { it.value > 0 }.takeIf { it.isNotEmpty() }?.let { tenth(it.map { s -> s.value }.average()) },
                spo2 = oxygen[date].orEmpty().filter { it.value > 0 }.takeIf { it.isNotEmpty() }?.let { tenth(it.map { s -> s.value }.average()) },
                workouts = workouts[date].orEmpty().sortedBy { it.start }.map(::workout),
            )
            val old = before[date]
            if (day == old) continue
            repo.upsertHumeDay(day)
            daysUpdated++
            if (day.sleepS != null && day.sleepS != old?.sleepS) nightsUpdated++
            val known = old?.workouts.orEmpty().map { it.id }.toSet()
            added += day.workouts.count { it.id !in known }
        }
        return HumeSyncReport(daysUpdated, nightsUpdated, added, origins)
    }

    private companion object {
        fun tenth(v: Double): Double = Math.rint(v * 10) / 10

        fun seconds(stages: List<HumeStage>, kind: HumeStageKind): Int =
            stages.filter { it.kind == kind }.sumOf { Duration.between(it.start, it.end).seconds.coerceAtLeast(0) }.toInt()

        /** Time asleep: light, deep, REM and unspecified sleep; the whole session when the app wrote no stages. */
        fun asleepS(sleep: HumeSleep): Int {
            if (sleep.stages.isEmpty()) return Duration.between(sleep.start, sleep.end).seconds.coerceAtLeast(0).toInt()
            return sleep.stages.filter { it.kind != HumeStageKind.AWAKE && it.kind != HumeStageKind.OUT_OF_BED }
                .sumOf { Duration.between(it.start, it.end).seconds.coerceAtLeast(0) }.toInt()
        }

        fun workout(e: HumeExercise) = HumeWorkout(
            id = e.id,
            activity = e.type,
            startMs = e.start.toEpochMilli(),
            endMs = e.end.toEpochMilli(),
            calories = e.calories?.takeIf { it > 0 },
            distanceM = e.distanceM?.takeIf { it > 0 },
            title = e.title,
        )
    }
}
