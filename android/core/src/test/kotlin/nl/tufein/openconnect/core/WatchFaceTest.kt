package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.watch.InMemoryWatchSource
import nl.tufein.openconnect.core.watch.MtpEntry
import nl.tufein.openconnect.core.watch.MtpTransport
import nl.tufein.openconnect.core.watch.MtpWatchSource
import nl.tufein.openconnect.core.watch.WatchFace
import nl.tufein.openconnect.core.watch.WatchFaceException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchFaceTest {
    /** A watch over MTP with `Primary/GARMIN` and, optionally, an `Apps` folder; records what is written. */
    private class FakeMtp(withApps: Boolean) : MtpTransport {
        val entries = mutableMapOf<Long?, MutableList<MtpEntry>>(
            null to mutableListOf(MtpEntry(1, "Primary", true, 0)),
            1L to mutableListOf(MtpEntry(2, "GARMIN", true, 0)),
            2L to mutableListOf(MtpEntry(3, "Activity", true, 0)),
        )
        val written = mutableMapOf<String, ByteArray>()
        private var next = 100L

        init {
            if (withApps) entries.getValue(2L) += MtpEntry(4, "APPS", true, 0)
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

    private val face = byteArrayOf(1, 2, 3)

    @Test
    fun `a watch face goes into the existing Apps folder under a safe name`() {
        val mtp = FakeMtp(withApps = true)
        val name = WatchFace.install(MtpWatchSource(mtp, "fenix 8"), "content/Retro Face (v2).prg", face)
        assertEquals("Retro_Face__v2.prg", name)
        assertTrue(mtp.written.getValue("APPS/Retro_Face__v2.prg").contentEquals(face))
    }

    @Test
    fun `the Apps folder is made when the watch has none`() {
        val mtp = FakeMtp(withApps = false)
        WatchFace.install(MtpWatchSource(mtp, "fenix 8"), "face.prg", face)
        assertTrue("Apps/face.prg" in mtp.written)
    }

    @Test
    fun `wrong files and a second copy are refused before anything is written`() {
        val mtp = FakeMtp(withApps = true)
        val watch = MtpWatchSource(mtp, "fenix 8")
        assertEquals(WatchFaceException.Kind.NOT_PRG, assertFailsWith<WatchFaceException> { WatchFace.install(watch, "photo.jpg", face) }.kind)
        assertEquals(WatchFaceException.Kind.EMPTY, assertFailsWith<WatchFaceException> { WatchFace.install(watch, "a.prg", ByteArray(0)) }.kind)
        assertEquals(WatchFaceException.Kind.TOO_BIG, assertFailsWith<WatchFaceException> { WatchFace.install(watch, "a.prg", ByteArray(WatchFace.MAX_BYTES + 1)) }.kind)
        assertTrue(mtp.written.isEmpty())
        WatchFace.install(watch, "a.prg", face)
        assertEquals(WatchFaceException.Kind.ALREADY_THERE, assertFailsWith<WatchFaceException> { WatchFace.install(watch, "a.prg", face) }.kind)
    }

    @Test
    fun `a link that cannot write says so`() {
        val readOnly = object : MtpTransport by FakeMtp(withApps = true) {
            override fun upload(storageId: Int, parent: Long, name: String, bytes: ByteArray) = false
        }
        assertEquals(WatchFaceException.Kind.CANNOT_WRITE, assertFailsWith<WatchFaceException> { WatchFace.install(MtpWatchSource(readOnly, "w"), "a.prg", face) }.kind)
        val memory = InMemoryWatchSource("w", emptyMap())
        WatchFace.install(memory, "a.prg", face)
        assertFalse(memory.installed.isEmpty())
    }
}
