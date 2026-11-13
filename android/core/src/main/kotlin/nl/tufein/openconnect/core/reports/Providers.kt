package nl.tufein.openconnect.core.reports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** A report could not be generated (provider refused, unreachable, rate limited, ...). */
class ReportError(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ReportRequest(
    /** "activity" or "weekly". */
    val kind: String,
    val subject: String,
    val facts: ReportFacts,
    val language: String = "en",
)

data class ReportResult(val text: String, val provider: String, val model: String? = null)

/** One earlier message of a conversation with the coach. [role] is "user" or "assistant". */
data class ChatTurn(val role: String, val text: String)

/** A question for the coach, with the person's earlier turns and the digest of their data it is answered from. */
data class CoachRequest(
    val question: String,
    val history: List<ChatTurn>,
    val facts: CoachFacts,
    val language: String = "en",
)

/** The seam: tests and offline use run the rule-based template; the app can also use Claude or Gemini. */
interface ReportProvider {
    val name: String

    /** The model behind the provider, when there is one; part of the cache key. */
    val modelId: String? get() = null

    fun generate(request: ReportRequest): ReportResult

    /** Answers a free question about the person's own data. Only the AI providers can; the template cannot. */
    fun answer(request: CoachRequest): ReportResult =
        throw ReportError("The offline reports cannot answer questions: add an AI key (Claude or Gemini) in Settings")

    /** Asks the provider whether its key works, with a request that costs next to nothing. Throws [ReportError] if not. */
    fun checkKey() {}
}

// ------------------------------------------------------------------------------- template

private class Text(
    val activity: String,
    val distance: String,
    val pace: String,
    val speed: String,
    val hr: String,
    val drift: String,
    val climb: String,
    val longest: String,
    val week: String,
    val count: String,
    val activityOne: String,
    val activityMany: String,
    val vs: String,
    val steps: String,
    val sleep: String,
    val none: String,
    val month: String,
    val year: String,
    val vsMonth: String,
    val vsYear: String,
    val nonePeriod: String,
)

private val TEXT = mapOf(
    "en" to Text(
        activity = "%s (%s, %s)",
        distance = "Distance: %.1f km in %.0f min",
        pace = "average pace %s /km",
        speed = "average speed %s km/h",
        hr = "Heart rate: average %s bpm, max %s bpm",
        drift = "Your heart rate rose %d bpm between the first and second half.",
        climb = "Climbing: %d m",
        longest = "This was your longest %s so far.",
        week = "Week of %s",
        count = "%d %s, %.1f km in %.1f h",
        activityOne = "activity",
        activityMany = "activities",
        vs = "Distance vs previous week: %+.0f%%",
        steps = "Steps: %.0f per day on average, goal reached on %d of %d days",
        sleep = "Sleep: %.1f h per night on average",
        none = "No activities recorded this week.",
        month = "Month %s",
        year = "Year %s",
        vsMonth = "Distance vs previous month: %+.0f%%",
        vsYear = "Distance vs previous year: %+.0f%%",
        nonePeriod = "No activities recorded in this period.",
    ),
    "nl" to Text(
        activity = "%s (%s, %s)",
        distance = "Afstand: %.1f km in %.0f min",
        pace = "gemiddeld tempo %s /km",
        speed = "gemiddelde snelheid %s km/u",
        hr = "Hartslag: gemiddeld %s bpm, max %s bpm",
        drift = "Je hartslag steeg %d bpm tussen de eerste en tweede helft.",
        climb = "Hoogtemeters: %d m",
        longest = "Dit was je langste %s tot nu toe.",
        week = "Week van %s",
        count = "%d %s, %.1f km in %.1f u",
        activityOne = "activiteit",
        activityMany = "activiteiten",
        vs = "Afstand t.o.v. vorige week: %+.0f%%",
        steps = "Stappen: gemiddeld %.0f per dag, doel gehaald op %d van %d dagen",
        sleep = "Slaap: gemiddeld %.1f u per nacht",
        none = "Geen activiteiten deze week.",
        month = "Maand %s",
        year = "Jaar %s",
        vsMonth = "Afstand t.o.v. vorige maand: %+.0f%%",
        vsYear = "Afstand t.o.v. vorig jaar: %+.0f%%",
        nonePeriod = "Geen activiteiten in deze periode.",
    ),
)

private fun f(pattern: String, vararg args: Any?) = String.format(Locale.ROOT, pattern, *args)

/** Deterministic, rule-based reports. No network and no key: the default without an API key. */
class TemplateReportProvider : ReportProvider {
    override val name = "template"

    override fun generate(request: ReportRequest): ReportResult {
        val t = TEXT[request.language] ?: TEXT.getValue("en")
        val text = when (val facts = request.facts) {
            is ActivityFacts -> activity(facts, t)
            is WeeklyFacts -> weekly(facts, t)
            is PeriodFacts -> period(facts, t)
            is CoachFacts -> throw ReportError("The offline reports cannot answer questions: add an AI key (Claude or Gemini) in Settings")
        }
        return ReportResult(text = text, provider = name, model = null)
    }

    private fun activity(fa: ActivityFacts, t: Text): String {
        val lines = ArrayList<String>()
        lines.add("**${f(t.activity, fa.name, fa.sport, fa.date)}**")
        var detail = f(t.distance, fa.distanceKm, fa.durationMin)
        if (fa.paceMinPerKm != null) {
            detail += ", " + f(t.pace, fa.paceMinPerKm)
        } else if (fa.avgSpeedKmh != null) {
            detail += ", " + f(t.speed, fa.avgSpeedKmh.toString())
        }
        lines.add("- $detail")
        if (fa.avgHr != null && fa.avgHr != 0) lines.add("- " + f(t.hr, fa.avgHr.toString(), fa.maxHr?.toString() ?: "?"))
        val hr = fa.hr
        if (hr != null && hr.secondHalfAvg - hr.firstHalfAvg >= 8) {
            lines.add("- " + f(t.drift, hr.secondHalfAvg - hr.firstHalfAvg))
        }
        if (fa.elevationGainM != 0) lines.add("- " + f(t.climb, fa.elevationGainM))
        if (fa.context.isLongestOfSport) lines.add("- " + f(t.longest, fa.sport))
        return lines.joinToString("\n")
    }

    private fun period(fp: PeriodFacts, t: Text): String {
        val w = fp.thisPeriod
        val prev = fp.previousPeriod
        val lines = ArrayList<String>()
        val heading = if (fp.kind == "year") f(t.year, fp.periodStart.take(4)) else f(t.month, fp.periodStart.take(7))
        lines.add("**$heading**")
        if (w.activityCount == 0) {
            lines.add("- ${t.nonePeriod}")
        } else {
            val noun = if (w.activityCount == 1) t.activityOne else t.activityMany
            lines.add("- " + f(t.count, w.activityCount, noun, w.totalDistanceM / 1000, w.totalDurationS / 3600))
            if (prev.totalDistanceM != 0.0) {
                val pct = (w.totalDistanceM - prev.totalDistanceM) / prev.totalDistanceM * 100
                lines.add("- " + f(if (fp.kind == "year") t.vsYear else t.vsMonth, pct))
            }
        }
        if (w.avgSteps != null) lines.add("- " + f(t.steps, w.avgSteps, w.goalDays, w.daysWithData))
        if (w.avgSleepHours != null) lines.add("- " + f(t.sleep, w.avgSleepHours))
        return lines.joinToString("\n")
    }

    private fun weekly(fw: WeeklyFacts, t: Text): String {
        val w = fw.thisWeek
        val prev = fw.previousWeek
        val lines = ArrayList<String>()
        lines.add("**${f(t.week, fw.weekStart)}**")
        if (w.activityCount == 0) {
            lines.add("- ${t.none}")
        } else {
            val noun = if (w.activityCount == 1) t.activityOne else t.activityMany
            lines.add("- " + f(t.count, w.activityCount, noun, w.totalDistanceM / 1000, w.totalDurationS / 3600))
            if (prev.totalDistanceM != 0.0) {
                val pct = (w.totalDistanceM - prev.totalDistanceM) / prev.totalDistanceM * 100
                lines.add("- " + f(t.vs, pct))
            }
        }
        if (w.avgSteps != null) lines.add("- " + f(t.steps, w.avgSteps, w.goalDays, w.daysWithData))
        if (w.avgSleepHours != null) lines.add("- " + f(t.sleep, w.avgSleepHours))
        return lines.joinToString("\n")
    }
}

// --------------------------------------------------------------------------------- Claude

data class HttpResult(val status: Int, val headers: Map<String, String>, val body: String)

/** Minimal HTTP seam so the Claude provider needs no network under test. */
fun interface HttpTransport {
    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): HttpResult
}

