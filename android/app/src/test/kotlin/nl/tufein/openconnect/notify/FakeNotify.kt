// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.notify

import nl.tufein.openconnect.core.watch.ble.PostResult
import nl.tufein.openconnect.core.watch.ble.WatchNotification
import java.util.concurrent.CopyOnWriteArrayList

/** A connection to a pretend watch: remembers what it was given, answers with [result]. */
class FakeNotifyConnection(override val watchName: String = "fenix 8") : WatchNotifyConnection {
    val posted = CopyOnWriteArrayList<WatchNotification>()
    val removed = CopyOnWriteArrayList<Int>()

    @Volatile var closed = false

    @Volatile var result = PostResult.SENT

    override fun post(notification: WatchNotification): PostResult {
        posted += notification
        return result
    }

    override fun remove(id: Int) {
        removed += id
    }

    override fun close() {
        closed = true
    }
}

/** A persistent notification that only writes down what it was asked to do. */
class FakeForwardingKeeper : ForwardingKeeper {
    val events = CopyOnWriteArrayList<String>()

    @Volatile var onStop: (() -> Unit)? = null

    override fun start(state: ForwardUi, onStop: () -> Unit) {
        events += "start:${state.phase}"
        this.onStop = onStop
    }

    override fun update(state: ForwardUi) {
        events += "update:${state.phase}"
    }

    override fun stop() {
        events += "stop"
    }
}
