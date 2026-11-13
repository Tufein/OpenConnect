package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.watch.Metric
import nl.tufein.openconnect.core.watch.MetricSample
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackupTest {
    private val day = LocalDate.parse("2026-06-10")

    private fun filled(): InMemoryRepository {
        val repo = InMemoryRepository()
        repo.upsertActivity(
            Activity(
                id = "a1", source = "garmin", externalId = "99", name = "Morning \"run\" ünïcode", sport = Sport.RUNNING,
                startTime = Instant.parse("2026-06-10T06:00:00Z"), durationS = 1800.5, distanceM = 5000.25, elevationGainM = 31.0,
                avgHr = 150, maxHr = 175, calories = 420, deviceName = "fenix 8",
                track = listOf(
                    TrackPoint(Instant.parse("2026-06-10T06:00:00Z"), 52.1, 5.1, 10.5, 120, 80, 200, 0.0),
                    TrackPoint(Instant.parse("2026-06-10T06:00:01Z")),
                ),
            ),
        )
        repo.upsertActivity(Activity(id = "a2", startTime = Instant.parse("2026-06-11T06:00:00Z")))
        repo.upsertDaily(
            DailySummary(
                date = day, steps = 9000, stepsGoal = 10000, distanceM = 7000.0, activeKcal = 400, floorsClimbed = 8,
                moderateIntensityMin = 20, vigorousIntensityMin = 10, restingHr = 52, minHr = 45, maxHr = 160, avgStress = 30,
                maxStress = 80, bodyBatteryHigh = 90, bodyBatteryLow = 20, hrvMs = 55, spo2Avg = 96, spo2Min = 91, respirationAvg = 14.5,
            ),
        )
        repo.upsertDaily(DailySummary(date = day.plusDays(1)))
        repo.upsertSleep(SleepSummary(date = day, durationS = 27000, deepS = 5000, lightS = 14000, remS = 6000, awakeS = 2000, score = 84))
        repo.upsertSleep(SleepSummary(date = day.plusDays(1), durationS = 3600))
        repo.upsertSamples(
            listOf(
                MetricSample(Metric.HEART_RATE, Instant.parse("2026-06-10T08:00:00Z"), day, 61.0),
                MetricSample(Metric.STRESS, Instant.parse("2026-06-10T08:05:00Z"), day, 25.5),
            ),
        )
        repo.upsertOuraDay(
            nl.tufein.openconnect.core.oura.OuraDay(
                day = day.toString(), readiness = 81, sleepS = 27000, spo2Avg = 96.6, temperatureDeviation = -0.1,
                workouts = listOf(nl.tufein.openconnect.core.oura.OuraWorkout("w1", "running", null, 1_000L, 2_000_000L, 300, 5000.0)),
            ),
        )
        return repo
    }

    private fun snapshot(repo: Repository) = listOf(
        repo.listActivities(limit = 10_000).map { repo.getActivity(it.id) },
        repo.listDaily(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)),
        repo.listSleep(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)),
        repo.listSamples(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)),
        repo.listOuraDays(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)),
    )

    @Test
    fun `everything comes back exactly as it went in`() {
        val source = filled()
        val bytes = Backup.write(source, now = Instant.parse("2026-11-13T12:00:00Z"))
        val target = InMemoryRepository()
        val report = Backup.restore(bytes, target)
        assertEquals(snapshot(source), snapshot(target))
        assertEquals(2, report.activities)
        assertEquals(2, report.days)
        assertEquals(2, report.nights)
        assertEquals(2, report.samples)
        assertEquals(Instant.parse("2026-11-13T12:00:00Z"), report.createdAt)
    }

    @Test
    fun `the file is compressed and starts with the gzip marker`() {
        val bytes = Backup.write(filled())
        assertEquals(0x1f, bytes[0].toInt() and 0xff)
        assertEquals(0x8b, bytes[1].toInt() and 0xff)
    }

    @Test
    fun `restoring twice changes nothing`() {
        val bytes = Backup.write(filled())
        val target = InMemoryRepository()
        Backup.restore(bytes, target)
        val once = snapshot(target)
        Backup.restore(bytes, target)
        assertEquals(once, snapshot(target))
    }

    @Test
    fun `restore merges into what is there and keeps other data`() {
        val target = InMemoryRepository()
        target.upsertActivity(Activity(id = "other", startTime = Instant.parse("2026-05-01T06:00:00Z")))
        Backup.restore(Backup.write(filled()), target)
        assertEquals(3, target.listActivities(limit = 100).size)
    }

    @Test
    fun `an empty app makes a valid backup`() {
        val report = Backup.restore(Backup.write(InMemoryRepository()), InMemoryRepository())
        assertEquals(0, report.activities)
    }

    @Test
    fun `a file that is not a backup is refused and changes nothing`() {
        val target = InMemoryRepository()
        assertFailsWith<BackupException> { Backup.restore("hello".toByteArray(), target) }
        assertFailsWith<BackupException> { Backup.restore(gzip("""{"hello": 1}"""), target) }
        assertFailsWith<BackupException> { Backup.restore(gzip("not json"), target) }
        assertTrue(target.listActivities().isEmpty())
    }

    @Test
    fun `a backup from a newer version is refused with a clear reason`() {
        val e = assertFailsWith<BackupException> {
            Backup.restore(gzip("""{"format":"openconnect-backup","version":99,"createdAt":"2026-01-01T00:00:00Z"}"""), InMemoryRepository())
        }
        assertTrue(e.message!!.contains("newer"), e.message)
    }

    @Test
    fun `a damaged entry stops the restore before anything is written`() {
        val bad = gzip(
            """{"format":"openconnect-backup","version":1,"createdAt":"2026-01-01T00:00:00Z",""" +
                """"activities":[{"id":"x","startTime":"not a time"}],"daily":[],"sleep":[],"samples":[]}""",
        )
        val target = InMemoryRepository()
        assertFailsWith<BackupException> { Backup.restore(bad, target) }
        assertTrue(target.listActivities().isEmpty())
    }

    @Test
    fun `unknown fields from a later minor addition are ignored`() {
        val ok = gzip(
            """{"format":"openconnect-backup","version":1,"createdAt":"2026-01-01T00:00:00Z","extra":true,""" +
                """"activities":[{"id":"x","startTime":"2026-06-10T06:00:00Z","future":"field"}],"daily":[],"sleep":[],"samples":[]}""",
        )
        val target = InMemoryRepository()
        assertEquals(1, Backup.restore(ok, target).activities)
        assertNotNull(target.getActivity("x"))
    }

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }

    @Test
    fun `a plain unzipped json file is not accepted`() {
        assertFailsWith<BackupException> { Backup.restore("""{"format":"openconnect-backup","version":1}""".toByteArray(), InMemoryRepository()) }
    }
}
