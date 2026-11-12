package nl.tufein.openconnect.watch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.formats.FormatError
import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import nl.tufein.openconnect.core.watch.FileTree
import nl.tufein.openconnect.core.watch.FolderWatchSource
import nl.tufein.openconnect.core.watch.InMemoryWatchSource
import nl.tufein.openconnect.core.watch.SensorContact
import nl.tufein.openconnect.core.watch.WatchFileInfo
import nl.tufein.openconnect.core.watch.WatchSource
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchControllerTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun bytes(path: String): ByteArray =
        checkNotNull(WatchControllerTest::class.java.getResourceAsStream(path)) { "missing $path" }.readBytes()

    private val watchFiles = mapOf(
        "Monitor/46DB3F05.FIT" to bytes("/watch/monitoring.fit"),
        "Sleep/46DC0001.FIT" to bytes("/watch/sleep.fit"),
    )

    private var clock = Instant.parse("2026-06-12T09:00:00Z")
    private val repo = InMemoryRepository()
    private var changed = 0
    private var closed = 0
    private val ble = FakeBle()

    private fun controller(
        usb: FakeUsb,
        folder: FolderLink = FolderLink { throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN) },
        reconnectDelay: suspend (Int) -> Unit = {},
        maxReconnects: Int = 5,
    ) = WatchController(
        repo = repo,
        usb = usb,
        ble = ble,
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
        zone = { amsterdam },
        now = { clock },
        onDataChanged = { changed++ },
        folder = folder,
        reconnectDelay = reconnectDelay,
        maxReconnects = maxReconnects,
    )

    private val forerunner = WatchInfo("usb-1", "Forerunner 265")

    private fun opened(source: WatchSource = InMemoryWatchSource("Forerunner 265", watchFiles)) = OpenedWatch(source) { closed++ }

    // -------------------------------------------------------------------------------- USB sync

    @Test
    fun `attached watches are listed`() {
        val usb = FakeUsb(emptyList()) { opened() }
        val c = controller(usb)
        assertTrue(c.state.value.watches.isEmpty())
        usb.watches = listOf(forerunner)
        c.refreshUsb()
        assertEquals(listOf(forerunner), c.state.value.watches)
    }

    @Test
    fun `a successful sync stores the data, reports it and lets go of the watch`() {
        val c = controller(FakeUsb(listOf(forerunner)) { opened() })
        c.syncUsb(forerunner)

        val status = assertIs<SyncStatus.Done>(c.state.value.sync)
        assertEquals("Forerunner 265", status.device)
        assertEquals(2, status.report.filesRead)
        assertEquals(10_200, repo.listDaily(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10")).single().steps)
        assertEquals(1, repo.listSleep(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-11")).size)
        assertEquals(1, changed)
        assertEquals(1, closed)
    }

    @Test
    fun `progress is visible while files are being read`() {
        lateinit var c: WatchController
        val seen = ArrayList<SyncStatus>()
        val source = object : WatchSource {
            private val inner = InMemoryWatchSource("Forerunner 265", watchFiles)
            override val deviceName = inner.deviceName
            override fun listFiles() = inner.listFiles()
            override fun read(file: WatchFileInfo): ByteArray {
                seen.add(c.state.value.sync)
                return inner.read(file)
            }
        }
        c = controller(FakeUsb(listOf(forerunner)) { opened(source) })
        c.syncUsb(forerunner)

        assertEquals(2, seen.size)
        val running = seen.map { assertIs<SyncStatus.Running>(it) }
        assertEquals(listOf(0, 1), running.map { it.done })
        assertTrue(running.all { it.total == 2 && it.device == "Forerunner 265" })
    }

    @Test
    fun `no permission is reported as such and nothing is stored`() {
        val c = controller(FakeUsb(listOf(forerunner)) { throw WatchLinkException(WatchLinkException.Reason.NO_PERMISSION) })
        c.syncUsb(forerunner)
        assertEquals(SyncStatus.Failed(SyncFailure.NO_PERMISSION), c.state.value.sync)
        assertEquals(0, changed)
    }

    @Test
    fun `a watch that cannot be opened or has gone is reported`() {
        val gone = controller(FakeUsb(emptyList()) { throw WatchLinkException(WatchLinkException.Reason.NOT_FOUND) })
        gone.syncUsb(forerunner)
        assertEquals(SyncStatus.Failed(SyncFailure.NOT_FOUND), gone.state.value.sync)

        val stuck = controller(FakeUsb(listOf(forerunner)) { throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN) })
        stuck.syncUsb(forerunner)
        assertEquals(SyncStatus.Failed(SyncFailure.CANNOT_OPEN), stuck.state.value.sync)
    }

    @Test
    fun `a watch without a garmin folder is reported`() {
        val c = controller(FakeUsb(listOf(forerunner)) { opened(InMemoryWatchSource("Phone", emptyMap())) })
        c.syncUsb(forerunner)
        assertEquals(SyncStatus.Failed(SyncFailure.NO_FILES), c.state.value.sync)
        assertEquals(1, closed)
    }

    @Test
    fun `the cable being pulled halfway is reported and the watch is released`() {
        val source = object : WatchSource {
            override val deviceName = "Forerunner 265"
            override fun listFiles() = listOf(WatchFileInfo(nl.tufein.openconnect.core.watch.WatchFolder.MONITOR, "A.FIT", 10))
            override fun read(file: WatchFileInfo): ByteArray = throw IOException("USB transfer failed")
        }
        val c = controller(FakeUsb(listOf(forerunner)) { opened(source) })
        c.syncUsb(forerunner)
        val failed = assertIs<SyncStatus.Failed>(c.state.value.sync)
        assertEquals(SyncFailure.OTHER, failed.reason)
        assertTrue("USB transfer failed" in failed.detail.orEmpty(), failed.detail)
        assertEquals(1, closed)
        assertEquals(0, changed)
    }

    @Test
    fun `files that are not valid are listed as problems but the sync still counts as done`() {
        val broken = bytes("/watch/monitoring.fit").also { it[40] = (it[40].toInt() xor 0x55).toByte() }
        val c = controller(FakeUsb(listOf(forerunner)) { opened(InMemoryWatchSource("Forerunner 265", watchFiles + ("Monitor/BAD.FIT" to broken))) })
        c.syncUsb(forerunner)
        val done = assertIs<SyncStatus.Done>(c.state.value.sync)
        assertEquals(1, done.report.errors.size)
        assertEquals(1, changed)
    }

    // ------------------------------------------------------------------------------ live heart rate

    private fun hr(bpm: Int, rr: List<Double> = emptyList()) = HeartRateMeasurement(bpm, SensorContact.DETECTED, rr, null)

    @Test
    fun `scanning lists each sensor once`() {
        val c = controller(FakeUsb { opened() })
        c.startScan()
        assertEquals(LivePhase.SCANNING, c.state.value.live.phase)
        ble.onFound!!(BleSensor("AA:01", "Forerunner 265 1234"))
        ble.onFound!!(BleSensor("AA:02", "HRM-Dual"))
        ble.onFound!!(BleSensor("AA:01", "Forerunner 265 1234"))
        assertEquals(listOf("AA:01", "AA:02"), c.state.value.live.sensors.map { it.address })
        c.stopScan()
        assertFalse(ble.scanning)
        assertEquals(LivePhase.IDLE, c.state.value.live.phase)
    }

    @Test
    fun `connecting streams heart rate with running statistics`() {
        val c = controller(FakeUsb { opened() })
        val sensor = BleSensor("AA:01", "Forerunner 265 1234")
        c.startScan()
        c.connect(sensor)
        assertFalse(ble.scanning, "connecting ends the scan")
        assertEquals(LivePhase.CONNECTING, c.state.value.live.phase)

        ble.listener!!.onConnected()
        assertEquals(LivePhase.STREAMING, c.state.value.live.phase)
        assertEquals("Forerunner 265 1234", c.state.value.live.sensorName)

        for ((i, bpm) in listOf(60, 70, 80, 0, 90).withIndex()) {
            clock = Instant.parse("2026-06-12T09:00:00Z").plusSeconds(i.toLong())
            ble.listener!!.onMeasurement(hr(bpm, if (i == 0) listOf(1000.0, 1010.0, 990.0, 1000.0) else emptyList()))
        }
        val live = c.state.value.live
        assertEquals(90, live.bpm)
        assertEquals(60, live.min)
        assertEquals(75, live.avg)
        assertEquals(90, live.max)
        assertEquals(14, live.rmssdMs)
        assertEquals(4, live.seconds)
        assertTrue(live.hasData)
    }

    @Test
    fun `losing the sensor keeps what was recorded and says why`() {
        val c = controller(FakeUsb { opened() }, maxReconnects = 0) // no retries: the first loss is final
        c.connect(BleSensor("AA:01", "Strap"))
        ble.listener!!.onConnected()
        ble.listener!!.onMeasurement(hr(100))
        ble.listener!!.onDisconnected("Connection lost")

        val live = c.state.value.live
        assertEquals(LivePhase.STOPPED, live.phase)
        assertEquals("Connection lost", live.error)
        assertEquals(100, live.bpm)
        assertTrue(live.hasData)
    }

    @Test
    fun `a recording can be saved once as an activity`() {
        val c = controller(FakeUsb { opened() })
        c.connect(BleSensor("AA:01", "Strap"))
        ble.listener!!.onConnected()
        for ((i, bpm) in listOf(100, 110, 120).withIndex()) {
            clock = Instant.parse("2026-06-12T09:00:00Z").plusSeconds(i.toLong())
            ble.listener!!.onMeasurement(hr(bpm))
        }
        c.stopLive()
        assertEquals(LivePhase.STOPPED, c.state.value.live.phase)
        assertNull(ble.connected, "the connection is closed")

        assertTrue(c.saveLive("Evening ride", Sport.CYCLING))
        val saved = repo.listActivities().single()
        assertEquals("Evening ride", saved.name)
        assertEquals(Sport.CYCLING, saved.sport)
        assertEquals(110, saved.avgHr)
        assertEquals("ble", saved.source)
        assertTrue(c.state.value.live.saved)
        assertEquals(1, changed)

        assertFalse(c.saveLive("again", Sport.CYCLING), "saving twice would duplicate it")
        assertEquals(1, repo.listActivities().size)
    }

    @Test
    fun `nothing to save when nothing was recorded, and a new recording starts clean`() {
        val c = controller(FakeUsb { opened() })
        assertFalse(c.saveLive("x", Sport.OTHER))

        c.connect(BleSensor("AA:01", "Strap"))
        ble.listener!!.onConnected()
        ble.listener!!.onMeasurement(hr(100))
        c.stopLive()
        c.connect(BleSensor("AA:02", "Other strap"))
        val live = c.state.value.live
        assertEquals(LivePhase.CONNECTING, live.phase)
        assertNull(live.bpm)
        assertFalse(live.hasData)
        assertFalse(live.saved)
    }

    @Test
    fun `a format error from a watch does not escape`() {
        val source = object : WatchSource {
            override val deviceName = "Forerunner 265"
            override fun listFiles() = throw FormatError("odd")
            override fun read(file: WatchFileInfo): ByteArray = ByteArray(0)
        }
        val c = controller(FakeUsb(listOf(forerunner)) { opened(source) })
        c.syncUsb(forerunner)
        assertEquals(SyncStatus.Failed(SyncFailure.OTHER, "odd"), c.state.value.sync)
    }

    // ------------------------------------------------------------------------------------- folders

    /** A folder somewhere on the phone holding [files], keyed by path from the folder that was picked. */
    private class MemoryTree(files: Map<String, ByteArray>) : FileTree {
        private class Node(val name: String, val dir: Boolean, val data: ByteArray?, val handle: Long)

        private val children = HashMap<Long?, MutableList<Node>>()
        private val data = HashMap<Long, ByteArray>()
        private val dirs = HashMap<String, Long>()
        private var next = 0L

        init {
            for ((path, bytes) in files) {
                val parts = path.split('/')
                var parent: Long? = null
                var prefix = ""
                for (dir in parts.dropLast(1)) {
                    prefix += "$dir/"
                    parent = dirs.getOrPut(prefix) { Node(dir, true, null, next++).also { children.getOrPut(parent) { ArrayList() }.add(it) }.handle }
                }
                val file = Node(parts.last(), false, bytes, next++)
                data[file.handle] = bytes
                children.getOrPut(parent) { ArrayList() }.add(file)
            }
        }

        override fun children(parent: Long?) = children[parent].orEmpty().map { FileTree.Entry(it.name, it.dir, it.data?.size?.toLong() ?: 0, it.handle) }

        override fun read(handle: Long, size: Long): ByteArray = data.getValue(handle)
    }

    private fun folderOf(files: Map<String, ByteArray>) = FolderLink { OpenedWatch(FolderWatchSource(MemoryTree(files), "GARMIN"), onClose = { closed++ }) }

    @Test
    fun `a picked folder is read like a watch and its data is stored`() {
        val c = controller(FakeUsb { opened() }, folder = folderOf(watchFiles.mapKeys { "GARMIN/" + it.key }))
        c.syncFolder("content://tree/garmin")

        val done = assertIs<SyncStatus.Done>(c.state.value.sync)
        assertEquals("GARMIN", done.device)
        assertEquals(2, done.report.filesRead)
        assertEquals(10_200, repo.listDaily(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10")).single().steps)
        assertEquals(1, repo.listSleep(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-11")).size)
        assertEquals(1, changed)
        assertEquals(1, closed)
    }

    @Test
    fun `a folder reading the same files again skips them`() {
        val c = controller(FakeUsb { opened() }, folder = folderOf(watchFiles.mapKeys { "GARMIN/" + it.key }))
        c.syncFolder("content://tree/garmin")
        c.syncFolder("content://tree/garmin")
        val again = assertIs<SyncStatus.Done>(c.state.value.sync)
        assertEquals(0, again.report.filesRead)
        assertEquals(2, again.report.filesSkipped)
    }

    @Test
    fun `a folder without fit files is reported in terms of a folder`() {
        val c = controller(FakeUsb { opened() }, folder = folderOf(mapOf("Pictures/holiday.jpg" to ByteArray(10), "notes.txt" to ByteArray(3))))
        c.syncFolder("content://tree/other")
        assertEquals(SyncStatus.Failed(SyncFailure.NO_FILES_IN_FOLDER), c.state.value.sync)
        assertEquals(1, closed)
        assertEquals(0, changed)
    }

    @Test
    fun `a folder that cannot be opened is reported and the usb list is left alone`() {
        val usb = FakeUsb(listOf(forerunner)) { opened() }
        val c = controller(usb, folder = FolderLink { throw WatchLinkException(WatchLinkException.Reason.NOT_FOUND) })
        c.syncFolder("content://tree/gone")
        assertEquals(SyncStatus.Failed(SyncFailure.NOT_FOUND), c.state.value.sync)
        assertTrue(c.state.value.watches.isEmpty(), "reading a folder does not look for watches on the cable")
    }

    @Test
    fun `a folder that stops answering is reported as a failure`() {
        val tree = object : FileTree {
            override fun children(parent: Long?) = listOf(FileTree.Entry("A.FIT", false, 10, 1))
            override fun read(handle: Long, size: Long): ByteArray = throw IOException("The folder went away")
        }
        val c = controller(FakeUsb { opened() }, folder = FolderLink { OpenedWatch(FolderWatchSource(tree, "GARMIN"), onClose = { closed++ }) })
        c.syncFolder("content://tree/x")
        // Nothing could be read at all, so it is a failure and not a "has been read" with a footnote.
        val failed = assertIs<SyncStatus.Failed>(c.state.value.sync)
        assertTrue("The folder went away" in failed.detail.orEmpty(), failed.detail)
        assertEquals(1, closed)
    }

    // ------------------------------------------------------------------------------- reconnecting

    private fun streaming(c: WatchController, vararg bpm: Int) {
        c.connect(BleSensor("AA:01", "Forerunner 265 1234"))
        ble.listener!!.onConnected()
        for ((i, b) in bpm.withIndex()) {
            clock = Instant.parse("2026-06-12T09:00:00Z").plusSeconds(i.toLong())
            ble.listener!!.onMeasurement(hr(b))
        }
    }

    @Test
    fun `a sensor that drops out is reconnected and the recording goes on`() {
        val c = controller(FakeUsb { opened() })
        streaming(c, 100, 110)
        ble.listener!!.onDisconnected("Connection lost")

        assertEquals(2, ble.connectCalls, "the phone tried again by itself")
        var live = c.state.value.live
        assertEquals(LivePhase.RECONNECTING, live.phase)
        assertEquals(1, live.reconnectAttempt)
        assertNull(live.error)
        assertEquals(110, live.bpm, "what was recorded stays on screen")

        ble.listener!!.onConnected()
        clock = Instant.parse("2026-06-12T09:00:05Z")
        ble.listener!!.onMeasurement(hr(130))
        live = c.state.value.live
        assertEquals(LivePhase.STREAMING, live.phase)
        assertEquals(0, live.reconnectAttempt)
        assertEquals(130, live.max)
        assertEquals(100, live.min)
        assertEquals(113, live.avg) // 100, 110 and 130 are one recording

        c.stopLive()
        assertTrue(c.saveLive("Ride", Sport.CYCLING))
        assertEquals(113, repo.listActivities().single().avgHr, "one activity holds the recording from before and after the gap")
    }

    @Test
    fun `after a few failed attempts the recording stops with the reason and can still be saved`() {
        val c = controller(FakeUsb { opened() })
        streaming(c, 100, 110, 120)
        repeat(6) { ble.listener!!.onDisconnected("Connection lost") } // the first drop and five failed retries

        assertEquals(6, ble.connectCalls, "one connection and five retries")
        val live = c.state.value.live
        assertEquals(LivePhase.STOPPED, live.phase)
        assertEquals("Connection lost", live.error)
        assertTrue(live.hasData)
        assertTrue(c.saveLive("Ride", Sport.CYCLING))
        assertEquals(110, repo.listActivities().single().avgHr)
    }

    @Test
    fun `a connection that worked starts the count of failed attempts over`() {
        val c = controller(FakeUsb { opened() })
        streaming(c, 100)
        repeat(4) { ble.listener!!.onDisconnected("lost") } // four failed retries in a row
        ble.listener!!.onConnected() // the fifth works
        ble.listener!!.onDisconnected("lost again")
        assertEquals(1, c.state.value.live.reconnectAttempt, "the next streak begins at one")
        assertEquals(LivePhase.RECONNECTING, c.state.value.live.phase)
    }

    @Test
    fun `closing the connection on purpose does not reconnect`() {
        val c = controller(FakeUsb { opened() })
        streaming(c, 100)
        ble.listener!!.onDisconnected(null)
        assertEquals(1, ble.connectCalls)
        assertEquals(LivePhase.STOPPED, c.state.value.live.phase)
    }

    @Test
    fun `stopping while the phone waits to retry cancels the retry`() {
        val gate = CompletableDeferred<Unit>()
        val c = controller(FakeUsb { opened() }, reconnectDelay = { gate.await() })
        streaming(c, 100)
        ble.listener!!.onDisconnected("lost")
        assertEquals(LivePhase.RECONNECTING, c.state.value.live.phase)
        assertEquals(1, ble.connectCalls, "still waiting")

        c.stopLive()
        assertEquals(LivePhase.STOPPED, c.state.value.live.phase)
        gate.complete(Unit)
        assertEquals(1, ble.connectCalls, "the retry that was waiting never happens")
        assertEquals(LivePhase.STOPPED, c.state.value.live.phase)
    }

    @Test
    fun `choosing another sensor while retrying drops the old retry`() {
        val gate = CompletableDeferred<Unit>()
        val c = controller(FakeUsb { opened() }, reconnectDelay = { gate.await() })
        streaming(c, 100)
        ble.listener!!.onDisconnected("lost")
        c.connect(BleSensor("AA:02", "Chest strap"))
        assertEquals(2, ble.connectCalls)
        gate.complete(Unit)
        assertEquals(2, ble.connectCalls, "only the new connection exists")
        assertEquals("Chest strap", ble.connected?.name)
        assertEquals(LivePhase.CONNECTING, c.state.value.live.phase)
    }

    @Test
    fun `late answers of an old connection are ignored`() {
        val c = controller(FakeUsb { opened() })
        streaming(c, 100)
        val old = ble.listener!!
        old.onDisconnected("lost")
        ble.listener!!.onConnected()

        old.onMeasurement(hr(200))
        old.onDisconnected("late echo")
        val live = c.state.value.live
        assertEquals(100, live.max, "the old connection's reading is not part of the recording")
        assertEquals(LivePhase.STREAMING, live.phase)
        assertEquals(2, ble.connectCalls)
    }
}
