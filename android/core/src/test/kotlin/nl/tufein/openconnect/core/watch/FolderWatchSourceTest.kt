package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.InMemoryRepository
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderWatchSourceTest {
    /** A tree in memory: `Dir("GARMIN", Dir("Monitor", File(...)))`. */
    private sealed interface Node {
        val name: String
    }

    private class Dir(override val name: String, vararg val items: Node) : Node

    private class File(override val name: String, val bytes: ByteArray = ByteArray(3) { it.toByte() }) : Node

    private class MemoryTree(vararg roots: Node) : FileTree {
        private val nodes = ArrayList<Node>()
        private val parents = HashMap<Long, List<Long>>()
        private val top: List<Long>

        init {
            fun register(node: Node): Long {
                val handle = nodes.size.toLong()
                nodes.add(node)
                if (node is Dir) parents[handle] = node.items.map { register(it) }
                return handle
            }
            top = roots.map { register(it) }
        }

        override fun children(parent: Long?): List<FileTree.Entry> = (if (parent == null) top else parents.getValue(parent)).map { h ->
            val node = nodes[h.toInt()]
            FileTree.Entry(node.name, node is Dir, (node as? File)?.bytes?.size?.toLong() ?: 0, h)
        }

        override fun read(handle: Long, size: Long): ByteArray = (nodes[handle.toInt()] as File).bytes
    }

    private fun paths(tree: FileTree) = FolderWatchSource(tree, "folder").listFiles().map { it.path }.sorted()

    @Test
    fun `the GARMIN folder itself can be picked`() {
        val tree = MemoryTree(Dir("Monitor", File("A.FIT")), Dir("Sleep", File("B.FIT")), Dir("Activity", File("C.fit")), Dir("HSA", File("D.FIT")))
        assertEquals(listOf("Activity/C.fit", "HSA/D.FIT", "Monitor/A.FIT", "Sleep/B.FIT"), paths(tree))
    }

    @Test
    fun `the folder that holds GARMIN works too, at any depth`() {
        val tree = MemoryTree(Dir("Download", Dir("backup", Dir("GARMIN", Dir("Monitor", File("A.FIT"), Dir("old", File("X.FIT"))), File("GarminDevice.xml")))))
        assertEquals(listOf("Monitor/A.FIT", "Monitor/X.FIT"), paths(tree)) // folders below a watch folder keep its meaning
    }

    @Test
    fun `folder names are matched without regard to case`() {
        assertEquals(listOf("Sleep/B.FIT"), paths(MemoryTree(Dir("SLEEP", File("B.FIT")))))
    }

    @Test
    fun `files with no watch folder above them are tried as activities`() {
        val tree = MemoryTree(File("run.fit"), File("notes.txt"), Dir("exports", File("ride.FIT")))
        assertEquals(listOf("Activity/ride.FIT", "Activity/run.fit"), paths(tree))
    }

    @Test
    fun `only fit files are listed and the same file name is listed once`() {
        val tree = MemoryTree(Dir("Monitor", File("A.FIT"), File("A.FIT"), File("readme.md"), File("A.FIT.bak")), Dir("Other", Dir("Monitor", File("A.FIT"))))
        assertEquals(listOf("Monitor/A.FIT"), paths(tree))
    }

    @Test
    fun `very deep folders are not followed`() {
        var node: Node = File("deep.fit")
        repeat(10) { node = Dir("d$it", node) }
        assertTrue(paths(MemoryTree(node)).isEmpty())
    }

    @Test
    fun `reading gives the file's bytes`() {
        val bytes = byteArrayOf(9, 8, 7)
        val source = FolderWatchSource(MemoryTree(Dir("Monitor", File("A.FIT", bytes))), "folder")
        assertContentEquals(bytes, source.read(source.listFiles().single()))
    }

    @Test
    fun `a folder of real watch files syncs like a watch on a cable`() {
        fun fixture(name: String) = checkNotNull(FolderWatchSourceTest::class.java.getResourceAsStream("/watch/$name")).readBytes()
        val tree = MemoryTree(
            Dir(
                "GARMIN",
                Dir("Monitor", File("46DB3F05.FIT", fixture("monitoring.fit"))),
                Dir("Sleep", File("46DC0001.FIT", fixture("sleep.fit"))),
                Dir("HSA", File("46DB0002.FIT", fixture("hsa.fit"))),
                Dir("Activity", File("run.fit", checkNotNull(FolderWatchSourceTest::class.java.getResourceAsStream("/fixtures/python-run.fit")).readBytes())),
            ),
        )
        val fromFolder = InMemoryRepository()
        val report = WatchSyncService(ZoneOffset.UTC).sync(FolderWatchSource(tree, "Downloads"), fromFolder)
        assertEquals(4, report.filesRead)
        assertEquals(1, report.activitiesAdded)

        val fromCable = InMemoryRepository()
        WatchSyncService(ZoneOffset.UTC).sync(
            InMemoryWatchSource(
                "watch",
                mapOf(
                    "Monitor/46DB3F05.FIT" to fixture("monitoring.fit"),
                    "Sleep/46DC0001.FIT" to fixture("sleep.fit"),
                    "HSA/46DB0002.FIT" to fixture("hsa.fit"),
                ),
            ),
            fromCable,
        )
        val days = LocalDate.parse("2026-06-10") to LocalDate.parse("2026-06-11")
        assertEquals(fromCable.listDaily(days.first, days.second), fromFolder.listDaily(days.first, days.second))
    }
}
