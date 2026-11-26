// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.watch

import nl.tufein.openconnect.core.watch.ble.GarminFileAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The one place where the notification connection and a sync find out about each other. */
class LiveWatchRegistryTest {
    private val files = object : GarminFileAccess {
        override fun listDirectory() = emptyList<nl.tufein.openconnect.core.watch.ble.GarminDirectoryEntry>()

        override fun download(index: Int) = ByteArray(0)
    }

    private fun live(
        address: String = "AA:BB:CC:DD:EE:FF",
        usable: () -> Boolean = { true },
        finish: () -> Unit = {},
        refresh: () -> Unit = {},
    ) = LiveWatchRegistry.Live(address, "fenix 8", files, usable, finish, refresh)

    @Test
    fun `nothing is shared until a connection is published`() {
        assertNull(LiveWatchRegistry().sharedFor("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `a published connection is shared with a sync for the same watch, whatever the case of the address`() {
        val registry = LiveWatchRegistry()
        val one = live()
        registry.publish(one)
        assertSame(one, registry.sharedFor("aa:bb:cc:dd:ee:ff"))
        assertNull(registry.sharedFor("11:22:33:44:55:66"), "another watch has its own connection")
    }

    @Test
    fun `a connection that no longer works is not shared`() {
        val registry = LiveWatchRegistry()
        var alive = true
        registry.publish(live(usable = { alive }))
        assertNotNull(registry.sharedFor("AA:BB:CC:DD:EE:FF"))
        alive = false
        assertNull(registry.sharedFor("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `closing the handle takes the connection away, but not a newer one`() {
        val registry = LiveWatchRegistry()
        val first = registry.publish(live())
        val second = live()
        registry.publish(second)
        first.close() // the old connection ends after a new one was published
        assertSame(second, registry.sharedFor("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `closing the only handle empties the registry`() {
        val registry = LiveWatchRegistry()
        registry.publish(live()).close()
        assertNull(registry.sharedFor("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `a sync through the shared connection can ask for a fresh charge`() {
        var asked = 0
        val registry = LiveWatchRegistry()
        registry.publish(live(refresh = { asked++ }))
        registry.sharedFor("AA:BB:CC:DD:EE:FF")?.refreshBattery()
        assertEquals(1, asked)
    }

    @Test
    fun `a sync is busy until it says it is done, and saying so twice counts once`() {
        val registry = LiveWatchRegistry()
        assertFalse(registry.syncBusy)
        val a = registry.beginSync()
        val b = registry.beginSync()
        assertTrue(registry.syncBusy)
        a.close()
        a.close()
        assertTrue(registry.syncBusy, "the other sync is still going")
        b.close()
        assertFalse(registry.syncBusy)
    }
}
