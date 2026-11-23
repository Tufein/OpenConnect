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

/** Switches on the notification conversation for a session; without it the watch is told notifications are off. */
data class NotificationOptions(
    /** The label of the one action the watch may offer on a notification (dismiss). */
    val dismissLabel: String = "Dismiss",
    /** The name of an app, for the watch's "what is this app called" question. */
    val appName: (String) -> String? = { null },
)

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
    /** Null for a session that only syncs files; set to also show phone notifications on the watch (see [notifier]). */
    notifications: NotificationOptions? = null,
    /** How long a notification session waits for the watch to ask for notifications before it sends them anyway. */
    private val askGraceMs: Long = ASK_GRACE_MS,
    /** How often a notification session checks that the watch is still there; a quiet link gets a small question. */
    private val heartbeatMs: Long = HEARTBEAT_MS,
    /** How long the watch gets to answer an announcement (the tests make it short). */
    private val answerWaitMs: Long = GarminNotifications.ANSWER_WAIT_MS,
) {
    private val lock = Any()
    private val cobs = CobsStream()
    private val readyFuture = CompletableFuture<GarminDeviceInfo>()
    @Volatile private var batteryFuture = CompletableFuture<Int>()
    private var batteryRequestId: Int? = null
    private var nextRequestId = 0

    /** The charge the watch last reported, 0..100, or null until it answers. */
    @Volatile
    var batteryPercent: Int? = null
        private set

    private var gfdiHandle: Int? = null
    private var mlr: MlrChannel? = null
    private var triedPlain = false
    private var info: GarminDeviceInfo? = null
    private var initialized = false
    private var assumeTimer: Scheduler.Cancellable? = null
    private var heartbeat: Scheduler.Cancellable? = null
    @Volatile private var closed = false

    /** When anything last came in from the watch (any packet at all), in epoch milliseconds; 0 before the first. */
    @Volatile private var lastHeardMs = 0L

    /** How many whole GFDI messages came in from the watch: the link can carry acknowledgements while this stands still. */
    private val messagesIn = java.util.concurrent.atomic.AtomicLong()

    /** Seconds since the watch last sent anything, or null when it never has: a link that went quiet shows here. */
    fun heardSecondsAgo(): Long? = heardMillisAgo()?.let { it / 1000 }

    private fun heardMillisAgo(): Long? = lastHeardMs.takeIf { it > 0 }?.let { (now().toEpochMilli() - it).coerceAtLeast(0) }
    private var transfer: Transfer? = null

    /**
     * The notification conversation, when this session was made with [NotificationOptions]: hand it notifications with
     * [GarminNotifications.post] and [GarminNotifications.remove].
     */
    val notifier: GarminNotifications? = notifications?.let {
        GarminNotifications(
            send = ::send, scheduler = scheduler, zone = zone, now = now, dismissLabel = it.dismissLabel,
            appName = it.appName, log = log, heardAgo = ::heardSecondsAgo, answerWaitMs = answerWaitMs,
            watchMessages = { messagesIn.get() },
            // the watch took the announcement on the link but its message layer says nothing: a new connection gets it going
            onStalled = { fail(GarminBleException(GarminBleException.Reason.DISCONNECTED, "The watch stopped answering; connecting again")) },
        )
    }

    /**
     * Called once when the session ends, for whatever reason: [GarminBleException.Reason.CLOSED] when we closed it,
     * anything else when the link broke. A connection kept open for notifications uses it to know it must reconnect.
     * It may be called from a Bluetooth thread.
     */
    @Volatile var onEnded: (GarminBleException) -> Unit = {}

    /** True once the session has ended, whatever the reason. */
    val isEnded: Boolean get() = closed

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

    /** The charge the watch reports, or null when it did not answer within [timeoutMs]; never throws. */
    fun awaitBattery(timeoutMs: Long = 2_000): Int? = try {
        batteryFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: Exception) {
        batteryPercent
    }

    fun onNotification(packet: ByteArray) {
        if (closed || packet.isEmpty()) return
        lastHeardMs = now().toEpochMilli()
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
        assumeTimer?.cancel()
        heartbeat?.cancel()
        notifier?.close()
        readyFuture.completeExceptionally(error)
        pending?.result?.completeExceptionally(error)
        runCatching { onEnded(error) } // a listener that throws must not break the shutdown
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
                        MlrChannel(
                            m.handle, maxWrite, pipe::write, ::onGfdiBytes, scheduler, log = log,
                            onDead = { fail(GarminBleException(GarminBleException.Reason.DISCONNECTED, "The watch stopped acknowledging what we send")) },
                        )
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
            messagesIn.incrementAndGet()
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
        // what the phone starts (answers and the bulk of file and notification data are left out, the notifier logs its own)
        if (message.type != GfdiType.RESPONSE && message.type != GfdiType.NOTIFICATION_DATA && message.type != GfdiType.FILE_TRANSFER_DATA) {
            log("to the watch: ${GfdiType.describe(message.type)}${channel?.let { ", link: ${it.state()}" }.orEmpty()}")
        }
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
        if (message.type == GfdiType.RESPONSE) {
            // the watch confirming our protobuf answers is not worth a line each (every 30 s); onResponse logs a refusal
            if (!isProtobufConfirmation(message)) log("watch answered ${answerText(message)}")
        } else if (message.type != GfdiType.FILE_TRANSFER_DATA && message.type != GfdiType.PROTOBUF_REQUEST) {
            // a protobuf question gets a line of its own that names it, below
            log("watch sent ${GfdiType.describe(message.type)}")
        }
        when (message.type) {
            GfdiType.DEVICE_INFORMATION -> {
                val device = GarminDeviceInfo.parse(message)
                synchronized(lock) { info = device }
                val known = device.recognised?.let { "${it.displayName} (${it.recognisedBy.label})" } ?: "no known model"
                log("watch: ${device.deviceName} ${device.deviceModel}, software ${device.softwareVersionText}, protocol ${device.protocolVersion}, recognised as $known")
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
            // the watch's answer to a notification chunk belongs to the notification conversation; any other answer is ours
            GfdiType.RESPONSE -> if (notifier?.handle(message) != true) onResponse(message.reader())
            GfdiType.NOTIFICATION_SUBSCRIPTION -> notifier?.handle(message) ?: declineNotifications(message)
            GfdiType.NOTIFICATION_CONTROL -> if (notifier?.handle(message) != true) log("ignored a notification message; notifications are off")
            GfdiType.FILE_TRANSFER_DATA -> onFileData(message.reader())
            GfdiType.PROTOBUF_REQUEST, GfdiType.PROTOBUF_RESPONSE -> onProtobuf(message)
            // the watch asks which music commands we can do: none, but it gets its answer (Gadgetbridge answers with its list)
            GfdiType.MUSIC_CONTROL_CAPABILITIES -> send(GfdiMessage.response(message.type) { byte(0) })
            GfdiType.BATTERY_STATUS, GfdiType.FILE_AVAILABLE, GfdiType.SYNCHRONIZATION, GfdiType.WEATHER_REQUEST,
            GfdiType.FIND_MY_PHONE_REQUEST, GfdiType.FIND_MY_PHONE_CANCEL,
            -> send(GfdiMessage.response(message.type))
            // every message gets an answer, as Gadgetbridge does: one we do not handle gets "unsupported", never silence
            else -> send(GfdiMessage.response(message.type, GfdiStatus.UNSUPPORTED))
        }
    }

    /**
     * We hardly speak protobuf (location, calendars, Connect IQ web requests and the like). A plain ACK made the watch wait
     * for an answer that never came and ask again a minute later; on a fenix 8 it stopped answering anything after the second
     * round. So a whole question this app can answer with "nothing" ([GarminProtobuf.replyTo]: the calendar, Connect IQ web
     * requests) gets a plain ACK and then that answer, as Gadgetbridge does. Every other chunk is answered as Gadgetbridge
     * answers a request it does not handle: kept while more is coming, "discarded, unknown request" on the last one. The log
     * names the service, never the content.
     */
    private fun onProtobuf(message: GfdiMessage) {
        val chunk = GarminProtobuf.parse(message)
        val asked = synchronized(lock) { batteryRequestId }
        if (message.type == GfdiType.PROTOBUF_RESPONSE && asked != null && chunk.requestId == asked) {
            // our own answer: confirm it as kept, and take the level when this is the last piece
            val level = if (chunk.last) GarminProtobuf.batteryLevel(chunk.data) else null
            send(GarminProtobuf.accepted(message.type, chunk))
            if (level != null) {
                batteryPercent = level
                batteryFuture.complete(level)
                log("watch battery: $level%")
            } else if (chunk.last) {
                log("watch battery: no level in the answer")
            }
            return
        }
        val reply = if (message.type == GfdiType.PROTOBUF_REQUEST && chunk.offset == 0 && chunk.last) GarminProtobuf.replyTo(chunk.data) else null
        if (reply != null) {
            log("${GarminProtobuf.LOG_PREFIX}${GarminProtobuf.describe(chunk.data)}; ${reply.said}")
            send(GfdiMessage.response(message.type))
            send(GarminProtobuf.response(chunk.requestId, reply.smart))
            return
        }
        if (chunk.offset == 0) {
            val what = GarminProtobuf.describe(chunk.data)
            val more = if (chunk.last) "" else " (in parts)"
            log("${GarminProtobuf.LOG_PREFIX}$what$more; not handled, the watch is told so")
        }
        send(GarminProtobuf.answer(message.type, chunk))
    }

    /** True for the watch's ACK to one of our protobuf answers. */
    private fun isProtobufConfirmation(message: GfdiMessage): Boolean = try {
        val r = message.reader()
        r.short() == GfdiType.PROTOBUF_RESPONSE && r.byte() == GfdiStatus.ACK
    } catch (e: GfdiFormatException) {
        false
    }

    /** What a RESPONSE from the watch says, for the log: which message it answers and with what status. */
    private fun answerText(message: GfdiMessage): String = try {
        val r = message.reader()
        val original = r.short()
        val status = r.byte()
        "${GfdiType.describe(original)}: ${GfdiStatus.name(status)}"
    } catch (e: GfdiFormatException) {
        "(unreadable answer)"
    }

    /** A session that only syncs files answers the watch's "may I send you notifications" with no, as Gadgetbridge does when they are off. */
    private fun declineNotifications(message: GfdiMessage) {
        val requested = try {
            GfdiNotifications.parseSubscription(message)
        } catch (e: GfdiFormatException) {
            log("unreadable notification subscription: ${e.message}")
            return
        }
        send(GfdiNotifications.subscriptionReply(requested, allowed = false))
    }

    /** Asks for the charge once; the answer arrives as PROTOBUF_RESPONSE and is confirmed as kept. */
    private fun askBattery() {
        val id = synchronized(lock) {
            if (batteryRequestId != null) return
            val next = (nextRequestId + 1) and 0xFFFF
            nextRequestId = next
            batteryRequestId = next
            next
        }
        send(GarminProtobuf.batteryRequest(id))
    }

    /**
     * Asks the watch for its charge once more. A Garmin recalculates its gauge just after the handshake and the first
     * answer can be a percent lower than what the watch itself shows a moment later; the sync is a good moment for a
     * second, fresher answer. The earlier value stays until the new one arrives.
     */
    fun refreshBattery() {
        val id = synchronized(lock) {
            if (closed) return
            val next = (nextRequestId + 1) and 0xFFFF
            nextRequestId = next
            batteryRequestId = next
            batteryFuture = CompletableFuture()
            next
        }
        send(GarminProtobuf.batteryRequest(id))
    }

    private fun completeInitialization() {
        val device = synchronized(lock) {
            if (initialized) return
            initialized = true
            info
        }
        send(GfdiOut.supportedFileTypesRequest())
        send(GfdiOut.systemEvent(SystemEvent.SYNC_READY))
        askBattery()
        if (firstConnect) {
            send(GfdiOut.systemEvent(SystemEvent.PAIR_COMPLETE))
            send(GfdiOut.systemEvent(SystemEvent.SYNC_COMPLETE))
            send(GfdiOut.systemEvent(SystemEvent.SETUP_WIZARD_COMPLETE))
        }
        notifier?.let { n ->
            // the watch normally asks for notifications by itself soon after the introductions; if it does not, send anyway
            assumeTimer = scheduler.schedule(askGraceMs) { n.assumeSubscribed() }
            scheduleHeartbeat()
        }
        readyFuture.complete(device ?: GarminDeviceInfo(0, 0, 0, 0, 0, "", "Garmin", ""))
    }

    /**
     * A link can die without Android saying so, and nothing notices until something is sent (a notification, which is then
     * lost). So a connection kept open for notifications asks the watch something small when it has been quiet for most
     * of [heartbeatMs]: the answer proves the link lives, and no answer makes the channel give up and the connection
     * start again, before the next notification needs it.
     */
    private fun scheduleHeartbeat() {
        if (closed) return
        heartbeat = scheduler.schedule(heartbeatMs) { beat() }
    }

    private fun beat() {
        if (closed) return
        val quiet = heardMillisAgo()
        if (quiet == null || quiet >= heartbeatMs * 3 / 4) {
            log("heartbeat: the watch has been quiet; asking it something small")
            send(GfdiOut.supportedFileTypesRequest())
        }
        scheduleHeartbeat()
    }

    companion object {
        const val ASK_GRACE_MS = 10_000L
        const val HEARTBEAT_MS = 60_000L
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
            GfdiType.SET_FILE_FLAG -> flagAck?.complete(status)
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

    @Volatile private var flagAck: CompletableFuture<Int>? = null

    /**
     * Marks a file as synced on the watch, as Garmin Connect does; the watch may then delete it to make room. Waits for
     * the watch to confirm, so the link is not closed while marks are still on their way. One at a time.
     */
    fun archive(index: Int, timeoutMs: Long = 5_000) {
        if (!ready) throw GarminBleException(GarminBleException.Reason.NOT_READY, "The watch is not ready")
        val ack = CompletableFuture<Int>()
        synchronized(lock) {
            if (closed) throw GarminBleException(GarminBleException.Reason.CLOSED, "The connection is closed")
            flagAck = ack
        }
        try {
            send(GfdiOut.archive(index))
            val status = try {
                ack.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                throw GarminBleException(GarminBleException.Reason.TIMEOUT, "The watch did not confirm file $index")
            } catch (e: ExecutionException) {
                throw GarminBleException(GarminBleException.Reason.BROKEN, e.cause?.message ?: "archive failed")
            }
            if (status != GfdiStatus.ACK) throw GarminBleException(GarminBleException.Reason.REFUSED, "The watch refused to mark file $index (status $status)")
        } finally {
            flagAck = null
        }
    }

    /** Tells the watch this sync is over (its sync icon stops). */
    fun finishSync() = send(GfdiOut.systemEvent(SystemEvent.SYNC_COMPLETE))
}
