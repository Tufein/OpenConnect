package nl.tufein.openconnect.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BestEffortsTest {
    private val t0 = Instant.parse("2026-06-10T06:00:00Z")

    /** A track with one point per 100 m; [pace] gives the seconds for each 100 m step. */
    private fun track(steps: Int, hr: Int? = null, pace: (Int) -> Double = { 30.0 }, ele: (Int) -> Double? = { null }): List<TrackPoint> {
        var t = 0.0
        return (0..steps).map { i ->
            if (i > 0) t += pace(i)
            TrackPoint(t0.plusMillis((t * 1000).toLong()), ele = ele(i), hr = hr, distanceM = i * 100.0)
        }
    }

    @Test
    fun `a steady run has the same time for every kilometre`() {
        val tr = track(100) // 10 km at 5:00 per km
        assertEquals(300.0, BestEfforts.inTrack(tr, 1000.0)!!, 0.01)
        assertEquals(1500.0, BestEfforts.inTrack(tr, 5000.0)!!, 0.01)
        assertEquals(3000.0, BestEfforts.inTrack(tr, 10_000.0)!!, 0.01)
        assertNull(BestEfforts.inTrack(tr, 21_097.5), "shorter than a half marathon")
    }

    @Test
    fun `the fastest kilometre is found wherever it sits, also between two points`() {
        // steps 31 to 40 are faster (27 s per 100 m): the best km is those ten steps
        val tr = track(100, pace = { if (it in 31..40) 27.0 else 30.0 })
        assertEquals(270.0, BestEfforts.inTrack(tr, 1000.0)!!, 0.01)
        // a 1 km window that starts between points: 5 steps at 27 and 5 at 30, shifted by 50 m, is slower than the pure one
        assertTrue(BestEfforts.inTrack(tr, 1050.0)!! > 270.0)
    }

    @Test
    fun `a gps jump that would be faster than anyone can run is not believed`() {
        val tr = track(100, pace = { if (it == 50) 1.0 else 30.0 }) // 100 m in one second
        assertTrue(BestEfforts.inTrack(tr, 1000.0)!! > 250.0)
        assertNull(BestEfforts.inTrack(track(20, pace = { 5.0 }), 1000.0), "nothing believable at 20 m/s")
    }

    @Test
    fun `positions are used when the track has no distance`() {
        val tr = (0..40).map { TrackPoint(t0.plusSeconds(it * 30L), lat = 52.0 + it * 0.0009, lon = 5.0) } // about 100 m per step
        val seconds = assertNotNull(BestEfforts.inTrack(tr, 1000.0))
        assertEquals(300.0, seconds, 8.0)
        assertTrue(BestEfforts.efforts(emptyList()).isEmpty())
        assertTrue(BestEfforts.laps(listOf(TrackPoint(t0))).isEmpty())
    }

    @Test
    fun `laps are a kilometre each with heart rate and climbing, and the rest is the last lap`() {
        val tr = track(25, hr = 150, ele = { it * 1.0 }) // 2.5 km climbing 1 m per step
        val laps = BestEfforts.laps(tr)
        assertEquals(3, laps.size)
        assertEquals(listOf(1000.0, 1000.0, 500.0), laps.map { it.distanceM })
        assertEquals(300.0, laps[0].seconds, 0.01)
        assertEquals(300.0, laps[0].pace, 0.01)
        assertEquals(150, laps[0].avgHr)
        assertEquals(10, laps[0].climbM)
        assertEquals(150.0, laps[2].seconds, 0.01)
        assertEquals(300.0, laps[2].pace, 0.01)
    }

    @Test
    fun `a rest of less than 100 m is not a lap, no heart rate means none shown`() {
        val laps = BestEfforts.laps(track(10))
        assertEquals(1, laps.size)
        assertNull(laps[0].avgHr)
        assertEquals(0, laps[0].climbM)
    }

    @Test
    fun `personal bests take the fastest over all runs, and ignore other sports and short runs`() {
        val repo = InMemoryRepository()
        fun run(id: String, sport: Sport, km: Int, secondsPer100: Double) = repo.upsertActivity(
            Activity(id = id, name = id, sport = sport, startTime = t0, durationS = km * 10 * secondsPer100, distanceM = km * 1000.0, track = track(km * 10, pace = { secondsPer100 })),
        )
        run("slow", Sport.RUNNING, 10, 32.0)
        run("fast", Sport.RUNNING, 5, 28.0)
        run("bike", Sport.CYCLING, 10, 10.0)
        repo.upsertActivity(Activity(id = "short", sport = Sport.RUNNING, startTime = t0, distanceM = 800.0, track = track(8, pace = { 20.0 })))
        val pbs = BestEfforts.personalBests(repo)
        assertEquals(listOf(1000.0, 5000.0, 10_000.0), pbs.map { it.distanceM })
        assertEquals(280.0, pbs[0].seconds, 0.01)
        assertEquals("fast", pbs[0].activityId)
        assertEquals("fast", pbs[1].activityId)
        assertEquals("slow", pbs[2].activityId)
    }
}
