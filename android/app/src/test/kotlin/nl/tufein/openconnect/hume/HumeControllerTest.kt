package nl.tufein.openconnect.hume

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.tufein.openconnect.FakeHumeHealth
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.hume.HumeReadings
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HumeControllerTest {
    private class MemorySettings : HumeSettings {
        override var origin: String? = null
        override var lastSyncMs: Long = 0
    }

    private val hume = "com.elink.fittrackhealth.pro"
    private var clock = Instant.parse("2026-11-14T12:00:00Z")
    private val settings = MemorySettings()
    private val gateway = FakeHumeHealth()
    private val background = ArrayList<Boolean>()
    private val log = ArrayList<SyncLogEntry>()
    private val repo = InMemoryRepository()

    private fun controller() = HumeController(
        settings, repo, gateway, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { ZoneOffset.UTC }, { clock },
        onSynced = { log += it },
        onBackgroundWanted = { background += it },
    )

    private fun today(): LocalDate = LocalDate.now(ZoneOffset.UTC)

    @Test
    fun `a first sync looks a month back, a later automatic one a couple of days, or a week after a long pause`() {
        gateway.byOrigin[hume] = HumeReadings(steps = mapOf(today() to 1_000))
        val c = controller()

        c.sync()
        assertEquals(today().minusDays(29), gateway.reads.last().first, "30 days including today")

        clock = clock.plusSeconds(20 * 60)
        c.syncIfDue(Duration.ofMinutes(10))
        assertEquals(today().minusDays(1), gateway.reads.last().first, "two days after a short pause")

        clock = clock.plus(Duration.ofHours(13))
        c.syncIfDue(Duration.ofMinutes(10))
        assertEquals(today().minusDays(6), gateway.reads.last().first, "a week after a pause of twelve hours or more")
    }

    @Test
    fun `automatic syncs stay quiet without permission, the button says what is wrong`() {
        gateway.access = HealthAccess.NEEDS_PERMISSION
        gateway.byOrigin[hume] = HumeReadings(steps = mapOf(today() to 1_000))
        val c = controller()

        c.syncIfDue(Duration.ZERO)
        assertTrue(gateway.reads.isEmpty())
        assertTrue(log.isEmpty(), "nothing is logged for a phone that was never allowed to be read")
        assertNull(c.state.value.problem)

        c.sync()
        assertEquals(HumeProblem.NEEDS_PERMISSION, c.state.value.problem)
        assertEquals(1, log.size)
        assertEquals(SyncLog.SOURCE_HUME, log.single().source)
        assertEquals(false, log.single().ok)
    }

    @Test
    fun `allowing it later clears the problem and starts the background job, losing it stops the job`() {
        gateway.access = HealthAccess.NEEDS_PERMISSION
        val c = controller()
        assertEquals(false, background.last())
        c.sync()
        assertEquals(HumeProblem.NEEDS_PERMISSION, c.state.value.problem)

        gateway.access = HealthAccess.READY
        c.refreshAccess()
        assertEquals(true, background.last())
        assertTrue(c.state.value.ready)
        assertNull(c.state.value.problem)
        assertTrue(c.state.value.permissions.isNotEmpty(), "the dialog knows what to ask")

        gateway.access = HealthAccess.UNAVAILABLE
        c.refreshAccess()
        assertEquals(false, background.last())
        assertTrue(c.state.value.permissions.isEmpty())
    }

    @Test
    fun `without data from the Hume app the screen can offer the apps Health Connect does hold, and choosing one syncs again`() {
        gateway.byOrigin["com.example.otherband"] = HumeReadings(steps = mapOf(today() to 4_000))
        val c = controller()

        c.sync()
        assertEquals(HumeProblem.NO_DATA, c.state.value.problem)
        assertEquals(setOf("com.example.otherband"), c.state.value.found)

        c.chooseOrigin("com.example.otherband")
        assertEquals("com.example.otherband", settings.origin)
        assertNull(c.state.value.problem)
        assertEquals(setOf("com.example.otherband"), c.state.value.using)
        assertEquals(4_000, c.state.value.days.single().steps)

        c.chooseOrigin(null)
        assertNull(settings.origin)
        assertEquals(HumeProblem.NO_DATA, c.state.value.problem, "back to recognising the Hume app: there is none")
    }

    @Test
    fun `a sync that works is logged with what it added, remembered, and tells the screens to reload`() {
        gateway.byOrigin[hume] = HumeReadings(steps = mapOf(today() to 1_000, today().minusDays(1) to 2_000))
        var changed = 0
        val c = HumeController(
            settings, repo, gateway, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { ZoneOffset.UTC }, { clock },
            onDataChanged = { changed++ }, onSynced = { log += it }, onBackgroundWanted = { background += it },
        )
        c.sync()
        assertEquals(2, c.state.value.days.size)
        assertEquals(clock.toEpochMilli(), settings.lastSyncMs)
        assertEquals(1, changed)
        assertEquals(true, log.single().ok)
        assertEquals(2, log.single().added)

        c.sync() // nothing new
        assertEquals(1, changed, "the same numbers again change nothing, so nothing reloads")
    }

    @Test
    fun `a failing Health Connect is a problem to show, not a crash, and a revoked permission is told apart`() {
        gateway.byOrigin[hume] = HumeReadings(steps = mapOf(today() to 1_000))
        val c = controller()
        gateway.failWith = IOException("Health Connect refused")
        c.sync()
        assertEquals(HumeProblem.FAILED, c.state.value.problem)

        gateway.failWith = SecurityException("no permission")
        c.sync()
        assertEquals(HumeProblem.NEEDS_PERMISSION, c.state.value.problem)

        gateway.failWith = null
        c.sync()
        assertNull(c.state.value.problem)
    }

    @Test
    fun `the background job syncs only when Health Connect lets it read and the last sync is old enough`() = runBlocking {
        gateway.byOrigin[hume] = HumeReadings(steps = mapOf(today() to 1_000))
        val c = controller()
        gateway.access = HealthAccess.NEEDS_PERMISSION
        assertEquals(false, c.syncInBackground(Duration.ZERO))
        assertTrue(log.isEmpty())

        gateway.access = HealthAccess.READY
        assertEquals(true, c.syncInBackground(Duration.ofMinutes(10)))
        assertEquals(false, c.syncInBackground(Duration.ofMinutes(10)), "just synced")
    }
}
