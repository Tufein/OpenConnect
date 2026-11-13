// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GarminBleSessionTest {
    private val phone = PhoneIdentity("Stefan's phone", "Google", "Pixel 9")
    private val scheduler = ExecutorScheduler()
    private val watches = ArrayList<FakeGarminWatch>()
    private val log = java.util.concurrent.CopyOnWriteArrayList<String>()

    @AfterTest
    fun stop() {
        watches.forEach { it.shutdown() }
        scheduler.shutdown()
    }

    private fun connect(
        watch: FakeGarminWatch,
        useMlr: Boolean = true,
        firstConnect: Boolean = false,
    ): GarminBleSession {
        watches += watch
        val session = GarminBleSession(
            watch.pipe, phone, scheduler, maxWrite = 20, useMlr = useMlr, firstConnect = firstConnect,
            now = { Instant.parse("2026-11-13T09:00:00Z") }, zone = { ZoneId.of("Europe/Brussels") }, log = { log += it },
        )
        watch.phone = session
        session.start()
        return session
    }

    private fun bytes(size: Int, seed: Int) = Random(seed).nextBytes(size)

    @Test
    fun `the handshake over a reliable link ends ready, with the watch's name`() {
        val watch = FakeGarminWatch(files = emptyMap())
        val session = connect(watch)
        val info = session.awaitReady(5_000)
        assertEquals("fenix 8", info.deviceName)
        assertEquals("21.19", info.softwareVersionText)
        assertTrue(session.ready)
        watch.drain()

        // the phone closed old handles first, then asked for a reliable GFDI handle
        assertEquals(listOf(5, 0), watch.managementFromPhone.map { it[1].toInt() })
        assertEquals(2, watch.managementFromPhone[1][12].toInt())
        val types = watch.fromPhone.map { it.type }
        // ACK + our introduction, ACK + our capabilities, then "what files do you have" and "ready to sync"
        assertEquals(
            listOf(GfdiType.RESPONSE, GfdiType.RESPONSE, GfdiType.RESPONSE, GfdiType.CONFIGURATION, GfdiType.SUPPORTED_FILE_TYPES_REQUEST, GfdiType.SYSTEM_EVENT),
            types.take(6),
        )
        val intro = watch.fromPhone[1].reader()
        assertEquals(GfdiType.DEVICE_INFORMATION, intro.short())
        assertEquals(GfdiStatus.ACK, intro.byte())
        assertEquals(150, intro.short())
        intro.short(); intro.int(); intro.short(); intro.short()
        assertEquals("Stefan's phone", intro.string())
        assertEquals("Google", intro.string())
        assertEquals("Pixel 9", intro.string())
        assertEquals(SystemEvent.SYNC_READY.ordinal, watch.fromPhone[5].payload[0].toInt())
        session.close()
    }

    @Test
    fun `right after pairing the phone also says pairing and setup are done`() {
        val watch = FakeGarminWatch(files = emptyMap())
        val session = connect(watch, firstConnect = true)
        session.awaitReady(5_000)
        watch.drain()
        val events = watch.fromPhone.filter { it.type == GfdiType.SYSTEM_EVENT }.map { SystemEvent.entries[it.payload[0].toInt()] }
        assertEquals(listOf(SystemEvent.SYNC_READY, SystemEvent.PAIR_COMPLETE, SystemEvent.SYNC_COMPLETE, SystemEvent.SETUP_WIZARD_COMPLETE), events)
        session.close()
    }

    @Test
    fun `a watch that refuses a reliable link gets a plain one`() {
        val watch = FakeGarminWatch(files = mapOf(3 to (GarminFileType.MONITOR to bytes(700, 3))), refuseReliable = true)
        val session = connect(watch)
        session.awaitReady(5_000)
        assertEquals(listOf(2, 0), watch.managementFromPhone.drop(1).map { it[12].toInt() })
        assertContentEquals(watch.files.getValue(3).second, session.download(3))
        session.close()
    }

    @Test
    fun `the directory and files come over in chunks, checked and acknowledged`() {
        val files = mapOf(
            5 to (GarminFileType.MONITOR to bytes(5_000, 1)),
            6 to (GarminFileType.ACTIVITY to bytes(12_345, 2)),
            7 to (GarminFileType.SLEEP to bytes(1, 3)),
        )
        for (mlr in listOf(true, false)) {
            val watch = FakeGarminWatch(files, reliable = mlr, chunkSize = 377)
            val session = connect(watch, useMlr = mlr)
            session.awaitReady(5_000)
            val dir = session.listDirectory()
            assertEquals(listOf(5, 6, 7), dir.map { it.index })
            assertEquals(listOf(GarminFileType.MONITOR, GarminFileType.ACTIVITY, GarminFileType.SLEEP), dir.map { it.fileType })
            val progress = ArrayList<Int>()
            assertContentEquals(files.getValue(6).second, session.download(6) { got, _ -> progress += got })
            assertEquals(12_345, progress.last())
            assertTrue(progress.size >= 12_345 / 377)
            assertContentEquals(files.getValue(5).second, session.download(5))
            assertContentEquals(files.getValue(7).second, session.download(7))
            session.close()
        }
    }

    @Test
    fun `a damaged chunk fails that file and the next file still comes`() {
        val files = mapOf(1 to (GarminFileType.MONITOR to bytes(1_000, 4)), 2 to (GarminFileType.SLEEP to bytes(50, 5)))
        val watch = FakeGarminWatch(files, chunkSize = 100, damageEvery = 3)
        val session = connect(watch)
        session.awaitReady(5_000)
        val error = assertFailsWith<GarminBleException> { session.download(1) }
        assertEquals(GarminBleException.Reason.BROKEN, error.reason)
        // the phone told the watch to stop (transfer status 2: abort)
        watch.drain()
        val abort = watch.fromPhone.last { it.type == GfdiType.RESPONSE && it.reader().short() == GfdiType.FILE_TRANSFER_DATA }.reader()
        abort.short(); abort.byte()
        assertEquals(2, abort.byte())
        assertContentEquals(files.getValue(2).second, session.download(2))
        session.close()
    }

    @Test
    fun `a file the watch does not have is refused, not waited for`() {
        val watch = FakeGarminWatch(files = emptyMap())
        val session = connect(watch)
        session.awaitReady(5_000)
        val error = assertFailsWith<GarminBleException> { session.download(42) }
        assertEquals(GarminBleException.Reason.REFUSED, error.reason)
        session.close()
    }

    @Test
    fun `a watch that goes quiet ends the download with a timeout`() {
        val watch = FakeGarminWatch(files = mapOf(1 to (GarminFileType.MONITOR to bytes(1_000, 6))), goQuietAfterChunks = 2)
        val session = connect(watch)
        session.awaitReady(5_000)
        val error = assertFailsWith<GarminBleException> { session.download(1, idleTimeoutMs = 500) }
        assertEquals(GarminBleException.Reason.TIMEOUT, error.reason)
        session.close()
    }

    @Test
    fun `a lost connection fails what is waiting`() {
        val watch = FakeGarminWatch(files = mapOf(1 to (GarminFileType.MONITOR to bytes(1_000, 6))), goQuietAfterChunks = 1)
        val session = connect(watch)
        session.awaitReady(5_000)
        Thread { Thread.sleep(300); session.onDisconnected() }.start()
        val error = assertFailsWith<GarminBleException> { session.download(1) }
        assertEquals(GarminBleException.Reason.DISCONNECTED, error.reason)
        assertFailsWith<GarminBleException> { session.download(1) }
    }

    @Test
    fun `the watch's questions get answers it can use`() {
        val extras = listOf(
            GfdiMessage.build(GfdiType.CURRENT_TIME_REQUEST) { int(99) },
            GfdiMessage.build(GfdiType.AUTH_NEGOTIATION) { byte(0); int(0x01) },
            GfdiMessage.build(GfdiType.PROTOBUF_REQUEST) { short(12); int(0); int(3); int(3); bytes(byteArrayOf(8, 1, 0)) },
            GfdiMessage.build(GfdiType.BATTERY_STATUS) { byte(0x10); byte(39); byte(80) },
            GfdiMessage.build(GfdiType.MUSIC_CONTROL_CAPABILITIES) { byte(0) },
            GfdiMessage.build(4999) { byte(1) },
        )
        val watch = FakeGarminWatch(files = emptyMap(), extraAfterHello = extras)
        val session = connect(watch)
        session.awaitReady(5_000)
        watch.drain()
        Thread.sleep(100)
        watch.drain()
        val answers = watch.fromPhone.filter { it.type == GfdiType.RESPONSE }.associateBy { it.reader().short() }

        val time = answers.getValue(GfdiType.CURRENT_TIME_REQUEST).reader()
        time.short()
        assertEquals(GfdiStatus.ACK, time.byte())
        assertEquals(99, time.int())
        assertEquals(Instant.parse("2026-11-13T09:00:00Z").epochSecond - 631065600L, time.int().toLong())

        val auth = answers.getValue(GfdiType.AUTH_NEGOTIATION).reader()
        auth.short(); auth.byte()
        assertEquals(0, auth.byte()) // "guess ok"
        auth.byte()
        assertEquals(0x01, auth.int())

        assertEquals(3, answers.getValue(GfdiType.PROTOBUF_REQUEST).payload.size) // a plain ACK: the message was whole
        assertEquals(GfdiStatus.ACK, answers.getValue(GfdiType.BATTERY_STATUS).payload[2].toInt())
        assertEquals(GfdiStatus.UNSUPPORTED, answers.getValue(4999).payload[2].toInt())
        // a known message we have no use for is left alone, as Gadgetbridge does
        assertTrue(GfdiType.MUSIC_CONTROL_CAPABILITIES !in answers)
        session.close()
    }

    @Test
    fun `download before the handshake is refused`() {
        val session = GarminBleSession({ }, phone, scheduler)
        assertEquals(GarminBleException.Reason.NOT_READY, assertFailsWith<GarminBleException> { session.download(0) }.reason)
        assertEquals(GarminBleException.Reason.TIMEOUT, assertFailsWith<GarminBleException> { session.awaitReady(100) }.reason)
    }

    @Test
    fun `archiving tells the watch which file`() {
        val watch = FakeGarminWatch(files = mapOf(4 to (GarminFileType.ACTIVITY to bytes(10, 1))))
        val session = connect(watch)
        session.awaitReady(5_000)
        session.archive(4)
        session.finishSync()
        watch.drain()
        Thread.sleep(100)
        watch.drain()
        assertEquals(listOf(4), watch.archived.toList())
        assertEquals(SystemEvent.SYNC_COMPLETE.ordinal, watch.fromPhone.last { it.type == GfdiType.SYSTEM_EVENT }.payload[0].toInt())
        session.close()
    }
}
