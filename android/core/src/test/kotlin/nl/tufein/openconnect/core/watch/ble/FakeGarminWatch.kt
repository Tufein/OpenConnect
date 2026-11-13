// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A pretend Garmin watch on the other end of the Bluetooth link, written from the protocol notes rather than from the
 * phone's code: it hands out a GFDI handle, introduces itself, serves its directory and files in chunks and waits for
 * each acknowledgement. It runs on its own thread, like a real Bluetooth callback thread.
 *
 * Knobs make it misbehave the way a real link can.
 */
class FakeGarminWatch(
    val files: Map<Int, Pair<GarminFileType, ByteArray>>,
    private val reliable: Boolean = true,
    /** Refuse a reliable (MLR) registration, as older firmware might. */
    private val refuseReliable: Boolean = false,
    private val chunkSize: Int = 100,
    private val deviceName: String = "fenix 8",
    /** Every n-th file chunk goes out with a wrong checksum (0: never). */
    private val damageEvery: Int = 0,
    /** Stop sending after this many chunks (simulates a watch that goes quiet). */
    private val goQuietAfterChunks: Int = Int.MAX_VALUE,
    /** Send these extra messages right after DEVICE_INFORMATION, to see how the phone answers. */
    private val extraAfterHello: List<GfdiMessage> = emptyList(),
    private val fileDates: Map<Int, Long> = emptyMap(),
) {
    lateinit var phone: GarminBleSession
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "fake-watch").apply { isDaemon = true } }
    private val scheduler = ExecutorScheduler()

    /** Every GFDI message the phone sent, in order. */
    val fromPhone = CopyOnWriteArrayList<GfdiMessage>()
    val managementFromPhone = CopyOnWriteArrayList<ByteArray>()
    val archived = CopyOnWriteArrayList<Int>()

    private var handle = 0
    private var mlr: MlrChannel? = null
    private val cobs = CobsStream()
    private var serving: ByteArray? = null
    private var crc = 0
    private var chunksSent = 0

    /** The phone's pipe: a Bluetooth write arriving at the watch. */
    val pipe = GarminBlePipe { packet -> thread.execute { receive(packet) } }

    fun shutdown() {
        thread.shutdownNow()
        scheduler.shutdown()
    }

    /** Waits until everything queued so far has been handled. */
    fun drain() {
        repeat(3) { thread.submit {}.get(5, TimeUnit.SECONDS) }
    }

    private fun toPhone(packet: ByteArray) = phone.onNotification(packet)

    private fun receive(packet: ByteArray) {
        val first = packet[0].toInt() and 0xFF
        val channel = mlr
        when {
            channel != null && first and 0x80 != 0 -> channel.onPacket(packet)
            first == 0 -> management(packet)
            first == handle -> gfdiBytes(packet.copyOfRange(1, packet.size))
        }
    }

    private fun management(packet: ByteArray) {
        managementFromPhone += packet
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.get()
        val type = buf.get().toInt()
        val client = buf.getLong()
        when (type) {
            5 -> toPhone(managementAnswer(6, client) { putShort(0) })
            0 -> {
                val service = buf.getShort().toInt()
                val wantsReliable = buf.get().toInt() == 2
                if (wantsReliable && refuseReliable) {
                    toPhone(managementAnswer(1, client) { putShort(service.toShort()); put(1) })
                    return
                }
                val useReliable = wantsReliable && reliable
                handle = if (useReliable) 0x81 else 0x01
                mlr = if (useReliable) MlrChannel(handle, 20, ::toPhone, ::gfdiBytes, scheduler) else null
                toPhone(managementAnswer(1, client) { putShort(service.toShort()); put(0); put(handle.toByte()); put(if (useReliable) 1 else 0) })
                hello()
            }
        }
    }

    private fun managementAnswer(type: Int, client: Long, body: ByteBuffer.() -> Unit): ByteArray {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0)
        buf.put(type.toByte())
        buf.putLong(client)
        buf.body()
        return buf.array().copyOf(buf.position())
    }

    private fun send(message: GfdiMessage) {
        val wire = Cobs.encode(message.encode())
        val channel = mlr
        if (channel != null) {
            channel.send(wire)
        } else {
            wire.toList().chunked(19).forEach { toPhone(byteArrayOf(handle.toByte()) + it.toByteArray()) }
        }
    }

    private fun hello() {
        send(
            GfdiMessage.build(GfdiType.DEVICE_INFORMATION) {
                short(150); short(4536); int(123456789); short(2119); short(4096)
                string(deviceName); string(deviceName); string("47mm")
                byte(0); byte(0)
            },
        )
        extraAfterHello.forEach(::send)
    }

    private fun gfdiBytes(bytes: ByteArray) {
        for (frame in cobs.feed(bytes)) {
            val message = GfdiMessage.parse(frame)
            fromPhone += message
            onMessage(message)
        }
    }

    private fun onMessage(message: GfdiMessage) {
        val r = message.reader()
        when (message.type) {
            GfdiType.RESPONSE -> {
                val original = r.short()
                when {
                    // the phone's full answer to our introduction (the plain ACK is 3 bytes)
                    original == GfdiType.DEVICE_INFORMATION && message.payload.size > 3 ->
                        send(GfdiMessage.build(GfdiType.CONFIGURATION) { byte(2); byte(0xFF); byte(0x01) })
                    original == GfdiType.FILE_TRANSFER_DATA -> {
                        r.byte()
                        val status = r.byte()
                        val next = r.int()
                        if (status == 0) sendChunk(next)
                    }
                }
            }
            GfdiType.DOWNLOAD_REQUEST -> {
                val index = r.short()
                val bytes = if (index == 0) directory() else files[index]?.second
                if (bytes == null) {
                    send(GfdiMessage.response(GfdiType.DOWNLOAD_REQUEST) { byte(1); int(0) }) // index not found
                    return
                }
                serving = bytes
                crc = 0
                send(GfdiMessage.response(GfdiType.DOWNLOAD_REQUEST) { byte(0); int(bytes.size) })
                sendChunk(0)
            }
            GfdiType.SET_FILE_FLAG -> {
                archived += r.short()
                send(GfdiMessage.response(GfdiType.SET_FILE_FLAG))
            }
        }
    }

    private fun sendChunk(offset: Int) {
        val data = serving ?: return
        if (offset >= data.size) {
            serving = null
            return
        }
        if (chunksSent >= goQuietAfterChunks) return
        chunksSent++
        val chunk = data.copyOfRange(offset, minOf(data.size, offset + chunkSize))
        crc = GarminCrc.compute(chunk, seed = crc)
        val damaged = damageEvery > 0 && chunksSent % damageEvery == 0
        send(
            GfdiMessage.build(GfdiType.FILE_TRANSFER_DATA) {
                byte(0)
                short(if (damaged) crc xor 0x5A5A else crc)
                int(offset)
                bytes(chunk)
            },
        )
    }

    private fun directory(): ByteArray = GfdiWriter().apply {
        for ((index, file) in files.toSortedMap()) {
            short(index); byte(file.first.dataType); byte(file.first.subType); short(index); byte(0); byte(0)
            int(file.second.size); int((fileDates[index] ?: 1_100_000_000L).toInt())
        }
    }.toByteArray()
}
