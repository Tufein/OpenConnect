/*
 * OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * The message layouts and the conversation follow Gadgetbridge's Garmin support (NotificationsHandler and the
 * NotificationUpdateMessage, NotificationControlMessage, NotificationDataMessage and status messages),
 * https://codeberg.org/Freeyourgadget/Gadgetbridge
 * Copyright (C) 2024-2026 Daniele Gobbetti, José Rebelo, Thomas Kuehne and other Gadgetbridge contributors.
 */
package nl.tufein.openconnect.core.watch.ble

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The kind of thing a notification is. The numbers are the ones Apple's notification protocol uses; Garmin kept them. */
enum class WatchNotificationCategory {
    OTHER, INCOMING_CALL, MISSED_CALL, VOICEMAIL, SOCIAL, SCHEDULE, EMAIL, NEWS, HEALTH_AND_FITNESS, BUSINESS_AND_FINANCE,
    LOCATION, ENTERTAINMENT, SMS,
}

/** What the phone wants the watch to show. [id] stays the same while the app updates the same notification. */
data class WatchNotification(
    val id: Int,
    /** The package name of the app that posted it. */
    val appId: String,
    val appName: String,
    val title: String,
    val body: String,
    /** Milliseconds since 1970; 0 means "now". */
    val whenMillis: Long,
    val category: WatchNotificationCategory = WatchNotificationCategory.OTHER,
    val subtitle: String = "",
    /** True when the person can dismiss it from the watch: the phone then swipes it away. */
    val canDismiss: Boolean = true,
)

enum class NotificationUpdate { ADD, MODIFY, REMOVE }

/** What the watch can ask for about a notification. */
enum class NotificationAttribute(val code: Int, val hasLength: Boolean = false, val hasExtra: Boolean = false) {
    APP_IDENTIFIER(0),
    TITLE(1, hasLength = true),
    SUBTITLE(2, hasLength = true),
    MESSAGE(3, hasLength = true),
    MESSAGE_SIZE(4),
    DATE(5),
    NEGATIVE_ACTION_LABEL(7),
    ACTIONS(127, hasExtra = true),
    ATTACHMENTS(128),
    ;

    companion object {
        fun of(code: Int): NotificationAttribute? = entries.firstOrNull { it.code == code }
    }
}

data class AttributeRequest(val attribute: NotificationAttribute, val maxLength: Int)

/** A message of type [GfdiType.NOTIFICATION_CONTROL]: the watch asking for details or doing something with a notification. */
sealed interface NotificationControl {
    data class GetAttributes(val id: Int, val attributes: List<AttributeRequest>) : NotificationControl

    /** Only attribute 0, the app's name, is known. */
    data class GetAppAttributes(val appId: String, val attributes: List<Int>) : NotificationControl

    data class PerformAction(val id: Int, val action: Int, val text: String?) : NotificationControl

    /** The old accept-or-refuse form. */
    data class LegacyAction(val id: Int, val accept: Boolean) : NotificationControl

    data class Malformed(val why: String) : NotificationControl
}

/** Building and reading the bytes of the notification messages. */
object GfdiNotifications {
    /** Notification flags: FOREGROUND (bit 1) and ACTION_DECLINE (bit 4), which is what Gadgetbridge sends for everything. */
    private const val FLAGS = 0x02 or 0x10

    /** Phone flags: bit 1 means the notification has actions the watch may offer. */
    private const val PHONE_NEW_ACTIONS = 0x02

    const val COMMAND_GET_NOTIFICATION_ATTRIBUTES = 0
    const val COMMAND_GET_APP_ATTRIBUTES = 1
    const val COMMAND_LEGACY_ACTION = 2
    const val COMMAND_PERFORM_ACTION = 128

    /** The action that dismisses a notification (Gadgetbridge's DISMISS_NOTIFICATION). */
    const val ACTION_DISMISS = 98

    fun update(type: NotificationUpdate, category: WatchNotificationCategory, count: Int, id: Int, hasActions: Boolean): GfdiMessage =
        GfdiMessage.build(GfdiType.NOTIFICATION_UPDATE) {
            byte(type.ordinal)
            byte(FLAGS)
            byte(category.ordinal)
            byte(count.coerceIn(0, 255))
            int(id)
            byte(if (hasActions) PHONE_NEW_ACTIONS else 0)
        }

