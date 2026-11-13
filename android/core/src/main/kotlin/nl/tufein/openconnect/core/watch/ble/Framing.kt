/*
 * OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Ported from Gadgetbridge's Garmin support (ChecksumCalculator, CobsCoDec),
 * https://codeberg.org/Freeyourgadget/Gadgetbridge
 * Copyright (C) 2023-2026 Petr Kadlec, Daniele Gobbetti, José Rebelo, Thomas Kuehne and other Gadgetbridge contributors.
 */
package nl.tufein.openconnect.core.watch.ble

import java.io.ByteArrayOutputStream

/** The 16-bit checksum Garmin puts at the end of every GFDI message and of every file chunk. */
object GarminCrc {
    private val TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    /** Continues [seed] over [length] bytes of [data] from [offset]; a new checksum starts at 0. */
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset, seed: Int = 0): Int {
        var crc = seed
        for (i in offset until offset + length) {
            val b = data[i].toInt()
            crc = (((crc shr 4) and 0x0FFF) xor TABLE[crc and 15]) xor TABLE[b and 15]
            crc = (((crc shr 4) and 0x0FFF) xor TABLE[crc and 15]) xor TABLE[(b shr 4) and 15]
        }
        return crc
    }
}

/**
 * Garmin's flavour of COBS (consistent overhead byte stuffing): a message travels as `00 <cobs> 00`, so a frame can be
 * found again in a stream of Bluetooth packets that split it anywhere.
 */
object Cobs {
    fun encode(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size + data.size / 254 + 3)
        out.write(0) // Garmin's leading zero
        var pos = 0
        var lastWasZero = false
        while (pos < data.size) {
            var start = pos
            var zero = pos
            while (pos < data.size) {
                val b = data[pos++]
                if (b.toInt() == 0) break
                zero++
            }
            lastWasZero = pos > zero
            var size = zero - start
            while (size >= 0xFE) {
                out.write(0xFF)
                out.write(data, start, 0xFE)
                size -= 0xFE
                start += 0xFE
            }
            out.write(size + 1)
            out.write(data, start, size)
        }
        if (lastWasZero) out.write(0x01)
        out.write(0)
        return out.toByteArray()
    }

    /** Decodes the stuffed bytes between the two zeros of one frame. Null when they are not valid COBS. */
    fun decodeBody(body: ByteArray): ByteArray? {
        val out = ByteArrayOutputStream(body.size)
        var pos = 0
        while (pos < body.size) {
            val code = body[pos++].toInt() and 0xFF
            if (code == 0) return null
            val size = code - 1
            if (pos + size > body.size) return null
            out.write(body, pos, size)
            pos += size
            if (code != 0xFF && pos < body.size) out.write(0)
        }
        return out.toByteArray()
    }
}

/**
 * Collects the bytes of incoming Bluetooth packets and hands out every complete `00 <cobs> 00` frame, decoded.
 * Bytes that cannot be part of a frame (the start of a frame was lost) are dropped, so one lost packet costs one
 * message and never jams the stream.
 */
class CobsStream(private val maxBuffered: Int = 1 shl 20) {
    private var buffer = ByteArray(0)

    fun feed(bytes: ByteArray): List<ByteArray> {
        buffer += bytes
        val messages = ArrayList<ByteArray>()
        var start = buffer.indexOf(0)
        if (start < 0) {
            // nothing that starts a frame: whatever this is, it can never become one
            buffer = ByteArray(0)
            return messages
        }
        while (true) {
            var end = -1
            for (i in start + 1 until buffer.size) {
                if (buffer[i].toInt() == 0) {
                    end = i
                    break
                }
            }
            if (end < 0) break
            if (end == start + 1) {
                // two zeros in a row: the end of one frame and the start of the next, or a lost frame body
                start = end
                continue
            }
            Cobs.decodeBody(buffer.copyOfRange(start + 1, end))?.let(messages::add)
            // Garmin sends a leading zero for every frame, so the next zero is normally right behind this one and
            // is skipped above; keeping this end as a possible start also copes with frames that share their zero.
            start = end
        }
        buffer = buffer.copyOfRange(start, buffer.size)
        if (buffer.size > maxBuffered) buffer = ByteArray(0)
        return messages
    }
}
