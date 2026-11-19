// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.tufein.openconnect.core.watch.PhoneNotification
import nl.tufein.openconnect.core.watch.SkipReason
import nl.tufein.openconnect.core.watch.NotificationRules
import nl.tufein.openconnect.core.watch.ble.PostResult
import nl.tufein.openconnect.core.watch.ble.WatchAsk
import nl.tufein.openconnect.watch.GarminStep
import nl.tufein.openconnect.watch.FakeGarminBle
import nl.tufein.openconnect.watch.GarminBleWatch
import nl.tufein.openconnect.watch.InMemoryGarminWatchMemory
import nl.tufein.openconnect.watch.WatchLinkException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The part of notification forwarding that decides and keeps the link: a fake watch link, a fake phone, no Bluetooth.
 * Coroutines run on the test thread (Unconfined), and the waits between attempts are gates the test opens.
 */
class NotificationForwarderTest {
    private val fenix = GarminBleWatch("AA:BB:CC:DD:EE:FF", "fenix 8", paired = true)
    private val store = InMemoryForwardStore(enabled = true, apps = setOf("com.whatsapp"))
    private val memory = InMemoryGarminWatchMemory(fenix)
    private val keeper = FakeForwardingKeeper()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val retryGate = Channel<Unit>(Channel.UNLIMITED)
    private val busyGate = Channel<Unit>(Channel.UNLIMITED)
    private val retriesAsked = CopyOnWriteArrayList<Int>()
    private val connections = CopyOnWriteArrayList<FakeNotifyConnection>()
    private val listeners = CopyOnWriteArrayList<NotifyListener>()
    private val cancelled = CopyOnWriteArrayList<String>()

    private var access = true
    private var bluetooth = true
    private var screenOn = false
    private var busy = false
    private var now = Instant.parse("2026-11-19T14:00:00Z")

    private val link = FakeGarminBle(openNotifications = { _, listener ->
        listeners += listener
        FakeNotifyConnection(listener = listener).also { connections += it }
    })

    @AfterTest
    fun stop() {
        scope.cancel()
    }

    private val trail = ForwardTrail()
    private val rebinds = CopyOnWriteArrayList<String>()

    private fun forwarder(link: FakeGarminBle = this.link) = NotificationForwarder(
        store = store, memory = memory, link = link, scope = scope, io = Dispatchers.Unconfined,
        ownPackage = "nl.tufein.openconnect",
        accessGranted = { access }, screenOn = { screenOn }, bluetoothAllowed = { bluetooth },
        keeper = keeper, hiddenBody = { "Nieuwe melding" },
        dismissOnPhone = { cancelled += it },
        syncBusy = { busy },
        waitWhileBusy = { busyGate.receive() },
        now = { now },
        retryDelay = { attempt ->
            retriesAsked += attempt
            retryGate.receive()
        },
        trail = trail,
        rebindListener = { rebinds += "rebind" },
    )

    private fun message(text: String = "Zie je morgen?", pkg: String = "com.whatsapp", key: String = "0|com.whatsapp|1|null|10001") = PhoneNotification(
        key = key, packageName = pkg, appName = "WhatsApp", title = "Jan", text = text, postTime = 1_700_000_000_000, category = "msg",
    )

    // ----------------------------------------------------------------------------- what is needed

    @Test
    fun `switched off, nothing is tried`() {
        store.enabled = false
        val f = forwarder()
        f.refresh()
        assertEquals(ForwardPhase.OFF, f.state.value.phase)
        assertTrue(link.notifyOpened.isEmpty())
    }

    @Test
    fun `switched on without notification access, it waits for the access`() {
        access = false
        val f = forwarder()
        f.refresh()
        assertEquals(ForwardPhase.NEEDS_ACCESS, f.state.value.phase)
        assertTrue(link.notifyOpened.isEmpty())
        access = true
        f.refresh()
        assertEquals(ForwardPhase.CONNECTED, f.state.value.phase)
    }

