package nl.tufein.openconnect.core.watch

import nl.tufein.openconnect.core.Repository
import nl.tufein.openconnect.core.formats.FormatError
import nl.tufein.openconnect.core.formats.Formats
import java.time.LocalDate
import java.time.ZoneId

/** The folders of a Garmin watch's `GARMIN` directory that hold something we can read. */
enum class WatchFolder(val dirName: String) {
    ACTIVITY("Activity"),
    MONITOR("Monitor"),
    SLEEP("Sleep"),
    HSA("HSA"),
    ;

    companion object {
        fun fromDirName(name: String): WatchFolder? = entries.firstOrNull { it.dirName.equals(name, ignoreCase = true) }
    }
}

/** A file on the watch. [handle] is whatever the source needs to fetch it again (an MTP object handle, for instance). */
data class WatchFileInfo(val folder: WatchFolder, val name: String, val size: Long, val handle: Long = 0) {
    val path: String get() = "${folder.dirName}/$name"
}

/** Where a watch's files come from: USB today, anything that can list and read files tomorrow. */
interface WatchSource {
    val deviceName: String

    fun listFiles(): List<WatchFileInfo>

    fun read(file: WatchFileInfo): ByteArray

    /**
     * Tells the watch these files are safely stored here (what Garmin Connect does after a sync), when the person
     * switched that on and the watch can do it. Returns how many were marked; most sources mark none.
     */
    fun archive(files: List<WatchFileInfo>): Int = 0

    /**
     * Copies a Connect IQ app or watch face (`.prg`) into `GARMIN/Apps`, the way sideloading over a USB cable works.
     * False when this link cannot write to the watch (only a USB cable can).
     */
    fun installApp(name: String, bytes: ByteArray): Boolean = false

    /**
     * Copies a workout (`.fit`) into `GARMIN/NewFiles`, where a Garmin watch picks up new workouts and courses.
     * False when this link cannot write to the watch (only a USB cable can).
     */
    fun installWorkout(name: String, bytes: ByteArray): Boolean = false
}

/** A watch that lives in memory: for tests, and for trying the sync flow without a watch. Keys look like `Monitor/ABC.FIT`. */
class InMemoryWatchSource(override val deviceName: String, val files: Map<String, ByteArray>) : WatchSource {
    /** What was copied to `GARMIN/Apps`, by file name. */
    val installed = LinkedHashMap<String, ByteArray>()

    /** What was copied to `GARMIN/NewFiles`, by file name. */
    val workouts = LinkedHashMap<String, ByteArray>()

    override fun installWorkout(name: String, bytes: ByteArray): Boolean {
        if (name in workouts) throw WatchFaceException(WatchFaceException.Kind.ALREADY_THERE)
        workouts[name] = bytes
        return true
    }

    override fun installApp(name: String, bytes: ByteArray): Boolean {
        installed[name] = bytes
        return true
    }

    override fun listFiles(): List<WatchFileInfo> = files.mapNotNull { (path, bytes) ->
        val folder = WatchFolder.fromDirName(path.substringBefore('/')) ?: return@mapNotNull null
        WatchFileInfo(folder, path.substringAfter('/'), bytes.size.toLong())
    }

    override fun read(file: WatchFileInfo): ByteArray = files.getValue(file.path)
}

data class WatchSyncProgress(val done: Int, val total: Int, val current: String)

data class WatchSyncReport(
    /** FIT files found in the folders we read. */
    val filesFound: Int = 0,
    val filesRead: Int = 0,
    /** Files not read because the last sync already read them in full. */
    val filesSkipped: Int = 0,
    val activitiesAdded: Int = 0,
    val activitiesKnown: Int = 0,
    val daysUpdated: Int = 0,
    val nightsUpdated: Int = 0,
    val errors: List<String> = emptyList(),
    val cancelled: Boolean = false,
    /** Files marked as synced on the watch (only when archiving is on). */
    val archived: Int = 0,
)

