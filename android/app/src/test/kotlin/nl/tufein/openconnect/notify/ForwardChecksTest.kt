// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import nl.tufein.openconnect.core.watch.ble.NotifyStats
import nl.tufein.openconnect.core.watch.ble.WatchAsk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "It does not arrive" turned into the first step that is not working. */
class ForwardChecksTest {
    private fun ui(
        phase: ForwardPhase = ForwardPhase.CONNECTED,
        listener: Boolean = true,
        seen: Int = 3,
        notify: NotifyStats = NotifyStats(watch = WatchAsk.ON, announced = 2, asked = 2, answered = 2),
        apps: Set<String> = setOf("com.whatsapp"),
        error: String? = null,
        everConnected: Boolean = true,
        ownNotification: Boolean = true,
    ) = ForwardUi(enabled = true, phase = phase, watchName = "fenix 8", listenerBound = listener, seen = seen, notify = notify, apps = apps, error = error, everConnected = everConnected, ownNotificationAllowed = ownNotification)

    private fun states(ui: ForwardUi) = ForwardChecks.of(ui).associate { it.id to it.state }

    @Test
    fun `when everything works every step is ok`() {
        val checks = ForwardChecks.of(ui())
        assertTrue(checks.all { it.state == CheckState.OK }, checks.toString())
        assertNull(ForwardChecks.firstProblem(checks))
        assertTrue(ForwardChecks.hints(ui(), garminConnectInstalled = true).isEmpty())
    }

    @Test
    fun `without notification access that is the first problem and what follows is unknown`() {
        val ui = ui(phase = ForwardPhase.NEEDS_ACCESS, listener = false, seen = 0)
        val s = states(ui)
        assertEquals(CheckState.FAIL, s[ForwardCheckId.ACCESS])
        assertEquals(CheckState.UNKNOWN, s[ForwardCheckId.LISTENER])
        assertEquals(CheckState.UNKNOWN, s[ForwardCheckId.CONNECTION])
        assertEquals(ForwardCheckId.ACCESS, ForwardChecks.firstProblem(ForwardChecks.of(ui))?.id)
    }

    @Test
    fun `access given but no listener is the listener's step`() {
        val ui = ui(listener = false, seen = 0)
        assertEquals(CheckState.FAIL, states(ui)[ForwardCheckId.LISTENER])
        assertEquals(ForwardCheckId.LISTENER, ForwardChecks.firstProblem(ForwardChecks.of(ui))?.id)
        assertTrue(ForwardHint.LISTENER_NOT_STARTED in ForwardChecks.hints(ui, garminConnectInstalled = false))
    }

    @Test
    fun `a listener that has seen nothing yet is waiting, not failing`() {
        val ui = ui(seen = 0)
        assertEquals(CheckState.WAIT, states(ui)[ForwardCheckId.SEEN])
        assertTrue(ForwardHint.NOTHING_SEEN in ForwardChecks.hints(ui, garminConnectInstalled = false))
    }

    @Test
    fun `a watch that never asked for notifications is a warning with its hint`() {
        val ui = ui(notify = NotifyStats(watch = WatchAsk.ASSUMED, announced = 1, asked = 1, answered = 1))
        assertEquals(CheckState.WARN, states(ui)[ForwardCheckId.WATCH_ASKED])
        assertEquals(ForwardCheckId.WATCH_ASKED, ForwardChecks.firstProblem(ForwardChecks.of(ui))?.id)
        assertEquals(listOf(ForwardHint.WATCH_NOT_ASKING), ForwardChecks.hints(ui, garminConnectInstalled = false))
    }

    @Test
    fun `a watch that has not asked yet is waited for`() {
        val ui = ui(notify = NotifyStats(watch = WatchAsk.NOT_YET))
        assertEquals(CheckState.WAIT, states(ui)[ForwardCheckId.WATCH_ASKED])
        assertEquals(CheckState.WAIT, states(ui)[ForwardCheckId.DELIVERY])
    }

