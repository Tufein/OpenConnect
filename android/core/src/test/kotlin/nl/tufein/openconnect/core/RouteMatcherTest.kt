package nl.tufein.openconnect.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteMatcherTest {
    private val day0 = Instant.parse("2026-06-01T08:00:00Z")

    /** A run along a line from ([lat], [lon]) going east for [km], taking [minutes]. */
    private fun run(
        id: String,
        dayOffset: Long,
        km: Double = 5.0,
        minutes: Double = 30.0,
        lat: Double = 52.0,
        lon: Double = 5.0,
        sport: Sport = Sport.RUNNING,
    ): Activity {
        val steps = 50
        val dLon = km * 1000 / 68_000.0 // about 68 km per degree of longitude at 52 north
        val track = (0..steps).map { TrackPoint(time = day0.plusSeconds(dayOffset * 86_400 + it), lat = lat, lon = lon + dLon * it / steps) }
        return Activity(id = id, sport = sport, startTime = day0.plusSeconds(dayOffset * 86_400), durationS = minutes * 60, distanceM = km * 1000, track = track)
    }

    @Test
    fun `the same road at about the same length is the same route`() {
        assertTrue(RouteMatcher.isSameRoute(run("a", 0), run("b", 3, km = 5.2, minutes = 31.0)))
    }

    @Test
    fun `another sport, another length or another start is another route`() {
        val base = run("a", 0)
        assertEquals(false, RouteMatcher.isSameRoute(base, run("b", 3, sport = Sport.CYCLING)))
        assertEquals(false, RouteMatcher.isSameRoute(base, run("c", 3, km = 6.0, minutes = 36.0))) // 20 percent longer
        assertEquals(false, RouteMatcher.isSameRoute(base, run("d", 3, lat = 52.01))) // start 1.1 km away
        assertEquals(false, RouteMatcher.isSameRoute(base, run("e", 3, lat = 52.0, lon = 5.02)))
    }

    @Test
    fun `an activity without a track matches nothing`() {
        val bare = Activity(id = "x", sport = Sport.RUNNING, startTime = day0, durationS = 1800.0, distanceM = 5000.0)
        assertEquals(false, RouteMatcher.isSameRoute(run("a", 0), bare))
        assertEquals(false, RouteMatcher.isSameRoute(bare, run("a", 0)))
    }

    @Test
    fun `matches leave out the activity itself and come oldest first`() {
        val target = run("t", 10)
        val matches = RouteMatcher.matches(target, listOf(run("b", 5), target, run("a", 1), run("far", 2, lat = 53.0)))
        assertEquals(listOf("a", "b"), matches.map { it.id })
    }

    @Test
    fun `the history ranks this run by pace among the others`() {
        val target = run("t", 10, minutes = 28.0)
        val history = RouteMatcher.history(target, listOf(run("a", 1, minutes = 31.0), run("b", 5, minutes = 26.0), run("c", 7, minutes = 30.0)))!!
        assertEquals(4, history.total)
        assertEquals(2, history.rank) // only b is faster
        assertEquals("b", history.fastest.id)
        assertEquals(28.0 * 60 - 26.0 * 60, history.secondsSlowerThanBest, 1e-6)
        assertEquals(listOf("c", "b", "a"), history.others.map { it.id }) // newest first
        val average = (31.0 + 26.0 + 30.0) / 3 * 60
        assertEquals(28.0 * 60 - average, history.secondsVersusAverage, 1e-6)
    }

    @Test
    fun `being the fastest ranks first and is zero behind the best`() {
        val history = RouteMatcher.history(run("t", 10, minutes = 25.0), listOf(run("a", 1, minutes = 31.0)))!!
        assertEquals(1, history.rank)
        assertEquals(0.0, history.secondsSlowerThanBest, 1e-9)
        assertEquals("t", history.fastest.id)
    }

    @Test
    fun `no other run on the route means no history`() {
        assertNull(RouteMatcher.history(run("t", 10), listOf(run("far", 2, lat = 53.0))))
        assertNotNull(RouteMatcher.history(run("t", 10), listOf(run("a", 2))))
    }
}
