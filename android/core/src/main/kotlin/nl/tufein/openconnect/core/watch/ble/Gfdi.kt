/*
 * OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Ported from Gadgetbridge's Garmin support (GFDIMessage and the messages in messages/ and messages/status/),
 * https://codeberg.org/Freeyourgadget/Gadgetbridge
 * Copyright (C) 2024-2026 Daniele Gobbetti, José Rebelo, Thomas Kuehne and other Gadgetbridge contributors.
 */
package nl.tufein.openconnect.core.watch.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneId

/** GFDI message ids (the "Garmin Flexible Device Interface" that runs on top of the multi-link service). */
object GfdiType {
    const val RESPONSE = 5000
    const val DOWNLOAD_REQUEST = 5002
    const val UPLOAD_REQUEST = 5003
    const val FILE_TRANSFER_DATA = 5004
    const val CREATE_FILE = 5005
    const val FILTER = 5007
    const val SET_FILE_FLAG = 5008
    const val FILE_AVAILABLE = 5009
    const val FIT_DEFINITION = 5011
    const val FIT_DATA = 5012
    const val WEATHER_REQUEST = 5014
    const val BATTERY_STATUS = 5023
    const val DEVICE_INFORMATION = 5024
    const val DEVICE_SETTINGS = 5026
    const val SYSTEM_EVENT = 5030
    const val SUPPORTED_FILE_TYPES_REQUEST = 5031
    const val NOTIFICATION_UPDATE = 5033
    const val NOTIFICATION_CONTROL = 5034
    const val NOTIFICATION_DATA = 5035
    const val NOTIFICATION_SUBSCRIPTION = 5036
    const val SYNCHRONIZATION = 5037
    const val FIND_MY_PHONE_REQUEST = 5039
    const val FIND_MY_PHONE_CANCEL = 5040
    const val MUSIC_CONTROL = 5041
    const val MUSIC_CONTROL_CAPABILITIES = 5042
    const val PROTOBUF_REQUEST = 5043
    const val PROTOBUF_RESPONSE = 5044
    const val MUSIC_CONTROL_ENTITY_UPDATE = 5049
    const val CONFIGURATION = 5050
    const val CURRENT_TIME_REQUEST = 5052
    const val AUTH_NEGOTIATION = 5101

    /** Ids Garmin uses that we know of; anything else is answered with "unsupported". */
    val KNOWN: Set<Int> = (5000..5066).toSet() + (5101..5112).toSet()
}

/** The status byte of a [GfdiType.RESPONSE]. */
object GfdiStatus {
    const val ACK = 0
    const val NAK = 1
    const val UNSUPPORTED = 2
    const val DECODE_ERROR = 3
    const val CRC_ERROR = 4
    const val LENGTH_ERROR = 5
}

/** Events the phone announces to the watch with [GfdiType.SYSTEM_EVENT]. */
enum class SystemEvent {
    SYNC_COMPLETE, SYNC_FAIL, FACTORY_RESET, PAIR_START, PAIR_COMPLETE, PAIR_FAIL,
    HOST_DID_ENTER_FOREGROUND, HOST_DID_ENTER_BACKGROUND, SYNC_READY, NEW_DOWNLOAD_AVAILABLE,
    DEVICE_SOFTWARE_UPDATE, DEVICE_DISCONNECT, TUTORIAL_COMPLETE, SETUP_WIZARD_START, SETUP_WIZARD_COMPLETE,
    SETUP_WIZARD_SKIPPED, TIME_UPDATED,
}

class GfdiFormatException(message: String) : Exception(message)

/**
 * One GFDI message: `[length u16][type u16][payload][crc u16]`, little endian, where the length counts everything and
 * the checksum covers everything before it.
 */
class GfdiMessage(val type: Int, val payload: ByteArray) {
    fun reader(): GfdiReader = GfdiReader(payload)

    fun encode(): ByteArray {
        val size = 2 + 2 + payload.size + 2
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(size.toShort())
        buf.putShort(type.toShort())
        buf.put(payload)
        val crc = GarminCrc.compute(buf.array(), 0, size - 2)
        buf.putShort(crc.toShort())
        return buf.array()
    }

