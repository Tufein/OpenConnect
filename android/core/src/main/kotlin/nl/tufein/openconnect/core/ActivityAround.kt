package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.oura.OuraDay
import nl.tufein.openconnect.core.oura.OuraWorkout
import nl.tufein.openconnect.core.watch.Metric
import nl.tufein.openconnect.core.watch.MetricSample
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the watch measured around one activity, next to what is in the activity's own file. The first five come from
 * the watch's readings through the day (only there when the watch itself was read: cable, folder or Bluetooth); the
 * rest from its day and night summaries, which every source has.
 */
data class WatchAround(
    /** Body Battery at the start and at the end: the reading closest to that moment, at most half an hour away. */
    val bodyBatteryStart: Int? = null,
    val bodyBatteryEnd: Int? = null,
    /** Average stress in the hour before the start and in the hour after the end. */
    val stressBefore: Int? = null,
    val stressAfter: Int? = null,
    /** Heart rate from the all-day readings during the activity; only filled in when the activity has none of its own. */
    val heartRateAvg: Int? = null,
    val heartRateMax: Int? = null,
    /** The day of the activity: resting heart rate, last night's HRV, average stress and the range of Body Battery. */
    val restingHr: Int? = null,
    val hrvMs: Int? = null,
    val dayStress: Int? = null,
    val dayBodyBatteryHigh: Int? = null,
    val dayBodyBatteryLow: Int? = null,
    /** The night that ended on the day of the activity. */
    val sleep: SleepSummary? = null,
) {
    /** True when there is a reading from close around the activity, not only totals of the day. */
    val hasReadingsAround: Boolean
        get() = bodyBatteryStart != null || bodyBatteryEnd != null || stressBefore != null || stressAfter != null || heartRateAvg != null

    val hasDay: Boolean
        get() = restingHr != null || hrvMs != null || dayStress != null || dayBodyBatteryHigh != null || dayBodyBatteryLow != null || sleep != null
}

/** What the Oura Ring knew on the day of an activity: its morning scores, the night, and its own record of the effort. */
data class RingAround(
    val readiness: Int? = null,
    val sleepScore: Int? = null,
    /** Time asleep in seconds. */
    val sleepS: Int? = null,
    val lowestHr: Int? = null,
    val averageHrv: Int? = null,
    /** Skin temperature against the person's normal, in degrees Celsius. */
    val temperatureDeviation: Double? = null,
    /** The ring's own record of the same effort, when their times overlap; null for a row that is the ring's workout itself. */
    val workout: OuraWorkout? = null,
) {
    val hasDay: Boolean
        get() = readiness != null || sleepScore != null || sleepS != null || lowestHr != null || averageHrv != null || temperatureDeviation != null
}

/** Something a person could expect here that is not shown, so the screen can say why instead of leaving a hole. */
enum class AroundGap {
    /** The watch has nothing for this day at all. */
    WATCH_NOTHING,

    /** Only totals of the day: no readings from around the activity, because the watch itself was not read. */
    WATCH_NO_READINGS_AROUND,

    /** The ring has no day here: not worn, not synced with the Oura app yet, or before the first sync. */
    RING_NO_DAY,

    /** The ring's heart rate during the effort: Oura has it, OpenConnect does not ask for it. */
    RING_NO_HEART_RATE,
}

data class ActivityAround(
    val watch: WatchAround? = null,
    val ring: RingAround? = null,
    val gaps: List<AroundGap> = emptyList(),
) {
    /** Nothing to show and nothing to explain: no device that could have known was chosen. */
    val isEmpty: Boolean get() = watch == null && ring == null && gaps.isEmpty()
}

/**
 * Fills an activity in with what the watch and the Oura Ring measured around it (v0.43.0). Nothing is stored and
 * nothing is mixed: the watch's and the ring's numbers stay in their own tables and are only read side by side here.
 */
object ActivityAroundCalculator {
    /** How far a Body Battery reading may be from the start or the end and still count as "at" that moment. */
    val NEAR: Duration = Duration.ofMinutes(30)

    /** How long before the start and after the end stress is averaged. */
    val WINDOW: Duration = Duration.ofMinutes(60)

    /** The calendar day of the activity on the person's own clock: the day its night and its scores belong to. */
    fun dayOf(activity: Activity, zone: ZoneId): LocalDate = activity.startTime.atZone(zone).toLocalDate()

