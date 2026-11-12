package nl.tufein.openconnect.core.watch

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A watch as MTP shows it: storages holding folders holding files, each with a numeric handle. */
class MtpWatchSourceTest {
    private class Node(val name: String, val folder: Boolean, val content: ByteArray = ByteArray(0), val kids: List<Node> = emptyList())

    private class FakeTransport(private val storages: Map<Int, List<Node>>) : MtpTransport {
        private var next = 100L
        private val byHandle = HashMap<Long, Node>()
        private val kidsOf = HashMap<Long?, List<MtpEntry>>()
        private val rootsOf = HashMap<Int, List<MtpEntry>>()
        val reads = ArrayList<Long>()

        init {
            fun register(nodes: List<Node>): List<MtpEntry> = nodes.map { n ->
                val handle = next++
                byHandle[handle] = n
                if (n.folder) kidsOf[handle] = register(n.kids)
                MtpEntry(handle, n.name, n.folder, n.content.size.toLong())
            }
            for ((id, nodes) in storages) rootsOf[id] = register(nodes)
        }

        override fun storageIds() = storages.keys.toList()
        override fun children(storageId: Int, parent: Long?) = if (parent == null) rootsOf.getValue(storageId) else kidsOf.getValue(parent)
        override fun read(handle: Long, size: Long): ByteArray {
            reads.add(handle)
            return byHandle.getValue(handle).content
        }
    }

    private fun file(name: String, vararg content: Int) = Node(name, false, ByteArray(content.size) { content[it].toByte() })
    private fun dir(name: String, vararg kids: Node) = Node(name, true, kids = kids.toList())

    private val garmin = dir(
        "GARMIN",
        dir("Activity", file("A1.FIT", 1), file("A2.fit", 2, 2), file("notes.txt", 9)),
        dir("Monitor", file("M1.FIT", 3, 3, 3)),
        dir("Sleep", file("S1.FIT", 4)),
        dir("HSA", file("H1.FIT", 5)),
        dir("Metrics", file("X.FIT", 6)), // not something we read
        dir("Temp", file("T.FIT", 7)),
        file("GarminDevice.xml", 8),
    )

    private fun paths(source: MtpWatchSource) = source.listFiles().map { it.path }.sorted()

    @Test
    fun `lists the fit files of the folders we read and nothing else`() {
        val source = MtpWatchSource(FakeTransport(mapOf(65537 to listOf(garmin))), "Forerunner")
        assertEquals(listOf("Activity/A1.FIT", "Activity/A2.fit", "HSA/H1.FIT", "Monitor/M1.FIT", "Sleep/S1.FIT"), paths(source))
    }

    @Test
    fun `sizes and bytes come from the transport by handle`() {
        val transport = FakeTransport(mapOf(1 to listOf(garmin)))
        val source = MtpWatchSource(transport, "Forerunner")
        val monitor = source.listFiles().single { it.folder == WatchFolder.MONITOR }
        assertEquals(3, monitor.size)
        assertContentEquals(byteArrayOf(3, 3, 3), source.read(monitor))
        assertEquals(listOf(monitor.handle), transport.reads)
    }

    @Test
    fun `finds the garmin folder one level down, as on watches that show a Primary volume`() {
        val source = MtpWatchSource(FakeTransport(mapOf(1 to listOf(dir("Primary", garmin)))), "Fenix")
        assertEquals(5, source.listFiles().size)
    }

    @Test
    fun `folder names are matched without regard to case`() {
        val lower = dir("garmin", dir("activity", file("a.fit", 1)), dir("MONITOR", file("m.fit", 2)))
        val source = MtpWatchSource(FakeTransport(mapOf(1 to listOf(lower))), "Venu")
        assertEquals(listOf("Activity/a.fit", "Monitor/m.fit"), paths(source))
    }

    @Test
    fun `a storage without a garmin folder is skipped, the one with it is used`() {
        val transport = FakeTransport(mapOf(1 to listOf(dir("DCIM", file("x.jpg", 1))), 2 to listOf(garmin)))
        assertEquals(5, MtpWatchSource(transport, "Edge").listFiles().size)
    }

    @Test
    fun `a device without a garmin folder lists nothing`() {
        assertTrue(MtpWatchSource(FakeTransport(mapOf(1 to listOf(dir("Music")))), "Phone").listFiles().isEmpty())
        assertTrue(MtpWatchSource(FakeTransport(emptyMap()), "Nothing").listFiles().isEmpty())
    }

    @Test
    fun `the device name is what the transport owner passed in`() {
        assertEquals("Forerunner 265", MtpWatchSource(FakeTransport(emptyMap()), "Forerunner 265").deviceName)
    }
}