    companion object {
        fun parse(bytes: ByteArray): GfdiMessage {
            if (bytes.size < 6) throw GfdiFormatException("message too short (${bytes.size} bytes)")
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val length = buf.getShort(0).toInt() and 0xFFFF
            if (length != bytes.size) throw GfdiFormatException("length says $length, got ${bytes.size} bytes")
            val crc = buf.getShort(length - 2).toInt() and 0xFFFF
            val expected = GarminCrc.compute(bytes, 0, length - 2)
            if (crc != expected) throw GfdiFormatException("bad checksum")
            var type = buf.getShort(2).toInt() and 0xFFFF
            // Newer watches put a sequence number in the type: 1sss ssss tttt tttt, where t is the id minus 5000.
            if (type and 0x8000 != 0) type = (type and 0xFF) + 5000
            return GfdiMessage(type, bytes.copyOfRange(4, length - 2))
        }

        fun build(type: Int, write: GfdiWriter.() -> Unit = {}): GfdiMessage = GfdiMessage(type, GfdiWriter().apply(write).toByteArray())

        /** A [GfdiType.RESPONSE] to a message of type [to]: `[original type u16][status u8][...]`. */
        fun response(to: Int, status: Int = GfdiStatus.ACK, more: GfdiWriter.() -> Unit = {}): GfdiMessage = build(GfdiType.RESPONSE) {
            short(to)
            byte(status)
            more()
        }
    }

    override fun toString(): String = "GfdiMessage($type, ${payload.size} bytes)"
}

class GfdiReader(bytes: ByteArray) {
    private val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val remaining: Int get() = buf.remaining()

    private fun need(n: Int) {
        if (buf.remaining() < n) throw GfdiFormatException("message ends early")
    }

    fun byte(): Int = need(1).let { buf.get().toInt() and 0xFF }
    fun short(): Int = need(2).let { buf.getShort().toInt() and 0xFFFF }
    fun int(): Int = need(4).let { buf.getInt() }
    fun long(): Long = need(8).let { buf.getLong() }
    fun bytes(n: Int): ByteArray = need(n).let { ByteArray(n).also { buf.get(it) } }
    fun string(): String = String(bytes(byte()), Charsets.UTF_8)
    fun rest(): ByteArray = bytes(buf.remaining())
}

class GfdiWriter {
    private val out = java.io.ByteArrayOutputStream()

    fun byte(v: Int) = out.write(v and 0xFF)
    fun short(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }
    fun int(v: Int) {
        short(v and 0xFFFF)
        short((v ushr 16) and 0xFFFF)
    }
    fun bytes(v: ByteArray) = out.write(v)
    fun string(v: String) {
        val b = v.toByteArray(Charsets.UTF_8)
        require(b.size <= 255) { "string too long" }
        byte(b.size)
        bytes(b)
    }
    fun toByteArray(): ByteArray = out.toByteArray()
}

/** What the watch says about itself in [GfdiType.DEVICE_INFORMATION]. */
data class GarminDeviceInfo(
    val protocolVersion: Int,
    val productNumber: Int,
    val unitNumber: Long,
    val softwareVersion: Int,
    val maxPacketSize: Int,
    val bluetoothName: String,
    val deviceName: String,
    val deviceModel: String,
) {
    /** "21.19" for 2119. */
    val softwareVersionText: String get() = "%d.%02d".format(softwareVersion / 100, softwareVersion % 100)

    companion object {
        fun parse(message: GfdiMessage): GarminDeviceInfo {
            val r = message.reader()
            return GarminDeviceInfo(
                protocolVersion = r.short(),
                productNumber = r.short(),
                unitNumber = r.int().toLong() and 0xFFFFFFFFL,
                softwareVersion = r.short(),
                maxPacketSize = r.short(),
                bluetoothName = r.string(),
                deviceName = r.string(),
                deviceModel = r.string(),
            )
        }
    }
}

/** One file in the watch's directory (file index 0), 16 bytes each. */
data class GarminDirectoryEntry(
    val index: Int,
    val dataType: Int,
    val subType: Int,
    val fileNumber: Int,
    val specificFlags: Int,
    val fileFlags: Int,
    val size: Long,
    /** Null when the watch gave no date. */
    val date: Instant?,
) {
    val fileType: GarminFileType? get() = GarminFileType.of(dataType, subType)

    companion object {
        /** Seconds between the Unix epoch and Garmin's (1989-12-31T00:00:00Z). */
        const val GARMIN_EPOCH = 631065600L

        fun parseDirectory(bytes: ByteArray): List<GarminDirectoryEntry> {
            if (bytes.size % 16 != 0) throw GfdiFormatException("directory of ${bytes.size} bytes is not a multiple of 16")
            val r = GfdiReader(bytes)
            val entries = ArrayList<GarminDirectoryEntry>(bytes.size / 16)
            while (r.remaining > 0) {
                val index = r.short()
                val dataType = r.byte()
                val subType = r.byte()
                val fileNumber = r.short()
                val specific = r.byte()
                val flags = r.byte()
                val size = r.int().toLong() and 0xFFFFFFFFL
                val stamp = r.int().toLong() and 0xFFFFFFFFL
                entries += GarminDirectoryEntry(
                    index, dataType, subType, fileNumber, specific, flags, size,
                    if (stamp == 0L) null else Instant.ofEpochSecond(stamp + GARMIN_EPOCH),
                )
            }
            return entries
        }
    }
}

