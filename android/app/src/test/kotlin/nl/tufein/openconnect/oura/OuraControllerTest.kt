package nl.tufein.openconnect.oura

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.oura.OuraAuth
import nl.tufein.openconnect.core.oura.OuraHttp
import nl.tufein.openconnect.core.oura.OuraResponse
import nl.tufein.openconnect.core.oura.OuraTokens
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OuraControllerTest {
    private class MemorySettings : OuraSettings {
        override val canStoreSecrets = true
        override var clientId: String? = "client-1"
        override var clientSecret: String? = "secret-FAKE"
        override var redirectUri: String = OuraAuth.DEFAULT_REDIRECT
        override var tokens: OuraTokens? = OuraTokens("access-1", "refresh-1", Instant.parse("2027-01-01T00:00:00Z"))
        override var pendingLogin: String? = null
        override var lastSyncMs: Long = 0
    }

    private class FakeHttp(var status: Int, var headers: Map<String, String> = emptyMap()) : OuraHttp {
        var calls = 0

        override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
            calls++
            return if (status == 200) OuraResponse(200, """{"data":[],"next_token":null}""") else OuraResponse(status, "{}", this.headers)
        }
    }

    private var clock = Instant.parse("2026-11-14T12:00:00Z")
    private val settings = MemorySettings()

    private fun controller(http: OuraHttp) = OuraController(
        settings, InMemoryRepository(), http, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { ZoneOffset.UTC }, { clock },
    )

    @Test
    fun `after a rate limit the automatic syncs wait for what Oura asked, the button does not`() {
        val http = FakeHttp(429, mapOf("retry-after" to "600"))
        val c = controller(http)

        c.syncIfDue(Duration.ZERO)
        assertEquals(OuraProblem.RATE_LIMITED, c.state.value.problem)
        val asked = http.calls

        clock = clock.plusSeconds(5 * 60) // inside the ten minutes Oura asked for
        c.syncIfDue(Duration.ZERO)
        assertEquals(asked, http.calls, "not asked again while Oura says wait")

        c.sync() // the person presses the button: their choice
        assertTrue(http.calls > asked)
    }

    @Test
    fun `once the cool-down is over the automatic syncs go on and a good sync clears the problem`() {
        val http = FakeHttp(429, mapOf("retry-after" to "600"))
        val c = controller(http)
        c.syncIfDue(Duration.ZERO)

        clock = clock.plusSeconds(11 * 60)
        http.status = 200
        c.syncIfDue(Duration.ZERO)
        assertNull(c.state.value.problem)
        assertTrue(settings.lastSyncMs > 0)
    }

    @Test
    fun `a rate limit without a wait in its answer pauses the automatic syncs for half an hour`() {
        val http = FakeHttp(429)
        val c = controller(http)
        c.syncIfDue(Duration.ZERO)
        val asked = http.calls

        clock = clock.plusSeconds(29 * 60)
        c.syncIfDue(Duration.ZERO)
        assertEquals(asked, http.calls)

        clock = clock.plusSeconds(2 * 60)
        http.status = 200
        c.syncIfDue(Duration.ZERO)
        assertTrue(http.calls > asked)
    }
}
