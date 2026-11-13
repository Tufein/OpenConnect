package nl.tufein.openconnect.ai

import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.core.reports.HttpResult
import nl.tufein.openconnect.core.reports.HttpTransport

/** Answers the AI providers from memory and remembers every request. */
class FakeAiTransport(var handler: (Call) -> HttpResult = { gemini("OK") }) : HttpTransport {
    data class Call(val url: String, val headers: Map<String, String>, val body: String)

    val calls = java.util.concurrent.CopyOnWriteArrayList<Call>()

    override fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): HttpResult {
        val call = Call(url, headers, body)
        calls.add(call)
        return handler(call)
    }

    companion object {
        fun gemini(text: String) = HttpResult(
            200, emptyMap(),
            """{"candidates":[{"content":{"role":"model","parts":[{"text":${quote(text)}}]},"finishReason":"STOP"}],"modelVersion":"gemini-fake"}""",
        )

        fun claude(text: String) = HttpResult(
            200, emptyMap(),
            """{"model":"claude-fake","stop_reason":"end_turn","content":[{"type":"text","text":${quote(text)}}]}""",
        )

        fun status(code: Int, body: String = "{}") = HttpResult(code, emptyMap(), body)

        private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    }
}

/** AI settings in memory, so a test does not need Android's encrypted preferences. */
class FakeAiSettings(
    override var canStoreKeys: Boolean = true,
    override var choice: String = "auto",
    override var language: String = "en",
) : AiSettings {
    private val keys = HashMap<AiVendor, String>()
    private val models = HashMap<AiVendor, String>()

    override fun key(vendor: AiVendor): String? = keys[vendor]

    override fun setKey(vendor: AiVendor, key: String?) {
        if (key == null) keys.remove(vendor) else keys[vendor] = key
    }

    override fun model(vendor: AiVendor): String? = models[vendor]

    override fun setModel(vendor: AiVendor, model: String?) {
        if (model == null) models.remove(vendor) else models[vendor] = model
    }
}
