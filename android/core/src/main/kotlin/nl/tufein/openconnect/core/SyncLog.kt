package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate

/** One finished sync, good or bad. [source] is one of the SOURCE_ keys, [added] what it brought in, [detail] the reason it failed. */
data class SyncLogEntry(val time: Instant, val source: String, val ok: Boolean, val added: Int = 0, val detail: String = "")

/**
 * The record of the last syncs, newest first, so a failed sync can be explained afterwards. Kept as plain text lines
 * (time, source, ok, added, detail separated by tabs) so the phone can store it in one preference.
 */
object SyncLog {
    const val MAX = 50
    const val SOURCE_USB = "usb"
    const val SOURCE_FOLDER = "folder"
    const val SOURCE_BLE = "garmin-ble"
    const val SOURCE_ACCOUNT = "garmin-account"
    const val SOURCE_OURA = "oura"
    private const val MAX_DETAIL = 200

    fun add(log: List<SyncLogEntry>, entry: SyncLogEntry): List<SyncLogEntry> = (listOf(entry) + log).take(MAX)

    fun encode(log: List<SyncLogEntry>): String = log.take(MAX).joinToString("\n") { e ->
        listOf(e.time.toString(), e.source, if (e.ok) "1" else "0", e.added.toString(), clean(e.detail)).joinToString("\t")
    }

    /** Lines that are damaged are skipped; the rest still shows. */
    fun decode(text: String?): List<SyncLogEntry> = text.orEmpty().lines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 4) return@mapNotNull null
        val time = runCatching { Instant.parse(p[0]) }.getOrNull() ?: return@mapNotNull null
        val added = p[3].toIntOrNull() ?: return@mapNotNull null
        SyncLogEntry(time, p[1], p[2] == "1", added, p.getOrElse(4) { "" })
    }.take(MAX)

    private fun clean(s: String) = s.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(MAX_DETAIL)
}

/** How many activities came from one source, and over which period. */
data class SourceCount(val source: String, val activities: Int, val first: Instant?, val last: Instant?)

/** What the app holds and where it came from; shown on the data-sources card. */
data class DataOverview(
    val sources: List<SourceCount>,
    val days: Int,
    val nights: Int,
    val samples: Int,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
) {
    companion object {
        private val FAR_PAST = LocalDate.of(1970, 1, 1)
        private val FAR_FUTURE = LocalDate.of(2200, 1, 1)

        fun of(repo: Repository): DataOverview {
            val sources = repo.listActivities(limit = Int.MAX_VALUE).groupBy { it.source }.map { (source, list) ->
                SourceCount(source, list.size, list.minOf { it.startTime }, list.maxOf { it.startTime })
            }.sortedByDescending { it.activities }
            val daily = repo.listDaily(FAR_PAST, FAR_FUTURE)
            val dates = daily.map { it.date }
            return DataOverview(
                sources, daily.size, repo.listSleep(FAR_PAST, FAR_FUTURE).size, repo.listSamples(FAR_PAST, FAR_FUTURE).size,
                dates.minOrNull(), dates.maxOrNull(),
            )
        }
    }
}
