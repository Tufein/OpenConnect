package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.formats.Fit
import nl.tufein.openconnect.core.formats.Formats
import nl.tufein.openconnect.core.watch.InMemoryWatchSource
import nl.tufein.openconnect.core.watch.WatchSyncService
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A FIT file says in its first message which device wrote it (file_id: manufacturer and product). That names the watch for
 * every Garmin, whatever route the file came by: a cable (which may only know "Garmin"), a folder (which only knows its
 * own name) or Bluetooth. These tests rewrite that message in a file OpenConnect wrote itself, so no real watch file is
 * needed, and check what comes out.
 */
class FitDeviceNameTest {
    /** The file_id message of an exported file: manufacturer 255 (development), product 0, rewritten to [manufacturer] and [product]. */
    private fun fileFrom(manufacturer: Int, product: Int): ByteArray {
        val data = Formats.export(TestData.activity(), "fit").bytes.copyOf()
        // 14 byte header, a 21 byte definition of file_id (5 fields), then its data message: local number, type, manufacturer, product
        val at = 14 + 21
        check(data[at].toInt() == 0 && data[at + 1].toInt() == 4 && data[at + 2].toInt() and 0xFF == 255) { "the exported file_id is not where this test expects it" }
        data[at + 2] = manufacturer.toByte()
        data[at + 3] = (manufacturer shr 8).toByte()
        data[at + 4] = product.toByte()
        data[at + 5] = (product shr 8).toByte()
        val crc = Fit.crc16(data, 0, data.size - 2)
        data[data.size - 2] = crc.toByte()
        data[data.size - 1] = (crc shr 8).toByte()
        return data
    }

    @Test
    fun aGarminFileNamesItsWatch() {
        assertEquals("Forerunner 965", Formats.import(fileFrom(1, 4315), "run.fit").deviceName)
        assertEquals("fenix 8", Formats.import(fileFrom(1, 4536), "run.fit").deviceName)
        assertEquals("Edge 1040", Formats.import(fileFrom(1, 3843), "ride.fit").deviceName)
    }

    @Test
    fun aFileFromAnotherMakerOrAnUnknownProductNamesNothing() {
        assertNull(Formats.import(fileFrom(255, 4315), "run.fit").deviceName)
        assertNull(Formats.import(fileFrom(1, 60_000), "run.fit").deviceName)
        assertNull(Formats.import(Formats.export(TestData.activity(), "fit").bytes, "own.fit").deviceName)
    }

    @Test
    fun aWatchSyncKeepsTheNameTheFileGivesAndFallsBackToTheTransportsName() {
        val repo = InMemoryRepository()
        val source = InMemoryWatchSource(
            "Garmin",
            mapOf(
                "Activity/A.FIT" to fileFrom(1, 4315),
                "Activity/B.FIT" to Formats.export(TestData.activity(id = "garmin:1002", start = Instant.parse("2026-03-02T08:00:00Z")), "fit").bytes,
            ),
        )
        WatchSyncService(ZoneOffset.UTC).sync(source, repo)
        val names = repo.listActivities().map { it.deviceName }.toSet()
        assertEquals(setOf("Forerunner 965", "Garmin"), names)
    }
}
