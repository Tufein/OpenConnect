package nl.tufein.openconnect.garmin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.garmin.GarminApiException
import nl.tufein.openconnect.core.garmin.GarminHttp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Syncing without the screen open, and telling the phone when a background job is wanted. */
class GarminBackgroundTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val http = FakeGarminHttp().also { it.answerLikeGarmin(now) }
    private val settings = FakeGarminSettings(key = GOOD_KEY, autoSync = true)
    private val repo = InMemoryRepository()
    private val wanted = ArrayList<Boolean>()
    private var changed = 0
    private var clock = now

    private fun controller(h: GarminHttp = http) = GarminController(
        settings = settings,
        repo = repo,
        http = h,
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
        zone = { ZoneOffset.UTC },
        now = { clock },
        onDataChanged = { changed++ },
        onBackgroundWanted = { wanted.add(it) },
    )

    // --------------------------------------------------------------------------- the background job

    @Test
    fun `a background sync covers the last seven days and stores them`() {
        settings.days = 90
        val c = controller()
        assertEquals(BackgroundOutcome.SYNCED, runBlocking { c.syncInBackground() })

        assertIs<GarminSyncUi.Done>(c.state.value.sync)
        assertEquals(8421, repo.listDaily(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-10")).single().steps)
        assertEquals(now.toEpochMilli(), settings.lastSyncMs)
        assertEquals(now, c.state.value.lastSync)
        assertEquals(1, changed)
        assertTrue(http.callsTo("steps/daily/2026-11-06/2026-11-12").isNotEmpty(), "7 days, not the 90 of the manual choice")
    }

    @Test
    fun `a background sync is skipped when there is nothing to do`() {
        fun outcome(prepare: () -> Unit): BackgroundOutcome {
            settings.key = GOOD_KEY
            settings.autoSync = true
            settings.lastSyncMs = 0
            prepare()
            return runBlocking { controller().syncInBackground() }
        }
        assertEquals(BackgroundOutcome.SKIPPED, outcome { settings.key = null })
        assertEquals(BackgroundOutcome.SKIPPED, outcome { settings.autoSync = false })
        assertEquals(BackgroundOutcome.SKIPPED, outcome { settings.lastSyncMs = now.minusSeconds(5 * 60).toEpochMilli() })
        assertTrue(http.calls.isEmpty())
        assertEquals(BackgroundOutcome.SYNCED, outcome { settings.lastSyncMs = now.minusSeconds(11 * 60).toEpochMilli() })
    }

    @Test
    fun `a background sync does not start while another one is running`() {
        val c = controller()
        var inner: BackgroundOutcome? = null
        http.on("GET", "/usersummary-service/stats/steps") {
            inner = runBlocking { c.syncInBackground() } // the screen's sync is still busy
            FakeGarminHttp.text(200, "[]")
        }
        c.sync()
        assertEquals(BackgroundOutcome.SKIPPED, inner)

        // A lone sync asks for the steps in the same number of requests: the second sync never ran.
        val lone = FakeGarminHttp().also { it.answerLikeGarmin(now) }
        controller(lone).sync()
        val oneSync = lone.callsTo("/usersummary-service/stats/steps").size
        assertTrue(oneSync > 0)
        assertEquals(oneSync, http.callsTo("/usersummary-service/stats/steps").size)
    }

    @Test
    fun `no connection or a busy Garmin means try again later`() {
        assertEquals(BackgroundOutcome.RETRY, runBlocking { controller(FakeGarminHttp.networkDown()).syncInBackground() })
        assertEquals(0, settings.lastSyncMs, "a sync that did not finish does not count")

        http.status("GET", "/usersummary-service/stats/steps", 429)
        assertEquals(BackgroundOutcome.RETRY, runBlocking { controller().syncInBackground() })

        val broken = FakeGarminHttp().also { it.answerLikeGarmin(now) }
        broken.status("GET", "/usersummary-service/stats/steps", 503)
        assertEquals(BackgroundOutcome.RETRY, runBlocking { controller(broken).syncInBackground() })
    }

    @Test
    fun `a refused key stops the background job instead of trying forever`() {
        http.status("POST", "/personal-api-key/token", 401)
        val c = controller()
        wanted.clear()
        assertEquals(BackgroundOutcome.KEY_REJECTED, runBlocking { c.syncInBackground() })
        assertEquals(KeyCheck.REJECTED, c.state.value.keyCheck)
        assertEquals(listOf(false), wanted, "the phone is told to stop the job")
    }

    @Test
    fun `a crash in a background sync is shown and retried`() {
        http.on("GET", "/usersummary-service/v2/dailysummaries") { throw IllegalStateException("boom") }
        val c = controller()
        assertEquals(BackgroundOutcome.RETRY, runBlocking { c.syncInBackground() })
        assertEquals(GarminSyncUi.Failed("boom"), c.state.value.sync)
    }

    // ------------------------------------------------------------------- how often the app syncs

    @Test
    fun `the app syncs when it opens if the last sync is ten minutes old`() {
        settings.lastSyncMs = now.minusSeconds(5 * 60).toEpochMilli()
        controller().autoSyncIfDue()
        assertTrue(http.calls.isEmpty())

        settings.lastSyncMs = now.minusSeconds(11 * 60).toEpochMilli()
        controller().autoSyncIfDue()
        assertTrue(http.calls.isNotEmpty())
    }

    @Test
    fun `a sync that finished a moment ago is not repeated by the next opening`() {
        val c = controller()
        c.autoSyncIfDue()
        val calls = http.calls.size
        c.autoSyncIfDue()
        controller().autoSyncIfDue()
        assertEquals(calls, http.calls.size)
    }

    // ------------------------------------------------------------- telling the phone about the job

    @Test
    fun `the phone is told whether a background job is wanted whenever that changes`() {
        settings.key = null
        val c = controller()
        assertEquals(listOf(false), wanted, "at the start: no key, no job")

        wanted.clear()
        c.saveKey("not a key")
        assertTrue(wanted.isEmpty(), "a key that was not saved changes nothing")

        c.saveKey(GOOD_KEY)
        assertEquals(true, wanted.last())

        c.setAutoSync(false)
        assertEquals(false, wanted.last())
        c.setAutoSync(true)
        assertEquals(true, wanted.last())

        c.forgetKey()
        assertEquals(false, wanted.last())
    }

    @Test
    fun `a key saved while auto-sync is off schedules nothing`() {
        settings.key = null
        settings.autoSync = false
        val c = controller()
        c.saveKey(GOOD_KEY)
        assertFalse(wanted.last())
    }

    @Test
    fun `the job is wanted from the start when the settings say so`() {
        controller()
        assertEquals(listOf(true), wanted)
    }

    @Test
    fun `no last sync time means a first sync is due`() {
        val c = controller()
        assertNull(c.state.value.lastSync)
        c.autoSyncIfDue()
        assertEquals(now, c.state.value.lastSync)
    }

    private companion object {
        const val GOOD_KEY = "GCDC_0123456789abcdef.fedcba9876543210.AbCdEf/12+34=="
    }
}