/** The kinds of watch files OpenConnect can use; Gadgetbridge's FileType has the full list. */
enum class GarminFileType(val dataType: Int, val subType: Int, val label: String) {
    ACTIVITY(128, 4, "ACTIVITY"),
    MONITOR(128, 32, "MONITOR"),
    SLEEP(128, 49, "SLEEP"),
    HRV_STATUS(128, 68, "HRV_STATUS"),
    HSA(128, 70, "HSA"),
    METRICS(128, 44, "METRICS"),
    ;

    companion object {
        fun of(dataType: Int, subType: Int): GarminFileType? = entries.firstOrNull { it.dataType == dataType && it.subType == subType }
    }
}

/** The messages the phone sends. Byte layouts follow Gadgetbridge's message classes of the same name. */
object GfdiOut {
    /** What Gadgetbridge tells the watch it can do (its GarminCapability.OUR_CAPABILITIES as a bit set). */
    val OUR_CAPABILITIES: ByteArray = "ffff3fefffffffffffffffffff0003ff01".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun deviceInformationReply(watch: GarminDeviceInfo, phoneName: String, manufacturer: String, model: String): GfdiMessage =
        GfdiMessage.response(GfdiType.DEVICE_INFORMATION) {
            short(150) // our protocol version
            short(0xFFFF) // product number: none
            int(-1) // unit number: none
            short(7791) // software version
            short(0xFFFF) // max packet size: no limit of our own
            string(phoneName)
            string(manufacturer)
            string(model)
            byte(if (watch.protocolVersion / 100 == 1) 1 else 0)
        }

    fun configuration(): GfdiMessage = GfdiMessage.build(GfdiType.CONFIGURATION) {
        byte(OUR_CAPABILITIES.size)
        bytes(OUR_CAPABILITIES)
    }

    fun systemEvent(event: SystemEvent, value: Int = 0): GfdiMessage = GfdiMessage.build(GfdiType.SYSTEM_EVENT) {
        byte(event.ordinal)
        byte(value)
    }

    fun supportedFileTypesRequest(): GfdiMessage = GfdiMessage.build(GfdiType.SUPPORTED_FILE_TYPES_REQUEST)

    /** Asks for file [index] (0 is the directory) from the start. */
    fun downloadRequest(index: Int): GfdiMessage = GfdiMessage.build(GfdiType.DOWNLOAD_REQUEST) {
        short(index)
        int(0) // offset
        byte(1) // request type: new
        short(0) // crc seed
        int(0) // size: all of it
        byte(0) // flags: no compression
    }

    /** Confirms a file chunk; [nextOffset] is where the next chunk should start. */
    fun transferDataAck(nextOffset: Int, transferStatus: Int = 0): GfdiMessage =
        GfdiMessage.response(GfdiType.FILE_TRANSFER_DATA) {
            byte(transferStatus) // 0 ok, 2 abort
            int(nextOffset)
        }

    /** Marks a file as synced ("archive", 0x10), which is what Garmin Connect does after a download. */
    fun archive(index: Int): GfdiMessage = GfdiMessage.build(GfdiType.SET_FILE_FLAG) {
        short(index)
        byte(0x10)
    }

    /** The answer to [GfdiType.CURRENT_TIME_REQUEST]: the time, the zone offset and the next two clock changes. */
    fun currentTime(referenceId: Int, now: Instant, zone: ZoneId): GfdiMessage {
        val rules = zone.rules
        val offset = rules.getOffset(now).totalSeconds
        fun garmin(i: Instant) = (i.epochSecond - GarminDirectoryEntry.GARMIN_EPOCH).toInt()
        val next = runCatching { rules.nextTransition(now) }.getOrNull()
        val after = next?.let { runCatching { rules.nextTransition(it.instant) }.getOrNull() }
        return GfdiMessage.response(GfdiType.CURRENT_TIME_REQUEST) {
            int(referenceId)
            int(garmin(now))
            int(offset)
            int(after?.let { garmin(it.instant) } ?: 0)
            int(next?.let { garmin(it.instant) } ?: 0)
        }
    }
}
