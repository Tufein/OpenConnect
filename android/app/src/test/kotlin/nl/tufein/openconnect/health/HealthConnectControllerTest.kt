package nl.tufein.openconnect.health

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.health.HealthConnectGateway
import nl.tufein.openconnect.core.health.HealthSession
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthConnectControllerTest {
    private class Gateway(var access: HealthAccess = HealthAccess.READY, var failWrite: Boolean = false) : HealthConnectGateway {
        val written = ArrayList<HealthSession>()
        var stepsByDay: Map<LocalDate, Int> = emptyMap()
        var weightByDay: Map<LocalDate, Int> = emptyMap()

        /** How often the phone was asked for steps; a background fill asks once. */
        var reads = 0

        override suspend fun access() = access
        override suspend fun write(sessions: List<HealthSession>): Int {
            if (failWrite) throw IOException("Health Connect is busy")
            written += sessions
            return sessions.size
        }
        override suspend fun steps(from: LocalDate, to: LocalDate): Map<LocalDate, Int> {
            reads++
            return stepsByDay
        }
        override suspend fun sleep(from: LocalDate, to: LocalDate) = emptyMap<LocalDate, Int>()
        override suspend fun weight(from: LocalDate, to: LocalDate) = weightByDay
    }

    private class Settings(override var auto: Boolean = false, override var autoImport: Boolean = false) : HealthSettings

    private val repo = InMemoryRepository().also {
        it.upsertActivity(Activity(id = "a1", startTime = Instant.parse("2026-11-12T08:00:00Z"), durationS = 600.0))
    }
    private var changed = 0

    private fun controller(
        g: Gateway,
        s: Settings = Settings(),
        onSynced: (SyncLogEntry) -> Unit = {},
        onBackgroundWanted: (Boolean) -> Unit = {},
    ) = HealthConnectController(
        repo, g, s, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { ZoneOffset.UTC },
        onDataChanged = { changed++ }, onSynced = onSynced, onBackgroundWanted = onBackgroundWanted,
    )

    @Test
    fun `refresh shows what the phone allows`() {
        val c = controller(Gateway(HealthAccess.NEEDS_PERMISSION))
        c.refresh()
        assertEquals(HealthAccess.NEEDS_PERMISSION, c.state.value.access)
    }

    @Test
    fun `sending reports how many went out`() {
        val g = Gateway()
        val c = controller(g)
        c.exportNow()
        assertEquals(HealthMessage.Exported(1, 0), c.state.value.message)
        assertEquals(1, g.written.size)
        assertEquals(false, c.state.value.busy)
    }

    @Test
    fun `nothing is attempted without permission, and a refusal from Health Connect becomes a message`() {
        val denied = Gateway(HealthAccess.NEEDS_PERMISSION)
        controller(denied).also { it.exportNow() }.state.value.message.let { assertIs<HealthMessage.Failed>(it) }
        assertTrue(denied.written.isEmpty())

        val c = controller(Gateway(failWrite = true))
        c.exportNow()
        assertEquals(HealthMessage.Failed("Health Connect is busy"), c.state.value.message)
    }

    @Test
    fun `reading fills gaps and tells the screens only when something changed`() {
        val g = Gateway()
        g.stepsByDay = mapOf(LocalDate.now(ZoneOffset.UTC) to 4000)
        val c = controller(g)
        c.importNow()
        assertEquals(HealthMessage.Imported(1, 0), c.state.value.message)
        assertEquals(1, changed)
        c.importNow()
        assertEquals(HealthMessage.Imported(0, 0), c.state.value.message)
        assertEquals(1, changed)
    }

    @Test
    fun `the automatic send leaves out what Health Connect already has, the button sends everything`() {
        val g = Gateway()
        val c = controller(g, Settings(auto = true))
        c.autoExport()
        assertEquals(1, g.written.size)
        c.autoExport()
        assertEquals(1, g.written.size, "nothing new since the last send")

        repo.upsertActivity(Activity(id = "a2", startTime = Instant.parse("2026-11-12T09:00:00Z"), durationS = 600.0))
        c.autoExport()
        assertEquals(listOf("openconnect-a1", "openconnect-a2"), g.written.map { it.clientId }, "only the new one went out")

        c.exportNow()
        assertEquals(4, g.written.size, "the button sends all of them again")
    }

    @Test
    fun `the automatic send only happens when the switch is on and Health Connect is ready`() {
        val off = Gateway()
        controller(off).autoExport()
        assertTrue(off.written.isEmpty())

        val on = Gateway()
        val c = controller(on, Settings(auto = true))
        c.autoExport()
        assertEquals(1, on.written.size)
        on.access = HealthAccess.NEEDS_PERMISSION
        c.autoExport()
        assertEquals(1, on.written.size)
        c.setAuto(false)
        assertEquals(false, c.state.value.auto)
    }

    @Test
    fun `weight from the phone fills the Body card where the app has none`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val g = Gateway()
        g.weightByDay = mapOf(today to 82_400)
        val c = controller(g)
        c.importNow()
        assertEquals(HealthMessage.Imported(0, 0, 1), c.state.value.message)
        assertEquals(82_400, repo.listBody(today, today).single().grams)
        assertEquals(1, changed)
    }

    @Test
    fun `the background fill stays quiet while the switch is off`() = runBlocking<Unit> {
        val g = Gateway()
        g.stepsByDay = mapOf(LocalDate.now(ZoneOffset.UTC) to 4000)
        val logged = ArrayList<SyncLogEntry>()
        var wanted: Boolean? = null
        val c = controller(g, Settings(autoImport = false), onSynced = { logged += it }, onBackgroundWanted = { wanted = it })

        assertEquals(false, wanted, "the app is told not to schedule the job")
        assertNull(c.importInBackground())
        assertTrue(logged.isEmpty())
        assertEquals(0, g.reads)
        assertEquals(0, changed)
    }

    @Test
    fun `the background fill brings in what is missing, reloads the screens and lands in the sync log`() = runBlocking<Unit> {
        val g = Gateway()
        g.stepsByDay = mapOf(LocalDate.now(ZoneOffset.UTC) to 4000)
        val logged = ArrayList<SyncLogEntry>()
        var wanted: Boolean? = null
        val c = controller(g, Settings(autoImport = true), onSynced = { logged += it }, onBackgroundWanted = { wanted = it })

        assertEquals(true, wanted)
        assertEquals(1, c.importInBackground()?.daysFilled)
        assertEquals(1, changed)
        assertEquals(1, logged.size)
        assertEquals(SyncLog.SOURCE_HEALTH, logged.single().source)
        assertEquals(true, logged.single().ok)
        assertEquals(1, logged.single().added)
        assertEquals(null, c.state.value.message, "the card shows nothing for a background run")

        c.importInBackground()
        assertEquals(1, logged.size, "nothing new, so nothing is logged")
        assertEquals(1, changed)
    }

    @Test
    fun `a background fill without permission does nothing and says nothing`() = runBlocking<Unit> {
        val g = Gateway(access = HealthAccess.NEEDS_PERMISSION)
        val logged = ArrayList<SyncLogEntry>()
        val c = controller(g, Settings(autoImport = true), onSynced = { logged += it })
        assertNull(c.importInBackground())
        assertTrue(logged.isEmpty())
        assertEquals(0, changed)
    }

    @Test
    fun `opening the app fills at most once per gap`() = runBlocking<Unit> {
        val g = Gateway()
        g.stepsByDay = mapOf(LocalDate.now(ZoneOffset.UTC) to 4000)
        val c = controller(g, Settings(autoImport = true))

        c.autoImportIfDue(Duration.ofMinutes(1))
        assertEquals(1, g.reads, "the first opening fills")

        c.autoImportIfDue(Duration.ofMinutes(1))
        assertEquals(1, g.reads, "a second opening within the gap waits")

        c.autoImportIfDue(Duration.ZERO)
        assertEquals(2, g.reads, "once the gap has passed it may try again")
    }
}
