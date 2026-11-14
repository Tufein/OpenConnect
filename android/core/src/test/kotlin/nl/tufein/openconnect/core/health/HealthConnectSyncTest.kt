package nl.tufein.openconnect.core.health

import kotlinx.coroutines.runBlocking
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.Sport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthConnectSyncTest {
    private class Fake(
        val stepsByDay: Map<LocalDate, Int> = emptyMap(),
        val sleepByDay: Map<LocalDate, Int> = emptyMap(),
    ) : HealthConnectGateway {
        val written = ArrayList<HealthSession>()
        var batches = 0
        override suspend fun access() = HealthAccess.READY
        override suspend fun write(sessions: List<HealthSession>): Int {
            batches++
            written += sessions
            return sessions.size
        }
        override suspend fun steps(from: LocalDate, to: LocalDate) = stepsByDay.filterKeys { it in from..to }
        override suspend fun sleep(from: LocalDate, to: LocalDate) = sleepByDay.filterKeys { it in from..to }
    }

    private val repo = InMemoryRepository()
    private val today = LocalDate.parse("2026-11-13")

    private fun sync(g: HealthConnectGateway) = HealthConnectSync(repo, g, ZoneOffset.UTC)

    @Test
    fun `activities go out under a stable id with their end time, and the ones that came from Health Connect stay out`() = runBlocking {
        repo.upsertActivity(
            Activity(id = "a1", name = "Run", sport = Sport.RUNNING, startTime = Instant.parse("2026-11-12T08:00:00Z"), durationS = 1800.0, distanceM = 5000.0, calories = 400),
        )
        repo.upsertActivity(Activity(id = "h1", source = HealthConnectSync.SOURCE, startTime = Instant.parse("2026-11-11T08:00:00Z"), durationS = 600.0))
        val fake = Fake()
        val report = sync(fake).export()
        assertEquals(1, report.sent)
        val s = fake.written.single()
        assertEquals("openconnect-a1", s.clientId)
        assertEquals("running", s.sportKey)
        assertEquals(Instant.parse("2026-11-12T08:30:00Z"), s.end)
        assertEquals(400, s.calories)
    }

    @Test
    fun `sending twice sends the same ids, so Health Connect replaces instead of duplicating`() = runBlocking {
        repo.upsertActivity(Activity(id = "a1", startTime = Instant.parse("2026-11-12T08:00:00Z"), durationS = 60.0))
        val fake = Fake()
        sync(fake).export()
        sync(fake).export()
        assertEquals(listOf("openconnect-a1", "openconnect-a1"), fake.written.map { it.clientId })
    }

    @Test
    fun `an activity with no duration is skipped and counted, big lists go in batches`() = runBlocking {
        repo.upsertActivity(Activity(id = "zero", startTime = Instant.parse("2026-11-01T08:00:00Z"), durationS = 0.0))
        for (i in 1..250) repo.upsertActivity(Activity(id = "a$i", startTime = Instant.parse("2026-10-01T08:00:00Z").plusSeconds(i * 7200L), durationS = 600.0))
        val fake = Fake()
        val report = sync(fake).export()
        assertEquals(250, report.sent)
        assertEquals(1, report.skipped)
        assertEquals(3, fake.batches)
    }

    @Test
    fun `steps and sleep fill gaps only, what the watch gave is kept`() = runBlocking {
        val d1 = today.minusDays(1)
        val d2 = today.minusDays(2)
        val d3 = today.minusDays(3)
        repo.upsertDaily(DailySummary(date = d1, steps = 9000, restingHr = 50)) // watch data: kept
        repo.upsertDaily(DailySummary(date = d2, steps = 0, restingHr = 51)) // no steps yet: filled, rest kept
        repo.upsertSleep(SleepSummary(date = d1, durationS = 25_000, score = 80)) // kept
        val fake = Fake(
            stepsByDay = mapOf(d1 to 100, d2 to 4200, d3 to 7000, today.minusDays(40) to 5000),
            sleepByDay = mapOf(d1 to 3600, d2 to 27_000),
        )
        val report = sync(fake).import(days = 30, today = today)

        assertEquals(HealthImportReport(daysFilled = 2, nightsFilled = 1), report)
        assertEquals(9000, repo.listDaily(d1, d1).single().steps)
        assertEquals(4200, repo.listDaily(d2, d2).single().steps)
        assertEquals(51, repo.listDaily(d2, d2).single().restingHr)
        assertEquals(7000, repo.listDaily(d3, d3).single().steps)
        assertTrue(repo.listDaily(today.minusDays(40), today.minusDays(40)).isEmpty(), "outside the 30 days asked for")
        assertEquals(80, repo.listSleep(d1, d1).single().score)
        assertEquals(27_000, repo.listSleep(d2, d2).single().durationS)
    }

    @Test
    fun `importing twice changes nothing the second time`() = runBlocking {
        val fake = Fake(stepsByDay = mapOf(today to 3000), sleepByDay = mapOf(today to 20_000))
        assertEquals(HealthImportReport(1, 1), sync(fake).import(today = today))
        assertEquals(HealthImportReport(0, 0), sync(fake).import(today = today))
    }

    @Test
    fun `zero steps are not stored`() = runBlocking {
        assertEquals(HealthImportReport(0, 0), sync(Fake(stepsByDay = mapOf(today to 0), sleepByDay = mapOf(today to 0))).import(today = today))
        assertTrue(repo.listDaily(today, today).isEmpty())
    }

    // ------------------------------------------------------------------------ only what is new

    private fun run(id: String, name: String = "Run", hour: Int = 8) =
        Activity(id = id, name = name, sport = Sport.RUNNING, startTime = Instant.parse("2026-11-12T00:00:00Z").plusSeconds(hour * 3600L), durationS = 600.0, distanceM = 1500.0)

    @Test
    fun `an automatic export sends only what is new or changed since the last one`() = runBlocking {
        repo.upsertActivity(run("a1"))
        repo.upsertActivity(run("a2", hour = 10))
        val fake = Fake()
        val export = HealthConnectSync(repo, fake, ZoneOffset.UTC, InMemoryExportLedger())

        assertEquals(2, export.export(onlyNew = true).sent)
        val again = export.export(onlyNew = true)
        assertEquals(0, again.sent)
        assertEquals(2, again.unchanged, "nothing new: nothing goes to Health Connect")
        assertEquals(2, fake.written.size)

        repo.upsertActivity(run("a3", hour = 12))
        assertEquals(1, export.export(onlyNew = true).sent)
        assertEquals("openconnect-a3", fake.written.last().clientId)

        repo.upsertActivity(run("a1", name = "Morning run")) // edited: its content differs, so it goes again
        assertEquals(1, export.export(onlyNew = true).sent)
        assertEquals("openconnect-a1", fake.written.last().clientId)
        assertEquals("Morning run", fake.written.last().name)
    }

    @Test
    fun `the button sends everything again, for someone who emptied Health Connect`() = runBlocking {
        repo.upsertActivity(run("a1"))
        val fake = Fake()
        val export = HealthConnectSync(repo, fake, ZoneOffset.UTC, InMemoryExportLedger())
        export.export(onlyNew = true)
        assertEquals(1, export.export().sent, "a full export ignores what was sent before")
        assertEquals(2, fake.written.size)
        assertEquals(0, export.export(onlyNew = true).sent, "and what it sent is remembered as well")
    }

    @Test
    fun `a failure halfway keeps what was sent, so the next try continues where it stopped`() = runBlocking {
        for (i in 1..250) repo.upsertActivity(run("a$i", hour = i))
        var batches = 0
        val written = ArrayList<HealthSession>()
        val flaky = object : HealthConnectGateway by Fake() {
            override suspend fun write(sessions: List<HealthSession>): Int {
                if (++batches == 2) throw java.io.IOException("Health Connect is busy")
                written += sessions
                return sessions.size
            }
        }
        val ledger = InMemoryExportLedger()
        val export = HealthConnectSync(repo, flaky, ZoneOffset.UTC, ledger)
        try {
            export.export(onlyNew = true)
            error("the second batch should have failed")
        } catch (_: java.io.IOException) {
        }
        assertEquals(100, written.size)
        assertEquals(100, ledger.fingerprints().size)

        val rest = export.export(onlyNew = true)
        assertEquals(150, rest.sent)
        assertEquals(100, rest.unchanged)
        assertEquals(250, written.map { it.clientId }.toSet().size)
    }

    @Test
    fun `the ledger survives being written out and read back`() {
        val ledger = InMemoryExportLedger()
        ledger.remember(mapOf("openconnect-a1" to "f1", "openconnect-a2" to "f2"))
        val text = ExportLedger.encode(ledger.fingerprints())
        assertEquals(ledger.fingerprints(), ExportLedger.decode(text))
        assertEquals(emptyMap(), ExportLedger.decode(null))
        assertEquals(mapOf("x" to "y"), ExportLedger.decode("broken line\nx\ty\n"), "a damaged line is skipped")
    }
}
