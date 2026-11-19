// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The phone's side of the notification conversation, with a recording pipe and a clock moved by hand. */
class GarminNotificationsTest {
    private val sent = ArrayList<GfdiMessage>()
    private val scheduler = ManualScheduler()
    private val dismissed = ArrayList<Int>()
    private val apps = mapOf("com.whatsapp" to "WhatsApp")

    private fun handler(allowed: Boolean = true) = GarminNotifications(
        send = { sent += it },
        scheduler = scheduler,
        zone = { ZoneId.of("Europe/Amsterdam") },
        now = { Instant.parse("2026-11-19T13:30:05Z") },
        allowed = allowed,
        dismissLabel = "Dismiss",
        appName = { apps[it] },
    ).also { it.onDismissedOnWatch = { id -> dismissed += id } }

    private fun note(id: Int, category: WatchNotificationCategory = WatchNotificationCategory.SMS, body: String = "Zie je morgen?") = WatchNotification(
        id = id, appId = "com.whatsapp", appName = "WhatsApp", title = "Jan", body = body,
        whenMillis = Instant.parse("2026-11-19T13:30:05Z").toEpochMilli(), category = category,
    )

    private fun subscribe(h: GarminNotifications, on: Boolean = true) {
        h.handle(GfdiMessage(GfdiType.NOTIFICATION_SUBSCRIPTION, byteArrayOf(if (on) 1 else 0, 0)))
    }

    /** The messages the phone sent, minus the answer to the subscription. */
    private fun updates() = sent.filter { it.type == GfdiType.NOTIFICATION_UPDATE }

    private fun updateOf(m: GfdiMessage): Triple<NotificationUpdate, WatchNotificationCategory, Pair<Int, Int>> {
        val r = m.reader()
        val type = NotificationUpdate.entries[r.byte()]
        r.byte()
        val category = WatchNotificationCategory.entries[r.byte()]
        val count = r.byte()
        val id = r.int()
        return Triple(type, category, count to id)
    }

    private fun controlRequest(id: Int, vararg attributes: Pair<NotificationAttribute, Int>): GfdiMessage {
        val w = GfdiWriter()
        w.byte(0)
        w.int(id)
        for ((attr, max) in attributes) {
            w.byte(attr.code)
            if (attr.hasLength) w.short(max)
            if (attr.hasExtra) {
                w.short(max)
                w.byte(1)
            }
        }
        return GfdiMessage(GfdiType.NOTIFICATION_CONTROL, w.toByteArray())
    }

    /** The watch's answer to a chunk: RESPONSE to 5035, a status, and how the transfer is going (0 means send on). */
    private fun watchSays(status: Int = GfdiStatus.ACK, transfer: Int = 0) =
        GfdiMessage.response(GfdiType.NOTIFICATION_DATA, status) { byte(transfer) }

    private fun dataMessages() = sent.filter { it.type == GfdiType.NOTIFICATION_DATA }

    /** Puts the chunks back together into the one answer they carried. */
    private fun reassemble(chunks: List<GfdiMessage>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (m in chunks) {
            val r = m.reader()
            r.short(); r.short(); r.short()
            out.write(r.rest())
        }
        return out.toByteArray()
    }

    /** `[command][id][attribute, length, bytes]*` as the watch reads it. */
    private fun readAnswer(bytes: ByteArray): Pair<Int, List<Pair<Int, String>>> {
        val r = GfdiReader(bytes)
        assertEquals(0, r.byte())
        val id = r.int()
        val attributes = ArrayList<Pair<Int, String>>()
        while (r.remaining > 0) {
            val code = r.byte()
            val length = r.short()
            attributes += code to String(r.bytes(length), Charsets.UTF_8)
        }
        return id to attributes
    }

    // ------------------------------------------------------------------------ the subscription

    @Test
    fun `a notification that arrives before the watch asked for them waits and goes out once it does`() {
        val h = handler()
        assertEquals(PostResult.WAITING, h.post(note(1)))
        assertTrue(sent.isEmpty(), "nothing is sent to a watch that has not subscribed")
        subscribe(h)
        assertTrue(h.subscribed)
        // the answer to the subscription first, then what was waiting
        assertEquals(listOf(GfdiType.RESPONSE, GfdiType.NOTIFICATION_UPDATE), sent.map { it.type })
        assertEquals("ac1300000100", sent[0].payload.toHex())
        assertEquals(Triple(NotificationUpdate.ADD, WatchNotificationCategory.SMS, 1 to 1), updateOf(sent[1]))
    }

    @Test
    fun `a notification that waited too long is dropped instead of showing up late`() {
        val h = handler()
        assertEquals(PostResult.WAITING, h.post(note(1)))
        scheduler.advance(61_000)
        subscribe(h)
        assertTrue(updates().isEmpty())
    }