/**
 * Copies what is on a watch into the app's storage.
 *
 * Activities are imported as files. Everything else (Monitor, Sleep, HSA) becomes readings that are stored
 * by metric and time, and the daily summaries are then recalculated from all readings of the affected
 * days, so it does not matter in which order or how often files arrive. A file is read once: a sync
 * skips files whose size matches the last complete read, and retries files that failed.
 */
class WatchSyncService(private val zone: ZoneId = ZoneId.systemDefault()) {
    fun sync(
        source: WatchSource,
        repo: Repository,
        onProgress: (WatchSyncProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): WatchSyncReport {
        val files = source.listFiles()
            .filter { it.name.endsWith(".fit", ignoreCase = true) }
            // wellness first: it is small, and an interrupted sync then still has the days
            .sortedWith(compareBy({ if (it.folder == WatchFolder.ACTIVITY) 1 else 0 }, { it.path }))

        var read = 0
        var skipped = 0
        var added = 0
        var known = 0
        var nights = 0
        var cancelled = false
        val errors = ArrayList<String>()
        val touchedDays = LinkedHashSet<LocalDate>()
        val stored = ArrayList<WatchFileInfo>()

        for ((index, file) in files.withIndex()) {
            if (isCancelled()) {
                cancelled = true
                break
            }
            onProgress(WatchSyncProgress(done = index, total = files.size, current = file.path))
            if (repo.watchFileSize(file.path) == file.size) {
                skipped++
                stored.add(file)
                continue
            }
            try {
                val bytes = source.read(file)
                if (file.folder == WatchFolder.ACTIVITY) {
                    val activity = Formats.import(bytes, file.name)
                        .let { it.copy(source = "watch", deviceName = source.deviceName) }
                    // the same run may already be here under another id, for instance from the Garmin account route
                    val twin = repo.listActivities(start = activity.startTime.minusSeconds(120), end = activity.startTime.plusSeconds(120), limit = 1)
                    if (repo.getActivity(activity.id) == null && twin.isEmpty()) {
                        repo.upsertActivity(activity)
                        added++
                    } else {
                        known++
                    }
                } else {
                    val data = WatchFit.parse(bytes, zone)
                    repo.upsertSamples(data.samples)
                    data.samples.mapTo(touchedDays) { it.day }
                    data.sleep?.let { sleep ->
                        val existing = repo.listSleep(sleep.date, sleep.date).firstOrNull()
                        if (existing == null || existing.durationS <= sleep.durationS) {
                            repo.upsertSleep(sleep)
                            nights++
                        }
                    }
                }
                repo.markWatchFile(file.path, file.size)
                stored.add(file)
                read++
            } catch (e: FormatError) {
                errors.add("${file.path}: ${e.message}")
            } catch (e: java.io.IOException) {
                // One file that cannot be read (a folder entry that vanished, a flaky cable) must not end the sync.
                errors.add("${file.path}: ${e.message ?: "could not be read"}")
            }
        }
        if (files.isNotEmpty() && !cancelled) onProgress(WatchSyncProgress(files.size, files.size, ""))

        for (day in touchedDays) {
            val existing = repo.listDaily(day, day).firstOrNull()
            repo.upsertDaily(DailyAggregator.summarize(day, repo.listSamples(day, day), existing))
        }

        // Only after everything is written, and only for files that were stored; a failure here must not undo the sync.
        val archived = if (cancelled || stored.isEmpty()) 0 else try {
            source.archive(stored)
        } catch (e: Exception) {
            errors.add("archive: ${e.message ?: "failed"}")
            0
        }

        return WatchSyncReport(
            filesFound = files.size,
            filesRead = read,
            filesSkipped = skipped,
            activitiesAdded = added,
            activitiesKnown = known,
            daysUpdated = touchedDays.size,
            nightsUpdated = nights,
            errors = errors,
            cancelled = cancelled,
            archived = archived,
        )
    }
}
