/*
 * OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * The connection flow follows Gadgetbridge's GarminSupport, CommunicatorV2 and FileTransferHandler,
 * https://codeberg.org/Freeyourgadget/Gadgetbridge
 * Copyright (C) 2024-2026 Daniele Gobbetti, José Rebelo, Thomas Kuehne and other Gadgetbridge contributors.
 */
package nl.tufein.openconnect.core.watch.ble

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Bluetooth writes to the watch's multi-link send characteristic. The Android side queues them; this must not block. */
fun interface GarminBlePipe {
    fun write(packet: ByteArray)
}

/** How the phone introduces itself to the watch. */
data class PhoneIdentity(val name: String, val manufacturer: String, val model: String)

class GarminBleException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NOT_READY, REFUSED, TIMEOUT, BROKEN, DISCONNECTED, CLOSED }
}

/**
 * One Bluetooth conversation with a Garmin watch, from the first packet to the last file. It has no Android types:
 * packets from the watch go into [onNotification], packets for the watch come out of [pipe]. The blocking calls
 * ([awaitReady], [listDirectory], [download]) must be called from a background thread, never from the thread that
 * delivers notifications.
 *
 * The flow, as the Garmin Connect app and Gadgetbridge do it:
 * 1. close every handle of our client id, then ask for a handle for GFDI, reliable (MLR) if [useMlr];
 * 2. the watch sends DEVICE_INFORMATION and CONFIGURATION; we answer both, ask for its file types and say we are
 *    ready to sync; the session is then [ready];
 * 3. file 0 is the directory: 16 bytes per file; any other file is fetched by index in chunks that we acknowledge,
 *    each with a running checksum.
 */
