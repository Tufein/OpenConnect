package nl.tufein.openconnect.garmin

import nl.tufein.openconnect.core.garmin.GarminHttp
import nl.tufein.openconnect.core.garmin.GarminResponse
import java.io.IOException
import java.time.Instant
import java.util.Base64

/** Answers Garmin's Personal API from memory and remembers every request; the newest matching route answers. */
class FakeGarminHttp : GarminHttp {
    data class Call(val method: String, val url: String, val headers: Map<String, String>)

    private class Route(val method: String, val pathContains: String, val answer: (Call) -> GarminResponse)

    val calls = java.util.concurrent.CopyOnWriteArrayList<Call>()
    private val routes = java.util.concurrent.CopyOnWriteArrayList<Route>()

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

    /** The usual day: a good token and a few days of steps, everything else not found. */
    fun answerLikeGarmin(now: Instant) {
        on("POST", "/personal-api-key/token") { text(200, jwt(now.plusSeconds(86_400))) }
        json(
            "/usersummary-service/stats/steps/daily",
            """[{"calendarDate":"2026-11-10","totalSteps":8421,"totalDistance":6310,"stepGoal":10000},{"calendarDate":"2026-11-11","totalSteps":10234,"totalDistance":7800,"stepGoal":10000}]""",
        )
        json(
            "/sleep-service/sleep/dailySleepSummariesByDate",
            """[{"calendarDate":"2026-11-11","sleepTimeSeconds":27000,"deepSleepSeconds":5400,"lightSleepSeconds":15000,"remSleepSeconds":6600,"awakeSleepSeconds":900,"sleepScores":{"overall":{"value":84}}}]""",
        )
        json("/activitylist-service/activities/search/activities", "[]")
    }

    companion object {
        fun text(status: Int, body: String) = GarminResponse(status, body.toByteArray())

        /** A token shaped like Garmin's: three parts, the middle one holding `exp`. */
        fun jwt(expires: Instant): String {
            val enc = Base64.getUrlEncoder().withoutPadding()
            val header = enc.encodeToString("""{"alg":"RS256"}""".toByteArray())
            val payload = enc.encodeToString("""{"exp":${expires.epochSecond}}""".toByteArray())
            return "$header.$payload.${enc.encodeToString("sig".toByteArray())}"
        }

        fun networkDown(): GarminHttp = GarminHttp { _, _, _, _ -> throw IOException("Unable to resolve host") }
    }
}

/** Settings in memory, so a test does not need Android's encrypted preferences. */
class FakeGarminSettings(
    override var canStoreKey: Boolean = true,
    override var key: String? = null,
    override var days: Int = 30,
    override var autoSync: Boolean = false,
    override var lastSyncMs: Long = 0,
) : GarminSettings