    private fun endOf(activity: Activity): Instant = activity.startTime.plusMillis((activity.durationS * 1000).toLong().coerceAtLeast(0))

    /**
     * [daily] and [sleep] are the watch's summaries (used only when they are of the activity's day), [samples] its
     * readings from around that day, [ringDays] the ring's days around it. [useWatch] and [useRing] say which devices
     * the person chose: a device that was not chosen is neither shown nor missed.
     */
    fun compute(
        activity: Activity,
        zone: ZoneId,
        daily: DailySummary?,
        sleep: SleepSummary?,
        samples: List<MetricSample>,
        ringDays: List<OuraDay>,
        useWatch: Boolean,
        useRing: Boolean,
    ): ActivityAround {
        val day = dayOf(activity, zone)
        val gaps = ArrayList<AroundGap>()

        var watch: WatchAround? = null
        if (useWatch) {
            val found = watchAround(activity, daily?.takeIf { it.date == day }, sleep?.takeIf { it.date == day }, samples)
            when {
                !found.hasReadingsAround && !found.hasDay -> gaps += AroundGap.WATCH_NOTHING
                else -> {
                    watch = found
                    if (!found.hasReadingsAround) gaps += AroundGap.WATCH_NO_READINGS_AROUND
                }
            }
        }

        var ring: RingAround? = null
        if (useRing) {
            val ringDay = ringDays.firstOrNull { it.date == day }
            val isRingRow = activity.source == RingWorkouts.SOURCE
            val found = RingAround(
                readiness = ringDay?.readiness,
                sleepScore = ringDay?.sleepScore,
                sleepS = ringDay?.sleepS,
                lowestHr = ringDay?.lowestHr,
                averageHrv = ringDay?.averageHrv,
                temperatureDeviation = ringDay?.temperatureDeviation,
                workout = if (isRingRow) null else RingWorkouts.matching(activity, ringDays),
            )
            if (found.hasDay || found.workout != null) ring = found
            // A workout of the ring itself proves the ring was worn, so "no day" is only said when nothing came from it.
            if (!found.hasDay && found.workout == null && !isRingRow) gaps += AroundGap.RING_NO_DAY
            if (ring != null || isRingRow) gaps += AroundGap.RING_NO_HEART_RATE
        }
        return ActivityAround(watch, ring, gaps)
    }

    /** [compute] with everything it needs read from [repo]. */
    fun load(repo: Repository, activity: Activity, zone: ZoneId, useWatch: Boolean, useRing: Boolean): ActivityAround {
        val day = dayOf(activity, zone)
        return compute(
            activity = activity,
            zone = zone,
            daily = if (useWatch) repo.listDaily(day, day).firstOrNull() else null,
            sleep = if (useWatch) repo.listSleep(day, day).firstOrNull() else null,
            // A day on each side: an activity can run past midnight, and the watch's own clock may differ from the phone's.
            samples = if (useWatch) repo.listSamples(day.minusDays(1), day.plusDays(1)) else emptyList(),
            ringDays = if (useRing) repo.listOuraDays(day.minusDays(1), day.plusDays(1)) else emptyList(),
            useWatch = useWatch,
            useRing = useRing,
        )
    }

    private fun watchAround(activity: Activity, daily: DailySummary?, sleep: SleepSummary?, samples: List<MetricSample>): WatchAround {
        val start = activity.startTime
        val end = endOf(activity)

        fun nearest(metric: Metric, moment: Instant): Int? = samples
            .filter { it.metric == metric }
            .minByOrNull { abs(Duration.between(moment, it.time).toMillis()) }
            ?.takeIf { abs(Duration.between(moment, it.time).toMillis()) <= NEAR.toMillis() }
            ?.value?.roundToInt()

        /** Stress of 0 or less is the watch saying it could not measure (moving, off the wrist), not calm. */
        fun stress(from: Instant, to: Instant): Int? = samples
            .filter { it.metric == Metric.STRESS && it.value > 0 && !it.time.isBefore(from) && !it.time.isAfter(to) }
            .map { it.value }
            .takeIf { it.isNotEmpty() }
            ?.average()?.roundToInt()

        val during = if (activity.avgHr == null) {
            samples.filter { it.metric == Metric.HEART_RATE && it.value > 0 && !it.time.isBefore(start) && !it.time.isAfter(end) }.map { it.value }
        } else {
            emptyList()
        }
        return WatchAround(
            bodyBatteryStart = nearest(Metric.BODY_BATTERY, start),
            bodyBatteryEnd = nearest(Metric.BODY_BATTERY, end),
            stressBefore = stress(start.minus(WINDOW), start.minusMillis(1)),
            stressAfter = stress(end.plusMillis(1), end.plus(WINDOW)),
            heartRateAvg = during.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            heartRateMax = during.maxOrNull()?.roundToInt(),
            restingHr = daily?.restingHr?.takeIf { it > 0 },
            hrvMs = daily?.hrvMs?.takeIf { it > 0 },
            dayStress = daily?.avgStress?.takeIf { it > 0 },
            dayBodyBatteryHigh = daily?.bodyBatteryHigh,
            dayBodyBatteryLow = daily?.bodyBatteryLow,
            sleep = sleep?.takeIf { it.durationS > 0 },
        )
    }
}

