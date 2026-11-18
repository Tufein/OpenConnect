package nl.tufein.openconnect.whoop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.oura.OuraResponse
import nl.tufein.openconnect.core.whoop.WhoopAuth
import nl.tufein.openconnect.core.whoop.WhoopHttp
import nl.tufein.openconnect.core.whoop.WhoopTokens
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhoopControllerTest {
    private class MemorySettings : WhoopSettings {
        override val canStoreSecrets = true
        override var clientId: String? = "client-1"
        override var clientSecret: String? = "secret-FAKE"
        override var redirectUri: String = WhoopAuth.DEFAULT_REDIRECT
        override var tokens: WhoopTokens? = WhoopTokens("access-1", "refresh-1", Instant.parse("2027-01-01T00:00:00Z"))
        override var pendingLogin: String? = null
        override var lastSyncMs: Long = 0
    }

    private class FakeHttp(var status: Int, var headers: Map<String, String> = emptyMap()) : WhoopHttp {
        var calls = 0

        override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
            calls++
            return if (status == 200) OuraResponse(200, """{"records":[],"next_token":null}""") else OuraResponse(status, "{}", this.headers)
        }
    }

    private var clock = Instant.parse("2026-11-14T12:00:00Z")
    private val settings = MemorySettings()
    private val background = ArrayList<Boolean>()

    private fun controller(http: WhoopHttp) = WhoopController(
        settings, InMemoryRepository(), http, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { ZoneOffset.UTC }, { clock },
        onBackgroundWanted = { background += it },
    )

    @Test
    fun `after a rate limit the automatic syncs wait for what WHOOP asked, the button does not`() {
        val http = FakeHttp(429, mapOf("x-ratelimit-reset" to "600"))
        val c = controller(http)

        c.syncIfDue(Duration.ZERO)
        assertEquals(WhoopProblem.RATE_LIMITED, c.state.value.problem)
        val asked = http.calls

        clock = clock.plusSeconds(5 * 60) // inside the ten minutes WHOOP asked for
        c.syncIfDue(Duration.ZERO)
        assertEquals(asked, http.calls, "not asked again while WHOOP says wait")

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
    fun `a first sync looks a month back and a later automatic one a couple of days, or a week after a long pause`() {
        val http = FakeHttp(200)
        val urls = ArrayList<String>()
        val recording = WhoopHttp { m, u, h, f, t -> urls += u; http.send(m, u, h, f, t) }
        val c = controller(recording)
        // "Today" is the real calendar day on the phone's clock (the controller's zone); only the clock of the cool-downs is fake.
        val today = java.time.LocalDate.now(ZoneOffset.UTC)
        fun cycleStart() = urls.first { "/cycle" in it }.substringAfter("start=").substringBefore('&').replace("%3A", ":")
        fun startOf(daysBack: Long) = today.minusDays(daysBack).atStartOfDay(ZoneOffset.UTC).toInstant().toString()

        c.sync()
        assertEquals(startOf(30), cycleStart(), "30 days, and a day more for the night before the first morning")

        urls.clear()
        clock = clock.plusSeconds(20 * 60)
        c.syncIfDue(Duration.ofMinutes(10))
        assertEquals(startOf(2), cycleStart(), "two days after a short pause")

        urls.clear()
        clock = clock.plus(Duration.ofHours(13))
        c.syncIfDue(Duration.ofMinutes(10))
        assertEquals(startOf(7), cycleStart(), "a week after a pause of twelve hours or more")
    }

    @Test
    fun `logging out and removing the application stop the background job`() {
        val c = controller(FakeHttp(200))
        assertEquals(listOf(true), background, "a saved login asks for the job")
        c.disconnect()
        assertEquals(false, background.last())
        assertTrue(settings.tokens == null && settings.clientId != null, "log out keeps the application")
        c.forget()
        assertNull(settings.clientId)
        assertNull(settings.clientSecret)
    }

    @Test
    fun `a blank secret keeps the saved one and another client id ends the old login`() {
        val c = controller(FakeHttp(200))
        c.saveClient("client-1", "", "")
        assertEquals("secret-FAKE", settings.clientSecret)
        assertEquals(WhoopAuth.DEFAULT_REDIRECT, settings.redirectUri)
        assertTrue(settings.tokens != null, "the same application keeps its login")
        c.saveClient("client-2", "other-secret", "openconnect://whoop")
        assertNull(settings.tokens, "tokens of another application are of no use")
        c.saveClient("", "", "")
        assertEquals(WhoopProblem.MISSING_FIELDS, c.state.value.problem)
    }
}
