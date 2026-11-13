package nl.tufein.openconnect.sync

import nl.tufein.openconnect.core.SyncLogEntry
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncLogKeeperTest {
    private fun entry(n: Int) = SyncLogEntry(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(n.toLong()), "usb", true, n)

    @Test
    fun `entries are saved at once and found again by the next start`() {
        var stored = ""
        val keeper = SyncLogKeeper({ stored }, { stored = it })
        keeper.record(entry(1))
        keeper.record(entry(2))
        assertEquals(listOf(2, 1), keeper.entries.value.map { it.added })
        val next = SyncLogKeeper({ stored }, { stored = it })
        assertEquals(listOf(2, 1), next.entries.value.map { it.added })
    }

    @Test
    fun `clearing empties the screen and the storage`() {
        var stored = ""
        val keeper = SyncLogKeeper({ stored }, { stored = it })
        keeper.record(entry(1))
        keeper.clear()
        assertTrue(keeper.entries.value.isEmpty())
        assertEquals("", stored)
    }
}
