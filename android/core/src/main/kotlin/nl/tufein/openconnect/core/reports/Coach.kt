package nl.tufein.openconnect.core.reports

/**
 * Puts a question to an AI provider together with the current, bounded plan facts supplied by the app. The service
 * cannot read activities or settings and cannot change a plan; the conversation lives only on the screen.
 */
class CoachService(
    private val facts: () -> CoachFacts = { CoachFacts() },
) {
    fun ask(question: String, history: List<ChatTurn>, provider: ReportProvider, language: String): ReportResult {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) throw ReportError("Type a question first")
        return provider.answer(CoachRequest(trimmed, history, facts(), language))
    }
}
