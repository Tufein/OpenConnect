package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.formats.Formats
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * *Export everything* (v0.56.0): one ZIP that opens without OpenConnect and that OpenConnect can read back.
 *
 * - `daily.csv` and `activities.csv`, for a spreadsheet;
 * - `activities/`: every activity as a FIT file, and as GPX too when it has a position, for other apps;
 * - `openconnect-backup.ocbak`: the backup file, so *Restore* brings everything back, including the journal, shoes and goals;
 * - `README.txt`: what is inside. No keys, tokens or passwords are in any file.
 *
 * The FIT and GPX files are written from what the app stored (the original files are not kept), so they carry the same
 * numbers but not every field the watch recorded. Activities are written one at a time, so a large history does not
 * have to fit in memory twice.
 */
object ExportAll {
    private val FAR_PAST = LocalDate.of(1970, 1, 1)
    private val FAR_FUTURE = LocalDate.of(2200, 1, 1)

    fun write(repo: Repository, userData: UserData, now: Instant, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name).apply { time = now.toEpochMilli() })
                zip.write(bytes)
                zip.closeEntry()
            }
            val summaries = repo.listActivities(limit = Int.MAX_VALUE)
            val taken = HashSet<String>()
            var fit = 0
            var gpx = 0
            for (summary in summaries) {
                val activity = repo.getActivity(summary.id) ?: continue
                put("activities/" + unique(Formats.filename(activity, "fit"), taken), Formats.export(activity, "fit").bytes)
                fit++
                if (activity.hasGps) {
                    put("activities/" + unique(Formats.filename(activity, "gpx"), taken), Formats.export(activity, "gpx").bytes)
                    gpx++
                }
            }
            put("daily.csv", Csv.dailies(repo.listDaily(FAR_PAST, FAR_FUTURE), repo.listSleep(FAR_PAST, FAR_FUTURE)).toByteArray(Charsets.UTF_8))
            put("activities.csv", Csv.activities(summaries).toByteArray(Charsets.UTF_8))
            put("openconnect-backup.ocbak", Backup.write(repo, now, userData))
            put("README.txt", readme(now, summaries.size, fit, gpx).toByteArray(Charsets.UTF_8))
        }
    }

    /** `running-20260610-060000.fit`, then `running-20260610-060000-2.fit` when two activities share the name. */
    private fun unique(name: String, taken: MutableSet<String>): String {
        if (taken.add(name)) return name
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.')
        var n = 2
        while (!taken.add("$stem-$n.$ext")) n++
        return "$stem-$n.$ext"
    }

    private fun readme(now: Instant, activities: Int, fit: Int, gpx: Int): String = """
        Cercis export, ${now.atZone(ZoneOffset.UTC).toLocalDate()}

        daily.csv                  one row per day: steps, heart rate, stress, Body Battery, sleep and more
        activities.csv             one row per activity ($activities in this export)
        activities/*.fit           every activity as a FIT file ($fit), for other apps
        activities/*.gpx           the activities with a position as GPX ($gpx), for maps and other apps
        openconnect-backup.ocbak   everything OpenConnect holds, also the journal, shoes, plan and goals.
                                   Restore it in OpenConnect under Settings, Restore from backup.

        The FIT and GPX files are written from what the app stored; the original files from the watch are not kept.
        There are no keys, tokens or passwords in any file here.
    """.trimIndent() + "\n"
}