class UrlConnectionTransport : HttpTransport {
    override fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val responseHeaders = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.lowercase(Locale.ROOT) }
                .mapValues { it.value.firstOrNull().orEmpty() }
            return HttpResult(status, responseHeaders, text)
        } finally {
            connection.disconnect()
        }
    }
}

/** What an AI provider is asked, shared by reports, coach answers and the key check. */
private class AiCall(val system: String, val turns: List<ChatTurn>, val maxTokens: Int, val requireText: Boolean = true)

private const val CHECK_SYSTEM = "Reply with the single word OK."
private const val CHECK_TOKENS = 16

private fun singleTurn(prompt: String) = listOf(ChatTurn("user", prompt))

/**
 * Writes reports with Claude through the Messages API, called straight from the device with the
 * user's own key. A declined request is re-run server-side on a fallback model (`fallbacks`).
 */
class AnthropicReportProvider(
    private val apiKey: String,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val model: String = DEFAULT_MODEL,
    private val effort: String = "medium",
    private val maxTokens: Int = 16000,
    private val baseUrl: String = "https://api.anthropic.com",
    private val timeoutMs: Int = 120_000,
) : ReportProvider {
    override val name = "anthropic"
    override val modelId: String get() = model

    override fun generate(request: ReportRequest): ReportResult =
        complete(AiCall(SYSTEM_PROMPT, singleTurn(buildUserPrompt(request)), maxTokens))

    override fun answer(request: CoachRequest): ReportResult =
        complete(AiCall(COACH_PROMPT, coachTurns(request), maxTokens))

    override fun checkKey() {
        complete(AiCall(CHECK_SYSTEM, singleTurn("ping"), CHECK_TOKENS, requireText = false))
    }

    private fun complete(call: AiCall): ReportResult {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", call.maxTokens)
            put("system", call.system)
            put("fallbacks", "default") // safety-classifier refusals are re-run on a fallback model
            put("output_config", buildJsonObject { put("effort", effort) })
            put(
                "messages",
                buildJsonArray {
                    for (turn in call.turns) add(buildJsonObject { put("role", turn.role); put("content", turn.text) })
                },
            )
        }
        val headers = mapOf(
            "content-type" to "application/json",
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01",
            "anthropic-beta" to FALLBACK_BETA,
        )
        val response = try {
            transport.post("$baseUrl/v1/messages", headers, body.toString(), timeoutMs)
        } catch (e: IOException) {
            throw ReportError("Could not reach the Anthropic API", e)
        }
        when {
            response.status == 401 || response.status == 403 ->
                throw ReportError("The Anthropic API key was rejected; check it in Settings")
            response.status == 429 -> {
                val retry = response.headers["retry-after"]
                throw ReportError("Anthropic rate limit reached" + (retry?.let { "; retry after ${it}s" } ?: ""))
            }
            response.status >= 400 -> throw ReportError("Anthropic API error (HTTP ${response.status})")
        }

        val json = try {
            Json.parseToJsonElement(response.body).jsonObject
        } catch (e: Exception) {
            throw ReportError("Claude returned an unreadable response", e)
        }
        if (json["stop_reason"]?.jsonPrimitive?.contentOrNull == "refusal") {
            val category = json["stop_details"]?.let { (it as? JsonObject)?.get("category")?.jsonPrimitive?.contentOrNull }
            throw ReportError("Claude declined to write this report (category: $category)")
        }
        val content: JsonArray = json["content"]?.jsonArray ?: JsonArray(emptyList())
        val text = content
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
            .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            .trim()
        if (text.isEmpty() && call.requireText) throw ReportError("Claude returned an empty report")
        val usedModel = (json["model"] as? JsonPrimitive)?.contentOrNull ?: model
        return ReportResult(text = text, provider = name, model = usedModel)
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}

