package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncLogTest {
    private fun e(n: Int, ok: Boolean = true, detail: String = "") = SyncLogEntry(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(n.toLong()), SyncLog.SOURCE_USB, ok, n, detail)

    @Test
    fun `newest first and capped`() {
        var log = emptyList<SyncLogEntry>()
        for (i in 1..SyncLog.MAX + 5) log = SyncLog.add(log, e(i))
        assertEquals(SyncLog.MAX, log.size)
        assertEquals(SyncLog.MAX + 5, log.first().added)
        assertEquals(6, log.last().added)
    }

    @Test
    fun `survives being stored as text, including awkward details`() {
        val log = listOf(e(2, false, "line one\nline\ttwo"), e(1))
        val back = SyncLog.decode(SyncLog.encode(log))
        assertEquals(2, back.size)
        assertEquals("line one line two", back[0].detail)
        assertEquals(false, back[0].ok)
        assertEquals(log[1], back[1])
    }

    @Test
    fun `a long detail is cut and damaged lines are skipped`() {
        val long = SyncLog.decode(SyncLog.encode(listOf(e(1, false, "x".repeat(900)))))
        assertEquals(200, long[0].detail.length)
        assertEquals(1, SyncLog.decode("garbage\n" + SyncLog.encode(listOf(e(1))) + "\nnot-a-time\tusb\t1\t0").size)
        assertTrue(SyncLog.decode(null).isEmpty())
        assertTrue(SyncLog.decode("").isEmpty())
    }

    @Test
    fun `overview counts per source with the period`() {
        val repo = InMemoryRepository()
        repo.upsertActivity(Activity(id = "1", source = "garmin", startTime = Instant.parse("2026-06-01T06:00:00Z")))
        repo.upsertActivity(Activity(id = "2", source = "garmin", startTime = Instant.parse("2026-06-05T06:00:00Z")))
        repo.upsertActivity(Activity(id = "3", source = "watch", startTime = Instant.parse("2026-06-03T06:00:00Z")))
        repo.upsertDaily(DailySummary(date = LocalDate.parse("2026-06-02")))
        repo.upsertDaily(DailySummary(date = LocalDate.parse("2026-06-09")))
        val o = DataOverview.of(repo)
        assertEquals(listOf("garmin" to 2, "watch" to 1), o.sources.map { it.source to it.activities })
        assertEquals(Instant.parse("2026-06-05T06:00:00Z"), o.sources[0].last)
        assertEquals(2, o.days)
        assertEquals(LocalDate.parse("2026-06-02"), o.firstDay)
        assertEquals(LocalDate.parse("2026-06-09"), o.lastDay)
        assertEquals(DataOverview(emptyList(), 0, 0, 0, null, null), DataOverview.of(InMemoryRepository()))
    }
}