    @Test
    fun `at most eight notifications wait for the subscription, the oldest are dropped first`() {
        val h = handler()
        for (i in 1..10) h.post(note(i))
        subscribe(h)
        assertEquals((3..10).toList(), updates().map { updateOf(it).third.second })
    }

    @Test
    fun `a watch that switches notifications off gets none`() {
        val h = handler()
        subscribe(h)
        subscribe(h, on = false)
        assertFalse(h.subscribed)
        assertEquals(PostResult.OFF, h.post(note(1)))
        assertTrue(updates().isEmpty())
    }

    @Test
    fun `when the phone side is switched off the subscription is answered with disabled and nothing is sent`() {
        val h = handler(allowed = false)
        subscribe(h)
        assertEquals("ac1300010100", sent.single().payload.toHex())
        assertFalse(h.subscribed)
        assertEquals(PostResult.OFF, h.post(note(1)))
        assertEquals(1, sent.size)
    }

    // ------------------------------------------------------------------------ updates

    @Test
    fun `a new notification is added and the same one again is modified`() {
        val h = handler()
        subscribe(h)
        assertEquals(PostResult.SENT, h.post(note(7)))
        assertEquals(PostResult.SENT, h.post(note(7, body = "Toch niet")))
        assertEquals(
            listOf(NotificationUpdate.ADD, NotificationUpdate.MODIFY),
            updates().map { updateOf(it).first },
        )
    }

    @Test
    fun `the count is the number of notifications of the same category the watch holds`() {
        val h = handler()
        subscribe(h)
        h.post(note(1, WatchNotificationCategory.SMS))
        h.post(note(2, WatchNotificationCategory.SMS))
        h.post(note(3, WatchNotificationCategory.EMAIL))
        assertEquals(listOf(1, 2, 1), updates().map { updateOf(it).third.first })
    }

    @Test
    fun `removing a notification tells the watch, with the count that is left`() {
        val h = handler()
        subscribe(h)
        h.post(note(1))
        h.post(note(2))
        sent.clear()
        h.remove(1)
        val (type, category, countAndId) = updateOf(sent.single())
        assertEquals(NotificationUpdate.REMOVE, type)
        assertEquals(WatchNotificationCategory.SMS, category)
        assertEquals(1 to 1, countAndId)
    }

    @Test
    fun `removing one the watch never heard of says nothing, and a waiting one is simply dropped`() {
        val h = handler()
        h.post(note(1))
        h.remove(1)
        subscribe(h)
        h.remove(99)
        assertTrue(updates().isEmpty())
    }

    @Test
    fun `the 65th notification pushes the oldest off the watch`() {
        val h = handler()
        subscribe(h)
        for (i in 1..65) h.post(note(i))
        val last = updates().takeLast(2).map { updateOf(it).first to updateOf(it).third.second }
        assertTrue((NotificationUpdate.ADD to 65) in last, last.toString())
        assertTrue((NotificationUpdate.REMOVE to 1) in last, last.toString())
        // and an id that was pushed off is not answered any more
        sent.clear()
        h.handle(controlRequest(1, NotificationAttribute.TITLE to 10))
        assertTrue(dataMessages().isEmpty())
    }

    // ------------------------------------------------------------------------ the watch asks for details

    @Test
    fun `a request for details is acknowledged first and answered with the attributes in the order asked, the size last`() {
        val h = handler()
        subscribe(h)
        h.post(note(42))
        sent.clear()
        h.handle(
            controlRequest(
                42,
                NotificationAttribute.APP_IDENTIFIER to 0,
                NotificationAttribute.TITLE to 64,
                NotificationAttribute.MESSAGE_SIZE to 0,
                NotificationAttribute.MESSAGE to 500,
                NotificationAttribute.DATE to 0,
            ),
        )
        assertEquals(listOf(GfdiType.RESPONSE, GfdiType.NOTIFICATION_DATA), sent.map { it.type })
        assertEquals("aa13000000", sent[0].payload.toHex())
        val (id, attributes) = readAnswer(reassemble(dataMessages()))
        assertEquals(42, id)
        assertEquals(
            listOf(0 to "com.whatsapp", 1 to "Jan", 3 to "Zie je morgen?", 5 to "20261119T143005", 4 to "14"),
            attributes,
        )
    }