    /** One chunk of an answer: the size of the whole answer, the checksum so far, and where this chunk starts. */
    fun data(total: Int, crc: Int, offset: Int, chunk: ByteArray): GfdiMessage = GfdiMessage.build(GfdiType.NOTIFICATION_DATA) {
        short(total)
        short(crc)
        short(offset)
        bytes(chunk)
    }

    /** The answer to a subscription: whether the phone will send notifications, and what the watch asked. */
    fun subscriptionReply(requested: Boolean, allowed: Boolean): GfdiMessage =
        GfdiMessage.response(GfdiType.NOTIFICATION_SUBSCRIPTION) {
            byte(if (allowed) 0 else 1) // ENABLED or DISABLED
            byte(if (requested) 1 else 0)
            byte(0)
        }

    /** The acknowledgement of a control message: ok, or the "unknown command" error (160). */
    fun controlAck(ok: Boolean = true): GfdiMessage = GfdiMessage.response(GfdiType.NOTIFICATION_CONTROL) {
        byte(if (ok) 0 else 1) // chunk status OK or ERROR
        byte(if (ok) 0 else 160)
    }

    /** True to switch notifications on, false to switch them off. */
    fun parseSubscription(message: GfdiMessage): Boolean = message.reader().byte() == 1

    /** Reads a control message; never throws, a message that cannot be read comes back as [NotificationControl.Malformed]. */
    fun parseControl(message: GfdiMessage): NotificationControl {
        return try {
            readControl(message)
        } catch (e: GfdiFormatException) {
            NotificationControl.Malformed(e.message ?: "unreadable")
        }
    }

    private fun readControl(message: GfdiMessage): NotificationControl {
        val r = message.reader()
        return when (val command = r.byte()) {
            COMMAND_GET_NOTIFICATION_ATTRIBUTES -> {
                val id = r.int()
                val attributes = ArrayList<AttributeRequest>()
                while (r.remaining > 0) {
                    val code = r.byte()
                    val attribute = NotificationAttribute.of(code)
                        ?: return NotificationControl.Malformed("unknown attribute $code")
                    var max = 0
                    if (attribute.hasLength) {
                        max = r.short()
                    } else if (attribute.hasExtra) {
                        max = r.short()
                        r.byte() // Garmin sends one more byte here; Gadgetbridge notes that nobody knows what it is
                    }
                    attributes += AttributeRequest(attribute, max)
                }
                NotificationControl.GetAttributes(id, attributes)
            }
            COMMAND_GET_APP_ATTRIBUTES -> {
                val appId = r.nullTerminatedString()
                val attributes = ArrayList<Int>()
                while (r.remaining > 0) {
                    val code = r.byte()
                    if (code != 0) return NotificationControl.Malformed("unknown app attribute $code")
                    attributes += code
                }
                NotificationControl.GetAppAttributes(appId, attributes)
            }
            COMMAND_LEGACY_ACTION -> {
                val id = r.int()
                NotificationControl.LegacyAction(id, accept = r.byte() == 0)
            }
            COMMAND_PERFORM_ACTION -> {
                val id = r.int()
                val action = r.byte()
                // recent firmware sends no text with an action that has none
                val text = if (r.remaining > 0) r.nullTerminatedString() else null
                NotificationControl.PerformAction(id, action, text)
            }
            else -> NotificationControl.Malformed("unknown command $command")
        }
    }
}

/** The value of one [NotificationAttribute] of a notification, as bytes. */
object NotificationValues {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private const val ICON_LEFT = 4 // bit 2 of the icon position set: the left button of the watch

    fun of(
        attribute: NotificationAttribute,
        maxLength: Int,
        n: WatchNotification,
        zone: ZoneId,
        dismissLabel: String,
        now: Instant = Instant.now(),
    ): ByteArray = when (attribute) {
        NotificationAttribute.ACTIONS -> actions(n, dismissLabel)
        NotificationAttribute.TITLE -> cut(n.title, maxLength).toByteArray()
        NotificationAttribute.SUBTITLE -> cut(n.subtitle, maxLength).toByteArray()
        NotificationAttribute.MESSAGE -> cut(n.body, maxLength).toByteArray()
        NotificationAttribute.APP_IDENTIFIER -> cut(n.appId, maxLength).toByteArray()
        NotificationAttribute.MESSAGE_SIZE -> n.body.length.toString().toByteArray()
        NotificationAttribute.DATE -> {
            val at = if (n.whenMillis == 0L) now else Instant.ofEpochMilli(n.whenMillis)
            at.atZone(zone).format(DATE).toByteArray()
        }
        // Gadgetbridge answers "1" here whatever the notification holds, and has been seen to work on many models; we
        // never send a picture, so the watch finds nothing when it asks for one.
        NotificationAttribute.ATTACHMENTS -> "1".toByteArray()
        NotificationAttribute.NEGATIVE_ACTION_LABEL -> ByteArray(0)
    }

