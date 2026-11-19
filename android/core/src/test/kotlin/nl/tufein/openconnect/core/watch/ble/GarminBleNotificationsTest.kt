// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Phone notifications over a whole session: real handshake, real framing, a pretend watch that asks and acknowledges. */
class GarminBleNotificationsTest {
    private val phone = PhoneIdentity("Stefan's phone", "Google", "Pixel 9")
    private val scheduler = ExecutorScheduler()
    private val watches = ArrayList<FakeGarminWatch>()
    private val log = CopyOnWriteArrayList<String>()

    @AfterTest
    fun stop() {
        watches.forEach { it.shutdown() }
        scheduler.shutdown()
    }

    private fun connect(
        watch: FakeGarminWatch,
        useMlr: Boolean = true,
        notifications: NotificationOptions? = NotificationOptions("Dismiss", { if (it == "com.whatsapp") "WhatsApp" else null }),
        askGraceMs: Long = GarminBleSession.ASK_GRACE_MS,
    ): GarminBleSession {
        watches += watch
        val session = GarminBleSession(
            watch.pipe, phone, scheduler, maxWrite = 20, useMlr = useMlr,
            now = { Instant.parse("2026-11-19T13:30:05Z") }, zone = { ZoneId.of("Europe/Amsterdam") },
            log = { log += it }, notifications = notifications, askGraceMs = askGraceMs,
        )
        watch.phone = session
        session.start()
        session.awaitReady(5_000)
        return session
    }

