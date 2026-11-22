package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.formats.FormatError
import nl.tufein.openconnect.core.formats.Formats
import nl.tufein.openconnect.core.formats.defaultName
import nl.tufein.openconnect.core.formats.NoActivityData
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.TreeSet
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * *Verhuisdoos* (v1.3.0, milestone M13): bring a whole archive of activities in with one import.
 *
 * What it reads, by content first and by name second (a file picked from the phone's storage often has a name like
 * `msf:1234`, without an extension):
 *
 * - a **ZIP**, also a ZIP inside a ZIP (Garmin's export keeps the watch's FIT files in `UploadedFiles_*.zip` inside the
 *   export), up to [Limits.maxDepth] levels;
 * - a **gzip** file or member (Strava writes `activities/123.fit.gz` and `.tcx.gz`);
 * - **FIT, GPX and TCX** files, loose or inside any of the above;
 * - OpenConnect's own *Export everything* ZIP (its `.ocbak` backup is not read here; the report says there is one, so
 *   the person can use *Restore from backup* for the journal, shoes and goals).
 *
 * The archive is **streamed**: only one activity file (and one batch of activities) is in memory at a time, so a
 * multi-gigabyte Strava export is no problem. The limits guard against a ZIP bomb: one file may not be larger than
 * [Limits.maxFileBytes] (also after unpacking), and the walk stops after [Limits.maxTotalBytes] or [Limits.maxEntries].
 * Nothing is ever written to disk, so a name such as `../../x` is only a label.
 *
 * **What is already there stays as it is.** An activity is "already there" when its id exists, or when another activity
 * starts within [NEAR_SECONDS] of it (the same training from the watch, the Garmin account or an earlier import: ids
 * differ between sources, start times do not). Those are never overwritten, because the one in the app may carry a
 * name, shoes or watch data that the file does not have. Importing the same archive twice therefore adds nothing.
 *
 * What is not an activity (routes, workouts, photos, CSV and JSON files) is skipped and counted, not reported as a
 * failure. A broken file is a failure with its name and a reason, and the rest carries on.
 *
 * **Names (v1.4.0).** Strava's `activities.csv` and Garmin's `summarizedActivities.json` carry the names people gave their
 * activities (see [ArchiveMeta]). They are collected while the archive is read, in whatever order the files come, and
 * applied at the end to the activities this import brought in, and to activities that are already there under the same id
 * and still carry the name the app made up ("Running 2026-05-02"). A name the person or another source gave is never changed.
 * The JSON files that are not read have their file name pattern and key names written down in [Report.shapes].
 */
object ArchiveImport {
    /** Two activities that start this close together are taken to be the same training. */
    const val NEAR_SECONDS = 90L

    private const val MAX_FAILURES_KEPT = 20
    private const val HEAD = 2048
    private const val MAX_SHAPES = 25
    private const val SHAPE_HEAD = 64 * 1024

    /** A names file (CSV or JSON) larger than this is not read; the activities still come in. */
    private const val META_LIMIT = 32 * 1024 * 1024

    data class Limits(
        /** One activity file, before and after unpacking. */
        val maxFileBytes: Int = 25 * 1024 * 1024,
        /** All file contents together; a ZIP bomb stops here. */
        val maxTotalBytes: Long = 16L * 1024 * 1024 * 1024,
        val maxEntries: Int = 500_000,
        /** How many ZIPs may sit inside each other. */
        val maxDepth: Int = 3,
    )

    enum class Stop { CANCELLED, TOO_MANY_FILES, TOO_LARGE }

    /** Where the walk is. [entries] counts the files looked at, not the folders or the ZIPs themselves. */
    data class Progress(val entries: Int, val imported: Int, val alreadyThere: Int)

    data class Failure(val name: String, val reason: String)

    /** What a JSON file this version does not read looks like: its name pattern, how many there were, and its key names. */
    data class JsonShape(val pattern: String, val files: Int, val keys: List<String>)

