package nl.tufein.openconnect.core

import java.time.Instant

/**
 * What a diagnostics report may hold. There is no field for a key, a token, a name or a health value, on purpose: a report
 * that cannot hold a secret cannot leak one. [aiKeySet] only says that a key exists.
 */
data class DiagnosticInput(
    val appVersion: String,
    val androidVersion: String,
    val sdk: Int,
    val device: String,
    val language: String,
    val sourcesOn: List<String>,
    val aiProvider: String,
    val aiKeySet: Boolean,
    val days: Int,
    val nights: Int,
    val samples: Int,
    val activitiesBySource: List<Pair<String, Int>>,
    val log: List<SyncLogEntry>,
)

/** The text behind *Share diagnostics*: versions, what is switched on, what the last syncs did and why one failed. */
object Diagnostics {
    private const val LOG_LINES = 20
    private val url = Regex("""https?://[^\s"'<>]+""")
    private val bearer = Regex("""(?i)\bBearer\s+\S+""")
    // Words that name a secret take a value after ":" or "="; the plain words "key" and "code" only after "=", so that
    // "Garmin rejected the key: HTTP 401" stays readable.
    private val pair = Regex("""(?i)\b(access[_-]?token|refresh[_-]?token|id[_-]?token|token|api[_-]?key|secret|client[_-]?secret|password|passwd|authorization)\b(\s*[=:]\s*)\S+""")
    private val weakPair = Regex("""(?i)\b(key|code)\b(\s*=\s*)\S+""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val jwt = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
    private val opaque = Regex("""[A-Za-z0-9_\-+/=]{24,}""")
    private val hex = Regex("""\b[0-9a-fA-F]{16,}\b""")

    private val urlParts = Regex("""^(https?://)(?:[^/@]*@)?([^/]*)(.*)$""")

    /** A web address keeps its host and path (they explain a failure) and loses its query, fragment, login and long segments. */
    private fun cleanUrl(address: String): String {
        val bare = address.substringBefore('?').substringBefore('#')
        val parts = urlParts.find(bare) ?: return "<url>"
        val path = parts.groupValues[3].split('/').joinToString("/") { if (it.length >= 24) "<removed>" else it }
        return parts.groupValues[1] + parts.groupValues[2] + path
    }

    /** Cuts a text down to what explains a failure without carrying a secret: hosts and plain words stay, the rest goes. */
    fun redact(text: String): String {
        // Web addresses are cleaned first and set aside, so that the rules below cannot eat the end of a host name.
        val kept = ArrayList<String>()
        var t = url.replace(text) { m -> kept += cleanUrl(m.value); "\u0001${kept.size - 1}\u0001" }
        t = bearer.replace(t, "Bearer <removed>")
        t = pair.replace(t) { m -> m.groupValues[1] + m.groupValues[2] + "<removed>" }
        t = weakPair.replace(t) { m -> m.groupValues[1] + m.groupValues[2] + "<removed>" }
        t = jwt.replace(t, "<removed>")
        t = email.replace(t, "<email>")
        t = hex.replace(t, "<removed>")
        t = opaque.replace(t, "<removed>")
        return Regex("\u0001(\\d+)\u0001").replace(t) { kept[it.groupValues[1].toInt()] }
    }

    fun report(input: DiagnosticInput, now: Instant): String = buildString {
        appendLine("OpenConnect diagnostics")
        appendLine("App: ${input.appVersion} · Android ${input.androidVersion} (API ${input.sdk}) · ${redact(input.device)}")
        appendLine("Made: $now · language: ${input.language}")
        appendLine("Sources switched on: ${input.sourcesOn.joinToString(", ").ifBlank { "none" }}")
        appendLine("AI: ${input.aiProvider} (key stored: ${if (input.aiKeySet) "yes" else "no"})")
        appendLine("Stored: ${input.days} days, ${input.nights} nights, ${input.samples} samples")
        appendLine("Activities by source: ${input.activitiesBySource.joinToString(", ") { (s, n) -> "$s $n" }.ifBlank { "none" }}")
        appendLine()
        appendLine("What has been tried on a real device:")
        for (s in SourceLabels.all) {
            val label = if (s.tested) "tested ${s.testedOn} in v${s.version}" else "experimental"
            appendLine("- ${s.kind.key}: $label")
        }
        appendLine()
        appendLine("Last syncs, newest first:")
        if (input.log.isEmpty()) appendLine("(none)")
        for (e in input.log.take(LOG_LINES)) {
            val outcome = if (e.ok) "ok +${e.added}" else "FAILED" + if (e.detail.isNotBlank()) ": ${redact(e.detail)}" else ""
            appendLine("${e.time} ${e.source} $outcome")
        }
        appendLine()
        append("Not in this report: keys, tokens, names, health values and GPS tracks.")
    }
}