    @Test
    fun `without a remembered watch it asks for a sync first`() {
        memory.save(null)
        val f = forwarder()
        f.refresh()
        assertEquals(ForwardPhase.NEEDS_WATCH, f.state.value.phase)
        assertTrue(link.notifyOpened.isEmpty())
    }

    @Test
    fun `without the Bluetooth permission it says so and does not connect`() {
        bluetooth = false
        val f = forwarder()
        f.refresh()
        assertEquals(ForwardPhase.NEEDS_BLUETOOTH, f.state.value.phase)
        assertTrue(link.notifyOpened.isEmpty())
    }

    // ----------------------------------------------------------------------------- the link

    @Test
    fun `with everything in place it connects to the remembered watch`() {
        val f = forwarder()
        f.refresh()
        assertEquals(listOf(fenix), link.notifyOpened.toList())
        val ui = f.state.value
        assertEquals(ForwardPhase.CONNECTED, ui.phase)
        assertEquals("fenix 8", ui.watchName)
        assertEquals(listOf("start:CONNECTING", "update:CONNECTED"), keeper.events.take(2))
    }

    @Test
    fun `refreshing again does not open a second connection`() {
        val f = forwarder()
        f.refresh()
        f.refresh()
        assertEquals(1, link.notifyOpened.size)
    }

    @Test
    fun `a lost connection is closed and tried again after a wait`() {
        val f = forwarder()
        f.refresh()
        listeners.single().onLost("The watch disconnected")
        assertEquals(ForwardPhase.WAITING, f.state.value.phase)
        assertTrue(connections[0].closed)
        assertEquals(listOf(1), retriesAsked.toList())
        retryGate.trySend(Unit)
        assertEquals(2, link.notifyOpened.size)
        assertEquals(ForwardPhase.CONNECTED, f.state.value.phase)
    }

    @Test
    fun `Try now skips the wait and connects again`() {
        val f = forwarder()
        f.refresh()
        listeners.single().onLost("The watch disconnected")
        assertEquals(ForwardPhase.WAITING, f.state.value.phase)
        f.reconnect()
        assertEquals(2, link.notifyOpened.size, "no need to open the retry gate")
        assertEquals(ForwardPhase.CONNECTED, f.state.value.phase)
        assertTrue(connections[0].closed)
        assertFalse(connections[1].closed)
    }

    @Test
    fun `a connection that fails is tried again, a pairing problem waits for the person`() {
        var calls = 0
        val flaky = FakeGarminBle(openNotifications = { _, listener ->
            calls++
            listeners += listener
            when (calls) {
                1 -> throw WatchLinkException(WatchLinkException.Reason.NOT_FOUND, "The watch did not answer")
                2 -> throw WatchLinkException(WatchLinkException.Reason.NOT_PAIRED, "Pairing did not finish")
                else -> FakeNotifyConnection().also { connections += it }
            }
        })
        val f = forwarder(flaky)
        f.refresh()
        assertEquals(ForwardPhase.WAITING, f.state.value.phase)
        assertEquals("The watch did not answer", f.state.value.error)
        retryGate.trySend(Unit)
        assertEquals(ForwardPhase.PROBLEM, f.state.value.phase)
        assertEquals("Pairing did not finish", f.state.value.error)
        retryGate.trySend(Unit)
        assertEquals(2, calls, "a pairing problem is not retried on its own")
        f.refresh() // the person fixed it and came back
        assertEquals(ForwardPhase.CONNECTED, f.state.value.phase)
    }

