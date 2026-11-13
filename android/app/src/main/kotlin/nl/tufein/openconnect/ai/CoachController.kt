package nl.tufein.openconnect.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.StatsService
import nl.tufein.openconnect.core.reports.ChatTurn
import nl.tufein.openconnect.core.reports.CoachService
import nl.tufein.openconnect.core.reports.ReportError
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class CoachMessage(val role: String, val text: String, val provider: String? = null, val model: String? = null)

data class CoachUi(
    val messages: List<CoachMessage> = emptyList(),
    val busy: Boolean = false,
    /** False while only the offline reports are available: the coach needs an AI key. */
    val canAsk: Boolean = false,
    val error: String? = null,
)

/**
 * The conversation on the Coach screen. It lives only in memory: closing the app ends it. Each question goes to the
 * active AI provider with a digest of the person's own data (see [CoachService]); one question at a time.
 */
class CoachController(
    repo: Repository,
    stats: StatsService,
    private val ai: AiController,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    clock: () -> Instant = Instant::now,
) {
    private val service = CoachService(repo, stats, clock)
    private val busy = AtomicBoolean(false)

    /** Bumped by [clear] so an answer that arrives afterwards is dropped. */
    private val generation = AtomicInteger(0)

    private val _state = MutableStateFlow(CoachUi(canAsk = ai.active() != ActiveAi.TEMPLATE))
    val state: StateFlow<CoachUi> = _state.asStateFlow()

    /** Looks at the AI settings again, for example after a key was added or removed. */
    fun refresh() = _state.update { it.copy(canAsk = ai.active() != ActiveAi.TEMPLATE) }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || ai.active() == ActiveAi.TEMPLATE) return
        if (!busy.compareAndSet(false, true)) return
        val mine = generation.get()
        val history = _state.value.messages.map { ChatTurn(it.role, it.text) }
        _state.update { it.copy(messages = it.messages + CoachMessage("user", text), busy = true, error = null, canAsk = true) }
        scope.launch(io) {
            var answer: CoachMessage? = null
            var failure: String? = null
            try {
                val result = service.ask(text, history, ai.provider(), ai.language)
                answer = CoachMessage("assistant", result.text, result.provider, result.model)
            } catch (e: ReportError) {
                failure = e.message ?: "The coach could not answer"
            } catch (e: CancellationException) {
                busy.set(false)
                throw e
            } catch (e: Exception) {
                failure = e.message ?: "The coach could not answer"
            }
            if (generation.get() == mine) {
                _state.update { ui ->
                    ui.copy(
                        messages = if (answer != null) ui.messages + answer else ui.messages,
                        busy = false,
                        error = failure,
                    )
                }
            }
            busy.set(false)
        }
    }

    /** Starts a new conversation. An answer that was still on its way is dropped. */
    fun clear() {
        generation.incrementAndGet()
        _state.update { CoachUi(canAsk = ai.active() != ActiveAi.TEMPLATE) }
    }
}