    @Test
    fun `a long answer goes out one chunk at a time, each after the watch said the one before arrived`() {
        val h = handler()
        subscribe(h)
        h.post(note(5, body = "x".repeat(700)))
        sent.clear()
        h.handle(controlRequest(5, NotificationAttribute.MESSAGE to 1000))
        assertEquals(1, dataMessages().size, "only the first chunk until the watch answers")
        h.handle(watchSays())
        assertEquals(2, dataMessages().size)
        h.handle(watchSays())
        assertEquals(3, dataMessages().size)
        h.handle(watchSays()) // the answer to the last chunk: nothing more to send
        assertEquals(3, dataMessages().size)
        val (_, attributes) = readAnswer(reassemble(dataMessages()))
        assertEquals(3 to "x".repeat(700), attributes.single())
    }

    @Test
    fun `a watch that refuses a chunk gets no more of that answer, and the next request is served`() {
        val h = handler()
        subscribe(h)
        h.post(note(5, body = "x".repeat(700)))
        h.post(note(6))
        sent.clear()
        h.handle(controlRequest(5, NotificationAttribute.MESSAGE to 1000))
        h.handle(controlRequest(6, NotificationAttribute.TITLE to 10))
        assertEquals(1, dataMessages().size, "the second request waits its turn")
        h.handle(watchSays(transfer = 2)) // ABORT
        // the aborted answer ends, the waiting one starts
        assertEquals(2, dataMessages().size)
        assertEquals(6, readAnswer(reassemble(dataMessages().drop(1))).first)
    }

    @Test
    fun `an answer the watch never confirms is dropped after a while so the next one can go`() {
        val h = handler()
        subscribe(h)
        h.post(note(5, body = "x".repeat(700)))
        h.post(note(6))
        sent.clear()
        h.handle(controlRequest(5, NotificationAttribute.MESSAGE to 1000))
        h.handle(controlRequest(6, NotificationAttribute.TITLE to 10))
        scheduler.advance(14_000)
        assertEquals(1, dataMessages().size, "still waiting")
        scheduler.advance(2_000)
        assertEquals(2, dataMessages().size)
        assertEquals(6, readAnswer(reassemble(dataMessages().drop(1))).first)
    }

    @Test
    fun `a request for a notification that is gone is acknowledged and gets no data`() {
        val h = handler()
        subscribe(h)
        sent.clear()
        h.handle(controlRequest(404, NotificationAttribute.TITLE to 10))
        assertEquals(listOf(GfdiType.RESPONSE), sent.map { it.type })
        assertEquals("aa13000000", sent[0].payload.toHex())
    }

    @Test
    fun `a request for the name of an app is answered from the lookup, else with the package`() {
        val h = handler()
        subscribe(h)
        sent.clear()
        fun appRequest(pkg: String) = GfdiMessage(GfdiType.NOTIFICATION_CONTROL, byteArrayOf(1) + pkg.toByteArray() + byteArrayOf(0, 0))
        h.handle(appRequest("com.whatsapp"))
        h.handle(watchSays())
        h.handle(appRequest("org.unknown"))
        val answers = dataMessages().map { reassemble(listOf(it)) }
        val expected = "01" + "com.whatsapp".toByteArray().toHex() + "00" + "00" + "0800" + "WhatsApp".toByteArray().toHex()
        assertEquals(expected, answers[0].toHex())
        val fallback = "01" + "org.unknown".toByteArray().toHex() + "00" + "00" + "0b00" + "org.unknown".toByteArray().toHex()
        assertEquals(fallback, answers[1].toHex())
    }

    // ------------------------------------------------------------------------ actions

    private fun action(id: Int, code: Int, text: String? = null): GfdiMessage {
        val w = GfdiWriter()
        w.byte(0x80)
        w.int(id)
        w.byte(code)
        if (text != null) {
            w.bytes(text.toByteArray())
            w.byte(0)
        }
        return GfdiMessage(GfdiType.NOTIFICATION_CONTROL, w.toByteArray())
    }

    @Test
    fun `dismissing on the watch is acknowledged and handed to the phone`() {
        val h = handler()
        subscribe(h)
        h.post(note(9))
        sent.clear()
        h.handle(action(9, 98))
        assertEquals(listOf(9), dismissed)
        assertEquals("aa13000000", sent.single().payload.toHex())
    }

    @Test
    fun `actions this app cannot do are acknowledged and otherwise ignored`() {
        val h = handler()
        subscribe(h)
        h.post(note(9))
        sent.clear()
        h.handle(action(9, 95, "ja")) // reply
        h.handle(action(9, 96)) // accept a call
        h.handle(action(9, 99)) // block the app
        assertTrue(dismissed.isEmpty())
        assertEquals(3, sent.size)
        assertTrue(sent.all { it.type == GfdiType.RESPONSE })
    }

    @Test
    fun `an action on a notification that is gone is acknowledged and nothing else`() {
        val h = handler()
        subscribe(h)
        sent.clear()
        h.handle(action(123, 98))
        assertTrue(dismissed.isEmpty())
        assertEquals(1, sent.size)
    }