    data class Report(
        val entries: Int,
        val imported: Int,
        val alreadyThere: Int,
        /** Files that are not activities: CSV, JSON, photos, routes, workouts. */
        val skipped: Int,
        /** The first few; [failureCount] is the whole number. */
        val failures: List<Failure>,
        val failureCount: Int,
        /** The oldest and the newest activity this import brought in. */
        val first: Instant?,
        val last: Instant?,
        /** An OpenConnect backup (`.ocbak`) was inside. */
        val backupInside: Boolean,
        val stopped: Stop?,
        /** Activities that got their own name from the archive's CSV or JSON. */
        val named: Int = 0,
        val shapes: List<JsonShape> = emptyList(),
    ) {
        /** Two imports in a row (several files were picked) as one report. */
        operator fun plus(other: Report) = Report(
            entries + other.entries, imported + other.imported, alreadyThere + other.alreadyThere, skipped + other.skipped,
            (failures + other.failures).take(MAX_FAILURES_KEPT), failureCount + other.failureCount,
            listOfNotNull(first, other.first).minOrNull(), listOfNotNull(last, other.last).maxOrNull(),
            backupInside || other.backupInside, stopped ?: other.stopped,
            named + other.named, mergeShapes(shapes + other.shapes),
        )

        /**
         * A report to paste into a GitHub issue: counts, the period, the names of the files that failed and the shape of the
         * JSON files that were not read. No activity names, no values and no e-mail addresses (those are blanked).
         */
        fun diagnostics(): String = buildString {
            appendLine("OpenConnect archive import report")
            appendLine("files looked at: $entries")
            appendLine("new: $imported")
            appendLine("already there: $alreadyThere")
            appendLine("skipped (not activities): $skipped")
            appendLine("failed: $failureCount")
            appendLine("named: $named")
            if (first != null && last != null) appendLine("period: ${first.atZone(ZoneOffset.UTC).toLocalDate()} to ${last.atZone(ZoneOffset.UTC).toLocalDate()}")
            if (stopped != null) appendLine("stopped: $stopped")
            if (backupInside) appendLine("an OpenConnect backup was inside")
            if (failures.isNotEmpty()) {
                appendLine("failures:")
                failures.forEach { appendLine("- ${ArchiveMeta.redact(it.name)}: ${ArchiveMeta.redact(it.reason)}") }
            }
            if (shapes.isNotEmpty()) {
                appendLine("json files not read (name pattern and key names only, no values):")
                shapes.forEach { appendLine("- ${it.pattern} (${it.files} file(s)): ${it.keys.joinToString(", ")}") }
            }
        }
    }

    private fun mergeShapes(all: List<JsonShape>): List<JsonShape> {
        val merged = LinkedHashMap<String, JsonShape>()
        for (shape in all) {
            val known = merged[shape.pattern]
            if (known != null) {
                merged[shape.pattern] = JsonShape(shape.pattern, known.files + shape.files, (known.keys + shape.keys).distinct().take(ArchiveMeta.MAX_KEYS))
            } else if (merged.size < MAX_SHAPES) {
                merged[shape.pattern] = shape
            }
        }
        return merged.values.toList()
    }

    private enum class Kind { ZIP, GZIP, ACTIVITY, OTHER }

    /** Unwinds the walk when a limit is hit or the person cancels. */
    private class StopWalking(val why: Stop) : RuntimeException(why.name, null, false, false)

    /** A stream whose close leaves the one underneath open: a ZIP inside a ZIP shares the outer stream. */
    private class Keep(stream: InputStream) : FilterInputStream(stream) {
        override fun close() = Unit
    }

    private class Counting(stream: InputStream, private val onBytes: (Int) -> Unit) : FilterInputStream(stream) {
        override fun read(): Int = super.read().also { if (it >= 0) onBytes(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) onBytes(it) }
        override fun close() = Unit
    }

    /** An activity this import may give a name to: [originBase] is the last part of its path in the archive, lower case. */
    private class Candidate(val id: String, val startSec: Long, val originBase: String)

