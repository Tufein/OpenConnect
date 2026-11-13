package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.reports.ReportPeriod
import nl.tufein.openconnect.core.reports.ReportRequest
import nl.tufein.openconnect.core.reports.ReportService
import nl.tufein.openconnect.core.reports.TemplateReportProvider
import nl.tufein.openconnect.core.reports.buildUserPrompt
import nl.tufein.openconnect.core.reports.periodFacts
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeriodReportTest {
    private val repo = InMemoryRepository()
    private val stats = StatsService(repo) { Instant.parse("2026-11-13T12:00:00Z") }
    private val service = ReportService(repo, stats) { Instant.parse("2026-11-13T12:00:00Z") }

    private fun run(id: String, day: String, km: Double, minutes: Double) =
        repo.upsertActivity(Activity(id = id, name = "Run $id", sport = Sport.RUNNING, startTime = Instant.parse("${day}T08:00:00Z"), durationS = minutes * 60, distanceM = km * 1000))

    @Test
    fun `periods start, end and shift on calendar boundaries`() {
        val d = LocalDate.parse("2026-03-17")
        assertEquals(LocalDate.parse("2026-03-16"), ReportPeriod.WEEK.start(d))
        assertEquals(LocalDate.parse("2026-03-01"), ReportPeriod.MONTH.start(d))
        assertEquals(LocalDate.parse("2026-03-31"), ReportPeriod.MONTH.end(LocalDate.parse("2026-03-01")))
        assertEquals(LocalDate.parse("2026-02-28"), ReportPeriod.MONTH.end(LocalDate.parse("2026-02-01")))
        assertEquals(LocalDate.parse("2024-02-29"), ReportPeriod.MONTH.end(LocalDate.parse("2024-02-01")), "leap year")
        assertEquals(LocalDate.parse("2026-01-01"), ReportPeriod.YEAR.start(d))
        assertEquals(LocalDate.parse("2026-12-31"), ReportPeriod.YEAR.end(LocalDate.parse("2026-01-01")))
        assertEquals(LocalDate.parse("2025-12-01"), ReportPeriod.MONTH.shift(LocalDate.parse("2026-01-01"), -1))
        assertEquals(LocalDate.parse("2025-01-01"), ReportPeriod.YEAR.shift(LocalDate.parse("2026-01-01"), -1))
    }

    @Test
    fun `month facts hold this month against the one before, with the longest activities`() {
        run("oct", "2026-10-10", 10.0, 60.0)
        run("nov1", "2026-11-02", 5.0, 30.0)
        run("nov2", "2026-11-09", 21.0, 120.0)
        repo.upsertDaily(DailySummary(date = LocalDate.parse("2026-11-03"), steps = 9000))
        val facts = periodFacts(repo, stats, ReportPeriod.MONTH, LocalDate.parse("2026-11-20"))
        assertEquals("month", facts.kind)
        assertEquals("2026-11-01", facts.periodStart)
        assertEquals("2026-11-30", facts.periodEnd)
        assertEquals(2, facts.thisPeriod.activityCount)
        assertEquals(26_000.0, facts.thisPeriod.totalDistanceM)
        assertEquals(1, facts.previousPeriod.activityCount)
        assertEquals(listOf("Run nov1", "Run nov2"), facts.activities.map { it.name })
        assertEquals(9000.0, facts.thisPeriod.avgSteps)
    }

    @Test
    fun `an offline month and year report compare with the period before`() {
        run("oct", "2026-10-10", 10.0, 60.0)
        run("nov", "2026-11-09", 20.0, 120.0)
        val month = service.periodReport(ReportPeriod.MONTH, LocalDate.parse("2026-11-20"), TemplateReportProvider(), "en")
        assertEquals("monthly", month.kind)
        assertTrue(month.text.contains("Month 2026-11"), month.text)
        assertTrue(month.text.contains("+100%"), month.text)
        val year = service.periodReport(ReportPeriod.YEAR, LocalDate.parse("2026-11-20"), TemplateReportProvider(), "nl")
        assertEquals("yearly", year.kind)
        assertTrue(year.text.contains("Jaar 2026"), year.text)
    }

    @Test
    fun `an empty period says so, and the report is cached per period`() {
        val empty = service.periodReport(ReportPeriod.MONTH, LocalDate.parse("2026-05-01"), TemplateReportProvider(), "en")
        assertTrue(empty.text.contains("No activities recorded in this period"))
        assertEquals(empty, service.cached("monthly", "2026-05-01"))
        assertEquals(null, service.cached("monthly", "2026-06-01"))
    }

    @Test
    fun `weeks still go through the weekly report`() {
        run("w", "2026-11-10", 5.0, 30.0)
        val r = service.periodReport(ReportPeriod.WEEK, LocalDate.parse("2026-11-11"), TemplateReportProvider(), "en")
        assertEquals("weekly", r.kind)
        assertEquals("2026-11-09", r.subject)
    }

    @Test
    fun `what is sent to an AI provider for a year carries the kind and the data`() {
        run("a", "2026-02-02", 8.0, 50.0)
        val request = ReportRequest("yearly", "2026-01-01", periodFacts(repo, stats, ReportPeriod.YEAR, LocalDate.parse("2026-06-01")), "en")
        val prompt = buildUserPrompt(request)
        assertTrue(prompt.contains("training year"))
        assertTrue(prompt.contains("\"kind\": \"year\"") || prompt.contains("\"kind\":\"year\""), prompt)
    }
}
