package nl.tufein.openconnect.garmin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.garmin.GarminApiException
import nl.tufein.openconnect.garmin.FakeGarminHttp.Companion.jwt
import nl.tufein.openconnect.garmin.FakeGarminHttp.Companion.text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarminControllerTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val http = FakeGarminHttp().also { it.answerLikeGarmin(now) }
    private val settings = FakeGarminSettings()
    private val repo = InMemoryRepository()
    private var changed = 0

    /** Everything runs on the calling thread, so a test can look at the state right after an action. */
    private fun controller(h: nl.tufein.openconnect.core.garmin.GarminHttp = http) = GarminController(
        settings = settings,
        repo = repo,
        http = h,
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
        zone = { ZoneOffset.UTC },
        now = { now },
        onDataChanged = { changed++ },
    )

    private val goodKey = "GCDC_0123456789abcdef.fedcba9876543210.AbCdEf/12+34=="

    // ------------------------------------------------------------------------------------------ the key

    @Test
    fun `the state starts from what the settings remember`() {
        settings.key = goodKey
        settings.days = 90
        settings.autoSync = true
        settings.lastSyncMs = now.minusSeconds(3600).toEpochMilli()
        val ui = controller().state.value
        assertTrue(ui.hasKey)
        assertEquals(90, ui.days)
        assertTrue(ui.autoSync)
        assertEquals(now.minusSeconds(3600), ui.lastSync)
        assertEquals(KeyCheck.UNKNOWN, ui.keyCheck)

        settings.days = 14 // not a choice the screen offers
        assertEquals(30, controller().state.value.days)
    }

    @Test
    fun `a pasted key is cleaned up, saved and checked with Garmin`() {
        val c = controller()
        c.saveKey("  ApiKey $goodKey\n")
        assertEquals(goodKey, settings.key)
        assertEquals(KeyCheck.ACCEPTED, c.state.value.keyCheck)
        assertTrue(c.state.value.hasKey)
        assertNull(c.state.value.keyProblem)
        val token = http.calls.single()
        assertEquals("POST", token.method)
        assertEquals("ApiKey $goodKey", token.headers["Authorization"])
    }

    @Test
    fun `a key that was wrapped over two lines by the clipboard is joined`() {
        controller().saveKey(goodKey.take(20) + "\n" + goodKey.drop(20))
        assertEquals(goodKey, settings.key)
    }

    @Test
    fun `something that cannot be a key is not saved and Garmin is not asked`() {
        val c = controller()
        for (bad in listOf("", "   ", "short", "Key: GCDC_0123456789abcdef.fedcba98765432", "<html>not a key at all, really</html>")) {
            c.saveKey(bad)
            assertEquals(KeyProblem.MALFORMED, c.state.value.keyProblem, bad)
        }
        assertNull(settings.key)
        assertFalse(c.state.value.hasKey)
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun `a phone without secure storage keeps no key`() {
        settings.canStoreKey = false
        val c = controller()
        assertFalse(c.state.value.canStoreKey)
        c.saveKey(goodKey)
        assertEquals(KeyProblem.CANNOT_STORE, c.state.value.keyProblem)
        assertNull(settings.key)
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun `a key Garmin refuses is reported but stays until the person removes it`() {
        http.status("POST", "/personal-api-key/token", 401)
        val c = controller()
        c.saveKey(goodKey)
        assertEquals(KeyCheck.REJECTED, c.state.value.keyCheck)
        assertEquals(goodKey, settings.key)

        c.forgetKey()
        assertNull(settings.key)
        assertFalse(c.state.value.hasKey)
        assertEquals(KeyCheck.UNKNOWN, c.state.value.keyCheck)
    }

    @Test
    fun `no connection is not the same as a refused key`() {
        val c = controller(FakeGarminHttp.networkDown())
        c.saveKey(goodKey)
        assertEquals(KeyCheck.UNREACHABLE, c.state.value.keyCheck)
        assertEquals(goodKey, settings.key, "it may be fine, Garmin just could not be asked")
    }

    @Test
    fun `a new key replaces the old one and gets a fresh token`() {
        val c = controller()
        c.saveKey(goodKey)
        val other = "GCDC_ffffffffffffffff.0000000000000000.ZZZZZZZZZZZZZZ=="
        c.saveKey(other)
        assertEquals(other, settings.key)
        assertEquals(listOf("ApiKey $goodKey", "ApiKey $other"), http.calls.map { it.headers["Authorization"] })
    }

    // --------------------------------------------------------------------------------------- settings

    @Test
    fun `only the offered numbers of days are accepted and remembered`() {
        val c = controller()
        c.setDays(90)
        assertEquals(90, settings.days)
        assertEquals(90, c.state.value.days)
        c.setDays(5)
        assertEquals(90, settings.days)
        c.setAutoSync(true)
        assertTrue(settings.autoSync)
        assertTrue(c.state.value.autoSync)
    }

    // ------------------------------------------------------------------------------------------- sync

    @Test
    fun `a sync stores the data, tells the app and remembers when it was`() {
        settings.key = goodKey
        val c = controller()
        c.sync()

        val done = assertIs<GarminSyncUi.Done>(c.state.value.sync)
        assertEquals(2, done.report.daysUpdated)
        assertEquals(1, done.report.nightsUpdated)
        assertEquals(8421, repo.listDaily(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-10")).single().steps)
        assertEquals(1, changed)
        assertEquals(now, c.state.value.lastSync)
        assertEquals(now.toEpochMilli(), settings.lastSyncMs)
        assertEquals(KeyCheck.ACCEPTED, c.state.value.keyCheck)
        // 30 days by default: 2026-10-14 to 2026-11-12, asked for in windows of 28 days
        assertTrue(http.callsTo("steps/daily/2026-10-14/2026-11-10").isNotEmpty() && http.callsTo("steps/daily/2026-11-11/2026-11-12").isNotEmpty())
    }

    @Test
    fun `without a key nothing is asked`() {
        val c = controller()
        c.sync()
        assertEquals(GarminSyncUi.Idle, c.state.value.sync)
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun `a refused key during a sync is shown and the time of the last sync stays`() {
        settings.key = goodKey
        settings.lastSyncMs = 12345
        http.status("POST", "/personal-api-key/token", 401)
        val c = controller()
        c.sync()

        val done = assertIs<GarminSyncUi.Done>(c.state.value.sync)
        assertEquals(GarminApiException.Kind.KEY_REJECTED, done.report.stopped?.kind)
        assertEquals(KeyCheck.REJECTED, c.state.value.keyCheck)
        assertEquals(12345, settings.lastSyncMs)
        assertEquals(0, changed)
    }

    @Test
    fun `a problem that stops the sync early does not count as a complete sync`() {
        settings.key = goodKey
        http.status("GET", "/usersummary-service/stats/steps", 429)
        val c = controller()
        c.sync()
        assertEquals(GarminApiException.Kind.RATE_LIMITED, assertIs<GarminSyncUi.Done>(c.state.value.sync).report.stopped?.kind)
        assertEquals(0, settings.lastSyncMs)
    }

    @Test
    fun `cancelling ends the sync after the current step and saves what was fetched`() {
        settings.key = goodKey
        lateinit var c: GarminController
        // The person presses Cancel while the steps are being fetched.
        http.on("GET", "/usersummary-service/stats/steps") { c.cancel(); text(200, """[{"calendarDate":"2026-11-10","totalSteps":5000}]""") }
        c = controller()
        c.sync()

        val report = assertIs<GarminSyncUi.Done>(c.state.value.sync).report
        assertTrue(report.cancelled)
        assertEquals(5000, repo.listDaily(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-10")).single().steps)
        assertEquals(0, settings.lastSyncMs)
        assertTrue(http.callsTo("/sleep-service").isEmpty())
    }

    @Test
    fun `a second sync while one is running is ignored`() {
        settings.key = goodKey
        settings.days = 7 // one window per data type
        val release = CountDownLatch(1)
        val inRequest = CountDownLatch(1)
        http.on("GET", "/usersummary-service/stats/steps") {
            inRequest.countDown()
            release.await(10, TimeUnit.SECONDS)
            text(200, "[]")
        }
        val worker = Executors.newSingleThreadExecutor()
        try {
            val c = GarminController(settings, repo, http, CoroutineScope(Dispatchers.Unconfined), worker.asCoroutineDispatcher(), { ZoneOffset.UTC }, { now })
            c.sync()
            assertTrue(inRequest.await(10, TimeUnit.SECONDS))
            assertIs<GarminSyncUi.Running>(c.state.value.sync)
            c.sync() // ignored
            release.countDown()
            val deadline = System.currentTimeMillis() + 10_000
            while (c.state.value.sync !is GarminSyncUi.Done && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertIs<GarminSyncUi.Done>(c.state.value.sync)
            assertEquals(1, http.callsTo("/usersummary-service/stats/steps").size, "asked for once, not once per press")
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    @Test
    fun `an unexpected crash in a sync is shown instead of escaping`() {
        settings.key = goodKey
        http.on("GET", "/usersummary-service/v2/dailysummaries") { throw IllegalStateException("boom") }
        val c = controller()
        c.sync()
        assertEquals(GarminSyncUi.Failed("boom"), c.state.value.sync)
        c.sync() // and the controller is usable again
        assertIs<GarminSyncUi.Failed>(c.state.value.sync)
    }

    // ----------------------------------------------------------------------------------- auto-sync

    @Test
    fun `auto-sync runs when it is on, there is a key, and the last sync is old enough`() {
        settings.key = goodKey
        settings.autoSync = true
        settings.days = 90
        val c = controller()
        c.autoSyncIfDue()
        assertIs<GarminSyncUi.Done>(c.state.value.sync)
        assertTrue(http.callsTo("steps/daily/2026-11-06/2026-11-12").isNotEmpty(), "a background sync covers the last 7 days, not the 90 of the manual choice")
        assertEquals(now.toEpochMilli(), settings.lastSyncMs)
    }

    @Test
    fun `auto-sync leaves the network alone when it is off, has no key, or ran recently`() {
        // off
        settings.key = goodKey
        controller().autoSyncIfDue()
        // no key
        settings.key = null
        settings.autoSync = true
        controller().autoSyncIfDue()
        // recent
        settings.key = goodKey
        settings.lastSyncMs = now.minusSeconds(20 * 60).toEpochMilli()
        controller().autoSyncIfDue()
        assertTrue(http.calls.isEmpty())

        settings.lastSyncMs = now.minusSeconds(30 * 60 + 1).toEpochMilli()
        controller().autoSyncIfDue()
        assertNotNull(http.calls.firstOrNull())
    }
}