    @Test
    fun `a control message that cannot be read is answered with the error form of the acknowledgement`() {
        val h = handler()
        subscribe(h)
        sent.clear()
        assertTrue(h.handle(GfdiMessage(GfdiType.NOTIFICATION_CONTROL, byteArrayOf(0x55, 1, 2))))
        assertEquals("aa130001a0", sent.single().payload.toHex())
    }

    @Test
    fun `messages that are not about notifications are left alone`() {
        val h = handler()
        assertFalse(h.handle(GfdiMessage.build(GfdiType.SYSTEM_EVENT) { byte(8); byte(0) }))
        assertFalse(h.handle(GfdiMessage.response(GfdiType.FILE_TRANSFER_DATA)))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `closing stops the timers`() {
        val h = handler()
        subscribe(h)
        h.post(note(5, body = "x".repeat(700)))
        h.handle(controlRequest(5, NotificationAttribute.MESSAGE to 1000))
        val before = sent.size
        h.close()
        scheduler.advance(60_000)
        assertEquals(before, sent.size)
    }

    // ------------------------------------------------------------------ a watch that does not ask

    @Test
    fun `a watch that never asks gets its notifications anyway once we give up waiting`() {
        val h = handler()
        assertEquals(PostResult.WAITING, h.post(note(1)))
        assertEquals(WatchAsk.NOT_YET, h.stats.watch)
        assertEquals(1, h.stats.waiting)
        assertTrue(updates().isEmpty())

        h.assumeSubscribed()

        assertEquals(WatchAsk.ASSUMED, h.stats.watch)
        assertTrue(h.sending)
        assertFalse(h.subscribed, "the watch itself did not say yes")
        assertEquals(listOf(NotificationUpdate.ADD), updates().map { updateOf(it).first })
        assertEquals(0, h.stats.waiting)
        assertEquals(PostResult.SENT, h.post(note(2)))
        assertEquals(2, updates().size)
    }

    @Test
    fun `giving up waiting does nothing once the watch has answered, either way`() {
        val yes = handler()
        subscribe(yes)
        yes.assumeSubscribed()
        assertEquals(WatchAsk.ON, yes.stats.watch)

        val no = handler()
        subscribe(no, on = false)
        no.assumeSubscribed()
        assertEquals(WatchAsk.OFF, no.stats.watch)
        assertEquals(PostResult.OFF, no.post(note(1)))
    }

    @Test
    fun `a no from the watch after we sent anyway switches notifications off again`() {
        val h = handler()
        h.assumeSubscribed()
        h.post(note(1))
        subscribe(h, on = false)
        assertEquals(WatchAsk.OFF, h.stats.watch)
        assertFalse(h.sending)
        assertEquals(PostResult.OFF, h.post(note(2)))
    }

    @Test
    fun `giving up waiting after the connection closed does nothing`() {
        val h = handler()
        h.post(note(1))
        h.close()
        h.assumeSubscribed()
        assertTrue(updates().isEmpty())
        assertFalse(h.sending)
    }

    @Test
    fun `a notification that waits for the watch is not counted as announced`() {
        val h = handler()
        h.post(note(1))
        assertEquals(0, h.stats.announced)
        assertEquals(1, h.stats.waiting)
        subscribe(h)
        assertEquals(1, h.stats.announced)
        assertEquals(0, h.stats.waiting)
    }

    // ------------------------------------------------------------------ the counts

    @Test
    fun `the counts follow the conversation, and every change is reported`() {
        val h = handler()
        val seen = ArrayList<NotifyStats>()
        h.onChange = { seen += it }
        subscribe(h)
        h.post(note(5))
        assertEquals(1, h.stats.announced)

        h.handle(controlRequest(5, NotificationAttribute.TITLE to 100, NotificationAttribute.MESSAGE to 1000))
        assertEquals(1, h.stats.asked)
        assertEquals(0, h.stats.answered, "not until the watch confirmed the last chunk")
        h.handle(watchSays())
        assertEquals(1, h.stats.answered)

        h.handle(action(5, 98))
        assertEquals(1, h.stats.dismissedOnWatch)

        h.post(note(6, body = "x".repeat(700)))
        h.handle(controlRequest(6, NotificationAttribute.MESSAGE to 1000))
        scheduler.advance(GarminNotifications.CHUNK_TIMEOUT_MS)
        assertEquals(1, h.stats.failed)
        assertTrue(seen.size >= 6, "reported after each step: $seen")
        assertEquals(h.stats, seen.last())
    }

    @Test
    fun `a listener that throws does not break the conversation`() {
        val h = handler()
        h.onChange = { error("boom") }
        subscribe(h)
        assertEquals(PostResult.SENT, h.post(note(1)))
        assertEquals(1, updates().size)
    }
}
