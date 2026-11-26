// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.watch

import nl.tufein.openconnect.core.watch.ble.GarminFileAccess
import java.util.concurrent.atomic.AtomicInteger

/**
 * A watch has room for one phone connection. When notifications are on, that connection stays open all day, so a sync
 * must not open a second one: it reads through the open one instead. And while a sync does open its own, the notification
 * side must not try to connect at the same time. This is where the two find out about each other.
 */
class LiveWatchRegistry {
    /** The open notification connection: what a sync needs to read through it. */
    class Live(
        val address: String,
        val name: String,
        val files: GarminFileAccess,
        /** False once the connection is lost or closed. */
        val usable: () -> Boolean,
        /** Tells the watch a sync is over. */
        val finishSync: () -> Unit,
        /** Asks the open connection for a fresh charge, so a sync through it does not leave the card on an old value. */
        val refreshBattery: () -> Unit = {},
    )

    @Volatile private var live: Live? = null
    private val syncing = AtomicInteger(0)

    /** Makes [connection] available to syncs; closing the result takes it away again (if it is still the current one). */
    fun publish(connection: Live): AutoCloseable {
        live = connection
        return AutoCloseable { if (live === connection) live = null }
    }

    /** The open connection to the watch at [address], when there is one that still works. */
    fun sharedFor(address: String): Live? = live?.takeIf { it.address.equals(address, ignoreCase = true) && it.usable() }

    /** A sync that opens its own connection says so for as long as it holds it. */
    fun beginSync(): AutoCloseable {
        syncing.incrementAndGet()
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        return AutoCloseable { if (done.compareAndSet(false, true)) syncing.decrementAndGet() }
    }

    /** True while a sync holds its own connection; the notification side waits. */
    val syncBusy: Boolean get() = syncing.get() > 0
}