    @Test
    fun `the waits between attempts grow`() {
        val broken = FakeGarminBle(openNotifications = { _, _ -> throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "no") })
        val f = forwarder(broken)
        f.refresh()
        repeat(3) { retryGate.trySend(Unit) }
        assertEquals(listOf(1, 2, 3, 4), retriesAsked.toList())
    }

    @Test
    fun `while a sync holds the link, no connection is attempted`() {
        busy = true
        val f = forwarder()
        f.refresh()
        assertTrue(link.notifyOpened.isEmpty())
        busy = false
        busyGate.trySend(Unit)
        assertEquals(1, link.notifyOpened.size)
        assertEquals(ForwardPhase.CONNECTED, f.state.value.phase)
    }

    @Test
    fun `switching off closes the connection and the keeper`() {
        val f = forwarder()
        f.refresh()
        f.setEnabled(false)
        assertFalse(store.enabled)
        assertEquals(ForwardPhase.OFF, f.state.value.phase)
        assertTrue(connections.single().closed)
        assertEquals("stop", keeper.events.last())
    }

    @Test
    fun `switching on connects at once, and the keeper's Turn off switches it off`() {
        store.enabled = false
        val f = forwarder()
        f.setEnabled(true)
        assertTrue(store.enabled)
        assertEquals(ForwardPhase.CONNECTED, f.state.value.phase)
        keeper.onStop!!.invoke()
        assertFalse(store.enabled)
        assertEquals(ForwardPhase.OFF, f.state.value.phase)
    }

    // ----------------------------------------------------------------------------- the notifications

    @Test
    fun `a message from a chosen app goes to the watch`() {
        val f = forwarder()
        f.refresh()
        f.onPosted(message())
        val sent = connections.single().posted.single()
        assertEquals("Jan", sent.title)
        assertEquals("Zie je morgen?", sent.body)
        assertEquals(NotificationRules.idOf(message().key), sent.id)
        assertEquals(1, f.state.value.sent)
        assertEquals(now, f.state.value.lastSentAt)
    }

    @Test
    fun `an app that was not chosen is skipped and the reason is shown`() {
        val f = forwarder()
        f.refresh()
        f.onPosted(message(pkg = "com.example.game"))
        assertTrue(connections.single().posted.isEmpty())
        assertEquals(SkipReason.APP_NOT_CHOSEN, f.state.value.lastSkipped)
        assertEquals(0, f.state.value.sent)
    }

    @Test
    fun `the same words again are not sent twice, new words are`() {
        val f = forwarder()
        f.refresh()
        f.onPosted(message())
        f.onPosted(message())
        assertEquals(SkipReason.DUPLICATE, f.state.value.lastSkipped)
        f.onPosted(message("Toch niet"))
        assertEquals(listOf("Zie je morgen?", "Toch niet"), connections.single().posted.map { it.body })
    }

    @Test
    fun `a removed notification is removed from the watch and may come back`() {
        val f = forwarder()
        f.refresh()
        f.onPosted(message())
        f.onRemoved(message().key)
        assertEquals(listOf(NotificationRules.idOf(message().key)), connections.single().removed.toList())
        f.onPosted(message())
        assertEquals(2, connections.single().posted.size, "after it was removed, the same words are new again")
    }

    @Test
    fun `without a connection a notification is skipped, not kept for later`() {
        val f = forwarder()
        f.refresh()
        listeners.single().onLost(null)
        f.onPosted(message())
        assertEquals(SkipReason.NOT_CONNECTED, f.state.value.lastSkipped)
        assertTrue(connections.single().posted.isEmpty())
    }

    @Test
    fun `a watch that has notifications off is not counted as sent to`() {
        val f = forwarder()
        f.refresh()
        connections.single().result = PostResult.OFF
        f.onPosted(message())
        assertEquals(SkipReason.WATCH_OFF, f.state.value.lastSkipped)
        assertEquals(0, f.state.value.sent)
    }

    @Test
    fun `dismissing on the watch swipes the notification away on the phone`() {
        val f = forwarder()
        f.refresh()
        f.onPosted(message())
        listeners.single().onDismissedOnWatch(NotificationRules.idOf(message().key))
        assertEquals(listOf(message().key), cancelled.toList())
        listeners.single().onDismissedOnWatch(12345)
        assertEquals(1, cancelled.size, "an id nobody knows is ignored")
    }

    @Test
    fun `the options change what the watch is shown`() {
        val f = forwarder()
        f.refresh()
        f.setHideContent(true)
        f.onPosted(message())
        val sent = connections.single().posted.single()
        assertEquals("WhatsApp", sent.title)
        assertEquals("Nieuwe melding", sent.body)
        screenOn = true
        f.setOnlyWhenScreenOff(true)
        f.onPosted(message("Nog een", key = "0|com.whatsapp|2|null|10001"))
        assertEquals(SkipReason.SCREEN_ON, f.state.value.lastSkipped)
        assertEquals(1, connections.single().posted.size)
        assertTrue(store.hideContent && store.onlyWhenScreenOff)
    }

    @Test
    fun `choosing apps is remembered and shown`() {
        val f = forwarder()
        f.setApp("com.google.android.gm", true)
        f.setApp("com.whatsapp", false)
        assertEquals(setOf("com.google.android.gm"), store.apps)
        assertEquals(setOf("com.google.android.gm"), f.state.value.apps)
    }

    @Test
    fun `the test notification goes to the watch whatever apps were chosen`() {
        store.apps = emptySet()
        val f = forwarder()
        assertEquals(TestResult.NOT_CONNECTED, f.sendTest("OpenConnect test", "Hello"))
        f.refresh()
        assertEquals(TestResult.SENT, f.sendTest("OpenConnect test", "Hello"))
        val sent = connections.single().posted.single()
        assertEquals("OpenConnect test", sent.title)
        assertEquals("Hello", sent.body)
        connections.single().result = PostResult.OFF
        assertEquals(TestResult.WATCH_OFF, f.sendTest("OpenConnect test", "Hello"))
    }

    @Test
    fun `the person's own copy of the state is kept in sync with the store`() {
        val f = forwarder()
        assertTrue(f.state.value.enabled)
        assertEquals(setOf("com.whatsapp"), f.state.value.apps)
        assertNotNull(f.state.value)
        assertNull(f.state.value.error)
    }

    // --------------------------------------------------------------------------- what is going on

    @Test
    fun `a notification the watch has not asked for yet is waiting, and is not counted as sent`() {
        val f = forwarder()
        f.refresh()
        connections.single().result = PostResult.WAITING
        f.onPosted(message())
        assertEquals(0, f.state.value.sent)
        assertNull(f.state.value.lastSentAt)
        assertEquals(1, f.state.value.notify.waiting)
        assertEquals(WatchAsk.NOT_YET, f.state.value.notify.watch)
        assertTrue(trail.flow.value.any { "waiting, the watch has not asked" in it.text })
    }

    @Test
    fun `what the watch is told is counted when the connection says so`() {
        val f = forwarder()
        f.refresh()
        f.onPosted(message())
        f.onPosted(message("Toch niet"))
        assertEquals(2, f.state.value.sent)
        assertEquals(2, f.state.value.notify.announced)
        assertEquals(WatchAsk.ON, f.state.value.notify.watch)
        // a new connection starts counting again without losing what went before
        listeners.single().onLost("gone")
        retryGate.trySend(Unit)
        f.onPosted(message("Weer iets nieuws"))
        assertEquals(3, f.state.value.sent)
        assertEquals(1, f.state.value.notify.announced)
    }

    @Test
    fun `every notification Android hands over is counted, chosen or not`() {
        val f = forwarder()
        f.refresh()
        assertEquals(0, f.state.value.seen)
        f.onPosted(message())
        f.onPosted(message(pkg = "com.example.game", key = "0|com.example.game|1|null|1"))
        assertEquals(2, f.state.value.seen)
        assertEquals(now, f.state.value.lastSeenAt)
        assertEquals(mapOf(SkipReason.APP_NOT_CHOSEN to 1), f.state.value.skippedBy)
    }

    @Test
    fun `the reasons for holding notifications back are counted`() {
        val f = forwarder()
        f.refresh()
        repeat(3) { f.onPosted(message(pkg = "com.example.game", key = "0|com.example.game|$it|null|1")) }
        f.onPosted(message())
        f.onPosted(message())
        assertEquals(mapOf(SkipReason.APP_NOT_CHOSEN to 3, SkipReason.DUPLICATE to 1), f.state.value.skippedBy)
    }

    @Test
    fun `the listener starting and stopping is shown`() {
        val f = forwarder()
        assertFalse(f.state.value.listenerBound)
        f.listenerChanged(true)
        assertTrue(f.state.value.listenerBound)
        f.listenerChanged(false)
        assertFalse(f.state.value.listenerBound)
        assertTrue(trail.flow.value.any { "started the notification listener" in it.text })
        assertTrue(trail.flow.value.any { "stopped the notification listener" in it.text })
    }

    @Test
    fun `with access on but no listener, Android is asked to start it, once it has started it is left alone`() {
        val f = forwarder()
        f.refresh()
        assertEquals(1, rebinds.size, "access is on and the listener never started")
        f.listenerChanged(true)
        f.refresh()
        assertEquals(1, rebinds.size, "no need to ask again")
        access = false
        val other = forwarder()
        other.refresh()
        assertEquals(1, rebinds.size, "without access there is nothing to start")
    }

    @Test
    fun `the log names the steps of the connection and the watch, and never what a notification says`() {
        val f = forwarder()
        f.refresh()
        f.onPosted(message("geheime-inhoud-123"))
        f.onPosted(message(pkg = "com.example.game", key = "0|com.example.game|1|null|1"))
        val text = trail.asText("header")
        assertTrue("connecting to fenix 8" in text, text)
        assertTrue("connecting: CONNECTING" in text, text)
        assertTrue("connecting: HANDSHAKE" in text, text)
        assertTrue("connected to fenix 8" in text, text)
        assertTrue("com.whatsapp: announced to the watch" in text, text)
        assertTrue("com.example.game: held back, APP_NOT_CHOSEN" in text, text)
        assertFalse("geheime-inhoud-123" in text, "the words of a notification never reach the log")
    }

    @Test
    fun `while connecting the card knows which step it is on`() {
        val gate = CompletableDeferred<Unit>()
        val slow = FakeGarminBle(openNotifications = { _, listener ->
            gate.await()
            FakeNotifyConnection(listener = listener).also { connections += it }
        })
        val f = forwarder(slow)
        f.refresh()
        assertEquals(ForwardPhase.CONNECTING, f.state.value.phase)
        assertEquals(GarminStep.HANDSHAKE, f.state.value.connectStep)
        gate.complete(Unit)
        assertEquals(ForwardPhase.CONNECTED, f.state.value.phase)
        assertNull(f.state.value.connectStep)
        assertTrue(f.state.value.everConnected)
    }

    @Test
    fun `a connection that worked and was lost is remembered as having worked`() {
        val f = forwarder()
        f.refresh()
        assertTrue(f.state.value.everConnected)
        listeners.single().onLost("The watch disconnected")
        assertEquals(ForwardPhase.WAITING, f.state.value.phase)
        assertTrue(f.state.value.everConnected)
        assertTrue(trail.flow.value.any { "connection lost: The watch disconnected" in it.text })
        assertTrue(trail.flow.value.any { "trying again in 10 s" in it.text })
    }

    @Test
    fun `a failed connection puts the reason in the log`() {
        val broken = FakeGarminBle(openNotifications = { _, _ -> throw WatchLinkException(WatchLinkException.Reason.CANNOT_OPEN, "The watch refused the connection (status 1)") })
        val f = forwarder(broken)
        f.refresh()
        assertTrue(trail.flow.value.any { "The watch refused the connection (status 1)" in it.text })
        assertFalse(f.state.value.everConnected)
    }

    @Test
    fun `the test button says when the watch has not asked for notifications yet`() {
        val f = forwarder()
        f.refresh()
        connections.single().result = PostResult.WAITING
        assertEquals(TestResult.WATCH_NOT_ASKING, f.sendTest("t", "b"))
        connections.single().result = PostResult.OFF
        assertEquals(TestResult.WATCH_OFF, f.sendTest("t", "b"))
        connections.single().result = PostResult.SENT
        assertEquals(TestResult.SENT, f.sendTest("t", "b"))
    }
}
