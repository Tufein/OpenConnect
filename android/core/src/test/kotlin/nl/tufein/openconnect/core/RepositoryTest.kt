package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {
    private val day = LocalDate.parse("2026-05-02")

    @Test
    fun trackCodecRoundTripsEverythingIncludingMissingFields() {
        val track = listOf(
            TrackPoint(time = Instant.parse("2026-05-02T07:00:00.250Z"), lat = 52.3702, lon = 4.8952, ele = 5.5, hr = 140, cadence = 84, power = 210, distanceM = 0.0),
            TrackPoint(time = Instant.parse("2026-05-02T07:00:01.250Z"), hr = 141),
            TrackPoint(time = Instant.parse("2026-05-02T07:00:05Z"), lat = 52.371, lon = 4.896, distanceM = 31.5),
        )
        val decoded = TrackCodec.decode(TrackCodec.encode(track))
        assertEquals(track, decoded)
    }

    @Test
    fun trackCodecKeepsLargeTracksSmall() {
        val track = TestData.track(5000)
        val bytes = TrackCodec.encode(track)
        assertEquals(track.size, TrackCodec.decode(bytes).size)
        assertTrue(bytes.size < track.size * 12, "was ${bytes.size} bytes")
    }

    @Test
    fun emptyTrackCodec() {
        assertEquals(emptyList(), TrackCodec.decode(TrackCodec.encode(emptyList())))
        assertEquals(emptyList(), TrackCodec.decode(ByteArray(0)))
    }

    @Test
    fun listingIsNewestFirstWithoutTracksAndFiltersBySportAndRange() {
        val repo = InMemoryRepository()
        val run1 = TestData.activity("a:1", Sport.RUNNING, start = Instant.parse("2026-05-01T07:00:00Z"))
        val ride = TestData.activity("a:2", Sport.CYCLING, start = Instant.parse("2026-05-02T07:00:00Z"))
        val run2 = TestData.activity("a:3", Sport.RUNNING, start = Instant.parse("2026-05-03T07:00:00Z"))
        listOf(run1, ride, run2).forEach(repo::upsertActivity)

        assertEquals(listOf("a:3", "a:2", "a:1"), repo.listActivities().map { it.id })
        assertTrue(repo.listActivities().all { it.track.isEmpty() })
        assertEquals(listOf("a:3", "a:1"), repo.listActivities(sport = Sport.RUNNING).map { it.id })
        assertEquals(listOf("a:2"), repo.listActivities(start = ride.startTime, end = run2.startTime).map { it.id })
        assertEquals(listOf("a:2"), repo.listActivities(limit = 1, offset = 1).map { it.id })
        assertEquals(60, repo.getActivity("a:1")!!.track.size)
        repo.deleteActivity("a:1")
        assertNull(repo.getActivity("a:1"))
    }

    @Test
    fun upsertReplacesByIdAndDay() {
        val repo = InMemoryRepository()
        repo.upsertActivity(TestData.activity("a:1", name = "Before"))
        repo.upsertActivity(TestData.activity("a:1", name = "After"))
        assertEquals("After", repo.getActivity("a:1")!!.name)

        repo.upsertDaily(TestData.daily(day, steps = 1000))
        repo.upsertDaily(TestData.daily(day, steps = 2000))
        assertEquals(listOf(2000), repo.listDaily(day, day).map { it.steps })
        repo.upsertSleep(TestData.sleep(day))
        assertEquals(1, repo.listSleep(day, day).size)
    }

    @Test
    fun clearAllEmptiesEverything() {
        val repo = InMemoryRepository()
        repo.upsertActivity(TestData.activity())
        repo.upsertDaily(TestData.daily(day))
        repo.clearAll()
        assertEquals(emptyList(), repo.listActivities())
        assertEquals(emptyList(), repo.listDaily(day, day))
    }
}
