package nl.tufein.openconnect.oura

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertFalse
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
        override var ringBattery: Int = -1
        override var ringBatteryAt: Long = 0
        override var ringBatteryRefused: Boolean = false
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

    @Test
    fun `a background sync that is cancelled before it starts does not leave the controller stuck`() {
        val http = FakeHttp(200)
        val c = controller(http)
        runBlocking {
            val job = launch {
                currentCoroutineContext().job.cancel() // the job scheduler stops the worker right here
                c.syncInBackground(Duration.ZERO)
            }
            job.join()
            assertTrue(job.isCancelled)
        }
        assertEquals(0, http.calls, "Oura was never asked")
        assertEquals(false, c.state.value.busy, "no spinner left behind")
        assertTrue(runBlocking { c.syncInBackground(Duration.ZERO) }, "the next background sync is not blocked for good")
    }

    @Test
    fun `a sync keeps the charge of the ring for the battery card`() {
        val http = object : OuraHttp {
            override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int) =
                if ("ring_battery_level" in url) {
                    OuraResponse(200, """{"data":[{"timestamp_unix":1763121600000,"level":64}]}""")
                } else {
                    OuraResponse(200, """{"data":[],"next_token":null}""")
                }
        }
        val c = controller(http)
        c.sync()
        assertEquals(64, settings.ringBattery)
        assertEquals(1763121600000L, settings.ringBatteryAt)
        assertFalse(settings.ringBatteryRefused, "a charge that came in clears an earlier refusal")
        assertNull(c.state.value.problem)
    }

    @Test
    fun `a ring that will not tell its charge does not fail the sync`() {
        val http = object : OuraHttp {
            override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int) =
                if ("ring_battery_level" in url) {
                    OuraResponse(403, "{}")
                } else {
                    OuraResponse(200, """{"data":[],"next_token":null}""")
                }
        }
        val c = controller(http)
        c.sync()
        assertNull(c.state.value.problem)
        assertEquals(-1, settings.ringBattery, "the card shows a dash, not a wrong number")
        assertTrue(settings.ringBatteryRefused, "the card can say that a new login is needed")
    }
}
