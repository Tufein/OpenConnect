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

/** The seam: tests and offline use run the rule-based template; the app can also use Claude. */
interface ReportProvider {
    val name: String

    /** The model behind the provider, when there is one; part of the cache key. */
    val modelId: String? get() = null

    fun generate(request: ReportRequest): ReportResult
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

    override fun generate(request: ReportRequest): ReportResult {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", SYSTEM_PROMPT)
            put("fallbacks", "default") // safety-classifier refusals are re-run on a fallback model
            put("output_config", buildJsonObject { put("effort", effort) })
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject { put("role", "user"); put("content", buildUserPrompt(request)) })
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
        if (text.isEmpty()) throw ReportError("Claude returned an empty report")
        val usedModel = (json["model"] as? JsonPrimitive)?.contentOrNull ?: model
        return ReportResult(text = text, provider = name, model = usedModel)
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}