    /** At most [max] characters (0 means all of them), and never half of a character that takes two. */
    fun cut(text: String, max: Int): String {
        if (max <= 0 || text.length <= max) return text
        var end = max
        if (Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end)
    }

    /**
     * The actions the watch may offer: a count, then for each action its code, where its icon goes and its label. Only
     * "dismiss" is offered; the watch's reply and call buttons need things this app does not do (yet).
     */
    private fun actions(n: WatchNotification, dismissLabel: String): ByteArray {
        if (!n.canDismiss) return ByteArray(4) // "none": four zeros, as Gadgetbridge sends it
        val label = dismissLabel.toByteArray(Charsets.UTF_8)
        require(label.size <= 255) { "label too long" }
        val w = GfdiWriter()
        w.byte(1)
        w.byte(GfdiNotifications.ACTION_DISMISS)
        w.byte(ICON_LEFT)
        w.byte(label.size)
        w.bytes(label)
        return w.toByteArray()
    }
}

/** One answer being sent in chunks of at most [chunkSize] bytes, with the checksum running on from chunk to chunk. */
class NotificationUpload(private val data: ByteArray, private val chunkSize: Int = 300) {
    private var offset = 0
    private var crc = 0
    private var started = false

    val finished: Boolean get() = started && offset >= data.size

    /** The next chunk (the only one, for an empty answer). */
    fun next(): GfdiMessage {
        val end = minOf(data.size, offset + chunkSize)
        val chunk = data.copyOfRange(offset, end)
        crc = GarminCrc.compute(chunk, seed = crc)
        val message = GfdiNotifications.data(data.size, crc, offset, chunk)
        offset = end
        started = true
        return message
    }
}

/** What happened to a notification given to [GarminNotifications.post]. */
enum class PostResult {
    /** The watch was told. */
    SENT,

    /** The watch has not asked for notifications yet; it is kept for up to a minute and sent when it does. */
    WAITING,

    /** The watch, or the settings on this side, have notifications off. */
    OFF,
}

/** What the watch said about notifications: it has not asked yet, wants them, said no, or we send them without it asking. */
enum class WatchAsk { NOT_YET, ON, ASSUMED, OFF }

/**
 * How the conversation with the watch stands, for the card and the log. Counts since the connection opened.
 * [announced] is what the watch was told about (message 5033), [asked] how many times it wanted the details, [answered]
 * how many answers it confirmed, [failed] how many it did not.
 */
data class NotifyStats(
    val watch: WatchAsk = WatchAsk.NOT_YET,
    /** Held back until the watch asks (at most [GarminNotifications.MAX_WAITING], for up to a minute). */
    val waiting: Int = 0,
    val announced: Int = 0,
    val asked: Int = 0,
    val answered: Int = 0,
    val failed: Int = 0,
    val dismissedOnWatch: Int = 0,
    /** How many announcements the watch confirmed with its own answer (a RESPONSE to message 5033). */
    val acknowledged: Int = 0,
    /** How many announcements got no answer from the watch within [GarminNotifications.ANSWER_WAIT_MS]. */
    val unanswered: Int = 0,
)

/**
 * The phone's side of Garmin's notification conversation, for one connection:
 * 1. the watch subscribes (message 5036) and the phone says yes;
 * 2. the phone announces a notification (5033: add, modify or remove, with an id and a category);
 * 3. the watch asks for what it wants to show (5034) and the phone acknowledges, then answers in chunks (5035), each
 *    of which the watch acknowledges before the next one goes;
 * 4. the person may dismiss it on the watch (5034, action 98), which [onDismissedOnWatch] hands to the phone.
 *
 * It has no Android types. [send] puts a message on the link; calls may come from any thread.
 */
