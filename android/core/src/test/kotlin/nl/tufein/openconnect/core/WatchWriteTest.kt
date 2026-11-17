package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.watch.MtpEntry
import nl.tufein.openconnect.core.watch.MtpTransport
import nl.tufein.openconnect.core.watch.MtpWatchSource
import nl.tufein.openconnect.core.watch.WatchWriteException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Writing a file to a watch on the USB cable (MTP). Since v0.43.0 the only thing the app writes is a workout, into
 * `GARMIN/NewFiles`; copying a watch face into `GARMIN/Apps` is gone with the watch face itself.
 */
class WatchWriteTest {
    /** A watch over MTP with `Primary/GARMIN` and, optionally, a `NewFiles` folder; records what is written. */
    private class FakeMtp(withNewFiles: Boolean) : MtpTransport {
        val entries = mutableMapOf<Long?, MutableList<MtpEntry>>(
            null to mutableListOf(MtpEntry(1, "Primary", true, 0)),
            1L to mutableListOf(MtpEntry(2, "GARMIN", true, 0)),
            2L to mutableListOf(MtpEntry(3, "Activity", true, 0)),
        )
        val written = mutableMapOf<String, ByteArray>()
        private var next = 100L

        init {
            if (withNewFiles) entries.getValue(2L) += MtpEntry(4, "NEWFILES", true, 0)
        }

        override fun storageIds() = listOf(65537)
        override fun children(storageId: Int, parent: Long?) = entries[parent].orEmpty().toList()
        override fun read(handle: Long, size: Long) = ByteArray(0)
        override fun createFolder(storageId: Int, parent: Long, name: String): Long {
            val handle = next++
            entries.getOrPut(parent) { mutableListOf() } += MtpEntry(handle, name, true, 0)
            return handle
        }
        override fun upload(storageId: Int, parent: Long, name: String, bytes: ByteArray): Boolean {
            val folder = entries.values.flatten().first { it.handle == parent }.name
            written["$folder/$name"] = bytes
            entries.getOrPut(parent) { mutableListOf() } += MtpEntry(next++, name, false, bytes.size.toLong())
            return true
        }
    }

    private val workout = byteArrayOf(1, 2, 3)

    @Test
    fun `a workout goes into the existing NewFiles folder`() {
        val mtp = FakeMtp(withNewFiles = true)
        assertTrue(MtpWatchSource(mtp, "fenix 8").installWorkout("OC_EASY.fit", workout))
        assertTrue(mtp.written.getValue("NEWFILES/OC_EASY.fit").contentEquals(workout))
    }

    @Test
    fun `the NewFiles folder is made when the watch has none`() {
        val mtp = FakeMtp(withNewFiles = false)
        assertTrue(MtpWatchSource(mtp, "fenix 8").installWorkout("OC_EASY.fit", workout))
        assertTrue("NewFiles/OC_EASY.fit" in mtp.written)
    }

    @Test
    fun `a second copy under the same name is refused and the first one stays`() {
        val mtp = FakeMtp(withNewFiles = true)
        val watch = MtpWatchSource(mtp, "fenix 8")
        watch.installWorkout("OC_EASY.fit", workout)
        val again = assertFailsWith<WatchWriteException> { watch.installWorkout("oc_easy.FIT", byteArrayOf(9)) }
        assertEquals(WatchWriteException.Kind.ALREADY_THERE, again.kind)
        assertEquals(1, mtp.written.size)
    }

    @Test
    fun `a watch that refuses the upload or has no GARMIN folder gives false`() {
        val refusing = object : MtpTransport by FakeMtp(withNewFiles = true) {
            override fun upload(storageId: Int, parent: Long, name: String, bytes: ByteArray) = false
        }
        assertFalse(MtpWatchSource(refusing, "w").installWorkout("OC_EASY.fit", workout))
        val empty = object : MtpTransport by FakeMtp(withNewFiles = true) {
            override fun children(storageId: Int, parent: Long?) = emptyList<MtpEntry>()
        }
        assertFalse(MtpWatchSource(empty, "w").installWorkout("OC_EASY.fit", workout))
    }
}
