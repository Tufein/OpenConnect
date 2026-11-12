package nl.tufein.openconnect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.DemoData
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.StoredReport
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.data.SqliteRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SqliteRepositoryTest {
    private lateinit var repo: SqliteRepository

    @Before
    fun setUp() {
        repo = SqliteRepository(ApplicationProvider.getApplicationContext<Context>(), name = null)
    }

    @After
    fun tearDown() = repo.close()

    private fun activity(id: String, start: String, sport: Sport = Sport.RUNNING, track: List<TrackPoint> = emptyList()) = Activity(
        id = id,
        source = "import",
        name = "Run $id",
        sport = sport,
        startTime = Instant.parse(start),
        durationS = 1800.0,
        distanceM = 5000.0,
        elevationGainM = 20.0,
        track = track,
    )

    @Test
    fun activityRoundTripKeepsNullsAndTheTrack() {
        val track = listOf(
            TrackPoint(Instant.parse("2026-05-02T07:00:00Z"), lat = 52.37, lon = 4.89, ele = 3.5, hr = 140, cadence = 80),
            TrackPoint(Instant.parse("2026-05-02T07:00:10Z"), hr = 141),
        )
        val stored = activity("a:1", "2026-05-02T07:00:00Z", track = track).copy(avgHr = null, calories = 300, deviceName = "Edge")
        repo.upsertActivity(stored)
        val loaded = assertNotNull(repo.getActivity("a:1"))
        assertEquals(stored, loaded)
        assertNull(loaded.avgHr)
        assertEquals(2, loaded.track.size)
    }

    @Test
    fun listingIsNewestFirstWithoutTracksAndFilters() {
        val track = listOf(TrackPoint(Instant.parse("2026-05-01T07:00:00Z"), lat = 1.0, lon = 2.0))
        repo.upsertActivity(activity("a:1", "2026-05-01T07:00:00Z", track = track))
        repo.upsertActivity(activity("a:2", "2026-05-02T07:00:00Z", Sport.CYCLING))
        repo.upsertActivity(activity("a:3", "2026-05-03T07:00:00Z"))

        assertEquals(listOf("a:3", "a:2", "a:1"), repo.listActivities().map { it.id })
        assertTrue(repo.listActivities().all { it.track.isEmpty() })
        assertEquals(listOf("a:3", "a:1"), repo.listActivities(sport = Sport.RUNNING).map { it.id })
        assertEquals(
            listOf("a:2"),
            repo.listActivities(start = Instant.parse("2026-05-02T00:00:00Z"), end = Instant.parse("2026-05-03T07:00:00Z")).map { it.id },
        )
        assertEquals(listOf("a:2"), repo.listActivities(limit = 1, offset = 1).map { it.id })
        assertEquals(3, repo.countActivities())
        repo.deleteActivity("a:2")
        assertNull(repo.getActivity("a:2"))
    }

    @Test
    fun upsertReplacesTheSameId() {
        repo.upsertActivity(activity("a:1", "2026-05-01T07:00:00Z"))
        repo.upsertActivity(activity("a:1", "2026-05-01T07:00:00Z").copy(name = "Renamed"))
        assertEquals("Renamed", repo.getActivity("a:1")!!.name)
        assertEquals(1, repo.countActivities())
    }

    @Test
    fun demoDataStoresAndComesBackIdentically() {
        val data = DemoData.generate(LocalDate.parse("2026-05-10"), days = 20)
        repo.inTransaction { DemoData.store(repo, data) }
        assertEquals(data.activities.size, repo.countActivities())
        val first = data.activities.first()
        val loaded = repo.getActivity(first.id)!!
        assertEquals(first.track.size, loaded.track.size)
        assertEquals(first.copy(track = emptyList()), loaded.copy(track = emptyList()))
        assertEquals(data.dailies, repo.listDaily(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-05-31")))
        assertEquals(data.sleeps, repo.listSleep(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-05-31")))
    }

    @Test
    fun reportsAreStoredPerKindAndSubject() {
        val report = StoredReport("activity", "a:1", "text", "template", null, "hash", Instant.parse("2026-05-02T08:00:00Z"))
        repo.saveReport(report)
        assertEquals(report, repo.getReport("activity", "a:1"))
        assertNull(repo.getReport("weekly", "a:1"))
        repo.saveReport(report.copy(text = "newer", factsHash = "h2"))
        assertEquals("newer", repo.getReport("activity", "a:1")!!.text)
    }

    @Test
    fun clearAllEmptiesEveryTable() {
        repo.inTransaction { DemoData.store(repo, DemoData.generate(LocalDate.parse("2026-05-10"), days = 7)) }
        repo.saveReport(StoredReport("weekly", "w", "t", "template", null, "h", Instant.now()))
        repo.clearAll()
        assertEquals(0, repo.countActivities())
        assertEquals(emptyList(), repo.listDaily(LocalDate.parse("2000-01-01"), LocalDate.parse("2100-01-01")))
        assertEquals(emptyList(), repo.listSleep(LocalDate.parse("2000-01-01"), LocalDate.parse("2100-01-01")))
        assertNull(repo.getReport("weekly", "w"))
    }
}
