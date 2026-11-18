package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.formats.Formats
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v0.56.0 (milestone M3): "Export everything" is one ZIP that a person can open without OpenConnect (CSV, FIT, GPX) and
 * that OpenConnect can read back (the backup file inside). The test reads the ZIP back the way a stranger would.
 */
class ExportAllTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val day = LocalDate.parse("2026-06-10")

    private fun run(id: String, start: String, gps: Boolean) = Activity(
        id = id, source = "garmin", name = "Run $id", sport = Sport.RUNNING, startTime = Instant.parse(start),
        durationS = 120.0, distanceM = 400.0, avgHr = 150,
        track = (0 until 4).map { i ->
            TrackPoint(
                Instant.parse(start).plusSeconds(i * 30L),
                lat = if (gps) 52.0 + i * 0.001 else null, lon = if (gps) 5.0 + i * 0.001 else null,
                ele = if (gps) 10.0 else null, hr = 140 + i,
            )
        },
    )

    private fun filled(): InMemoryRepository {
        val repo = InMemoryRepository()
        repo.upsertActivity(run("a1", "2026-06-10T06:00:00Z", gps = true))
        repo.upsertActivity(run("a2", "2026-06-10T06:00:00Z", gps = true)) // same minute and sport: the name must not collide
        repo.upsertActivity(run("a3", "2026-06-11T06:00:00Z", gps = false)) // indoor: no GPX
        repo.upsertDaily(DailySummary(date = day, steps = 9000, restingHr = 52))
        repo.upsertSleep(SleepSummary(date = day, durationS = 27000, deepS = 5000, lightS = 14000, remS = 6000, awakeS = 2000, score = 84))
        return repo
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                out[entry.name] = zip.readBytes()
            }
        }
        return out
    }

    private fun export(repo: Repository, user: UserData = UserData()): Map<String, ByteArray> {
        val out = ByteArrayOutputStream()
        ExportAll.write(repo, user, now, out)
        return unzip(out.toByteArray())
    }

    @Test
    fun `the zip holds a readme, both csv files, the activities as FIT and GPX and the backup`() {
        val files = export(filled())
        assertTrue("README.txt" in files)
        assertTrue("daily.csv" in files)
        assertTrue("activities.csv" in files)
        assertTrue("openconnect-backup.ocbak" in files)
        assertEquals(3, files.keys.count { it.startsWith("activities/") && it.endsWith(".fit") }, "every activity as FIT")
        assertEquals(2, files.keys.count { it.startsWith("activities/") && it.endsWith(".gpx") }, "GPX only where there is a position")
        assertEquals(files.keys.size, files.keys.toSet().size)
    }

    @Test
    fun `two activities that start in the same minute do not overwrite each other`() {
        val names = export(filled()).keys.filter { it.startsWith("activities/") }
        assertEquals(names.size, names.toSet().size, "every file name is unique")
    }

    @Test
    fun `the files read back as what went in`() {
        val repo = filled()
        val files = export(repo)
        val csv = String(files.getValue("daily.csv"), Charsets.UTF_8)
        assertEquals(Csv.dailies(repo.listDaily(day, day), repo.listSleep(day, day)), csv)
        assertTrue("9000" in csv)
        val gpx = files.entries.first { it.key.endsWith(".gpx") }
        val back = Formats.import(gpx.value, gpx.key)
        assertEquals(4, back.track.size)
        val fit = files.entries.first { it.key.endsWith(".fit") }
        assertEquals(4, Formats.import(fit.value, fit.key).track.size)
    }

    @Test
    fun `the backup inside restores everything, including the user data`() {
        val user = UserData(maxHr = 188, raceGoal = "2026-10-11|half|6000")
        val files = export(filled(), user)
        val target = InMemoryRepository()
        val report = Backup.restore(files.getValue("openconnect-backup.ocbak"), target)
        assertEquals(3, report.activities)
        assertEquals(1, report.days)
        assertEquals(user, report.userData)
    }

    @Test
    fun `the readme says what is inside and that no keys are`() {
        val text = String(export(filled()).getValue("README.txt"), Charsets.UTF_8)
        assertTrue("2026-11-18" in text)
        assertTrue("no keys" in text.lowercase())
        assertTrue("openconnect-backup.ocbak" in text)
    }

    @Test
    fun `an empty app makes a valid zip`() {
        val files = export(InMemoryRepository())
        assertTrue("README.txt" in files)
        assertTrue(files.keys.none { it.startsWith("activities/") })
    }
}
