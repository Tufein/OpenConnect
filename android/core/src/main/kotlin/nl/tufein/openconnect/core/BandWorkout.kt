package nl.tufein.openconnect.core

import java.time.Instant

/**
 * A workout that a ring or a band saw: Oura Ring, WHOOP and Hume Band each keep theirs in their own stored days and none of
 * them is ever written to the activities table. This is the part they have in common, so the list of activities and
 * the "same effort" check do not need to know which device it was.
 */
interface BandWorkout {
    val id: String

    /** The device's own name for the kind of workout, such as "running" or "strengthTraining". */
    val activity: String
    val startMs: Long
    val endMs: Long
    val calories: Int?
    val distanceM: Double?
    val name: String

    val start: Instant get() = Instant.ofEpochMilli(startMs)
    val durationS: Long get() = ((endMs - startMs) / 1000).coerceAtLeast(0)
}

/**
 * The devices whose workouts can show up as rows among the activities. [idPrefix] keeps their ids apart from the
 * ids of recorded activities and from each other; [source] and [device] are what the row says it came from.
 */
enum class BandSource(val source: String, val idPrefix: String, val device: String) {
    OURA("oura", "oura:", "Oura Ring"),
    WHOOP("whoop", "whoop:", "WHOOP"),
    HUME("hume", "hume:", "Hume Band"),
    ;

    companion object {
        /** The device behind a row id made by [BandWorkouts.toActivity], or null when the id is of a recorded activity. */
        fun ofRow(id: String): BandSource? = entries.firstOrNull { id.startsWith(it.idPrefix) }
    }
}

/** Rows among the activities for the workouts of rings and bands, made on the fly from their stored days. */
object BandWorkouts {
    /** A row for the list and the detail screen. No heart rate and no track: only what the device keeps of the effort. */
    fun toActivity(workout: BandWorkout, band: BandSource): Activity = Activity(
        id = band.idPrefix + workout.id,
        source = band.source,
        externalId = workout.id,
        name = workout.name,
        sport = RingWorkouts.sport(workout.activity),
        startTime = workout.start,
        durationS = workout.durationS.toDouble(),
        distanceM = workout.distanceM ?: 0.0,
        calories = workout.calories,
        deviceName = band.device,
    )

    /** The rows of one device's workouts that no activity in [activities] covers, newest first. */
    fun extra(workouts: List<BandWorkout>, band: BandSource, activities: List<Activity>): List<Activity> = workouts
        .distinctBy { it.id }
        .filter { workout -> activities.none { RingWorkouts.sameEffort(it, workout) } }
        .map { toActivity(it, band) }
        .sortedByDescending { it.startTime }

    /** The workout behind a row id of [band]; null when it is gone or the id belongs to another device. */
    fun find(id: String, band: BandSource, workouts: List<BandWorkout>): BandWorkout? {
        if (!id.startsWith(band.idPrefix)) return null
        val key = id.removePrefix(band.idPrefix)
        return workouts.firstOrNull { it.id == key }
    }
}
