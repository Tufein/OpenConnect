// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.core.watch.ble

import nl.tufein.openconnect.core.watch.WatchFileInfo
import nl.tufein.openconnect.core.watch.WatchFolder
import nl.tufein.openconnect.core.watch.WatchSource
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The few things [GarminBleWatchSource] needs from a [GarminBleSession]; tests can stand in for it. */
interface GarminFileAccess {
    fun listDirectory(): List<GarminDirectoryEntry>

    fun download(index: Int): ByteArray

    /** Marks a file as synced on the watch. */
    fun archive(index: Int) {}
}

fun GarminBleSession.asFileAccess(): GarminFileAccess = object : GarminFileAccess {
    override fun listDirectory() = this@asFileAccess.listDirectory()
    override fun download(index: Int) = this@asFileAccess.download(index)
    override fun archive(index: Int) = this@asFileAccess.archive(index)
}

/**
 * A watch on Bluetooth seen as a [WatchSource], so the same [nl.tufein.openconnect.core.watch.WatchSyncService] that
 * reads a USB cable or a folder reads it too: the directory becomes the file list, and a file is only downloaded when
 * the sync service wants it (it skips files it read in full before, so a second sync downloads only what is new).
 *
 * Names are stable across syncs: `Monitor/MONITOR_2026-11-13_06-00-00_1234.fit` (UTC time of the file, its index).
 */
class GarminBleWatchSource(
    private val files: GarminFileAccess,
    override val deviceName: String,
    /** Off unless the person switched it on: marking files synced makes the watch treat them as sent to Garmin Connect. */
    private val archiveAfterRead: Boolean = false,
) : WatchSource {
    companion object {
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC)

        fun folderOf(type: GarminFileType?): WatchFolder? = when (type) {
            GarminFileType.ACTIVITY -> WatchFolder.ACTIVITY
            GarminFileType.MONITOR -> WatchFolder.MONITOR
            GarminFileType.SLEEP -> WatchFolder.SLEEP
            GarminFileType.HSA -> WatchFolder.HSA
            else -> null
        }

        fun fileName(entry: GarminDirectoryEntry): String {
            val label = entry.fileType?.label ?: "FILE_${entry.dataType}_${entry.subType}"
            val date = entry.date?.let { "_" + STAMP.format(it) } ?: ""
            return "$label${date}_${entry.index}.fit"
        }
    }

    /** What the last [listFiles] saw, including the files OpenConnect does not read. */
    var lastDirectory: List<GarminDirectoryEntry> = emptyList()
        private set

    override fun listFiles(): List<WatchFileInfo> {
        val entries = files.listDirectory()
        lastDirectory = entries
        return entries.mapNotNull { entry ->
            val folder = folderOf(entry.fileType) ?: return@mapNotNull null
            if (entry.size == 0L) return@mapNotNull null
            WatchFileInfo(folder, fileName(entry), entry.size, entry.index.toLong())
        }
    }

    override fun read(file: WatchFileInfo): ByteArray = try {
        files.download(file.handle.toInt())
    } catch (e: GarminBleException) {
        // the sync service carries on with the next file after an IOException
        throw java.io.IOException(e.message, e)
    }

    override fun archive(files: List<WatchFileInfo>): Int {
        if (!archiveAfterRead) return 0
        var marked = 0
        for (file in files) {
            try {
                this.files.archive(file.handle.toInt())
                marked++
            } catch (e: GarminBleException) {
                // a watch that refuses one file may still take the next; a lost link or silence would fail the same way again
                if (e.reason != GarminBleException.Reason.REFUSED) break
            }
        }
        return marked
    }
}
