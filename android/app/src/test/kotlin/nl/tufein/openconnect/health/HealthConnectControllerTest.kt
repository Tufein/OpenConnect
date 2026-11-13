package nl.tufein.openconnect.health

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.health.HealthConnectGateway
import nl.tufein.openconnect.core.health.HealthSession
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HealthConnectControllerTest {
    private class Gateway(var access: HealthAccess = HealthAccess.READY, var failWrite: Boolean = false) : HealthConnectGateway {
        val written = ArrayList<HealthSession>()
        var stepsByDay: Map<LocalDate, Int> = emptyMap()
        override suspend fun access() = access
        override suspend fun write(sessions: List<HealthSession>): Int {
            if (failWrite) throw IOException("Health Connect is busy")
            written += sessions
            return sessions.size
        }
        override suspend fun steps(from: LocalDate, to: LocalDate) = stepsByDay
        override suspend fun sleep(from: LocalDate, to: LocalDate) = emptyMap<LocalDate, Int>()
    }

    private class Settings(override var auto: Boolean = false) : HealthSettings

    private val repo = InMemoryRepository().also {
        it.upsertActivity(Activity(id = "a1", startTime = Instant.parse("2026-11-12T08:00:00Z"), durationS = 600.0))
    }
    private var changed = 0

    private fun controller(g: Gateway, s: Settings = Settings()) = HealthConnectController(
        repo, g, s, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { ZoneOffset.UTC }, onDataChanged = { changed++ },
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
}
