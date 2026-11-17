package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.oura.OuraDay
import nl.tufein.openconnect.core.oura.OuraWorkout
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v0.43.0: what the Oura Ring recorded and the watch did not is listed among the activities. */
class RingWorkoutsTest {
    private val t0: Instant = Instant.parse("2026-11-10T07:00:00Z")

    private fun minutes(n: Long): Long = t0.plusSeconds(n * 60).toEpochMilli()

    private fun workout(id: String, from: Long, to: Long, activity: String = "running", label: String? = null) =
        OuraWorkout(id, activity, label, minutes(from), minutes(to), calories = 420, distanceM = 6_800.0)

    private fun watchRun(id: String, from: Long, durationMin: Long) =
        Activity(id = id, source = "garmin-ble", name = "Run", sport = Sport.RUNNING, startTime = t0.plusSeconds(from * 60), durationS = durationMin * 60.0)

    @Test
    fun aRingWorkoutBecomesARowThatSaysWhereItCameFrom() {
        val row = RingWorkouts.toActivity(workout("w1", 0, 40))
        assertEquals("oura:w1", row.id)
        assertEquals("oura", row.source)
        assertEquals("w1", row.externalId)
        assertEquals("Running", row.name)
        assertEquals(Sport.RUNNING, row.sport)
        assertEquals(t0, row.startTime)
        assertEquals(2400.0, row.durationS)
        assertEquals(6_800.0, row.distanceM)
        assertEquals(420, row.calories)
        assertEquals("Oura Ring", row.deviceName)
        assertNull(row.avgHr, "the ring's heart rate is not read, so none is claimed")
        assertTrue(row.track.isEmpty())
        assertTrue(RingWorkouts.isRing(row.id))
        assertFalse(RingWorkouts.isRing("demo:20261110"))
    }

    @Test
    fun aLabelTypedInTheOuraAppIsTheName() {
        assertEquals("Lunch loop", RingWorkouts.toActivity(workout("w1", 0, 40, label = "Lunch loop")).name)
        assertEquals("House work", RingWorkouts.toActivity(workout("w2", 0, 40, activity = "house_work")).name)
    }

    @Test
    fun ourasNamesMapOntoTheSportsOfTheApp() {
        val expected = mapOf(
            "running" to Sport.RUNNING,
            "trailRunning" to Sport.RUNNING,
            "treadmill" to Sport.RUNNING,
            "cycling" to Sport.CYCLING,
            "indoorCycling" to Sport.CYCLING,
            "mountain_biking" to Sport.CYCLING,
            "walking" to Sport.WALKING,
            "hiking" to Sport.HIKING,
            "swimming" to Sport.SWIMMING,
            "strengthTraining" to Sport.STRENGTH,
            "strength_training" to Sport.STRENGTH,
            "weightlifting" to Sport.STRENGTH,
            "yoga" to Sport.YOGA,
            "pilates" to Sport.YOGA,
            "houseWork" to Sport.OTHER,
            "dance" to Sport.OTHER,
            "" to Sport.OTHER,
        )
        for ((name, sport) in expected) assertEquals(sport, RingWorkouts.sport(name), name)
    }

    @Test
    fun onlyWhatTheWatchDidNotRecordIsAnExtraRowNewestFirst() {
        val days = listOf(
            OuraDay("2026-11-10", workouts = listOf(workout("same-run", 2, 42), workout("walk", 300, 330, activity = "walking"))),
            OuraDay("2026-11-11", workouts = listOf(workout("next-day", 1440, 1470, activity = "cycling"))),
        )
        val watch = listOf(watchRun("a1", 0, 45))
        val extra = RingWorkouts.extra(days, watch)
        assertEquals(listOf("oura:next-day", "oura:walk"), extra.map { it.id })
        // Without any watch activity every ring workout is a row.
        assertEquals(3, RingWorkouts.extra(days, emptyList()).size)
    }

    @Test
    fun twoRecordsAreTheSameEffortWhenTheyShareHalfOfTheShorterOne() {
        val run = watchRun("a1", 0, 60)
        assertTrue(RingWorkouts.sameEffort(run, workout("w", 10, 50)), "inside the watch's run")
        assertTrue(RingWorkouts.sameEffort(run, workout("w", -15, 20)), "20 of its 35 minutes overlap")
        assertFalse(RingWorkouts.sameEffort(run, workout("w", 55, 95)), "5 of 40 minutes is a walk home, not the run")
        assertFalse(RingWorkouts.sameEffort(run, workout("w", 120, 160)))
        // A record without a length counts when it starts inside the other one.
        assertTrue(RingWorkouts.sameEffort(run, workout("w", 30, 30)))
        assertFalse(RingWorkouts.sameEffort(run, workout("w", 90, 90)))
    }

    @Test
    fun theRingsRecordOfAWatchActivityAndARowAreFoundAgain() {
        val same = workout("same-run", 2, 42)
        val days = listOf(OuraDay("2026-11-10", workouts = listOf(workout("walk", 300, 330, activity = "walking"), same)))
        assertEquals(same, RingWorkouts.matching(watchRun("a1", 0, 45), days))
        assertNull(RingWorkouts.matching(watchRun("a2", 600, 45), days))
        assertEquals("walk", RingWorkouts.find("oura:walk", days)?.id)
        assertNull(RingWorkouts.find("oura:gone", days))
        assertNull(RingWorkouts.find("a1", days))
    }
}
