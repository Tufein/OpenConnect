package nl.tufein.openconnect.core.garmin

import java.io.IOException
import java.time.Instant
import java.util.Base64

/** Answers Garmin's Personal API from memory and remembers every request. */
class FakeGarminHttp : GarminHttp {
    data class Call(val method: String, val url: String, val headers: Map<String, String>) {
        val path: String get() = url.substringAfter("https://personal.connectapi.garmin.com").substringBefore('?')
        val query: String get() = url.substringAfter('?', "")
    }

    private class Route(val method: String, val pathContains: String, val answer: (Call) -> GarminResponse)

    val calls = mutableListOf<Call>()
    private val routes = mutableListOf<Route>()

    /** The newest matching route answers, so a test can override an earlier default. */
    fun on(method: String, pathContains: String, answer: (Call) -> GarminResponse) {
        routes.add(Route(method, pathContains, answer))
    }

    fun json(pathContains: String, body: String) = on("GET", pathContains) { text(200, body) }

    fun status(method: String, pathContains: String, code: Int) = on(method, pathContains) { text(code, "") }

    fun callsTo(pathContains: String) = calls.filter { pathContains in it.url }

    override fun send(method: String, url: String, headers: Map<String, String>, timeoutMs: Int): GarminResponse {
        val call = Call(method, url, headers)
        calls.add(call)
        val route = routes.lastOrNull { it.method == method && it.pathContains in url }
        return route?.answer?.invoke(call) ?: text(404, "")
    }

    companion object {
        fun text(status: Int, body: String) = GarminResponse(status, body.toByteArray())

        fun bytes(status: Int, body: ByteArray) = GarminResponse(status, body)

        /** A token shaped like Garmin's: three parts, the middle one holding `exp`. */
        fun jwt(expires: Instant, marker: String = "t"): String {
            val enc = Base64.getUrlEncoder().withoutPadding()
            val header = enc.encodeToString("""{"alg":"RS256"}""".toByteArray())
            val payload = enc.encodeToString("""{"exp":${expires.epochSecond},"m":"$marker"}""".toByteArray())
            return "$header.$payload.${enc.encodeToString(marker.toByteArray())}"
        }
    }
}

fun networkDown(): GarminHttp = GarminHttp { _, _, _, _ -> throw IOException("Unable to resolve host") }
