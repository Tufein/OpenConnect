package nl.tufein.openconnect.core.reports

import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.StoredReport
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/**
 * Generates reports and keeps them. A report is only regenerated when its input facts, language,
 * provider or model change, or when [force] is set, so repeated taps cost nothing.
 */
class ReportService(
    private val repo: Repository,
    private val stats: StatsService,
    private val clock: () -> Instant = Instant::now,
) {
    fun activityReport(id: String, provider: ReportProvider, language: String, force: Boolean = false): StoredReport {
        val activity = repo.getActivity(id) ?: throw ReportError("Activity not found")
        val facts = activityFacts(repo, activity)
        return generate("activity", id, facts, provider, language, force)
    }

    fun weeklyReport(anyDay: LocalDate, provider: ReportProvider, language: String, force: Boolean = false): StoredReport {
        val facts = weeklyFacts(repo, stats, anyDay)
        return generate("weekly", facts.weekStart, facts, provider, language, force)
    }

    fun cached(kind: String, subject: String): StoredReport? = repo.getReport(kind, subject)

    private fun generate(
        kind: String,
        subject: String,
        facts: ReportFacts,
        provider: ReportProvider,
        language: String,
        force: Boolean,
    ): StoredReport {
        val hash = hashOf(facts, language, provider)
        val existing = repo.getReport(kind, subject)
        if (!force && existing != null && existing.factsHash == hash) return existing
        val result = provider.generate(ReportRequest(kind, subject, facts, language))
        val report = StoredReport(kind, subject, result.text, result.provider, result.model, hash, clock())
        repo.saveReport(report)
        return report
    }

    private fun hashOf(facts: ReportFacts, language: String, provider: ReportProvider): String {
        val input = "${facts.toJson()}|$language|${provider.name}|${provider.modelId.orEmpty()}"
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