/**
 * The Oura Ring's workouts as rows among the activities (v0.43.0). They are made on the fly from the ring's stored
 * days and never written to the activities table, so totals, training load, records and trophies keep counting only
 * what a watch or a file recorded.
 */
object RingWorkouts {
    const val SOURCE = "oura"
    const val ID_PREFIX = "oura:"
    const val DEVICE = "Oura Ring"

    fun isRing(id: String): Boolean = id.startsWith(ID_PREFIX)

    /** Oura's name for a kind of workout ("running", "strengthTraining", "house_work") as one of the app's sports. */
    fun sport(name: String): Sport {
        val key = name.lowercase().filter { it.isLetter() }
        return when {
            "run" in key || key == "treadmill" -> Sport.RUNNING
            "cycl" in key || "bik" in key -> Sport.CYCLING
            "hik" in key -> Sport.HIKING
            "walk" in key -> Sport.WALKING
            "swim" in key -> Sport.SWIMMING
            "strength" in key || "weight" in key -> Sport.STRENGTH
            "yoga" in key || "pilates" in key -> Sport.YOGA
            else -> Sport.OTHER
        }
    }

    /** A row for the list and the detail screen. No heart rate and no track: the ring's are not read. */
    fun toActivity(workout: OuraWorkout): Activity = Activity(
        id = ID_PREFIX + workout.id,
        source = SOURCE,
        externalId = workout.id,
        name = workout.name,
        sport = sport(workout.activity),
        startTime = workout.start,
        durationS = workout.durationS.toDouble(),
        distanceM = workout.distanceM ?: 0.0,
        calories = workout.calories,
        deviceName = DEVICE,
    )

    /**
     * True when the ring and the watch recorded the same effort: their times share at least half of the shorter one.
     * A record without a length counts when it starts inside the other.
     */
    fun sameEffort(activity: Activity, workout: BandWorkout): Boolean {
        val aStart = activity.startTime.toEpochMilli()
        val aEnd = aStart + (activity.durationS * 1000).toLong().coerceAtLeast(0)
        val wStart = workout.startMs
        val wEnd = maxOf(workout.endMs, wStart)
        val shorter = minOf(aEnd - aStart, wEnd - wStart)
        if (shorter <= 0) return if (wEnd == wStart) wStart in aStart..aEnd else aStart in wStart..wEnd
        val shared = minOf(aEnd, wEnd) - maxOf(aStart, wStart)
        return shared * 2 >= shorter
    }

    /** The ring's own record of [activity], if it made one. */
    fun matching(activity: Activity, days: List<OuraDay>): OuraWorkout? =
        days.asSequence().flatMap { it.workouts.asSequence() }.firstOrNull { sameEffort(activity, it) }

    /** The ring's workouts that no activity in [activities] covers, as rows, newest first. */
    fun extra(days: List<OuraDay>, activities: List<Activity>): List<Activity> = days
        .flatMap { it.workouts }
        .distinctBy { it.id }
        .filter { workout -> activities.none { sameEffort(it, workout) } }
        .map(::toActivity)
        .sortedByDescending { it.startTime }

    /** The workout behind a row id made by [toActivity]; null when it is gone or the id is not a ring row. */
    fun find(id: String, days: List<OuraDay>): OuraWorkout? {
        if (!isRing(id)) return null
        val key = id.removePrefix(ID_PREFIX)
        return days.asSequence().flatMap { it.workouts.asSequence() }.firstOrNull { it.id == key }
    }
}
