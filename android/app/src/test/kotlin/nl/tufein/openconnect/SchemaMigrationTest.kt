package nl.tufein.openconnect

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.TrackCodec
import nl.tufein.openconnect.data.SqliteRepository
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v0.56.0 (milestone M3, "your data is safe"): the database of every schema version that was ever released is rebuilt here
 * exactly as that release created it, filled, and opened by the current code. Nothing may be lost, the result must have
 * the same tables, columns and indexes as a fresh install, and the only thing a migration may delete is documented below.
 *
 * **When the schema changes** (`SqliteRepository.VERSION` goes up) add the next fixture here; the last test fails until
 * you do. The fixtures are written out in full and never generated from the current code, because a test that builds the
 * old database with today's code proves nothing about yesterday's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchemaMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After
    fun clean() {
        context.deleteDatabase(name)
        context.deleteDatabase("fresh-test.db")
    }

    // ----------------------------------------------------------------------------------------------- the old schemas

    private val activities = """CREATE TABLE activities (
        id TEXT PRIMARY KEY, source TEXT NOT NULL, external_id TEXT, name TEXT NOT NULL,
        sport TEXT NOT NULL, start_ms INTEGER NOT NULL, duration_s REAL NOT NULL,
        distance_m REAL NOT NULL, elevation_m REAL NOT NULL, avg_hr INTEGER, max_hr INTEGER,
        calories INTEGER, device TEXT, track BLOB NOT NULL)"""
    private val activitiesIndex = "CREATE INDEX idx_activities_start ON activities(start_ms)"
    private val dailyV1 = """CREATE TABLE daily (
        date TEXT PRIMARY KEY, steps INTEGER NOT NULL, steps_goal INTEGER, distance_m REAL NOT NULL,
        active_kcal INTEGER NOT NULL, floors INTEGER NOT NULL, moderate_min INTEGER NOT NULL,
        vigorous_min INTEGER NOT NULL, resting_hr INTEGER, min_hr INTEGER, max_hr INTEGER,
        avg_stress INTEGER, max_stress INTEGER, bb_high INTEGER, bb_low INTEGER)"""
    private val dailyV2 = """CREATE TABLE daily (
        date TEXT PRIMARY KEY, steps INTEGER NOT NULL, steps_goal INTEGER, distance_m REAL NOT NULL,
        active_kcal INTEGER NOT NULL, floors INTEGER NOT NULL, moderate_min INTEGER NOT NULL,
        vigorous_min INTEGER NOT NULL, resting_hr INTEGER, min_hr INTEGER, max_hr INTEGER,
        avg_stress INTEGER, max_stress INTEGER, bb_high INTEGER, bb_low INTEGER,
        hrv_ms INTEGER, spo2_avg INTEGER, spo2_min INTEGER, resp_avg REAL)"""
    private val sleep = """CREATE TABLE sleep (
        date TEXT PRIMARY KEY, duration_s INTEGER NOT NULL, deep_s INTEGER NOT NULL,
        light_s INTEGER NOT NULL, rem_s INTEGER NOT NULL, awake_s INTEGER NOT NULL, score INTEGER)"""
    private val reports = """CREATE TABLE reports (
        kind TEXT NOT NULL, subject TEXT NOT NULL, text TEXT NOT NULL, provider TEXT NOT NULL,
        model TEXT, facts_hash TEXT NOT NULL, created_ms INTEGER NOT NULL,
        PRIMARY KEY (kind, subject))"""
    private val samples = """CREATE TABLE samples (
        metric INTEGER NOT NULL, ts INTEGER NOT NULL, day TEXT NOT NULL, value REAL NOT NULL,
        PRIMARY KEY (metric, ts)) WITHOUT ROWID"""
    private val samplesIndex = "CREATE INDEX idx_samples_day ON samples(day)"
    private val watchFiles = "CREATE TABLE watch_files (path TEXT PRIMARY KEY, size INTEGER NOT NULL)"
    private val oura = "CREATE TABLE oura_days (date TEXT PRIMARY KEY, json TEXT NOT NULL)"
    private val whoop = "CREATE TABLE whoop_days (date TEXT PRIMARY KEY, json TEXT NOT NULL)"
    private val hume = "CREATE TABLE hume_days (date TEXT PRIMARY KEY, json TEXT NOT NULL)"

    /** v1.0.6: the weight entered by hand, one row per day, body fat optional. */
    private val bodyWeight = "CREATE TABLE body_weight (date TEXT PRIMARY KEY, grams INTEGER NOT NULL, body_fat REAL)"

    /** A track of two points as `TrackCodec` wrote it in v0.2.0, frozen as bytes (gzip). */
    private val frozenTrack = (
        "1f8b08000000000000ff63646060606260609cfb483e9ec15e9ef78a033397d101470da070054300c3090638607ea12e2f2bd6c02c59cae06801e4573af0402400fcef0f4c43000000"
        ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** Schema versions: 1 = v0.2.0, 2 = v0.3.0 (watch), 3 = v0.17.0 (Oura), 4 = v0.49.0 (WHOOP), 5 = v0.50.0 (Hume). */
    private val versions = listOf(1, 2, 3, 4, 5, 6)

    /** The database as that release created it, with a little of everything in it. */
    private fun build(version: Int) {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL(activities)
            db.execSQL(activitiesIndex)
            db.execSQL(if (version == 1) dailyV1 else dailyV2)
            db.execSQL(sleep)
            db.execSQL(reports)
            if (version >= 2) {
                db.execSQL(samples)
                db.execSQL(samplesIndex)
                db.execSQL(watchFiles)
            }
            if (version >= 3) db.execSQL(oura)
            if (version >= 4) db.execSQL(whoop)
            if (version >= 5) db.execSQL(hume)
            if (version >= 6) db.execSQL(bodyWeight)

            db.execSQL("INSERT INTO daily (date, steps, steps_goal, distance_m, active_kcal, floors, moderate_min, vigorous_min, resting_hr, min_hr, max_hr, avg_stress, max_stress, bb_high, bb_low) VALUES ('2026-05-01', 8000, 10000, 6000.0, 300, 5, 20, 5, 52, 48, 150, 30, 80, 90, 20)")
            db.execSQL("INSERT INTO sleep VALUES ('2026-05-01', 27000, 5000, 14000, 6000, 2000, 84)")
            db.execSQL(
                "INSERT INTO activities VALUES ('garmin-1', 'garmin', '99', 'Morning run', 'running', 1777615200000, 1800.5, 5000.25, 31.0, 150, 175, 420, 'fenix 8', ?)",
                arrayOf<Any?>(frozenTrack),
            )
            if (version < 3) {
                // Until v0.17.0 ring workouts were stored among the activities; the migration to 3 removes exactly these.
                db.execSQL("INSERT INTO activities VALUES ('oura-1', 'oura', 'w1', 'Ring workout', 'running', 1777701600000, 900.0, 2000.0, 0.0, NULL, NULL, 200, NULL, ?)", arrayOf<Any?>(frozenTrack))
            }
            db.execSQL("INSERT INTO reports VALUES ('weekly', '2026-W18', 'A good week.', 'claude', 'claude-opus-5-5', 'abc123', 1777700000000)")
            if (version >= 2) {
                db.execSQL("INSERT INTO daily (date, steps, distance_m, active_kcal, floors, moderate_min, vigorous_min, hrv_ms, spo2_avg, spo2_min, resp_avg) VALUES ('2026-05-02', 100, 80.0, 5, 0, 0, 0, 55, 96, 91, 14.5)")
                db.execSQL("INSERT INTO samples VALUES (0, 1777615200, '2026-05-01', 61.0)")
                db.execSQL("INSERT INTO samples VALUES (0, 1777615260, '2026-05-01', 62.0)")
                db.execSQL("INSERT INTO watch_files VALUES ('Monitor/A.FIT', 1234)")
            }
            if (version >= 3) db.execSQL("""INSERT INTO oura_days VALUES ('2026-05-01', '{"day":"2026-05-01","readiness":81}')""")
            if (version >= 4) db.execSQL("""INSERT INTO whoop_days VALUES ('2026-05-01', '{"day":"2026-05-01","recovery":70}')""")
            if (version >= 5) db.execSQL("""INSERT INTO hume_days VALUES ('2026-05-01', '{"day":"2026-05-01","steps":4000}')""")
            if (version >= 6) db.execSQL("INSERT INTO body_weight VALUES ('2026-05-03', 81500, 17.0)")
            db.version = version
        }
    }

    // -------------------------------------------------------------------------------------------------- the checks

    private fun SQLiteDatabase.scalar(sql: String): String? = rawQuery(sql, null).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun SQLiteDatabase.columnsOf(table: String): Set<String> = rawQuery("PRAGMA table_info($table)", null).use { c ->
        val out = HashSet<String>()
        while (c.moveToNext()) out += "${c.getString(1)} ${c.getString(2)} notnull=${c.getInt(3)} pk=${c.getInt(5)}"
        out
    }

    private fun SQLiteDatabase.names(type: String): Set<String> = rawQuery("SELECT name FROM sqlite_master WHERE type='$type' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'", null).use { c ->
        val out = HashSet<String>()
        while (c.moveToNext()) out += c.getString(0)
        out
    }

    private fun upgraded(version: Int): SqliteRepository {
        build(version)
        return SqliteRepository(context, name = name)
    }

    @Test
    fun `a database of every released schema opens and keeps all its rows`() {
        for (version in versions) {
            val repo = upgraded(version)
            try {
                val db = repo.writableDatabase
                assertEquals("8000", db.scalar("SELECT steps FROM daily WHERE date='2026-05-01'"), "v$version: daily steps")
                assertEquals("90", db.scalar("SELECT bb_high FROM daily WHERE date='2026-05-01'"), "v$version: Body Battery")
                assertEquals("84", db.scalar("SELECT score FROM sleep WHERE date='2026-05-01'"), "v$version: sleep score")
                assertEquals("A good week.", db.scalar("SELECT text FROM reports WHERE kind='weekly'"), "v$version: report")
                assertEquals("Morning run", db.scalar("SELECT name FROM activities WHERE id='garmin-1'"), "v$version: activity")
                val track = db.rawQuery("SELECT track FROM activities WHERE id='garmin-1'", null).use { it.moveToFirst(); it.getBlob(0) }
                assertTrue(frozenTrack.contentEquals(track), "v$version: the track bytes are untouched")
                assertEquals(2, TrackCodec.decode(track).size, "v$version: the old track still decodes")
                if (version >= 2) {
                    assertEquals("55", db.scalar("SELECT hrv_ms FROM daily WHERE date='2026-05-02'"), "v$version: HRV")
                    assertEquals("2", db.scalar("SELECT COUNT(*) FROM samples"), "v$version: samples")
                    assertEquals("1234", db.scalar("SELECT size FROM watch_files WHERE path='Monitor/A.FIT'"), "v$version: watch files")
                }
                if (version >= 3) assertEquals("""{"day":"2026-05-01","readiness":81}""", db.scalar("SELECT json FROM oura_days"), "v$version: Oura")
                if (version >= 4) assertEquals("""{"day":"2026-05-01","recovery":70}""", db.scalar("SELECT json FROM whoop_days"), "v$version: WHOOP")
                if (version >= 5) assertEquals("""{"day":"2026-05-01","steps":4000}""", db.scalar("SELECT json FROM hume_days"), "v$version: Hume")
            } finally {
                repo.close()
            }
        }
    }

    @Test
    fun `the application can read and write the upgraded database`() {
        for (version in versions) {
            val repo = upgraded(version)
            try {
                val day = LocalDate.parse("2026-05-01")
                val daily = repo.listDaily(day, day).single()
                assertEquals(8000, daily.steps, "v$version")
                assertEquals(1, repo.listSleep(day, day).size, "v$version")
                assertEquals("Morning run", repo.getActivity("garmin-1")!!.name, "v$version")
                assertEquals(2, repo.getActivity("garmin-1")!!.track.size, "v$version")
                // and it still takes new data, in the tables the old schema did not have
                repo.upsertDaily(daily.copy(steps = 8001, hrvMs = 60))
                assertEquals(8001, repo.listDaily(day, day).single().steps, "v$version")
            } finally {
                repo.close()
            }
        }
    }

    @Test
    fun `the only thing a migration deletes is the ring's workouts from before schema 3`() {
        for (version in versions) {
            val repo = upgraded(version)
            try {
                val ring = repo.writableDatabase.scalar("SELECT COUNT(*) FROM activities WHERE source='oura'")
                assertEquals("0", ring, "v$version: ring workouts no longer sit among the activities")
                val all = repo.writableDatabase.scalar("SELECT COUNT(*) FROM activities")
                assertEquals("1", all, "v$version: the Garmin activity stays")
            } finally {
                repo.close()
            }
        }
    }

    @Test
    fun `an upgraded database has the same tables, columns and indexes as a fresh install`() {
        context.deleteDatabase("fresh-test.db")
        val fresh = SqliteRepository(context, name = "fresh-test.db")
        val wanted = fresh.writableDatabase.let { db -> db.names("table") to db.names("index") }
        val wantedColumns = fresh.writableDatabase.let { db -> wanted.first.associateWith { t -> db.columnsOf(t) } }
        fresh.close()
        for (version in versions) {
            val repo = upgraded(version)
            try {
                val db = repo.writableDatabase
                assertEquals(wanted.first, db.names("table"), "v$version: tables")
                assertEquals(wanted.second, db.names("index"), "v$version: indexes")
                for (table in wanted.first) assertEquals(wantedColumns.getValue(table), db.columnsOf(table), "v$version: columns of $table")
            } finally {
                repo.close()
            }
        }
    }

    @Test
    fun `every schema version has a fixture here (add the next one when the schema changes)`() {
        assertEquals(SqliteRepository.VERSION, versions.max(), "SqliteRepository.VERSION changed: add a fixture for the new schema to SchemaMigrationTest")
        assertEquals((1..SqliteRepository.VERSION).toList(), versions, "no schema version may be skipped")
    }
}
