package nl.tufein.openconnect.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.core.reports.HttpTransport
import nl.tufein.openconnect.core.reports.ReportError
import nl.tufein.openconnect.core.reports.ReportProvider
import nl.tufein.openconnect.core.reports.TemplateReportProvider

/** Which writer is behind the reports and the coach right now. */
enum class ActiveAi { TEMPLATE, CLAUDE, GEMINI }

enum class AiKeyCheck {
    /** Nothing known: no key, or it was saved earlier and not tried since. */
    UNKNOWN,
    CHECKING,
    OK,
    REJECTED,

    /** The provider could not be asked (no connection, rate limit, server trouble): the key may be fine. */
    FAILED,
}

/** Why a pasted key was not saved. */
enum class AiKeyProblem { MALFORMED, CANNOT_STORE }

data class AiVendorUi(
    val vendor: AiVendor,
    val hasKey: Boolean,
    val model: String,
    val check: AiKeyCheck = AiKeyCheck.UNKNOWN,
    val problem: AiKeyProblem? = null,
    /** What went wrong, in words, when [check] is rejected or failed. Never contains the key. */
    val detail: String? = null,
)

data class AiUi(
    val choice: String,
    val language: String,
    val canStoreKeys: Boolean,
    val vendors: List<AiVendorUi>,
    val active: ActiveAi,
)

/**
 * The AI side of Settings: keep the person's own keys for Claude and Gemini, check them, choose which one writes the
 * reports, and build that provider. It has no Android types in it: the network sits behind [HttpTransport], the keys
 * behind [AiSettings].
 */
class AiController(
    private val settings: AiSettings,
    private val transport: HttpTransport,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    companion object {
        /** Offline, or exactly one AI. The old "auto" (and anything unknown) is read as the AI that has a key. */
        val CHOICES = listOf("template") + AiVendor.entries.map { it.id }
        val LANGUAGES = listOf("en", "nl")

        private val KEY_SHAPE = Regex("[A-Za-z0-9._~+/=:-]{16,512}")

        /**
         * The key as the provider showed it, or null when what was pasted cannot be one: spaces and line breaks from the
         * clipboard are removed, as are quotes, since both get pasted along sometimes.
         */
        fun normalizeKey(pasted: String): String? {
            val text = pasted.trim().trim('"', '\'').filterNot(Char::isWhitespace)
            return text.takeIf { KEY_SHAPE.matches(it) }
        }
    }

    private val checks = HashMap<AiVendor, Pair<AiKeyCheck, String?>>()
    private val problems = HashMap<AiVendor, AiKeyProblem>()

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<AiUi> = _state.asStateFlow()

    private fun currentChoice(): String = settings.choice.takeIf { it in CHOICES } ?: when {
        !settings.key(AiVendor.CLAUDE).isNullOrBlank() -> AiVendor.CLAUDE.id
        !settings.key(AiVendor.GEMINI).isNullOrBlank() -> AiVendor.GEMINI.id
        else -> AiVendor.CLAUDE.id
    }

    private fun snapshot(): AiUi = AiUi(
        choice = currentChoice(),
        language = settings.language.takeIf { it in LANGUAGES } ?: "en",
        canStoreKeys = settings.canStoreKeys,
        vendors = AiVendor.entries.map { v ->
            val (check, detail) = checks[v] ?: (AiKeyCheck.UNKNOWN to null)
            AiVendorUi(
                vendor = v,
                hasKey = !settings.key(v).isNullOrBlank(),
                model = settings.model(v) ?: v.defaultModel,
                check = check,
                problem = problems[v],
                detail = detail,
            )
        },
        active = active(),
    )

    private fun refresh() = _state.update { snapshot() }

    // ------------------------------------------------------------------------------------- keys

    /** Saves a pasted key and, in the background, asks the provider whether it is accepted. */
    fun saveKey(vendor: AiVendor, pasted: String) {
        if (!settings.canStoreKeys) {
            problems[vendor] = AiKeyProblem.CANNOT_STORE
            refresh()
            return
        }
        val key = normalizeKey(pasted)
        if (key == null) {
            problems[vendor] = AiKeyProblem.MALFORMED
            refresh()
            return
        }
        problems.remove(vendor)
        settings.setKey(vendor, key)
        checks[vendor] = AiKeyCheck.CHECKING to null
        refresh()
        scope.launch(io) { check(vendor) }
    }

    /** Asks the provider again whether the saved key is accepted. */
    fun checkKey(vendor: AiVendor) {
        if (settings.key(vendor).isNullOrBlank()) return
        checks[vendor] = AiKeyCheck.CHECKING to null
        refresh()
        scope.launch(io) { check(vendor) }
    }

    private fun check(vendor: AiVendor) {
        val key = settings.key(vendor) ?: return
        val outcome = try {
            vendor.create(key, settings.model(vendor), transport).checkKey()
            AiKeyCheck.OK to null
        } catch (e: ReportError) {
            val rejected = e.message.orEmpty().contains("key was rejected")
            (if (rejected) AiKeyCheck.REJECTED else AiKeyCheck.FAILED) to e.message?.replace(key, "***")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiKeyCheck.FAILED to e.message?.replace(key, "***")
        }
        checks[vendor] = outcome
        refresh()
    }

    fun forgetKey(vendor: AiVendor) {
        settings.setKey(vendor, null)
        checks.remove(vendor)
        problems.remove(vendor)
        refresh()
    }

    // ---------------------------------------------------------------------------------- choices

    /** A blank model means the vendor's default. */
    fun setModel(vendor: AiVendor, model: String) {
        settings.setModel(vendor, model.trim().takeIf { it.isNotEmpty() })
        refresh()
    }

    fun setChoice(choice: String) {
        if (choice !in CHOICES) return
        settings.choice = choice
        refresh()
    }

    fun setLanguage(language: String) {
        if (language !in LANGUAGES) return
        settings.language = language
        refresh()
    }

    // -------------------------------------------------------------------------------- provider

    /** The writer for reports and the coach: the chosen vendor when it has a key, otherwise the offline template. */
    fun active(): ActiveAi {
        val hasClaude = !settings.key(AiVendor.CLAUDE).isNullOrBlank()
        val hasGemini = !settings.key(AiVendor.GEMINI).isNullOrBlank()
        // Only the chosen one is ever used: a key of the other AI stays saved but is not touched.
        return when (currentChoice()) {
            AiVendor.CLAUDE.id -> if (hasClaude) ActiveAi.CLAUDE else ActiveAi.TEMPLATE
            AiVendor.GEMINI.id -> if (hasGemini) ActiveAi.GEMINI else ActiveAi.TEMPLATE
            else -> ActiveAi.TEMPLATE
        }
    }

    fun provider(): ReportProvider {
        val vendor = when (active()) {
            ActiveAi.CLAUDE -> AiVendor.CLAUDE
            ActiveAi.GEMINI -> AiVendor.GEMINI
            ActiveAi.TEMPLATE -> return TemplateReportProvider()
        }
        val key = settings.key(vendor) ?: return TemplateReportProvider()
        return vendor.create(key, settings.model(vendor), transport)
    }

    val language: String get() = settings.language.takeIf { it in LANGUAGES } ?: "en"
}