class GarminNotifications(
    private val send: (GfdiMessage) -> Unit,
    private val scheduler: Scheduler,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = { Instant.now() },
    /** False when the phone side is switched off: the watch is told "disabled" and nothing is sent. */
    private val allowed: Boolean = true,
    private val dismissLabel: String = "Dismiss",
    /** The name of an app, for the watch's "what is this app called" question. */
    private val appName: (String) -> String? = { null },
    private val log: (String) -> Unit = {},
    /** Seconds since the watch last sent anything (null: never), for the log line that says whether the link is alive. */
    private val heardAgo: () -> Long? = { null },
) {
    companion object {
        /** How long the watch gets to answer an announcement before the log says it did not. */
        const val ANSWER_WAIT_MS = 5_000L

        /** How many notifications the watch is told about before the oldest is pushed off (Gadgetbridge: 64). */
        const val MAX_KNOWN = 64
        const val MAX_WAITING = 8
        const val WAIT_MS = 60_000L
        const val CHUNK_TIMEOUT_MS = 15_000L
    }

    private enum class Asked { NOT_YET, ON, ASSUMED, OFF }

    private class Waiting(val notification: WatchNotification, val expiry: Scheduler.Cancellable)

    private val lock = Any()
    private var asked = Asked.NOT_YET
    private var closed = false
    private val known = LinkedHashMap<Int, WatchNotification>()
    private val waiting = ArrayList<Waiting>()
    private val queue = ArrayDeque<ByteArray>()
    private var current: NotificationUpload? = null
    private var timeout: Scheduler.Cancellable? = null

    private var announced = 0
    private var detailsAsked = 0
    private var answered = 0
    private var failed = 0
    private var dismissedOnWatch = 0
    private var acknowledged = 0
    private var unanswered = 0

    /** Called when the person dismisses notification [id] on the watch. Runs on the Bluetooth thread. */
    @Volatile var onDismissedOnWatch: (Int) -> Unit = {}

    /** Called after anything changed in [stats]: the watch asked, something was announced, an answer was confirmed. */
    @Volatile var onChange: (NotifyStats) -> Unit = {}

    /** True when the watch said it wants notifications. */
    val subscribed: Boolean get() = synchronized(lock) { asked == Asked.ON && allowed }

    /** True when notifications go out: the watch asked, or we gave up waiting for it to ask. */
    val sending: Boolean get() = synchronized(lock) { (asked == Asked.ON || asked == Asked.ASSUMED) && allowed && !closed }

    val stats: NotifyStats
        get() = synchronized(lock) {
            NotifyStats(
                watch = when (asked) {
                    Asked.NOT_YET -> WatchAsk.NOT_YET
                    Asked.ON -> WatchAsk.ON
                    Asked.ASSUMED -> WatchAsk.ASSUMED
                    Asked.OFF -> WatchAsk.OFF
                },
                waiting = waiting.size,
                announced = announced,
                asked = detailsAsked,
                answered = answered,
                failed = failed,
                dismissedOnWatch = dismissedOnWatch,
                acknowledged = acknowledged,
                unanswered = unanswered,
            )
        }

    private fun changed() {
        val now = stats
        runCatching { onChange(now) } // a listener that throws must not break the conversation
    }

    /**
     * The watch has not said whether it wants notifications. Gadgetbridge waits for it to ask, but not every model asks
     * at once (or at all), so after a grace period the phone sends them anyway: a watch that does not want them ignores
     * them, and a later "no" from the watch still switches them off.
     */
    fun assumeSubscribed() {
        synchronized(lock) {
            if (closed || !allowed || asked != Asked.NOT_YET) return
            asked = Asked.ASSUMED
            log("the watch has not asked for notifications yet; sending them anyway")
            flushWaiting()
        }
        changed()
    }

    private fun flushWaiting() {
        val pending = waiting.toList()
        waiting.clear()
        for (w in pending) {
            w.expiry.cancel()
            announce(w.notification)
        }
    }

    // ------------------------------------------------------------------------------ phone side

    fun post(notification: WatchNotification): PostResult {
        val result = synchronized(lock) {
            when {
                closed || !allowed || asked == Asked.OFF -> PostResult.OFF
                asked == Asked.NOT_YET -> {
                    wait(notification)
                    log("notification ${notification.id} is waiting: the watch has not asked for notifications")
                    PostResult.WAITING
                }
                else -> {
                    announce(notification)
                    PostResult.SENT
                }
            }
        }
        changed()
        return result
    }

    fun remove(id: Int) {
        synchronized(lock) {
            waiting.removeAll { w -> (w.notification.id == id).also { if (it) w.expiry.cancel() } }
            val gone = known.remove(id) ?: return
            if (asked == Asked.ON || asked == Asked.ASSUMED) {
                send(GfdiNotifications.update(NotificationUpdate.REMOVE, gone.category, countOf(gone.category), id, hasActions = false))
            }
        }
        changed()
    }

    fun close() {
        synchronized(lock) {
            closed = true
            timeout?.cancel()
            timeout = null
            current = null
            queue.clear()
            waiting.forEach { it.expiry.cancel() }
            waiting.clear()
            known.clear()
        }
    }

    private fun wait(notification: WatchNotification) {
        waiting.removeAll { w -> (w.notification.id == notification.id).also { if (it) w.expiry.cancel() } }
        val entry = Waiting(notification, scheduler.schedule(WAIT_MS) { synchronized(lock) { waiting.removeAll { it.notification === notification } } })
        waiting += entry
        while (waiting.size > MAX_WAITING) waiting.removeAt(0).expiry.cancel()
    }

    private fun announce(n: WatchNotification) {
        val type = if (known.remove(n.id) != null) NotificationUpdate.MODIFY else NotificationUpdate.ADD
        known[n.id] = n
        val message = GfdiNotifications.update(type, n.category, countOf(n.category), n.id, hasActions = n.canDismiss)
        send(message)
        announced++
        val mark = announced
        // the bytes of an announcement hold only the kind, a flag, the category, a count and the id: never any text
        val heard = heardAgo()?.let { ", last heard from the watch $it s ago" }.orEmpty()
        log("announced notification ${n.id} to the watch ($type, ${n.category}); bytes ${hex(message.encode())}$heard")
        scheduler.schedule(ANSWER_WAIT_MS) { checkAnswered(mark) }
        while (known.size > MAX_KNOWN) {
            val oldest = known.entries.first().value
            known.remove(oldest.id)
            send(GfdiNotifications.update(NotificationUpdate.REMOVE, oldest.category, countOf(oldest.category), oldest.id, hasActions = false))
        }
    }

    private fun countOf(category: WatchNotificationCategory) = known.values.count { it.category == category }

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }

    /** Announcement number [mark] was made [ANSWER_WAIT_MS] ago: say so if the watch has answered nothing since. */
    private fun checkAnswered(mark: Int) {
        synchronized(lock) {
            if (closed) return
            // an answer to this one or a later announcement, or the watch asking for its details, counts as heard
            if (acknowledged + detailsAsked >= mark) return
            unanswered++
            val heard = heardAgo()?.let { "last heard from the watch $it s ago" } ?: "never heard from the watch"
            log("the watch has not answered announcement $mark within ${ANSWER_WAIT_MS / 1000} s ($heard)")
        }
        changed()
    }

    // ------------------------------------------------------------------------------ watch side

    /** Reads a message from the watch; true when it was about notifications (so the caller has nothing more to do). */
    fun handle(message: GfdiMessage): Boolean {
        val about = when (message.type) {
            GfdiType.NOTIFICATION_SUBSCRIPTION -> {
                onSubscription(message)
                true
            }
            GfdiType.NOTIFICATION_CONTROL -> {
                onControl(message)
                true
            }
            GfdiType.RESPONSE -> onResponse(message)
            else -> false
        }
        if (about) changed()
        return about
    }

    private fun onSubscription(message: GfdiMessage) {
        val enable = try {
            GfdiNotifications.parseSubscription(message)
        } catch (e: GfdiFormatException) {
            log("unreadable notification subscription: ${e.message}")
            return
        }
        synchronized(lock) {
            if (closed) return
            asked = if (enable) Asked.ON else Asked.OFF
            send(GfdiNotifications.subscriptionReply(enable, allowed))
            val bytes = message.payload.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
            log("the watch switched notifications ${if (enable) "on" else "off"} (its message: $bytes)${if (allowed) "" else ", but they are off on the phone"}")
            if (enable && allowed) {
                flushWaiting()
            } else {
                known.clear()
                queue.clear()
                current = null
                timeout?.cancel()
                timeout = null
            }
        }
    }

    private fun onControl(message: GfdiMessage) {
        var dismissed: Int? = null
        synchronized(lock) {
            if (closed) return
            when (val control = GfdiNotifications.parseControl(message)) {
                is NotificationControl.Malformed -> {
                    log("unreadable notification request: ${control.why}")
                    send(GfdiNotifications.controlAck(ok = false))
                }
                is NotificationControl.GetAttributes -> {
                    send(GfdiNotifications.controlAck())
                    detailsAsked++
                    val notification = known[control.id]
                    if (notification == null) {
                        log("the watch asked about notification ${control.id}, which is gone")
                    } else {
                        log("the watch asked for the details of notification ${control.id} (${control.attributes.joinToString { it.attribute.name }})")
                        enqueue(answerFor(notification, control))
                    }
                }
                is NotificationControl.GetAppAttributes -> {
                    send(GfdiNotifications.controlAck())
                    log("the watch asked what ${control.appId} is called")
                    enqueue(appAnswer(control))
                }
                is NotificationControl.PerformAction -> {
                    send(GfdiNotifications.controlAck())
                    if (control.id in known && control.action == GfdiNotifications.ACTION_DISMISS) {
                        dismissed = control.id
                        dismissedOnWatch++
                        log("the person dismissed notification ${control.id} on the watch")
                    } else {
                        log("the watch did action ${control.action} on notification ${control.id}; not supported")
                    }
                }
                is NotificationControl.LegacyAction -> {
                    send(GfdiNotifications.controlAck())
                    log("the watch used the old action form on notification ${control.id}; not supported")
                }
            }
        }
        dismissed?.let { onDismissedOnWatch(it) }
    }

    /** `[0][id]` and then each attribute as `[code][length][bytes]`, in the order asked but with the size last. */
    private fun answerFor(n: WatchNotification, request: NotificationControl.GetAttributes): ByteArray {
        val w = GfdiWriter()
        w.byte(GfdiNotifications.COMMAND_GET_NOTIFICATION_ATTRIBUTES)
        w.int(request.id)
        val ordered = request.attributes.filter { it.attribute != NotificationAttribute.MESSAGE_SIZE } +
            request.attributes.filter { it.attribute == NotificationAttribute.MESSAGE_SIZE }
        for (r in ordered) {
            val value = NotificationValues.of(r.attribute, r.maxLength, n, zone(), dismissLabel, now())
            w.byte(r.attribute.code)
            w.short(value.size)
            w.bytes(value)
        }
        return w.toByteArray()
    }

    /** `[1][package][0]` and the app's name as `[0][length][name]`. */
    private fun appAnswer(request: NotificationControl.GetAppAttributes): ByteArray {
        val w = GfdiWriter()
        w.byte(GfdiNotifications.COMMAND_GET_APP_ATTRIBUTES)
        w.bytes(request.appId.toByteArray(Charsets.UTF_8))
        w.byte(0)
        for (code in request.attributes) {
            val name = (appName(request.appId)?.takeIf { it.isNotBlank() } ?: request.appId.ifBlank { "Unknown" }).toByteArray(Charsets.UTF_8)
            w.byte(code)
            w.short(name.size)
            w.bytes(name)
        }
        return w.toByteArray()
    }

    // ------------------------------------------------------------------------------ chunks

    private fun enqueue(answer: ByteArray) {
        queue.addLast(answer)
        if (current == null) startNext()
    }

    private fun startNext() {
        val next = queue.removeFirstOrNull() ?: return
        val upload = NotificationUpload(next)
        current = upload
        sendChunk(upload)
    }

    private fun sendChunk(upload: NotificationUpload) {
        send(upload.next())
        timeout?.cancel()
        timeout = scheduler.schedule(CHUNK_TIMEOUT_MS) {
            synchronized(lock) {
                if (current === upload) {
                    log("the watch did not confirm a notification chunk; giving up on it")
                    failed++
                    current = null
                    startNext()
                }
            }
            changed()
        }
    }

    /** The watch's answer to a chunk: RESPONSE to 5035, a status, and how the transfer is going. */
    private fun onResponse(message: GfdiMessage): Boolean {
        val r = message.reader()
        val original = try {
            r.short()
        } catch (e: GfdiFormatException) {
            return false
        }
        if (original == GfdiType.NOTIFICATION_UPDATE) {
            // the watch confirms it received an announcement; without this the link may be dead or the message dropped
            val answer = try {
                r.byte()
            } catch (e: GfdiFormatException) {
                return true
            }
            synchronized(lock) {
                acknowledged++
                log("the watch confirmed an announcement: ${GfdiStatus.name(answer)}")
            }
            return true
        }
        if (original != GfdiType.NOTIFICATION_DATA) return false
        val status = try {
            r.byte()
        } catch (e: GfdiFormatException) {
            return true
        }
        val transfer = if (r.remaining > 0) r.byte() else -1
        synchronized(lock) {
            val upload = current ?: return true
            timeout?.cancel()
            timeout = null
            when {
                status != GfdiStatus.ACK || transfer != 0 -> {
                    log("the watch answered $status/$transfer to a notification chunk; stopping that answer")
                    failed++
                    current = null
                    startNext()
                }
                upload.finished -> {
                    answered++
                    current = null
                    startNext()
                }
                else -> sendChunk(upload)
            }
        }
        return true
    }
}
