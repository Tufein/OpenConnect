// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.watch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.watch.InMemoryWatchSource
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarminBleControllerTest {
    private fun bytes(path: String): ByteArray =
        checkNotNull(GarminBleControllerTest::class.java.getResourceAsStream(path)) { "missing $path" }.readBytes()

    private val fenix = GarminBleWatch("AA:BB:CC:DD:EE:FF", "fenix 8 - 47mm", paired = false)
    private val repo = InMemoryRepository()
    private var closed = 0
    private var changed = 0
    private val steps = ArrayList<GarminStep?>()

    private fun watchSource() = InMemoryWatchSource(
        "fenix 8",
        mapOf("Monitor/MONITOR_1.fit" to bytes("/watch/monitoring.fit"), "Sleep/SLEEP_2.fit" to bytes("/watch/sleep.fit")),
    )

    private val bleWanted = ArrayList<Boolean>()
    private val options = InMemoryGarminBleOptions()

    private fun controller(ble: GarminBleLink, memory: GarminWatchMemory = InMemoryGarminWatchMemory()) = WatchController(
        repo = repo,
        usb = FakeUsb { throw AssertionError("no USB here") },
        ble = FakeBle(),
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
        zone = { ZoneId.of("Europe/Amsterdam") },
        onDataChanged = { changed++ },
        garminBle = ble,
        garminMemory = memory,
        garminOptions = options,
        onBleBackgroundWanted = { bleWanted += it },
    )

    @Test
    fun `finding lists the watches once each and stops on request`() {
        val ble = FakeGarminBle(nearby = listOf(fenix, fenix.copy(), fenix.copy(address = "11:22:33:44:55:66", name = "Forerunner 965"))) { _, _ -> error("not opened") }
        val c = controller(ble)
        c.findGarmin()
        assertTrue(c.state.value.garmin.searching)
        assertEquals(listOf("fenix 8 - 47mm", "Forerunner 965"), c.state.value.garmin.found.map { it.name })
        c.stopFindGarmin()
        assertFalse(ble.searching)
        assertFalse(c.state.value.garmin.searching)
    }

    @Test
    fun `a bluetooth sync goes through pairing, stores the data and remembers the watch`() {
        val memory = InMemoryGarminWatchMemory()
        lateinit var c: WatchController
        val ble = FakeGarminBle { _, onStep ->
            onStep(GarminStep.PAIRING)
            steps += c.state.value.garmin.step
            onStep(GarminStep.CONNECTING)
            onStep(GarminStep.HANDSHAKE)
            steps += c.state.value.garmin.step
            OpenedWatch(watchSource()) { closed++ }
        }
        c = controller(ble, memory)
        c.findGarmin()
        c.syncGarmin(fenix)

        assertFalse(c.state.value.garmin.searching, "syncing stops the search")
        assertEquals(listOf<GarminStep?>(GarminStep.PAIRING, GarminStep.HANDSHAKE), steps.toList())
        val done = assertIs<SyncStatus.Done>(c.state.value.sync)
        assertEquals("fenix 8", done.device)
        assertEquals(2, done.report.filesRead)
        assertEquals(1, done.report.nightsUpdated)
        assertEquals(SyncSource.GARMIN_BLE, c.state.value.syncSource)
        assertNull(c.state.value.garmin.step)
        assertEquals(1, closed, "the connection is let go")
        assertEquals(1, changed)
        assertEquals(fenix.copy(paired = true), memory.load())
        assertEquals(fenix.copy(paired = true), c.state.value.garmin.remembered)

        // the next app start offers the same watch at once
        assertEquals(fenix.copy(paired = true), controller(ble, memory).state.value.garmin.remembered)
    }

    @Test
    fun `pairing that fails says so and remembers nothing`() {
        val ble = FakeGarminBle { _, _ -> throw WatchLinkException(WatchLinkException.Reason.NOT_PAIRED, "Pairing did not finish") }
        val c = controller(ble)
        c.syncGarmin(fenix)
        val failed = assertIs<SyncStatus.Failed>(c.state.value.sync)
        assertEquals(SyncFailure.NOT_PAIRED, failed.reason)
        assertNull(c.state.value.garmin.remembered)
    }

    @Test
    fun `an unexpected error while connecting ends as a message, not a crash`() {
        val ble = FakeGarminBle { _, _ -> throw IllegalStateException("GATT 133") }
        val c = controller(ble)
        c.syncGarmin(fenix)
        val failed = assertIs<SyncStatus.Failed>(c.state.value.sync)
        assertEquals(SyncFailure.CANNOT_OPEN, failed.reason)
        assertEquals("GATT 133", failed.detail)
    }

    @Test
    fun `a watch that lists nothing readable is reported as such`() {
        val ble = FakeGarminBle { _, _ -> OpenedWatch(InMemoryWatchSource("fenix 8", emptyMap())) { closed++ } }
        val c = controller(ble)
        c.syncGarmin(fenix)
        assertEquals(SyncFailure.NO_FILES, assertIs<SyncStatus.Failed>(c.state.value.sync).reason)
        assertEquals(1, closed)
    }

    @Test
    fun `bluetooth off while looking is shown, and forgetting the watch clears it`() {
        val memory = InMemoryGarminWatchMemory(fenix)
        val ble = FakeGarminBle(findError = "Bluetooth is off or not available") { _, _ -> error("not opened") }
        val c = controller(ble, memory)
        assertEquals(fenix, c.state.value.garmin.remembered)
        c.findGarmin()
        assertEquals("Bluetooth is off or not available", c.state.value.garmin.error)
        assertFalse(c.state.value.garmin.searching)
        c.forgetGarmin()
        assertNull(c.state.value.garmin.remembered)
        assertNull(memory.load())
    }

    // ------------------------------------------------------------------------------ the two switches

    @Test
    fun `the switches start off, are remembered, and the job is told when the background one changes`() {
        val c = controller(FakeGarminBle { _, _ -> error("not opened") })
        assertEquals(listOf(false), bleWanted.toList(), "told at the start so a switch left on keeps its job")
        assertFalse(c.state.value.garmin.archiveAfterRead)
        c.setGarminArchive(true)
        c.setGarminBackground(true)
        assertTrue(options.archiveAfterRead && options.background)
        assertTrue(c.state.value.garmin.archiveAfterRead && c.state.value.garmin.background)
        assertEquals(listOf(false, true), bleWanted.toList())
        c.setGarminBackground(false)
        assertEquals(false, bleWanted.last())
    }

    // --------------------------------------------------------------------------- background attempts

    private fun background(c: WatchController) = kotlinx.coroutines.runBlocking { c.syncGarminInBackground() }

    @Test
    fun `the background sync does nothing unless it is switched on and a watch is remembered`() {
        val ble = FakeGarminBle { _, _ -> throw AssertionError("must not connect") }
        assertEquals(BleBackgroundOutcome.SKIPPED, background(controller(ble, InMemoryGarminWatchMemory(fenix))), "switch off")
        options.background = true
        assertEquals(BleBackgroundOutcome.SKIPPED, background(controller(ble)), "no watch remembered")
    }

    @Test
    fun `the background sync syncs the remembered watch, logs it, and never searches`() {
        options.background = true
        val log = ArrayList<nl.tufein.openconnect.core.SyncLogEntry>()
        var opened: GarminBleWatch? = null
        val ble = FakeGarminBle { w, _ -> opened = w; OpenedWatch(watchSource()) { closed++ } }
        val c = WatchController(
            repo = repo, usb = FakeUsb { throw AssertionError() }, ble = FakeBle(),
            scope = CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined, zone = { ZoneId.of("Europe/Amsterdam") },
            garminBle = ble, garminMemory = InMemoryGarminWatchMemory(fenix.copy(paired = true)), garminOptions = options,
            onSynced = { log += it },
        )
        assertEquals(BleBackgroundOutcome.SYNCED, background(c))
        assertEquals(fenix.address, opened?.address)
        assertFalse(ble.searching)
        assertEquals(1, closed)
        assertEquals(nl.tufein.openconnect.core.SyncLog.SOURCE_BLE, log.single().source)
        assertTrue(log.single().ok)
    }

    @Test
    fun `a watch that is out of range is retried later, other failures are not`() {
        options.background = true
        val away = FakeGarminBle { _, _ -> throw WatchLinkException(WatchLinkException.Reason.NOT_FOUND, "not in range") }
        assertEquals(BleBackgroundOutcome.RETRY, background(controller(away, InMemoryGarminWatchMemory(fenix))))
        val refused = FakeGarminBle { _, _ -> throw WatchLinkException(WatchLinkException.Reason.NO_PERMISSION, "no permission") }
        assertEquals(BleBackgroundOutcome.FAILED, background(controller(refused, InMemoryGarminWatchMemory(fenix))))
    }
}
