// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import nl.tufein.openconnect.core.watch.ble.NotifyStats
import nl.tufein.openconnect.core.watch.ble.PostResult
import nl.tufein.openconnect.core.watch.ble.WatchNotification

/**
 * A connection to the watch that stays open so notifications can go out the moment they arrive. The real one is made
 * by [nl.tufein.openconnect.watch.AndroidGarminBleLink]; tests use a fake.
 */
interface WatchNotifyConnection : AutoCloseable {
    /** The name the watch gave itself. */
    val watchName: String

    /** Hands a notification to the watch; never blocks. */
    fun post(notification: WatchNotification): PostResult

    /** Tells the watch a notification is gone. */
    fun remove(id: Int)
}

/** What a [WatchNotifyConnection] reports back. Calls may come from a Bluetooth thread. */
interface NotifyListener {
    /** The person tapped "dismiss" on the watch for notification [id]. */
    fun onDismissedOnWatch(id: Int)

    /** The connection ended by itself: the watch went out of reach or switched off. [reason] is a short note, or null. */
    fun onLost(reason: String?)

    /** How the conversation with the watch stands: what it asked for, what was announced, what it confirmed. */
    fun onStatus(stats: NotifyStats) {}

    /** One line for the log of what is happening (a step of the connection, a kind of message the watch sent). Never content. */
    fun onLog(line: String) {}
}

/** What the person chose for forwarding. The real one is kept in [nl.tufein.openconnect.data.SettingsStore]. */
interface ForwardStore {
    var enabled: Boolean
    var apps: Set<String>
    var hideContent: Boolean
    var onlyWhenScreenOff: Boolean
}

class InMemoryForwardStore(
    override var enabled: Boolean = false,
    override var apps: Set<String> = emptySet(),
    override var hideContent: Boolean = false,
    override var onlyWhenScreenOff: Boolean = false,
) : ForwardStore

/**
 * Keeps the process, and so the Bluetooth connection, alive while notifications are being forwarded: a quiet
 * persistent notification. The real one is [ForegroundForwarding]; tests use a fake.
 */
interface ForwardingKeeper {
    /** Shows the notification. [onStop] is called when the person taps "Turn off" on it. */
    fun start(state: ForwardUi, onStop: () -> Unit)

    fun update(state: ForwardUi)

    fun stop()
}

object NoForwardingKeeper : ForwardingKeeper {
    override fun start(state: ForwardUi, onStop: () -> Unit) {}

    override fun update(state: ForwardUi) {}

    override fun stop() {}
}

/** One app in the list of apps to choose from. */
data class InstalledApp(val packageName: String, val label: String)

/** The apps on the phone the person can choose from. The real one asks Android's package manager; tests use a fake. */
fun interface InstalledApps {
    fun list(): List<InstalledApp>
}

object NoInstalledApps : InstalledApps {
    override fun list(): List<InstalledApp> = emptyList()
}