    private class Run(
        val repo: Repository,
        val limits: Limits,
        val transaction: (() -> Unit) -> Unit,
        val batch: Int,
        val shouldStop: () -> Boolean,
        val onProgress: (Progress) -> Unit,
    ) {
        var entries = 0
        var imported = 0
        var alreadyThere = 0
        var skipped = 0
        var failureCount = 0
        var backupInside = false
        var bytes = 0L
        var first: Instant? = null
        var last: Instant? = null
        val failures = ArrayList<Failure>()
        val pending = ArrayList<Activity>()
        val pendingIds = HashSet<String>()
        val pendingStarts = TreeSet<Long>()
        val candidates = ArrayList<Candidate>()
        val stravaNames = HashMap<String, String>()
        val garminNames = ArrayList<ArchiveMeta.GarminName>()
        val shapes = LinkedHashMap<String, JsonShape>()
        var named = 0

        fun fail(name: String, reason: String) {
            failureCount++
            if (failures.size < MAX_FAILURES_KEPT) failures.add(Failure(name, reason))
        }

        fun flush() {
            if (pending.isEmpty()) return
            val toWrite = ArrayList(pending)
            transaction { toWrite.forEach(repo::upsertActivity) }
            pending.clear()
        }

        /** Another activity that starts within [NEAR_SECONDS]: in the app, or waiting in this batch. */
        fun nearOneThere(a: Activity): Boolean {
            val sec = a.startTime.epochSecond
            val near = pendingStarts.ceiling(sec - NEAR_SECONDS)
            if (near != null && near <= sec + NEAR_SECONDS) return true
            return repo.listActivities(
                start = a.startTime.minusSeconds(NEAR_SECONDS),
                end = a.startTime.plusSeconds(NEAR_SECONDS + 1),
                limit = 1,
            ).isNotEmpty()
        }

        fun add(a: Activity) {
            pending.add(a)
            pendingIds.add(a.id)
            pendingStarts.add(a.startTime.epochSecond)
            imported++
            if (first == null || a.startTime < first) first = a.startTime
            if (last == null || a.startTime > last) last = a.startTime
            if (pending.size >= batch) flush()
        }

        /** A file (not a container) is being looked at. */
        fun leaf() {
            entries++
            if (entries > limits.maxEntries) throw StopWalking(Stop.TOO_MANY_FILES)
        }

        fun progress() = onProgress(Progress(entries, imported, alreadyThere))
    }

    /**
     * Reads [input] (a ZIP, a gzip file or one activity file; [name] is only a hint) and adds what is new to [repo].
     * [transaction] wraps each batch of [batch] writes (the app passes the database's transaction, so thousands of
     * activities are not thousands of disk syncs). [shouldStop] is asked before each file; true ends the import after
     * what came in so far. The caller closes [input].
     */
    fun import(
        input: InputStream,
        name: String,
        repo: Repository,
        limits: Limits = Limits(),
        transaction: (() -> Unit) -> Unit = { it() },
        batch: Int = 25,
        shouldStop: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {},
    ): Report {
        val run = Run(repo, limits, transaction, batch.coerceAtLeast(1), shouldStop, onProgress)
        var stopped: Stop? = null
        try {
            attempt(run, name) { dispatch(run, BufferedInputStream(Keep(input), 8192), name, name, depth = 0) }
        } catch (e: StopWalking) {
            stopped = e.why
        } finally {
            run.flush()
        }
        applyNames(run)
        run.progress()
        return Report(
            run.entries, run.imported, run.alreadyThere, run.skipped, run.failures, run.failureCount,
            run.first, run.last, run.backupInside, stopped, run.named, run.shapes.values.toList(),
        )
    }

