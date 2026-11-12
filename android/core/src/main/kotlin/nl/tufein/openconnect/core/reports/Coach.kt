package nl.tufein.openconnect.core.reports

import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.StatsService
import java.time.Instant
import java.time.ZoneOffset

/**
 * Puts a question to an AI provider together with a digest of the person's own data. Nothing is stored: the
 * conversation lives on the screen, and only the digest (never rows or files) is sent.
 */
class CoachService(
    private val repo: Repository,
    private val stats: StatsService,
    private val clock: () -> Instant = Instant::now,
) {
    fun ask(question: String, history: List<ChatTurn>, provider: ReportProvider, language: String): ReportResult {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) throw ReportError("Type a question first")
        val today = clock().atZone(ZoneOffset.UTC).toLocalDate()
        return provider.answer(CoachRequest(trimmed, history, coachFacts(repo, stats, today), language))
    }
}