    private fun eventually(what: String, check: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            if (check()) return
            Thread.sleep(10)
        }
        fail("never happened: $what; log: $log")
    }

    private fun note(id: Int, body: String = "Zie je morgen?", category: WatchNotificationCategory = WatchNotificationCategory.SMS) = WatchNotification(
        id = id, appId = "com.whatsapp", appName = "WhatsApp", title = "Jan", body = body,
        whenMillis = Instant.parse("2026-11-19T13:30:05Z").toEpochMilli(), category = category,
    )

    private fun text(bytes: ByteArray?) = String(bytes ?: error("missing"), Charsets.UTF_8)

    @Test
    fun `a watch that subscribes is shown a notification with its title, text, date and a dismiss action`() {
        for (mlr in listOf(true, false)) {
            val watch = FakeGarminWatch(files = emptyMap(), reliable = mlr, notifications = true)
            val session = connect(watch, useMlr = mlr)
            val notifier = session.notifier!!
            eventually("the watch subscribes") { notifier.subscribed }
            assertEquals(PostResult.SENT, notifier.post(note(42)))
            eventually("the watch shows it") { watch.shown[42] != null }

            val shown = watch.shown.getValue(42)
            assertEquals("com.whatsapp", text(shown[0]))
            assertEquals("Jan", text(shown[1]))
            assertEquals("Zie je morgen?", text(shown[3]))
            assertEquals("14", text(shown[4]))
            assertEquals("20261119T143005", text(shown[5]))
            assertEquals("01" + "62" + "04" + "07" + "Dismiss".toByteArray().toHex(), shown[127]!!.toHex())

            // announced once: ADD, flags FOREGROUND|DECLINE, category SMS, count 1, with actions
            assertEquals(listOf(listOf(0, 0x12, 12, 1, 42, 2)), watch.updates.map { it.toList() })
            // the answer to the subscription said yes
            eventually("the watch saw the answer") { watch.subscriptionReply != null }
            assertEquals("ac1300000100", watch.subscriptionReply!!.toHex())
            session.close()
        }
    }

    @Test
    fun `a session tells whoever listens when it ends, once, and says whether it was on purpose`() {
        val lost = FakeGarminWatch(files = emptyMap(), reliable = true, notifications = true)
        val dropped = connect(lost)
        val endings = CopyOnWriteArrayList<GarminBleException>()
        dropped.onEnded = { endings += it }
        dropped.onDisconnected("The watch went out of reach")
        dropped.onDisconnected("again") // a second report of the same loss is not a second ending
        assertEquals(listOf(GarminBleException.Reason.DISCONNECTED), endings.map { it.reason })
        assertEquals("The watch went out of reach", endings.single().message)

        val onPurpose = connect(FakeGarminWatch(files = emptyMap(), reliable = true, notifications = true))
        val closings = CopyOnWriteArrayList<GarminBleException>()
        onPurpose.onEnded = { closings += it }
        onPurpose.close()
        assertEquals(listOf(GarminBleException.Reason.CLOSED), closings.map { it.reason })
    }

    @Test
    fun `a long message crosses in several chunks and arrives whole`() {
        for (mlr in listOf(true, false)) {
            val watch = FakeGarminWatch(files = emptyMap(), reliable = mlr, notifications = true)
            val session = connect(watch, useMlr = mlr)
            val notifier = session.notifier!!
            eventually("the watch subscribes") { notifier.subscribed }
            val long = (1..120).joinToString(" ") { "woord$it" }.take(500)
            notifier.post(note(7, body = long))
            eventually("the watch shows it") { watch.shown[7] != null }
            assertEquals(long, text(watch.shown.getValue(7)[3]))
            session.close()
        }
    }

    @Test
    fun `an update and a removal reach the watch, with the count that is left`() {
        val watch = FakeGarminWatch(files = emptyMap(), notifications = true)
        val session = connect(watch)
        val notifier = session.notifier!!
        eventually("the watch subscribes") { notifier.subscribed }
        notifier.post(note(1))
        notifier.post(note(2))
        notifier.post(note(1, body = "Toch niet"))
        eventually("three updates") { watch.updates.size == 3 }
        assertEquals(listOf(0, 0, 1), watch.updates.map { it[0] }) // ADD, ADD, MODIFY
        eventually("all three answers arrived") { watch.answersReceived.get() == 3 }
        assertEquals("Toch niet", text(watch.shown.getValue(1)[3]))
        notifier.remove(1)
        eventually("the removal") { watch.updates.size == 4 }
        val removal = watch.updates.last().toList()
        assertEquals(listOf(2, 0x12, 12, 1, 1, 0), removal) // REMOVE, SMS, one left, id 1, no actions
        eventually("the watch forgets it") { watch.shown[1] == null }
        session.close()
    }

    @Test
    fun `dismissing on the watch reaches the phone`() {
        val watch = FakeGarminWatch(files = emptyMap(), notifications = true)
        val session = connect(watch)
        val notifier = session.notifier!!
        val dismissed = CopyOnWriteArrayList<Int>()
        notifier.onDismissedOnWatch = { dismissed += it }
        eventually("the watch subscribes") { notifier.subscribed }
        notifier.post(note(9))
        eventually("the watch shows it") { watch.shown[9] != null }
        watch.dismissOnWatch(9)
        eventually("the phone hears it") { dismissed.isNotEmpty() }
        assertEquals(listOf(9), dismissed)
        session.close()
    }

    @Test
    fun `the watch can ask what an app is called`() {
        val watch = FakeGarminWatch(files = emptyMap(), notifications = true)
        val session = connect(watch)
        eventually("the watch subscribes") { session.notifier!!.subscribed }
        watch.askAppName("com.whatsapp")
        eventually("the name") { watch.appNames["com.whatsapp"] == "WhatsApp" }
        session.close()
    }

    @Test
    fun `a notification posted before the watch subscribes is shown once it does`() {
        val watch = FakeGarminWatch(files = emptyMap(), notifications = false)
        val session = connect(watch)
        val notifier = session.notifier!!
        assertEquals(PostResult.WAITING, notifier.post(note(3)))
        watch.subscribeToNotifications()
        eventually("the watch shows it") { watch.shown[3] != null }
        session.close()
    }

    @Test
    fun `files and notifications share one link without getting in each other's way`() {
        val files = mapOf(5 to (GarminFileType.MONITOR to Random(1).nextBytes(6_000)))
        val watch = FakeGarminWatch(files, chunkSize = 200, notifications = true)
        val session = connect(watch)
        val notifier = session.notifier!!
        eventually("the watch subscribes") { notifier.subscribed }
        val posting = Thread {
            repeat(5) { notifier.post(note(100 + it, body = "bericht $it")); Thread.sleep(20) }
        }
        posting.start()
        assertContentEquals(files.getValue(5).second, session.download(5))
        posting.join()
        eventually("all five are shown") { (100..104).all { watch.shown[it] != null } }
        session.close()
    }

    @Test
    fun `a session that only syncs tells the watch notifications are off`() {
        val watch = FakeGarminWatch(files = emptyMap())
        val session = connect(watch, notifications = null)
        watch.subscribeToNotifications()
        eventually("the watch hears the answer") { watch.subscriptionReply != null }
        // ACK, DISABLED, the request echoed
        assertEquals("ac1300010100", watch.subscriptionReply!!.toHex())
        assertEquals(null, session.notifier)
        session.close()
    }

    @Test
    fun `when the link drops, nothing more is sent`() {
        val watch = FakeGarminWatch(files = emptyMap(), notifications = true)
        val session = connect(watch)
        val notifier = session.notifier!!
        eventually("the watch subscribes") { notifier.subscribed }
        session.onDisconnected()
        assertEquals(PostResult.OFF, notifier.post(note(1)))
        assertTrue(watch.updates.isEmpty())
    }

    @Test
    fun `a watch that never asks for notifications is shown them anyway after the grace period`() {
        val watch = FakeGarminWatch(files = emptyMap(), notifications = false)
        val session = connect(watch, askGraceMs = 150)
        val notifier = session.notifier!!
        assertEquals(PostResult.WAITING, notifier.post(note(11)))
        assertEquals(WatchAsk.NOT_YET, notifier.stats.watch)
        eventually("the phone gives up waiting and sends it") { watch.shown[11] != null }
        assertEquals(WatchAsk.ASSUMED, notifier.stats.watch)
        assertEquals("Zie je morgen?", text(watch.shown.getValue(11)[3]))
        assertEquals(1, notifier.stats.answered)
        assertTrue(log.any { "has not asked for notifications yet" in it }, "the log says why: $log")
        session.close()
    }

    @Test
    fun `a watch that asks in time is not second-guessed`() {
        val watch = FakeGarminWatch(files = emptyMap(), notifications = true)
        val session = connect(watch, askGraceMs = 150)
        val notifier = session.notifier!!
        eventually("the watch subscribes") { notifier.subscribed }
        Thread.sleep(300)
        assertEquals(WatchAsk.ON, notifier.stats.watch)
        session.close()
    }

    @Test
    fun `the log names every kind of message the watch sends`() {
        val watch = FakeGarminWatch(files = emptyMap(), notifications = true)
        val session = connect(watch)
        eventually("the watch subscribes") { session.notifier!!.subscribed }
        assertTrue(log.any { "watch sent DEVICE_INFORMATION (5024)" in it }, "log: $log")
        assertTrue(log.any { "watch sent CONFIGURATION (5050)" in it }, "log: $log")
        assertTrue(log.any { "watch sent NOTIFICATION_SUBSCRIPTION (5036)" in it }, "log: $log")
        session.close()
    }
}