    /** Runs [block]; a damaged or unreadable stream becomes a failure named [name], a limit or a cancel unwinds. */
    private fun attempt(run: Run, name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: StopWalking) {
            throw e
        } catch (e: IOException) {
            run.fail(name, e.message ?: "could not be read")
        }
    }

    /**
     * One stream: work out what it is and deal with it. [name] is what the file is called now (a gzip file without its
     * `.gz`), [origin] the path it has in the archive, and [depth] how many ZIPs we are inside.
     */
    private fun dispatch(run: Run, stream: BufferedInputStream, name: String, origin: String, depth: Int) {
        when (peek(stream, name)) {
            Kind.ZIP -> walkZip(run, stream, name, depth)
            Kind.GZIP -> {
                val inner = if (name.endsWith(".gz", ignoreCase = true)) name.dropLast(3) else name
                dispatch(run, BufferedInputStream(GZIPInputStream(Keep(stream)), 8192), inner, origin, depth)
            }
            Kind.ACTIVITY -> {
                run.leaf()
                activity(run, stream, name, origin)
                run.progress()
            }
            Kind.OTHER -> {
                run.leaf()
                other(run, stream, name)
                run.progress()
            }
        }
    }

    /** A file that is not an activity: a backup, the names files, a JSON file to learn the shape of, or just something else. */
    private fun other(run: Run, stream: InputStream, name: String) {
        val base = name.lowercase().substringAfterLast('/')
        when {
            base.endsWith(".ocbak") -> run.backupInside = true
            base == "activities.csv" -> readNames(run, stream, name) { text ->
                ArchiveMeta.stravaNames(text).also { run.stravaNames.putAll(it) }.size
            }
            base.endsWith(".json") && "summarizedactivities" in base -> readNames(run, stream, name) { text ->
                ArchiveMeta.garminNames(text).also { run.garminNames.addAll(it) }.size
            }
            base.endsWith(".json") -> {
                run.skipped++
                shape(run, stream, name)
            }
            else -> run.skipped++
        }
    }

    /** Reads a names file and hands its text to [use], which returns how many names it found; none means it was no use, so it counts as skipped. */
    private fun readNames(run: Run, stream: InputStream, name: String, use: (String) -> Int) {
        val bytes = readLimited(stream, META_LIMIT)
        if (bytes == null) {
            run.fail(name, "too large to read the names from (more than ${META_LIMIT / (1024 * 1024)} MB)")
            return
        }
        try {
            if (use(String(bytes, Charsets.UTF_8)) == 0) run.skipped++
        } catch (e: IllegalArgumentException) {
            run.fail(name, "not valid JSON, so no names were read from it")
        }
    }

    /** Writes down what a JSON file we do not read looks like: the pattern of its name and its key names, nothing else. */
    private fun shape(run: Run, stream: InputStream, name: String) {
        val pattern = ArchiveMeta.shapePattern(name)
        val known = run.shapes[pattern]
        if (known == null && run.shapes.size >= MAX_SHAPES) return
        val head = ByteArray(SHAPE_HEAD)
        var n = 0
        while (n < head.size) {
            val r = stream.read(head, n, head.size - n)
            if (r < 0) break
            n += r
        }
        val keys = ArchiveMeta.shapeKeys(head.copyOf(n))
        run.shapes[pattern] = JsonShape(pattern, (known?.files ?: 0) + 1, ((known?.keys ?: emptyList()) + keys).distinct().take(ArchiveMeta.MAX_KEYS))
    }

    private fun walkZip(run: Run, stream: InputStream, name: String, depth: Int) {
        if (depth + 1 > run.limits.maxDepth) {
            run.fail(name, "ZIP nested too deep (more than ${run.limits.maxDepth} levels)")
            return
        }
        var seen = 0
        val zip = ZipInputStream(Keep(stream), Charsets.UTF_8)
        while (true) {
            val entry = try {
                zip.nextEntry
            } catch (e: IOException) {
                run.fail(name, "the archive is damaged after $seen file(s): ${e.message ?: "unreadable"}")
                return
            } catch (e: IllegalArgumentException) {
                run.fail(name, "the archive is damaged after $seen file(s): a file name could not be read")
                return
            } ?: break
            if (entry.isDirectory || isJunk(entry.name)) continue
            if (run.shouldStop()) throw StopWalking(Stop.CANCELLED)
            seen++
            // Only what comes out of the ZIP counts towards the total, so the size of the archive itself never trips it.
            val counted = Counting(Keep(zip)) { n ->
                run.bytes += n
                if (run.bytes > run.limits.maxTotalBytes) throw StopWalking(Stop.TOO_LARGE)
            }
            attempt(run, entry.name) { dispatch(run, BufferedInputStream(counted, 8192), entry.name, entry.name, depth + 1) }
        }
    }

    private fun isJunk(path: String): Boolean {
        val file = path.substringAfterLast('/')
        return path.startsWith("__MACOSX/") || "/__MACOSX/" in path || file.startsWith("._") || file == ".DS_Store" || file == "Thumbs.db"
    }

    /** Looks at the first bytes without using them up, and says what the stream is. */
    private fun peek(stream: BufferedInputStream, name: String): Kind {
        stream.mark(HEAD + 16)
        val head = ByteArray(HEAD)
        var n = 0
        while (n < head.size) {
            val r = stream.read(head, n, head.size - n)
            if (r < 0) break
            n += r
        }
        stream.reset()
        if (n >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            (head[2].toInt() == 3 || head[2].toInt() == 5) && (head[3].toInt() == 4 || head[3].toInt() == 6)
        ) {
            return Kind.ZIP
        }
        // The backup file is gzip inside, but it is not an activity and not for this import.
        if (name.endsWith(".ocbak", ignoreCase = true)) return Kind.OTHER
        if (n >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte()) return Kind.GZIP
        val sniffed = runCatching { Formats.detect(head.copyOf(n), name) }.getOrNull()
        return if (sniffed != null) Kind.ACTIVITY else Kind.OTHER
    }

    private fun activity(run: Run, stream: InputStream, name: String, origin: String) {
        val bytes = readLimited(stream, run.limits.maxFileBytes)
        if (bytes == null) {
            run.fail(name, "larger than ${run.limits.maxFileBytes / (1024 * 1024)} MB")
            return
        }
        val a = try {
            Formats.import(bytes, name)
        } catch (e: NoActivityData) {
            run.skipped++
            return
        } catch (e: FormatError) {
            run.fail(name, e.message ?: "not readable")
            return
        } catch (e: RuntimeException) {
            run.fail(name, "could not be read (${e.javaClass.simpleName})")
            return
        }
        val originBase = origin.substringAfterLast('/').lowercase()
        val sameRun = a.id in run.pendingIds
        val existing = if (sameRun) null else run.repo.getActivity(a.id)
        when {
            sameRun -> run.alreadyThere++
            existing != null -> {
                run.alreadyThere++
                // Still named by the app itself, so nobody chose that name: an archive that brings a real one may replace it.
                if (existing.name == defaultName(existing)) run.candidates.add(Candidate(existing.id, existing.startTime.epochSecond, originBase))
            }
            run.nearOneThere(a) -> run.alreadyThere++
            else -> {
                run.add(a)
                run.candidates.add(Candidate(a.id, a.startTime.epochSecond, originBase))
            }
        }
    }

    /** Gives the candidates the names the archive carried, once everything has been read (the CSV may come last). */
    private fun applyNames(run: Run) {
        if (run.candidates.isEmpty() || (run.stravaNames.isEmpty() && run.garminNames.isEmpty())) return
        val garmin = run.garminNames.sortedBy { it.epochSec }
        val starts = garmin.map { it.epochSec }
        val updates = ArrayList<Activity>()
        fun write() {
            if (updates.isEmpty()) return
            val batch = ArrayList(updates)
            run.transaction { batch.forEach(run.repo::upsertActivity) }
            run.named += batch.size
            updates.clear()
        }
        for (c in run.candidates) {
            val name = run.stravaNames[c.originBase] ?: garminNameAt(garmin, starts, c.startSec) ?: continue
            val current = run.repo.getActivity(c.id) ?: continue
            if (current.name == name) continue
            updates.add(current.copy(name = name))
            if (updates.size >= run.batch) write()
        }
        write()
    }

    /** The Garmin name whose start is nearest to [sec], within [NEAR_SECONDS]. */
    private fun garminNameAt(garmin: List<ArchiveMeta.GarminName>, starts: List<Long>, sec: Long): String? {
        var i = starts.binarySearch(sec)
        if (i < 0) i = -i - 1
        val best = listOfNotNull(garmin.getOrNull(i - 1), garmin.getOrNull(i)).minByOrNull { kotlin.math.abs(it.epochSec - sec) } ?: return null
        return if (kotlin.math.abs(best.epochSec - sec) <= NEAR_SECONDS) best.name else null
    }

    /** All bytes of [stream], or null as soon as there are more than [limit]. */
    private fun readLimited(stream: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }
}
