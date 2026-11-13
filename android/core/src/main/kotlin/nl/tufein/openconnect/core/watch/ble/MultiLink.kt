/*
 * OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Ported from Gadgetbridge's Garmin support (CommunicatorV2, MlrCommunicator),
 * https://codeberg.org/Freeyourgadget/Gadgetbridge
 * Copyright (C) 2024-2026 Daniele Gobbetti, José Rebelo, Thomas Kuehne and other Gadgetbridge contributors.
 * Protocol notes: https://gadgetbridge.org/internals/specifics/garmin-protocol/
 */
package nl.tufein.openconnect.core.watch.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Garmin's "multi-link" (ML) service: one pair of Bluetooth characteristics carries several logical services, each
 * packet starting with the one-byte handle the watch gave that service. Handle 0 is for managing handles.
 */
object MultiLink {
    val SERVICE: UUID = UUID.fromString("6a4e2800-667b-11e3-949a-0800200c9a66")

    /** The characteristic pairs a watch may offer, receive first; the send one is the receive one plus 0x10. */
    val CHARACTERISTIC_PAIRS: List<Pair<UUID, UUID>> = (0..4).map { i ->
        UUID.fromString("6a4e281$i-667b-11e3-949a-0800200c9a66") to UUID.fromString("6a4e282$i-667b-11e3-949a-0800200c9a66")
    }

    /** Identifies us to the watch; Gadgetbridge uses 2, and so do we, because that is what has been tried on watches. */
    const val CLIENT_ID = 2L

    const val SERVICE_GFDI = 1

    // request and response types on handle 0
    const val REGISTER_REQ = 0
    const val REGISTER_RESP = 1
    const val CLOSE_HANDLE_REQ = 2
    const val CLOSE_HANDLE_RESP = 3
    const val CLOSE_ALL_REQ = 5
    const val CLOSE_ALL_RESP = 6

    fun closeAll(): ByteArray = management(CLOSE_ALL_REQ) { putShort(0) }

    /** Asks for a handle for [service]; [reliable] asks for the MLR variant with acknowledgements and resends. */
    fun register(service: Int, reliable: Boolean): ByteArray = management(REGISTER_REQ) {
        putShort(service.toShort())
        put(if (reliable) 2 else 0)
    }

    private fun management(type: Int, body: ByteBuffer.() -> Unit): ByteArray {
        val buf = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0) // handle 0: management
        buf.put(type.toByte())
        buf.putLong(CLIENT_ID)
        buf.body()
        return buf.array()
    }

    sealed interface Management {
        data class Registered(val service: Int, val status: Int, val handle: Int, val reliable: Boolean) : Management
        data class HandleClosed(val service: Int, val handle: Int) : Management
        data object AllClosed : Management
        data object Other : Management
    }

    /** Reads a packet on handle 0; null when it is meant for another client. */
    fun parseManagement(packet: ByteArray): Management? {
        if (packet.size < 10) return Management.Other
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.get() // handle 0
        val type = buf.get().toInt() and 0xFF
        if (buf.getLong() != CLIENT_ID) return null
        return when (type) {
            REGISTER_RESP -> {
                if (buf.remaining() < 3) return Management.Other
                val service = buf.getShort().toInt() and 0xFFFF
                val status = buf.get().toInt() and 0xFF
                val handle = if (buf.hasRemaining()) buf.get().toInt() and 0xFF else 0
                val reliable = if (buf.hasRemaining()) buf.get().toInt() != 0 else false
                Management.Registered(service, status, handle, reliable)
            }
            CLOSE_HANDLE_RESP -> {
                if (buf.remaining() < 3) return Management.Other
                Management.HandleClosed(buf.getShort().toInt() and 0xFFFF, buf.get().toInt() and 0xFF)
            }
            CLOSE_ALL_RESP -> Management.AllClosed
            else -> Management.Other
        }
    }

    /** How many bytes fit in one write for a negotiated [mtu]. */
    fun maxWrite(mtu: Int): Int = minOf(512, maxOf(23, mtu) - 3)
}

/** Runs something later; the real one is a thread, tests use a clock they move by hand. */
interface Scheduler {
    fun schedule(delayMs: Long, task: () -> Unit): Cancellable

    fun interface Cancellable {
        fun cancel()
    }
}

class ExecutorScheduler(
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "garmin-mlr").apply { isDaemon = true } },
) : Scheduler {
    override fun schedule(delayMs: Long, task: () -> Unit): Scheduler.Cancellable {
        val future = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS)
        return Scheduler.Cancellable { future.cancel(false) }
    }

    fun shutdown() = executor.shutdownNow()
}

/**
 * "Multi-link reliable" (MLR): every packet of a handle carries a 2-byte header with the handle, the number of the
 * next packet we expect (an acknowledgement) and its own sequence number, both counting 0..63. Packets that are not
 * acknowledged in time are sent again with a growing timeout, like a tiny TCP.
 *
 * Header: `1hhh rrrr  rrss ssss` (h handle, r acknowledgement, s sequence).
 */
