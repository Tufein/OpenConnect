package nl.tufein.openconnect.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.StoredReport
import nl.tufein.openconnect.core.TrackCodec
import java.time.Instant
import java.time.LocalDate

/** SQLite storage for everything the app keeps. Health data never leaves this file. */
class SqliteRepository(context: Context, name: String? = "openconnect.db") :
    SQLiteOpenHelper(context, name, null, VERSION),
    Repository {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE activities (
                id TEXT PRIMARY KEY, source TEXT NOT NULL, external_id TEXT, name TEXT NOT NULL,
                sport TEXT NOT NULL, start_ms INTEGER NOT NULL, duration_s REAL NOT NULL,
                distance_m REAL NOT NULL, elevation_m REAL NOT NULL, avg_hr INTEGER, max_hr INTEGER,
                calories INTEGER, device TEXT, track BLOB NOT NULL)""",
        )
        db.execSQL("CREATE INDEX idx_activities_start ON activities(start_ms)")
        db.execSQL(
            """CREATE TABLE daily (
                date TEXT PRIMARY KEY, steps INTEGER NOT NULL, steps_goal INTEGER, distance_m REAL NOT NULL,
                active_kcal INTEGER NOT NULL, floors INTEGER NOT NULL, moderate_min INTEGER NOT NULL,
                vigorous_min INTEGER NOT NULL, resting_hr INTEGER, min_hr INTEGER, max_hr INTEGER,
                avg_stress INTEGER, max_stress INTEGER, bb_high INTEGER, bb_low INTEGER)""",
        )
        db.execSQL(
            """CREATE TABLE sleep (
                date TEXT PRIMARY KEY, duration_s INTEGER NOT NULL, deep_s INTEGER NOT NULL,
                light_s INTEGER NOT NULL, rem_s INTEGER NOT NULL, awake_s INTEGER NOT NULL, score INTEGER)""",
        )
        db.execSQL(
            """CREATE TABLE reports (
                kind TEXT NOT NULL, subject TEXT NOT NULL, text TEXT NOT NULL, provider TEXT NOT NULL,
                model TEXT, facts_hash TEXT NOT NULL, created_ms INTEGER NOT NULL,
                PRIMARY KEY (kind, subject))""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first schema; migrations go here.
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    // ------------------------------------------------------------------------- activities

    override fun upsertActivity(activity: Activity) {
        val values = ContentValues().apply {
            put("id", activity.id)
            put("source", activity.source)
            put("external_id", activity.externalId)
            put("name", activity.name)
            put("sport", activity.sport.key)
            put("start_ms", activity.startTime.toEpochMilli())
            put("duration_s", activity.durationS)
            put("distance_m", activity.distanceM)
            put("elevation_m", activity.elevationGainM)
            put("avg_hr", activity.avgHr)
            put("max_hr", activity.maxHr)
            put("calories", activity.calories)
            put("device", activity.deviceName)
            put("track", TrackCodec.encode(activity.track))
        }
        writableDatabase.insertWithOnConflict("activities", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun Cursor.activity(withTrack: Boolean): Activity = Activity(
        id = getString(getColumnIndexOrThrow("id")),
        source = getString(getColumnIndexOrThrow("source")),
        externalId = stringOrNull("external_id"),
        name = getString(getColumnIndexOrThrow("name")),
        sport = Sport.fromKey(getString(getColumnIndexOrThrow("sport"))),
        startTime = Instant.ofEpochMilli(getLong(getColumnIndexOrThrow("start_ms"))),
        durationS = getDouble(getColumnIndexOrThrow("duration_s")),
        distanceM = getDouble(getColumnIndexOrThrow("distance_m")),
        elevationGainM = getDouble(getColumnIndexOrThrow("elevation_m")),
        avgHr = intOrNull("avg_hr"),
        maxHr = intOrNull("max_hr"),
        calories = intOrNull("calories"),
        deviceName = stringOrNull("device"),
        track = if (withTrack) TrackCodec.decode(getBlob(getColumnIndexOrThrow("track"))) else emptyList(),
    )

    override fun getActivity(id: String): Activity? =
        readableDatabase.query("activities", null, "id = ?", arrayOf(id), null, null, null).use {
            if (it.moveToFirst()) it.activity(withTrack = true) else null
        }

    override fun listActivities(sport: Sport?, start: Instant?, end: Instant?, limit: Int, offset: Int): List<Activity> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        if (sport != null) {
            where.add("sport = ?")
            args.add(sport.key)
        }
        if (start != null) {
            where.add("start_ms >= ?")
            args.add(start.toEpochMilli().toString())
        }
        if (end != null) {
            where.add("start_ms < ?")
            args.add(end.toEpochMilli().toString())
        }
        // The track blob is deliberately not selected: listings only need the summary.
        val columns = arrayOf(
            "id", "source", "external_id", "name", "sport", "start_ms", "duration_s", "distance_m",
            "elevation_m", "avg_hr", "max_hr", "calories", "device",
        )
        readableDatabase.query(
            "activities", columns, where.joinToString(" AND ").ifEmpty { null }, args.toTypedArray().ifEmpty { null },
            null, null, "start_ms DESC", "$offset, $limit",
        ).use { cursor ->
            val out = ArrayList<Activity>(cursor.count)
            while (cursor.moveToNext()) out.add(cursor.activity(withTrack = false))
            return out
        }
    }

    override fun deleteActivity(id: String) {
        writableDatabase.delete("activities", "id = ?", arrayOf(id))
    }

    fun countActivities(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM activities", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ------------------------------------------------------------------------------- health

    override fun upsertDaily(daily: DailySummary) {
        val values = ContentValues().apply {
            put("date", daily.date.toString())
            put("steps", daily.steps)
            put("steps_goal", daily.stepsGoal)
            put("distance_m", daily.distanceM)
            put("active_kcal", daily.activeKcal)
            put("floors", daily.floorsClimbed)
            put("moderate_min", daily.moderateIntensityMin)
            put("vigorous_min", daily.vigorousIntensityMin)
            put("resting_hr", daily.restingHr)
            put("min_hr", daily.minHr)
            put("max_hr", daily.maxHr)
            put("avg_stress", daily.avgStress)
            put("max_stress", daily.maxStress)
            put("bb_high", daily.bodyBatteryHigh)
            put("bb_low", daily.bodyBatteryLow)
        }
        writableDatabase.insertWithOnConflict("daily", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun listDaily(start: LocalDate, end: LocalDate): List<DailySummary> =
        readableDatabase.query("daily", null, "date >= ? AND date <= ?", arrayOf(start.toString(), end.toString()), null, null, "date ASC")
            .use { c ->
                val out = ArrayList<DailySummary>(c.count)
                while (c.moveToNext()) {
                    out.add(
                        DailySummary(
                            date = LocalDate.parse(c.getString(c.getColumnIndexOrThrow("date"))),
                            steps = c.getInt(c.getColumnIndexOrThrow("steps")),
                            stepsGoal = c.intOrNull("steps_goal"),
                            distanceM = c.getDouble(c.getColumnIndexOrThrow("distance_m")),
                            activeKcal = c.getInt(c.getColumnIndexOrThrow("active_kcal")),
                            floorsClimbed = c.getInt(c.getColumnIndexOrThrow("floors")),
                            moderateIntensityMin = c.getInt(c.getColumnIndexOrThrow("moderate_min")),
                            vigorousIntensityMin = c.getInt(c.getColumnIndexOrThrow("vigorous_min")),
                            restingHr = c.intOrNull("resting_hr"),
                            minHr = c.intOrNull("min_hr"),
                            maxHr = c.intOrNull("max_hr"),
                            avgStress = c.intOrNull("avg_stress"),
                            maxStress = c.intOrNull("max_stress"),
                            bodyBatteryHigh = c.intOrNull("bb_high"),
                            bodyBatteryLow = c.intOrNull("bb_low"),
                        ),
                    )
                }
                out
            }

    override fun upsertSleep(sleep: SleepSummary) {
        val values = ContentValues().apply {
            put("date", sleep.date.toString())
            put("duration_s", sleep.durationS)
            put("deep_s", sleep.deepS)
            put("light_s", sleep.lightS)
            put("rem_s", sleep.remS)
            put("awake_s", sleep.awakeS)
            put("score", sleep.score)
        }
        writableDatabase.insertWithOnConflict("sleep", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun listSleep(start: LocalDate, end: LocalDate): List<SleepSummary> =
        readableDatabase.query("sleep", null, "date >= ? AND date <= ?", arrayOf(start.toString(), end.toString()), null, null, "date ASC")
            .use { c ->
                val out = ArrayList<SleepSummary>(c.count)
                while (c.moveToNext()) {
                    out.add(
                        SleepSummary(
                            date = LocalDate.parse(c.getString(c.getColumnIndexOrThrow("date"))),
                            durationS = c.getInt(c.getColumnIndexOrThrow("duration_s")),
                            deepS = c.getInt(c.getColumnIndexOrThrow("deep_s")),
                            lightS = c.getInt(c.getColumnIndexOrThrow("light_s")),
                            remS = c.getInt(c.getColumnIndexOrThrow("rem_s")),
                            awakeS = c.getInt(c.getColumnIndexOrThrow("awake_s")),
                            score = c.intOrNull("score"),
                        ),
                    )
                }
                out
            }

    // ------------------------------------------------------------------------------ reports

    override fun getReport(kind: String, subject: String): StoredReport? =
        readableDatabase.query("reports", null, "kind = ? AND subject = ?", arrayOf(kind, subject), null, null, null).use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                StoredReport(
                    kind = kind,
                    subject = subject,
                    text = c.getString(c.getColumnIndexOrThrow("text")),
                    provider = c.getString(c.getColumnIndexOrThrow("provider")),
                    model = c.stringOrNull("model"),
                    factsHash = c.getString(c.getColumnIndexOrThrow("facts_hash")),
                    createdAt = Instant.ofEpochMilli(c.getLong(c.getColumnIndexOrThrow("created_ms"))),
                )
            }
        }

    override fun saveReport(report: StoredReport) {
        val values = ContentValues().apply {
            put("kind", report.kind)
            put("subject", report.subject)
            put("text", report.text)
            put("provider", report.provider)
            put("model", report.model)
            put("facts_hash", report.factsHash)
            put("created_ms", report.createdAt.toEpochMilli())
        }
        writableDatabase.insertWithOnConflict("reports", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun clearAll() {
        writableDatabase.beginTransaction()
        try {
            for (table in listOf("activities", "daily", "sleep", "reports")) writableDatabase.delete(table, null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /** Run many writes in one transaction (a demo load is thousands of rows). */
    fun <T> inTransaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val VERSION = 1
    }
}

private fun Cursor.intOrNull(column: String): Int? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }

private fun Cursor.stringOrNull(column: String): String? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
