package nl.tufein.openconnect.core.garmin

import nl.tufein.openconnect.core.garmin.FakeGarminHttp.Companion.jwt
import nl.tufein.openconnect.core.garmin.FakeGarminHttp.Companion.text
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalApiClientTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private var clock = now
    private val http = FakeGarminHttp()
    private var key: String? = "SECRET-KEY-123"
    private val client = PersonalApiClient(http, apiKey = { key }, clock = { clock })

    private val tokenPath = "/personalapitoken-service/personal-api/personal-api-key/token"

    private fun tokenAnswers(vararg tokens: String) {
        val queue = tokens.toMutableList()
        http.on("POST", tokenPath) { text(200, if (queue.size > 1) queue.removeAt(0) else queue[0]) }
    }

    private fun kind(block: () -> Unit) = assertFailsWith<GarminApiException> { block() }.kind

    @Test
    fun `the key is exchanged with the documented request and the token is used as a bearer`() {
        val token = jwt(now.plusSeconds(86_400))
        tokenAnswers(token)
        http.json("/usersummary-service/stats/steps/daily/2026-11-01/2026-11-12", """[{"calendarDate":"2026-11-10"}]""")

        val json = client.getJson("/usersummary-service/stats/steps/daily/2026-11-01/2026-11-12")

        assertNotNull(json)
        val exchange = http.calls[0]
        assertEquals("POST", exchange.method)
        assertEquals("https://personal.connectapi.garmin.com$tokenPath", exchange.url)
        assertEquals("ApiKey SECRET-KEY-123", exchange.headers["Authorization"])
        val data = http.calls[1]
        assertEquals("GET", data.method)
        assertEquals("Bearer $token", data.headers["Authorization"])
        assertEquals("application/json", data.headers["Accept"])
    }

    @Test
    fun `the api key is only ever sent in the token request`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        http.json("/a", "[]")
        http.json("/b", "[]")
        client.getJson("/a", listOf("x" to "1"))
        client.getJson("/b")
        val withKey = http.calls.filter { "SECRET-KEY-123" in it.url || it.headers.values.any { v -> "SECRET-KEY-123" in v } }
        assertEquals(listOf("POST"), withKey.map { it.method })
    }

    @Test
    fun `query values are encoded`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        http.json("/search", "[]")
        client.getJson("/search", listOf("start" to "0", "activityType" to "trail running&x=1"))
        assertEquals("start=0&activityType=trail+running%26x%3D1", http.callsTo("/search").single().query)
    }

    @Test
    fun `a token is reused until shortly before it ends`() {
        tokenAnswers(jwt(now.plusSeconds(86_400), "first"), jwt(now.plusSeconds(2 * 86_400), "second"))
        http.json("/a", "[]")
        client.getJson("/a")
        client.getJson("/a")
        assertEquals(1, http.callsTo(tokenPath).size)

        clock = now.plusSeconds(86_400 - 9 * 60) // 9 minutes left: renewed
        client.getJson("/a")
        assertEquals(2, http.callsTo(tokenPath).size)
        assertTrue(http.calls.last().headers.getValue("Authorization").endsWith(jwt(now.plusSeconds(2 * 86_400), "second")))
    }

    @Test
    fun `a different key gets a new token`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        http.json("/a", "[]")
        client.getJson("/a")
        key = "ANOTHER-KEY"
        client.getJson("/a")
        assertEquals(listOf("ApiKey SECRET-KEY-123", "ApiKey ANOTHER-KEY"), http.callsTo(tokenPath).map { it.headers["Authorization"] })
    }

    @Test
    fun `a 401 on data renews the token once and repeats the request`() {
        tokenAnswers(jwt(now.plusSeconds(86_400), "old"), jwt(now.plusSeconds(86_400), "new"))
        http.on("GET", "/a") { call -> if (call.headers["Authorization"]!!.endsWith(jwt(now.plusSeconds(86_400), "new"))) text(200, "[1]") else text(401, "") }

        assertNotNull(client.getJson("/a"))
        assertEquals(2, http.callsTo(tokenPath).size)
        assertEquals(2, http.callsTo("/a").size)
    }

    @Test
    fun `a second 401 means the key is rejected`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        http.status("GET", "/a", 401)
        assertEquals(GarminApiException.Kind.KEY_REJECTED, kind { client.getJson("/a") })
        assertEquals(2, http.callsTo("/a").size) // not a loop
    }

    @Test
    fun `a rejected key is reported when the token is asked for`() {
        http.status("POST", tokenPath, 401)
        assertEquals(GarminApiException.Kind.KEY_REJECTED, kind { client.getJson("/a") })
        http.status("POST", tokenPath, 403)
        assertEquals(GarminApiException.Kind.KEY_REJECTED, kind { client.getJson("/a") })
    }

    @Test
    fun `no key means no request at all`() {
        key = null
        assertEquals(GarminApiException.Kind.NO_KEY, kind { client.getJson("/a") })
        key = "   "
        assertEquals(GarminApiException.Kind.NO_KEY, kind { client.getJson("/a") })
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun `verify only asks for a token and reports a rejected key`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        client.verify()
        client.verify() // the token is still good
        assertEquals(listOf("POST"), http.calls.map { it.method })
        assertEquals(1, http.calls.size)

        http.status("POST", tokenPath, 401)
        client.invalidate()
        assertEquals(GarminApiException.Kind.KEY_REJECTED, kind { client.verify() })
        key = null
        assertEquals(GarminApiException.Kind.NO_KEY, kind { client.verify() })
    }

    @Test
    fun `a key with spaces around it is trimmed`() {
        key = "  SECRET-KEY-123\n"
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        http.json("/a", "[]")
        client.getJson("/a")
        assertEquals("ApiKey SECRET-KEY-123", http.calls[0].headers["Authorization"])
    }

    @Test
    fun `http statuses become errors that say whether a sync should go on`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        val expected = mapOf(
            403 to (GarminApiException.Kind.ACCESS_DENIED to false),
            404 to (GarminApiException.Kind.NOT_FOUND to false),
            429 to (GarminApiException.Kind.RATE_LIMITED to true),
            500 to (GarminApiException.Kind.SERVER to true),
            503 to (GarminApiException.Kind.SERVER to true),
            418 to (GarminApiException.Kind.BAD_RESPONSE to false),
        )
        for ((status, want) in expected) {
            http.status("GET", "/a", status)
            val error = assertFailsWith<GarminApiException> { client.getJson("/a") }
            assertEquals(want.first, error.kind, "status $status")
            assertEquals(want.second, error.stopsSync, "status $status")
            assertEquals(status, error.status)
        }
    }

    @Test
    fun `losing the connection is reported as a network error`() {
        val offline = PersonalApiClient(networkDown(), apiKey = { key })
        val error = assertFailsWith<GarminApiException> { offline.getJson("/a") }
        assertEquals(GarminApiException.Kind.NETWORK, error.kind)
        assertTrue(error.stopsSync)
        assertFalse("SECRET-KEY-123" in error.message.orEmpty())
    }

    @Test
    fun `a token answer can be bare or json, and anything else is refused`() {
        val token = jwt(now.plusSeconds(86_400))
        http.json("/a", "[]")
        for (body in listOf(token, "\"$token\"", "$token\n", """{"access_token":"$token"}""")) {
            http.on("POST", tokenPath) { text(200, body) }
            client.invalidate()
            client.getJson("/a")
            assertEquals("Bearer $token", http.calls.last().headers["Authorization"], body)
        }
        for (body in listOf("", "  ", "<html>oops</html>", "two words", "{}")) {
            http.on("POST", tokenPath) { text(200, body) }
            client.invalidate()
            assertEquals(GarminApiException.Kind.BAD_RESPONSE, kind { client.getJson("/a") }, body)
        }
    }

    @Test
    fun `a token without a readable expiry is still used`() {
        http.on("POST", tokenPath) { text(200, "opaque-token-without-dots") }
        http.json("/a", "[]")
        client.getJson("/a")
        client.getJson("/a")
        assertEquals(1, http.callsTo(tokenPath).size)
        assertEquals("Bearer opaque-token-without-dots", http.calls.last().headers["Authorization"])
    }

    @Test
    fun `empty answers are null and broken json is an error`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        http.json("/empty", "")
        http.json("/null", "null")
        http.json("/broken", "<html>")
        assertNull(client.getJson("/empty"))
        assertNull(client.getJson("/null"))
        assertEquals(GarminApiException.Kind.BAD_RESPONSE, kind { client.getJson("/broken") })
    }

    @Test
    fun `downloads return the bytes and refuse an empty file`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        val zip = byteArrayOf(0x50, 0x4B, 3, 4, 9)
        http.on("GET", "/download-service/files/activity/1") { FakeGarminHttp.bytes(200, zip) }
        http.on("GET", "/download-service/files/activity/2") { FakeGarminHttp.bytes(200, ByteArray(0)) }
        assertContentEquals(zip, client.download("/download-service/files/activity/1"))
        assertEquals(GarminApiException.Kind.BAD_RESPONSE, kind { client.download("/download-service/files/activity/2") })
        assertTrue("zip" in http.callsTo("/activity/1").single().headers.getValue("Accept"))
    }

    // ------------------------------------------------------------------------------------ hiccups and Retry-After

    private val waits = ArrayList<Long>()
    private fun retrying() = PersonalApiClient(http, apiKey = { key }, clock = { clock }, retry = nl.tufein.openconnect.core.RetryPolicy(listOf(1_500L, 5_000L)) { waits += it })

    @Test
    fun `a busy Garmin is asked again before the sync is given up`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        var asked = 0
        http.on("GET", "/a") { if (asked++ == 0) text(503, "") else text(200, "[1]") }

        assertNotNull(retrying().getJson("/a"))
        assertEquals(2, http.callsTo("/a").size)
        assertEquals(listOf(1_500L), waits)
    }

    @Test
    fun `a dropped connection during the token request is retried too`() {
        var asked = 0
        val flaky = nl.tufein.openconnect.core.garmin.GarminHttp { method, url, headers, timeout ->
            if (asked++ == 0) throw java.io.IOException("connection reset")
            http.send(method, url, headers, timeout)
        }
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        http.json("/a", "[1]")
        val client = PersonalApiClient(flaky, apiKey = { key }, clock = { clock }, retry = nl.tufein.openconnect.core.RetryPolicy(listOf(10L)) { waits += it })
        assertNotNull(client.getJson("/a"))
        assertEquals(listOf(10L), waits)
    }

    @Test
    fun `a rate limit that asks for a short wait is waited out, a long one is reported with its wait`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        var asked = 0
        http.on("GET", "/short") { if (asked++ == 0) text(429, "", mapOf("retry-after" to "3")) else text(200, "[1]") }
        assertNotNull(retrying().getJson("/short"))
        assertEquals(listOf(3_000L), waits)

        waits.clear()
        http.on("GET", "/long") { text(429, "", mapOf("retry-after" to "900")) }
        val error = assertFailsWith<GarminApiException> { retrying().getJson("/long") }
        assertEquals(GarminApiException.Kind.RATE_LIMITED, error.kind)
        assertEquals(900L, error.retryAfterS)
        assertEquals(1, http.callsTo("/long").size, "no point waiting a quarter of an hour inside a sync")
        assertEquals(emptyList(), waits)
    }

    @Test
    fun `a refused or missing request is never repeated`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        http.status("GET", "/a", 404)
        assertEquals(GarminApiException.Kind.NOT_FOUND, kind { retrying().getJson("/a") })
        assertEquals(1, http.callsTo("/a").size)
        assertEquals(emptyList(), waits)
    }

    @Test
    fun `without a retry policy nothing is repeated, as before`() {
        tokenAnswers(jwt(now.plusSeconds(86_400)))
        http.status("GET", "/a", 503)
        assertEquals(GarminApiException.Kind.SERVER, kind { client.getJson("/a") })
        assertEquals(1, http.callsTo("/a").size)
    }
}