    @Test
    fun `a watch that said no fails at its own step and says what to do`() {
        val ui = ui(notify = NotifyStats(watch = WatchAsk.OFF))
        assertEquals(CheckState.FAIL, states(ui)[ForwardCheckId.WATCH_ASKED])
        assertEquals(CheckState.UNKNOWN, states(ui)[ForwardCheckId.DELIVERY])
        assertTrue(ForwardHint.WATCH_SAID_NO in ForwardChecks.hints(ui, garminConnectInstalled = false))
    }

    @Test
    fun `told but never asked what it says is where delivery fails`() {
        val ui = ui(notify = NotifyStats(watch = WatchAsk.ON, announced = 2, asked = 0, answered = 0))
        assertEquals(CheckState.FAIL, states(ui)[ForwardCheckId.DELIVERY])
        assertTrue(ForwardHint.WATCH_IGNORES in ForwardChecks.hints(ui, garminConnectInstalled = false))
    }

    @Test
    fun `answers that did not all arrive are a warning`() {
        val ui = ui(notify = NotifyStats(watch = WatchAsk.ON, announced = 3, asked = 3, answered = 2, failed = 1))
        assertEquals(CheckState.WARN, states(ui)[ForwardCheckId.DELIVERY])
    }

    @Test
    fun `a connection that fails shows its error and suggests closing Garmin Connect when it is installed`() {
        val ui = ui(phase = ForwardPhase.WAITING, error = "The watch refused the connection (status 1)", everConnected = false, notify = NotifyStats())
        val connection = ForwardChecks.of(ui).first { it.id == ForwardCheckId.CONNECTION }
        assertEquals(CheckState.FAIL, connection.state)
        assertEquals("The watch refused the connection (status 1)", connection.detail)
        assertEquals(listOf(ForwardHint.CLOSE_GARMIN_CONNECT), ForwardChecks.hints(ui, garminConnectInstalled = true))
        assertTrue(ForwardChecks.hints(ui, garminConnectInstalled = false).isEmpty(), "without Garmin Connect there is nothing to close")
    }

    @Test
    fun `a connection that worked and dropped suggests the battery settings`() {
        val ui = ui(phase = ForwardPhase.WAITING, error = "Connection lost (status 8)", everConnected = true, notify = NotifyStats())
        assertTrue(ForwardHint.BATTERY in ForwardChecks.hints(ui, garminConnectInstalled = false))
    }

    @Test
    fun `an app that sent nothing because none is chosen says so`() {
        val ui = ui(apps = emptySet(), notify = NotifyStats(watch = WatchAsk.ON))
        assertTrue(ForwardHint.CHOOSE_APPS in ForwardChecks.hints(ui, garminConnectInstalled = false))
    }

    @Test
    fun `switched off, there is nothing to say`() {
        assertTrue(ForwardChecks.hints(ForwardUi(enabled = false), garminConnectInstalled = true).isEmpty())
    }

    @Test
    fun `while connecting the step is named`() {
        val ui = ui(phase = ForwardPhase.CONNECTING, notify = NotifyStats()).copy(connectStep = nl.tufein.openconnect.watch.GarminStep.HANDSHAKE)
        val connection = ForwardChecks.of(ui).first { it.id == ForwardCheckId.CONNECTION }
        assertEquals(CheckState.WAIT, connection.state)
        assertEquals("HANDSHAKE", connection.detail)
    }

    @Test
    fun `android hiding the persistent notification is a warning with its hint, but only while a connection is held or sought`() {
        val hidden = ui(ownNotification = false)
        assertEquals(CheckState.WARN, states(hidden)[ForwardCheckId.OWN_NOTIFICATION])
        assertEquals(ForwardCheckId.OWN_NOTIFICATION, ForwardChecks.firstProblem(ForwardChecks.of(hidden))?.id)
        assertEquals(ForwardHint.ALLOW_OWN_NOTIFICATION, ForwardChecks.hints(hidden, garminConnectInstalled = false).first())
        val waiting = ui(phase = ForwardPhase.NEEDS_ACCESS, listener = false, seen = 0, ownNotification = false)
        assertTrue(ForwardHint.ALLOW_OWN_NOTIFICATION !in ForwardChecks.hints(waiting, garminConnectInstalled = false), "nothing is held yet")
    }
}
