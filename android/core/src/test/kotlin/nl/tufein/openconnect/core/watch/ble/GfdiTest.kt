// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GfdiTest {
    @Test
    fun `a real device information message is read`() {
        val message = GfdiMessage.parse(hex("2C00A0139600310F684C1BCA840508020B496E7374696E637420325308496E7374696E6374023253000004B8"))
        assertEquals(GfdiType.DEVICE_INFORMATION, message.type)
        val info = GarminDeviceInfo.parse(message)
        assertEquals(150, info.protocolVersion)
        assertEquals(3889, info.productNumber)
        assertEquals("14.12", info.softwareVersionText)
        assertEquals(520, info.maxPacketSize)
        assertEquals("Instinct 2S", info.bluetoothName)
        assertEquals("Instinct", info.deviceName)
        assertEquals("2S", info.deviceModel)
    }

    @Test
    fun `a damaged message is refused`() {
        val good = GfdiMessage.build(GfdiType.SYSTEM_EVENT) { byte(8); byte(0) }.encode()
        val flipped = good.copyOf().also { it[4] = (it[4] + 1).toByte() }
        assertFailsWith<GfdiFormatException> { GfdiMessage.parse(flipped) }
        assertFailsWith<GfdiFormatException> { GfdiMessage.parse(good.copyOfRange(0, good.size - 1)) }
    }

    @Test
    fun `messages are laid out as Gadgetbridge lays them out`() {
        // length, type 5030, event SYNC_READY (8), value 0, checksum
        val syncReady = GfdiOut.systemEvent(SystemEvent.SYNC_READY).encode()
        assertEquals("0800a613" + "0800", syncReady.copyOfRange(0, 6).toHex())
        assertEquals(syncReady.size, 8)
        // download request: index, offset, "new", crc seed, size, flags
        val download = GfdiOut.downloadRequest(0x1234).encode()
        assertEquals("14008a13" + "3412" + "00000000" + "01" + "0000" + "00000000" + "00", download.copyOfRange(0, download.size - 2).toHex())
        // acknowledgement of a file chunk: RESPONSE to 5004, ACK, transfer OK, next offset
        val ack = GfdiOut.transferDataAck(0x0102).encode()
        assertEquals("0e008813" + "8c13" + "00" + "00" + "02010000", ack.copyOfRange(0, ack.size - 2).toHex())
        // our capabilities are the 17 bytes Gadgetbridge sends
        val config = GfdiOut.configuration()
        assertEquals(GfdiType.CONFIGURATION, config.type)
        assertEquals(17, config.payload[0].toInt())
        assertEquals("ffff3fefffffffffffffffffff0003ff01", config.payload.copyOfRange(1, 18).toHex())
    }

    @Test
    fun `a type with a sequence number is read as the plain type`() {
        // 0x80 | sequence 5 in the high byte, 24 (5024 - 5000) in the low byte
        val raw = GfdiMessage(0x8518, byteArrayOf(1, 2)).encode()
        assertEquals(GfdiType.DEVICE_INFORMATION, GfdiMessage.parse(raw).type)
    }

    @Test
    fun `the time answer carries the zone offset and the next two clock changes`() {
        val now = Instant.parse("2026-11-13T09:00:00Z")
        val reply = GfdiOut.currentTime(77, now, ZoneId.of("Europe/Brussels"))
        val r = reply.reader()
        assertEquals(GfdiType.CURRENT_TIME_REQUEST, r.short())
        assertEquals(GfdiStatus.ACK, r.byte())
        assertEquals(77, r.int())
        assertEquals(now.epochSecond - 631065600L, r.int().toLong())
        assertEquals(3600, r.int()) // winter time
        val summerEnds = Instant.parse("2027-10-31T01:00:00Z").epochSecond - 631065600L
        val summerStarts = Instant.parse("2027-03-28T01:00:00Z").epochSecond - 631065600L
        assertEquals(summerEnds, r.int().toLong())
        assertEquals(summerStarts, r.int().toLong())
    }

    @Test
    fun `the directory is read 16 bytes at a time`() {
        val dir = GfdiWriter().apply {
            // index, type 128/32 (monitor), file number, flags, size, Garmin time
            short(7); byte(128); byte(32); short(1); byte(0); byte(0); int(4096); int(1_100_000_000)
            short(9); byte(128); byte(4); short(2); byte(0); byte(0); int(123_456); int(0)
        }.toByteArray()
        val entries = GarminDirectoryEntry.parseDirectory(dir)
        assertEquals(2, entries.size)
        assertEquals(GarminFileType.MONITOR, entries[0].fileType)
        assertEquals(4096, entries[0].size)
        assertEquals(Instant.ofEpochSecond(1_100_000_000L + 631065600L), entries[0].date)
        assertEquals(GarminFileType.ACTIVITY, entries[1].fileType)
        assertNull(entries[1].date)
        assertFailsWith<GfdiFormatException> { GarminDirectoryEntry.parseDirectory(dir.copyOf(20)) }
    }

    @Test
    fun `multi-link management messages`() {
        assertEquals("00" + "05" + "0200000000000000" + "0000" + "00", MultiLink.closeAll().toHex())
        assertEquals("00" + "00" + "0200000000000000" + "0100" + "02", MultiLink.register(MultiLink.SERVICE_GFDI, reliable = true).toHex())
        val registered = MultiLink.parseManagement(hex("00" + "01" + "0200000000000000" + "0100" + "00" + "81" + "01"))
        assertEquals(MultiLink.Management.Registered(service = 1, status = 0, handle = 0x81, reliable = true), registered)
        // another app's client id is not ours to handle
        assertNull(MultiLink.parseManagement(hex("00" + "06" + "0900000000000000" + "0000")))
        assertEquals(MultiLink.Management.AllClosed, MultiLink.parseManagement(hex("00" + "06" + "0200000000000000" + "0000")))
        assertContentEquals(listOf(20, 512, 509), listOf(MultiLink.maxWrite(10), MultiLink.maxWrite(600), MultiLink.maxWrite(512)))
    }
}
