package nl.tufein.openconnect.core.watch

/**
 * The few things needed from a folder somewhere on the phone, whatever app put it there (a copy of the watch's
 * `GARMIN` folder, a folder an app like Gadgetbridge exports to, a downloads folder with watch files). The Android
 * implementation reads a folder the user picked; tests use a tree in memory.
 */
interface FileTree {
    class Entry(val name: String, val isDirectory: Boolean, val size: Long, val handle: Long)

    /** The entries inside [parent], or the entries of the folder that was picked when [parent] is null. */
    fun children(parent: Long?): List<Entry>

    fun read(handle: Long, size: Long): ByteArray
}

/**
 * Watch files in a folder the user picked. They may have picked the `GARMIN` folder itself, the folder that holds
 * it, or one that holds nothing but `.fit` files: a file counts for the nearest folder above it that is named
 * like one of the watch's (`Activity`, `Monitor`, `Sleep`, `HSA`), and a file that has none is tried as an activity.
 */
class FolderWatchSource(
    private val tree: FileTree,
    override val deviceName: String,
    private val maxDepth: Int = 6,
) : WatchSource {
    override fun listFiles(): List<WatchFileInfo> {
        val found = LinkedHashMap<String, WatchFileInfo>()
        visit(null, WatchFolder.ACTIVITY, depth = 0, found)
        return found.values.toList()
    }

    private fun visit(parent: Long?, folder: WatchFolder, depth: Int, found: MutableMap<String, WatchFileInfo>) {
        for (entry in tree.children(parent)) {
            if (entry.isDirectory) {
                if (depth < maxDepth) visit(entry.handle, WatchFolder.fromDirName(entry.name) ?: folder, depth + 1, found)
            } else if (entry.name.endsWith(".fit", ignoreCase = true)) {
                val info = WatchFileInfo(folder, entry.name, entry.size, entry.handle)
                found.putIfAbsent(info.path, info)
            }
        }
    }

    override fun read(file: WatchFileInfo): ByteArray = tree.read(file.handle, file.size)
}