class GarminBleSession(
    private val pipe: GarminBlePipe,
    private val phone: PhoneIdentity,
    private val scheduler: Scheduler,
    /** Bytes per Bluetooth write; set it again when the MTU changes. */
    @Volatile var maxWrite: Int = 20,
    private val useMlr: Boolean = true,
    /** True right after pairing: then we also tell the watch that pairing and setup are complete, as Gadgetbridge does. */
    private val firstConnect: Boolean = false,
    private val now: () -> Instant = { Instant.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val log: (String) -> Unit = {},
) {
    private val lock = Any()
    private val cobs = CobsStream()
    private val readyFuture = CompletableFuture<GarminDeviceInfo>()

    private var gfdiHandle: Int? = null
    private var mlr: MlrChannel? = null
    private var triedPlain = false
    private var info: GarminDeviceInfo? = null
    private var initialized = false
    @Volatile private var closed = false
    private var transfer: Transfer? = null

    /** The watch's own description, once it has sent it. */
    val deviceInfo: GarminDeviceInfo? get() = synchronized(lock) { info }

    val ready: Boolean get() = readyFuture.isDone && !readyFuture.isCompletedExceptionally

    /** Starts the conversation; call once notifications on the receive characteristic are on. */
    fun start() {
        log("closing all handles")
        pipe.write(MultiLink.closeAll())
    }

    fun awaitReady(timeoutMs: Long = 30_000): GarminDeviceInfo = try {
        readyFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        throw GarminBleException(GarminBleException.Reason.TIMEOUT, "The watch did not finish the handshake")
    } catch (e: ExecutionException) {
        throw (e.cause as? GarminBleException) ?: GarminBleException(GarminBleException.Reason.BROKEN, e.cause?.message ?: "handshake failed")
    }

    fun onNotification(packet: ByteArray) {
        if (closed || packet.isEmpty()) return
        val (channel, gfdi) = synchronized(lock) { mlr to gfdiHandle }
        if (channel != null && gfdi != null && MlrChannel.isMlr(packet) && MlrChannel.handleOf(packet) == (gfdi and 0x07)) {
            channel.onPacket(packet)
            return
        }
        val handle = packet[0].toInt() and 0xFF
        when {
            handle == 0 -> onManagement(packet)
            handle == gfdi -> onGfdiBytes(packet.copyOfRange(1, packet.size))
            else -> log("packet for unknown handle $handle")
        }
    }

    /** The phone lost the connection: everything waiting fails. */
    fun onDisconnected(reason: String = "The watch disconnected") {
        fail(GarminBleException(GarminBleException.Reason.DISCONNECTED, reason))
    }

    fun close() {
        fail(GarminBleException(GarminBleException.Reason.CLOSED, "closed"))
    }

    private fun fail(error: GarminBleException) {
        val pending: Transfer?
        synchronized(lock) {
            if (closed) return
            closed = true
            mlr?.close()
            pending = transfer
            transfer = null
        }
        readyFuture.completeExceptionally(error)
        pending?.result?.completeExceptionally(error)
    }

    // ------------------------------------------------------------------------------- multi-link

    private fun onManagement(packet: ByteArray) {
        when (val m = MultiLink.parseManagement(packet)) {
            null, MultiLink.Management.Other -> Unit
            MultiLink.Management.AllClosed -> {
                synchronized(lock) {
                    mlr?.close()
                    mlr = null
                    gfdiHandle = null
                }
                registerGfdi(useMlr)
            }
            is MultiLink.Management.Registered -> {
                if (m.service != MultiLink.SERVICE_GFDI) return
                if (m.status != 0) {
                    log("GFDI registration refused (status ${m.status})")
                    if (useMlr && !triedPlain) {
                        triedPlain = true
                        registerGfdi(false)
                    } else {
                        fail(GarminBleException(GarminBleException.Reason.REFUSED, "The watch refused the connection (status ${m.status})"))
                    }
                    return
                }
                synchronized(lock) {
                    mlr?.close()
                    gfdiHandle = m.handle
                    mlr = if (m.reliable) {
                        MlrChannel(m.handle, maxWrite, pipe::write, ::onGfdiBytes, scheduler)
                    } else {
                        null
                    }
                }
                log("GFDI on handle ${m.handle}${if (m.reliable) " (reliable)" else ""}")
            }
            is MultiLink.Management.HandleClosed -> {
                val ours = synchronized(lock) { m.service == MultiLink.SERVICE_GFDI && m.handle == gfdiHandle }
                if (ours) {
                    log("the watch closed GFDI; registering again")
                    synchronized(lock) {
                        mlr?.close()
                        mlr = null
                        gfdiHandle = null
                    }
                    registerGfdi(useMlr)
                }
            }
        }
    }

    private fun registerGfdi(reliable: Boolean) {
        pipe.write(MultiLink.register(MultiLink.SERVICE_GFDI, reliable))
    }

    private fun onGfdiBytes(bytes: ByteArray) {
        for (frame in synchronized(cobs) { cobs.feed(bytes) }) {
            val message = try {
                GfdiMessage.parse(frame)
            } catch (e: GfdiFormatException) {
                log("dropped a message: ${e.message}")
                continue
            }
            try {
                handle(message)
            } catch (e: GfdiFormatException) {
                log("could not read message ${message.type}: ${e.message}")
            }
        }
    }

    private fun send(message: GfdiMessage) {
        val (handle, channel) = synchronized(lock) { gfdiHandle to mlr }
        if (handle == null || closed) return
        val payload = Cobs.encode(message.encode())
        if (channel != null) {
            channel.maxPacketSize = maxWrite
            channel.send(payload)
            return
        }
        val chunk = maxWrite - 1
        var pos = 0
        while (pos < payload.size) {
            val end = minOf(payload.size, pos + chunk)
            pipe.write(byteArrayOf(handle.toByte()) + payload.copyOfRange(pos, end))
            pos = end
        }
    }

    // --------------------------------------------------------------------------------------- GFDI

    private fun handle(message: GfdiMessage) {
        when (message.type) {
            GfdiType.DEVICE_INFORMATION -> {
                val device = GarminDeviceInfo.parse(message)
                synchronized(lock) { info = device }
                log("watch: ${device.deviceName} ${device.deviceModel}, software ${device.softwareVersionText}, protocol ${device.protocolVersion}")
                send(GfdiMessage.response(GfdiType.DEVICE_INFORMATION))
                send(GfdiOut.deviceInformationReply(device, phone.name, phone.manufacturer, phone.model))
            }
            GfdiType.CONFIGURATION -> {
                send(GfdiMessage.response(GfdiType.CONFIGURATION))
                send(GfdiOut.configuration())
                completeInitialization()
            }
            GfdiType.CURRENT_TIME_REQUEST -> send(GfdiOut.currentTime(message.reader().int(), now(), zone()))
            GfdiType.AUTH_NEGOTIATION -> {
                val r = message.reader()
                val unknown = r.byte()
                val flags = r.int()
                send(
                    GfdiMessage.response(GfdiType.AUTH_NEGOTIATION) {
                        byte(0) // "guess ok"
                        byte(unknown)
                        int(flags)
                    },
                )
            }
            GfdiType.RESPONSE -> onResponse(message.reader())
            GfdiType.FILE_TRANSFER_DATA -> onFileData(message.reader())
            GfdiType.PROTOBUF_REQUEST, GfdiType.PROTOBUF_RESPONSE -> {
                // We do not speak protobuf (settings, calendars and the like); acknowledge so the watch is not left waiting.
                val r = message.reader()
                val requestId = r.short()
                val offset = r.int()
                val total = r.int()
                val length = r.int()
                if (offset == 0 && total == length) {
                    send(GfdiMessage.response(message.type))
                } else {
                    send(
                        GfdiMessage.response(message.type) {
                            short(requestId)
                            int(offset)
                            byte(0) // kept
                            byte(0) // no error
                        },
                    )
                }
            }
            GfdiType.BATTERY_STATUS, GfdiType.FILE_AVAILABLE, GfdiType.SYNCHRONIZATION, GfdiType.WEATHER_REQUEST,
            GfdiType.FIND_MY_PHONE_REQUEST, GfdiType.FIND_MY_PHONE_CANCEL,
            -> send(GfdiMessage.response(message.type))
            in GfdiType.KNOWN -> log("ignored message ${message.type}")
            else -> send(GfdiMessage.response(message.type, GfdiStatus.UNSUPPORTED))
        }
    }

    private fun completeInitialization() {
        val device = synchronized(lock) {
            if (initialized) return
            initialized = true
            info
        }
        send(GfdiOut.supportedFileTypesRequest())
        send(GfdiOut.systemEvent(SystemEvent.SYNC_READY))
        if (firstConnect) {
            send(GfdiOut.systemEvent(SystemEvent.PAIR_COMPLETE))
            send(GfdiOut.systemEvent(SystemEvent.SYNC_COMPLETE))
            send(GfdiOut.systemEvent(SystemEvent.SETUP_WIZARD_COMPLETE))
        }
        readyFuture.complete(device ?: GarminDeviceInfo(0, 0, 0, 0, 0, "", "Garmin", ""))
    }

    private fun onResponse(r: GfdiReader) {
        val original = r.short()
        val status = r.byte()
        when (original) {
            GfdiType.DOWNLOAD_REQUEST -> {
                val current = synchronized(lock) { transfer } ?: return
                val downloadStatus = if (r.remaining >= 1) r.byte() else -1
                val size = if (r.remaining >= 4) r.int().toLong() and 0xFFFFFFFFL else 0
                if (status != GfdiStatus.ACK || downloadStatus != 0) {
                    finishTransfer(current, error = GarminBleException(GarminBleException.Reason.REFUSED, "The watch refused file ${current.index} (status $status/$downloadStatus)"))
                    return
                }
                if (size > Int.MAX_VALUE) {
                    finishTransfer(current, error = GarminBleException(GarminBleException.Reason.BROKEN, "File ${current.index} is too big"))
                    return
                }
                synchronized(lock) {
                    current.data = ByteArray(size.toInt())
                    current.touch()
                }
                if (size == 0L) finishTransfer(current)
            }
            else -> if (status != GfdiStatus.ACK) log("watch answered $status to message $original")
        }
    }

    private fun onFileData(r: GfdiReader) {
        r.byte() // flags
        val crc = r.short()
        val offset = r.int()
        val chunk = r.rest()
        val current = synchronized(lock) { transfer }
        if (current == null || current.data == null) {
            log("file data without a download; ignored")
            return
        }
        val data = current.data!!
        if (offset != current.received) {
            // a chunk we already have (resent): tell the watch where we are
            send(GfdiOut.transferDataAck(current.received))
            return
        }
        val expected = GarminCrc.compute(chunk, seed = current.crc)
        if (expected != crc || current.received + chunk.size > data.size) {
            send(GfdiOut.transferDataAck(current.received, transferStatus = 2))
            finishTransfer(current, error = GarminBleException(GarminBleException.Reason.BROKEN, "File ${current.index} arrived damaged"))
            return
        }
        System.arraycopy(chunk, 0, data, current.received, chunk.size)
        synchronized(lock) {
            current.received += chunk.size
            current.crc = crc
            current.touch()
        }
        send(GfdiOut.transferDataAck(current.received))
        current.onProgress(current.received, data.size)
        if (current.received == data.size) finishTransfer(current)
    }

    private fun finishTransfer(current: Transfer, error: GarminBleException? = null) {
        synchronized(lock) { if (transfer === current) transfer = null }
        if (error != null) current.result.completeExceptionally(error) else current.result.complete(current.data ?: ByteArray(0))
    }

    // ---------------------------------------------------------------------------------- files

    private inner class Transfer(val index: Int, val onProgress: (Int, Int) -> Unit) {
        val result = CompletableFuture<ByteArray>()
        var data: ByteArray? = null
        var received = 0
        var crc = 0
        @Volatile var lastActivity = System.nanoTime()

        fun touch() {
            lastActivity = System.nanoTime()
        }
    }

    /** The watch's file list (file 0). */
    fun listDirectory(idleTimeoutMs: Long = 20_000): List<GarminDirectoryEntry> =
        GarminDirectoryEntry.parseDirectory(download(0, idleTimeoutMs))

    /**
     * Fetches file [index]. Fails when nothing arrives for [idleTimeoutMs], so a large file over a slow link is fine as
     * long as it keeps coming. One download at a time.
     */
    fun download(index: Int, idleTimeoutMs: Long = 20_000, onProgress: (received: Int, total: Int) -> Unit = { _, _ -> }): ByteArray {
        if (!ready) throw GarminBleException(GarminBleException.Reason.NOT_READY, "The watch is not ready")
        val t = Transfer(index, onProgress)
        synchronized(lock) {
            if (closed) throw GarminBleException(GarminBleException.Reason.CLOSED, "The connection is closed")
            check(transfer == null) { "one download at a time" }
            transfer = t
        }
        send(GfdiOut.downloadRequest(index))
        while (true) {
            try {
                return t.result.get(250, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                val idleMs = (System.nanoTime() - t.lastActivity) / 1_000_000
                if (idleMs > idleTimeoutMs) {
                    finishTransfer(t, GarminBleException(GarminBleException.Reason.TIMEOUT, "File $index stopped arriving"))
                }
            } catch (e: ExecutionException) {
                throw (e.cause as? GarminBleException) ?: GarminBleException(GarminBleException.Reason.BROKEN, e.cause?.message ?: "download failed")
            }
        }
    }

    /** Marks a file as synced on the watch, as Garmin Connect does; the watch may then delete it to make room. */
    fun archive(index: Int) = send(GfdiOut.archive(index))

    /** Tells the watch this sync is over (its sync icon stops). */
    fun finishSync() = send(GfdiOut.systemEvent(SystemEvent.SYNC_COMPLETE))
}
