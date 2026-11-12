package nl.tufein.openconnect

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.DemoData
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.StoredReport
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.core.watch.Metric
import nl.tufein.openconnect.core.watch.MetricSample
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

    // ------------------------------------------------------------------------ watch data (schema 2)

    private fun sample(metric: Metric, time: String, value: Double, day: String = time.take(10)) =
        MetricSample(metric, Instant.parse(time), LocalDate.parse(day), value)

    @Test
    fun samplesAreStoredOncePerMetricAndTime() {
        val first = listOf(
            sample(Metric.HEART_RATE, "2026-06-10T08:00:00Z", 60.0),
            sample(Metric.HEART_RATE, "2026-06-10T08:01:00Z", 61.0),
            sample(Metric.STRESS, "2026-06-10T08:00:00Z", 25.0),
            sample(Metric.RESPIRATION, "2026-06-10T22:30:00Z", 14.5, day = "2026-06-11"),
        )
        repo.upsertSamples(first)
        repo.upsertSamples(first + sample(Metric.HEART_RATE, "2026-06-10T08:01:00Z", 99.0)) // same key: replaces

        val day = repo.listSamples(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))
        assertEquals(3, day.size)
        assertEquals(99.0, day.single { it.metric == Metric.HEART_RATE && it.time == Instant.parse("2026-06-10T08:01:00Z") }.value)
        assertEquals(listOf(Instant.parse("2026-06-10T08:00:00Z"), Instant.parse("2026-06-10T08:00:00Z"), Instant.parse("2026-06-10T08:01:00Z")), day.map { it.time })

        assertEquals(1, repo.listSamples(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-11"), Metric.RESPIRATION).size)
        assertEquals(14.5, repo.listSamples(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-11")).single().value)
        assertTrue(repo.listSamples(LocalDate.parse("2026-06-12"), LocalDate.parse("2026-06-13")).isEmpty())
    }

    @Test
    fun theDayOfASampleIsTheWatchsDayNotUtc() {
        // 22:10 UTC is already the 11th on a UTC+2 watch
        repo.upsertSamples(listOf(sample(Metric.STEPS_WALKING, "2026-06-10T22:10:00Z", 40.0, day = "2026-06-11")))
        assertTrue(repo.listSamples(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10")).isEmpty())
        assertEquals(1, repo.listSamples(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-11")).size)
    }

    @Test
    fun dailySummaryKeepsTheWatchFields() {
        val day = DailySummary(
            date = LocalDate.parse("2026-06-10"), steps = 10_200, hrvMs = 52, spo2Avg = 96, spo2Min = 94, respirationAvg = 15.0,
            bodyBatteryHigh = 90, bodyBatteryLow = 60,
        )
        repo.upsertDaily(day)
        assertEquals(day, repo.listDaily(day.date, day.date).single())
        val bare = DailySummary(date = LocalDate.parse("2026-06-11"))
        repo.upsertDaily(bare)
        assertEquals(bare, repo.listDaily(bare.date, bare.date).single())
    }

    @Test
    fun watchFilesRememberTheirSizeAndDeleteAllForgetsThem() {
        assertNull(repo.watchFileSize("Monitor/A.FIT"))
        repo.markWatchFile("Monitor/A.FIT", 1234)
        repo.markWatchFile("Monitor/A.FIT", 2345) // it grew
        assertEquals(2345L, repo.watchFileSize("Monitor/A.FIT"))

        repo.upsertSamples(listOf(sample(Metric.HEART_RATE, "2026-06-10T08:00:00Z", 60.0)))
        repo.clearAll()
        assertNull(repo.watchFileSize("Monitor/A.FIT"))
        assertTrue(repo.listSamples(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30")).isEmpty())
    }

    @Test
    fun aVersion1DatabaseIsUpgradedWithoutLosingData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("upgrade-test.db")
        // The schema exactly as v0.2.0 created it.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("upgrade-test.db"), null).use { db ->
            db.execSQL(
                """CREATE TABLE daily (
                    date TEXT PRIMARY KEY, steps INTEGER NOT NULL, steps_goal INTEGER, distance_m REAL NOT NULL,
                    active_kcal INTEGER NOT NULL, floors INTEGER NOT NULL, moderate_min INTEGER NOT NULL,
                    vigorous_min INTEGER NOT NULL, resting_hr INTEGER, min_hr INTEGER, max_hr INTEGER,
                    avg_stress INTEGER, max_stress INTEGER, bb_high INTEGER, bb_low INTEGER)""",
            )
            db.execSQL("INSERT INTO daily VALUES ('2026-05-01', 8000, 10000, 6000.0, 300, 5, 20, 5, 52, 48, 150, 30, 80, 90, 20)")
            db.execSQL("CREATE TABLE sleep (date TEXT PRIMARY KEY, duration_s INTEGER NOT NULL, deep_s INTEGER NOT NULL, light_s INTEGER NOT NULL, rem_s INTEGER NOT NULL, awake_s INTEGER NOT NULL, score INTEGER)")
            db.execSQL("CREATE TABLE activities (id TEXT PRIMARY KEY, source TEXT NOT NULL, external_id TEXT, name TEXT NOT NULL, sport TEXT NOT NULL, start_ms INTEGER NOT NULL, duration_s REAL NOT NULL, distance_m REAL NOT NULL, elevation_m REAL NOT NULL, avg_hr INTEGER, max_hr INTEGER, calories INTEGER, device TEXT, track BLOB NOT NULL)")
            db.execSQL("CREATE TABLE reports (kind TEXT NOT NULL, subject TEXT NOT NULL, text TEXT NOT NULL, provider TEXT NOT NULL, model TEXT, facts_hash TEXT NOT NULL, created_ms INTEGER NOT NULL, PRIMARY KEY (kind, subject))")
            db.version = 1
        }

        val upgraded = SqliteRepository(context, name = "upgrade-test.db")
        try {
            val old = upgraded.listDaily(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-01")).single()
            assertEquals(8000, old.steps)
            assertEquals(90, old.bodyBatteryHigh)
            assertNull(old.hrvMs)
            // and the new parts work
            upgraded.upsertDaily(old.copy(hrvMs = 48, spo2Avg = 95))
            assertEquals(48, upgraded.listDaily(old.date, old.date).single().hrvMs)
            upgraded.upsertSamples(listOf(sample(Metric.HEART_RATE, "2026-05-01T08:00:00Z", 60.0)))
            upgraded.markWatchFile("Sleep/X.FIT", 10)
            assertEquals(10L, upgraded.watchFileSize("Sleep/X.FIT"))
        } finally {
            upgraded.close()
            context.deleteDatabase("upgrade-test.db")
        }
    }
}