class MlrChannel(
    private val handle: Int,
    @Volatile var maxPacketSize: Int,
    private val transmit: (ByteArray) -> Unit,
    private val receive: (ByteArray) -> Unit,
    private val scheduler: Scheduler,
) {
    companion object {
        private const val SEQ_MOD = 64
        private const val INITIAL_WINDOW = 0x20
        private const val INITIAL_TIMEOUT = 1000L
        private const val MAX_TIMEOUT = 20_000L
        private const val ACK_DELAY = 20L
        private const val ACK_THRESHOLD = 5

        fun isMlr(packet: ByteArray) = packet.isNotEmpty() && packet[0].toInt() and 0x80 != 0

        /** The 3-bit handle in an MLR packet. */
        fun handleOf(packet: ByteArray) = (packet[0].toInt() and 0x70) shr 4

        fun header(handle: Int, ack: Int, seq: Int): ByteArray = byteArrayOf(
            (0x80 or ((handle and 0x07) shl 4) or ((ack shr 2) and 0x0F)).toByte(),
            (((ack and 0x03) shl 6) or (seq and 0x3F)).toByte(),
        )
    }

    private class Sent(val data: ByteArray, val ack: Int)

    private val lock = Any()
    private var lastSentAck = 0
    private var nextSendSeq = 0
    private var nextReceiveSeq = 0
    private var lastReceivedAck = 0
    private var window = INITIAL_WINDOW
    private var timeout = INITIAL_TIMEOUT
    private val queue = ArrayDeque<ByteArray>()
    private val sent = arrayOfNulls<Sent>(SEQ_MOD)
    private var ackTimer: Scheduler.Cancellable? = null
    private var resendTimer: Scheduler.Cancellable? = null
    @Volatile private var closed = false

    fun send(message: ByteArray) {
        if (message.isEmpty()) return
        synchronized(lock) {
            val chunk = maxPacketSize - 2
            var pos = 0
            while (pos < message.size) {
                val end = minOf(message.size, pos + chunk)
                queue.addLast(message.copyOfRange(pos, end))
                pos = end
            }
            pump()
        }
    }

    fun onPacket(packet: ByteArray) {
        if (packet.size < 2 || !isMlr(packet)) return
        val b0 = packet[0].toInt() and 0xFF
        val b1 = packet[1].toInt() and 0xFF
        if ((b0 and 0x70) shr 4 != (handle and 0x07)) return
        val ack = ((b0 and 0x0F) shl 2) or ((b1 shr 6) and 0x03)
        val seq = b1 and 0x3F
        val data: ByteArray?
        synchronized(lock) {
            if (ack != lastReceivedAck) acknowledged(ack)
            data = when {
                packet.size <= 2 -> null
                seq != nextReceiveSeq -> {
                    sendAck() // out of order: say again what we expect, the watch resends
                    null
                }
                else -> {
                    nextReceiveSeq = (nextReceiveSeq + 1) % SEQ_MOD
                    packet.copyOfRange(2, packet.size)
                }
            }
        }
        if (data != null) receive(data)
        synchronized(lock) {
            if (data != null) scheduleAck()
            pump()
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            ackTimer?.cancel()
            resendTimer?.cancel()
            queue.clear()
        }
    }

    private fun acknowledged(ack: Int) {
        val count = (ack - lastReceivedAck + SEQ_MOD) % SEQ_MOD
        resendTimer?.cancel()
        var i = lastReceivedAck
        while (i != ack) {
            sent[i] = null
            i = (i + 1) % SEQ_MOD
        }
        lastReceivedAck = ack
        timeout = INITIAL_TIMEOUT
        window = minOf(window + count, INITIAL_WINDOW)
        if (lastReceivedAck != nextSendSeq) startResendTimer()
    }

    private fun scheduleAck() {
        ackTimer?.cancel()
        val unacked = (nextReceiveSeq - lastSentAck + SEQ_MOD) % SEQ_MOD
        if (unacked >= ACK_THRESHOLD) {
            sendAck()
        } else {
            ackTimer = scheduler.schedule(ACK_DELAY) { synchronized(lock) { sendAck() } }
        }
    }

    private fun sendAck() {
        if (closed) return
        ackTimer?.cancel()
        transmit(header(handle, nextReceiveSeq, 0))
        lastSentAck = nextReceiveSeq
    }

    private fun pump() {
        if (closed) return
        while (queue.isNotEmpty()) {
            val inFlight = (nextSendSeq - lastReceivedAck + SEQ_MOD) % SEQ_MOD
            if (inFlight >= window) return
            val data = queue.removeFirst()
            transmit(header(handle, nextReceiveSeq, nextSendSeq) + data)
            sent[nextSendSeq] = Sent(data, nextReceiveSeq)
            nextSendSeq = (nextSendSeq + 1) % SEQ_MOD
            if (inFlight == 0) startResendTimer()
        }
    }

    private fun startResendTimer() {
        resendTimer?.cancel()
        resendTimer = scheduler.schedule(timeout) { onResendTimeout() }
    }

    private fun onResendTimeout() {
        synchronized(lock) {
            if (closed) return
            timeout = minOf(timeout * 2, MAX_TIMEOUT)
            window = maxOf(1, window / 2)
            var i = lastReceivedAck
            while (i != nextSendSeq) {
                sent[i]?.let { transmit(header(handle, it.ack, i) + it.data) }
                i = (i + 1) % SEQ_MOD
            }
            startResendTimer()
        }
    }
}
