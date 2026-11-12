package nl.tufein.openconnect.core.watch

/** One entry of an MTP directory listing. */
data class MtpEntry(val handle: Long, val name: String, val isFolder: Boolean, val size: Long)

/**
 * The few things we need from an MTP connection. The Android implementation wraps `android.mtp.MtpDevice`;
 * keeping this seam means the folder logic below can be tested without a USB stack.
 */
interface MtpTransport {
    fun storageIds(): List<Int>

    /** The entries inside [parent], or the entries at the root of the storage when [parent] is null. */
    fun children(storageId: Int, parent: Long?): List<MtpEntry>

    fun read(handle: Long, size: Long): ByteArray
}

/**
 * A Garmin watch connected with a USB cable. Recent watches show up as an MTP device with a `GARMIN`
 * folder (sometimes inside a `Primary` volume) holding `Activity`, `Monitor`, `Sleep`, `HSA` and more.
 */
class MtpWatchSource(private val transport: MtpTransport, override val deviceName: String) : WatchSource {
    private companion object {
        const val GARMIN_DIR = "GARMIN"
    }

    override fun listFiles(): List<WatchFileInfo> {
        val garmin = findGarminDir() ?: return emptyList()
        return transport.children(garmin.first, garmin.second).asSequence()
            .filter { it.isFolder }
            .mapNotNull { dir -> WatchFolder.fromDirName(dir.name)?.let { it to dir } }
            .flatMap { (folder, dir) ->
                transport.children(garmin.first, dir.handle).asSequence()
                    .filter { !it.isFolder && it.name.endsWith(".fit", ignoreCase = true) }
                    .map { WatchFileInfo(folder, it.name, it.size, it.handle) }
            }
            .toList()
    }

    override fun read(file: WatchFileInfo): ByteArray = transport.read(file.handle, file.size)

    /** The storage and handle of the GARMIN folder, at the root of a storage or one folder below it. */
    private fun findGarminDir(): Pair<Int, Long>? {
        for (storage in transport.storageIds()) {
            val top = transport.children(storage, null)
            top.firstOrNull { it.isFolder && it.name.equals(GARMIN_DIR, ignoreCase = true) }
                ?.let { return storage to it.handle }
            for (volume in top.filter { it.isFolder }) {
                transport.children(storage, volume.handle)
                    .firstOrNull { it.isFolder && it.name.equals(GARMIN_DIR, ignoreCase = true) }
                    ?.let { return storage to it.handle }
            }
        }
        return null
    }
}