// --------------------------------------------------------------------------------- Gemini

/**
 * Writes reports and answers with Google Gemini through the Generative Language API, called straight from the
 * device with the user's own key. The key travels in the `x-goog-api-key` header, never in the address, and is
 * masked if Google echoes it back inside an error.
 *
 * The request carries no temperature or other sampling settings: current Gemini models ignore them and Google
 * says later releases may refuse them. [maxOutputTokens] is generous because thinking models spend part of it
 * before the first word of the text.
 */
class GeminiReportProvider(
    private val apiKey: String,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val model: String = DEFAULT_MODEL,
    private val maxOutputTokens: Int = 8192,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
    private val timeoutMs: Int = 120_000,
) : ReportProvider {
    override val name = "gemini"
    override val modelId: String get() = model.removePrefix("models/")

    override fun generate(request: ReportRequest): ReportResult =
        complete(AiCall(SYSTEM_PROMPT, singleTurn(buildUserPrompt(request)), maxOutputTokens))

    override fun answer(request: CoachRequest): ReportResult =
        complete(AiCall(COACH_PROMPT, coachTurns(request), maxOutputTokens))

    override fun checkKey() {
        complete(AiCall(CHECK_SYSTEM, singleTurn("ping"), CHECK_TOKENS, requireText = false))
    }

    private fun mask(text: String) = if (apiKey.isNotEmpty()) text.replace(apiKey, "***") else text

    private fun complete(call: AiCall): ReportResult {
        val modelName = modelId
        if (!MODEL_NAME.matches(modelName)) throw ReportError("That is not a valid Gemini model name; choose one in Settings")
        val body = buildJsonObject {
            put("system_instruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", call.system) }) }) })
            put(
                "contents",
                buildJsonArray {
                    for (turn in call.turns) {
                        add(
                            buildJsonObject {
                                put("role", if (turn.role == "assistant") "model" else "user")
                                put("parts", buildJsonArray { add(buildJsonObject { put("text", turn.text) }) })
                            },
                        )
                    }
                },
            )
            put("generationConfig", buildJsonObject { put("maxOutputTokens", call.maxTokens) })
        }
        val headers = mapOf("content-type" to "application/json", "x-goog-api-key" to apiKey)
        val response = try {
            transport.post("$baseUrl/v1beta/models/$modelName:generateContent", headers, body.toString(), timeoutMs)
        } catch (e: IOException) {
            throw ReportError("Could not reach the Gemini API", e)
        }
        if (response.status >= 400) throw failure(response, modelName)

        val json = try {
            Json.parseToJsonElement(response.body).jsonObject
        } catch (e: Exception) {
            throw ReportError("Gemini returned an unreadable response", e)
        }
        val blocked = (json["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull
        if (blocked != null) throw ReportError("Gemini declined to write this report ($blocked)")
        val candidate = (json["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
        val finish = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
        val parts = ((candidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
        val text = parts
            .mapNotNull { it as? JsonObject }
            .filter { it["thought"]?.jsonPrimitive?.contentOrNull != "true" }
            .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            .trim()
        if (text.isEmpty() && call.requireText) {
            when (finish) {
                in DECLINED -> throw ReportError("Gemini declined to write this report ($finish)")
                "MAX_TOKENS" -> throw ReportError("Gemini ran out of room before it wrote the text; try again or pick another model")
                else -> throw ReportError("Gemini returned an empty report")
            }
        }
        val usedModel = json["modelVersion"]?.jsonPrimitive?.contentOrNull ?: modelName
        return ReportResult(text = text, provider = name, model = usedModel)
    }

    /** Turns an error answer into words for the screen. Google reports a wrong key as HTTP 400, not 401. */
    private fun failure(response: HttpResult, modelName: String): ReportError {
        val error = try {
            Json.parseToJsonElement(response.body).jsonObject["error"] as? JsonObject
        } catch (_: Exception) {
            null
        }
        val message = error?.get("message")?.jsonPrimitive?.contentOrNull
        val reasons = (error?.get("details") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("reason")?.jsonPrimitive?.contentOrNull }
        val keyRejected = "The Gemini API key was rejected; check it in Settings"
        return when {
            response.status == 401 || response.status == 403 -> ReportError(keyRejected)
            response.status == 400 && ("API_KEY_INVALID" in reasons || message?.contains("API key", ignoreCase = true) == true) ->
                ReportError(keyRejected)
            response.status == 404 -> ReportError("Gemini does not know the model '$modelName'; choose another one in Settings")
            response.status == 429 -> {
                val retry = response.headers["retry-after"]
                ReportError("Gemini rate limit reached" + (retry?.let { "; retry after ${it}s" } ?: ""))
            }
            response.status == 400 && !message.isNullOrBlank() -> ReportError("Gemini rejected the request: ${mask(message)}")
            else -> ReportError("Gemini API error (HTTP ${response.status})")
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.6-flash"
        private val MODEL_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private val DECLINED = setOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII")
    }
}

// ---------------------------------------------------------------------------------- vendors

/** The AI providers the app can use, with what Settings needs to know about each. */
enum class AiVendor(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    /** Where the person makes their own key. Never a key, only the page. */
    val keyPage: String,
) {
    CLAUDE(
        "anthropic", "Claude", AnthropicReportProvider.DEFAULT_MODEL,
        listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
        "https://console.anthropic.com/settings/keys",
    ),
    GEMINI(
        "gemini", "Gemini", GeminiReportProvider.DEFAULT_MODEL,
        listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite"),
        "https://aistudio.google.com/apikey",
    ),
    ;

    fun create(key: String, model: String?, transport: HttpTransport = UrlConnectionTransport()): ReportProvider {
        val chosen = model?.trim().takeUnless { it.isNullOrEmpty() } ?: defaultModel
        return when (this) {
            CLAUDE -> AnthropicReportProvider(key, transport, model = chosen)
            GEMINI -> GeminiReportProvider(key, transport, model = chosen)
        }
    }

    companion object {
        fun fromId(id: String?): AiVendor? = entries.firstOrNull { it.id == id }
    }
}
