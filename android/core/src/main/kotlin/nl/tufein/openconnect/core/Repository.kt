package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.watch.Metric
import nl.tufein.openconnect.core.watch.MetricSample
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Storage seam. The app implements it on SQLite; tests use [InMemoryRepository].
 *
 * Listing returns activity summaries with an empty track (tracks are large); [getActivity] returns
 * the full activity.
 */
interface Repository {
    fun upsertActivity(activity: Activity)

    fun getActivity(id: String): Activity?

    /** Newest first. [start] is inclusive and [end] exclusive. */
    fun listActivities(
        sport: Sport? = null,
        start: Instant? = null,
        end: Instant? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<Activity>

    fun deleteActivity(id: String)

    fun upsertDaily(daily: DailySummary)

    /** Oldest first, both ends inclusive. */
    fun listDaily(start: LocalDate, end: LocalDate): List<DailySummary>

    fun upsertSleep(sleep: SleepSummary)

    fun listSleep(start: LocalDate, end: LocalDate): List<SleepSummary>

    /** Readings from a watch, identified by metric and time: storing the same reading again changes nothing. */
    fun upsertSamples(samples: List<MetricSample>)

    /** Oldest first; [start] and [end] are calendar days on the watch's clock, both inclusive. */
    fun listSamples(start: LocalDate, end: LocalDate, metric: Metric? = null): List<MetricSample>

    /** The size a watch file had when it was last read completely, or null if it never was. */
    fun watchFileSize(path: String): Long?

    fun markWatchFile(path: String, size: Long)

    fun getReport(kind: String, subject: String): StoredReport?

    fun saveReport(report: StoredReport)

    /** A day as the Oura Ring saw it, kept apart from the watch's data (v0.17.0). */
    fun upsertOuraDay(day: nl.tufein.openconnect.core.oura.OuraDay)

    fun listOuraDays(start: LocalDate, end: LocalDate): List<nl.tufein.openconnect.core.oura.OuraDay>

    fun clearAll()
}

class InMemoryRepository : Repository {
    private val activities = LinkedHashMap<String, Activity>()
    private val dailies = HashMap<LocalDate, DailySummary>()
    private val sleeps = HashMap<LocalDate, SleepSummary>()
    private val reports = HashMap<Pair<String, String>, StoredReport>()
    private val samples = HashMap<Pair<Metric, Instant>, MetricSample>()
    private val watchFiles = HashMap<String, Long>()
    private val ouraDays = HashMap<LocalDate, nl.tufein.openconnect.core.oura.OuraDay>()

    override fun upsertActivity(activity: Activity) {
        activities[activity.id] = activity
    }

    override fun getActivity(id: String): Activity? = activities[id]

    override fun listActivities(sport: Sport?, start: Instant?, end: Instant?, limit: Int, offset: Int): List<Activity> =
        activities.values
            .filter { sport == null || it.sport == sport }
            .filter { start == null || it.startTime >= start }
            .filter { end == null || it.startTime < end }
            .sortedByDescending { it.startTime }
            .drop(offset)
            .take(limit)
            .map { it.copy(track = emptyList()) }

    override fun deleteActivity(id: String) {
        activities.remove(id)
    }

    override fun upsertDaily(daily: DailySummary) {
        dailies[daily.date] = daily
    }

    override fun listDaily(start: LocalDate, end: LocalDate): List<DailySummary> =
        dailies.values.filter { it.date in start..end }.sortedBy { it.date }

    override fun upsertSleep(sleep: SleepSummary) {
        sleeps[sleep.date] = sleep
    }

    override fun listSleep(start: LocalDate, end: LocalDate): List<SleepSummary> =
        sleeps.values.filter { it.date in start..end }.sortedBy { it.date }

    override fun upsertSamples(samples: List<MetricSample>) {
        for (sample in samples) this.samples[sample.metric to sample.time] = sample
    }

    override fun listSamples(start: LocalDate, end: LocalDate, metric: Metric?): List<MetricSample> =
        samples.values.filter { it.day in start..end && (metric == null || it.metric == metric) }.sortedBy { it.time }

    override fun watchFileSize(path: String): Long? = watchFiles[path]

    override fun markWatchFile(path: String, size: Long) {
        watchFiles[path] = size
    }

    override fun getReport(kind: String, subject: String): StoredReport? = reports[kind to subject]

    override fun saveReport(report: StoredReport) {
        reports[report.kind to report.subject] = report
    }

    override fun upsertOuraDay(day: nl.tufein.openconnect.core.oura.OuraDay) {
        ouraDays[day.date] = day
    }

    override fun listOuraDays(start: LocalDate, end: LocalDate): List<nl.tufein.openconnect.core.oura.OuraDay> =
        ouraDays.values.filter { it.date in start..end }.sortedBy { it.date }

    override fun clearAll() {
        ouraDays.clear()
        activities.clear()
        dailies.clear()
        sleeps.clear()
        reports.clear()
        samples.clear()
        watchFiles.clear()
    }
}

/** Compact binary form of a track for storage: gzip over fixed-width fields with presence flags. */
object TrackCodec {
    private const val VERSION = 1

    /** Positions are kept to 1e-7 degrees (about a centimetre), the precision FIT and GPX carry. */
    private const val DEGREE_SCALE = 1e7
    private const val LATLON = 1
    private const val ELE = 2
    private const val HR = 4
    private const val CADENCE = 8
    private const val POWER = 16
    private const val DISTANCE = 32

    fun encode(track: List<TrackPoint>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeByte(VERSION)
            out.writeInt(track.size)
            var previous = 0L
            for ((index, p) in track.withIndex()) {
                val millis = p.time.toEpochMilli()
                if (index == 0) out.writeLong(millis) else out.writeInt((millis - previous).toInt())
                previous = millis
                var flags = 0
                if (p.lat != null && p.lon != null) flags = flags or LATLON
                if (p.ele != null) flags = flags or ELE
                if (p.hr != null) flags = flags or HR
                if (p.cadence != null) flags = flags or CADENCE
                if (p.power != null) flags = flags or POWER
                if (p.distanceM != null) flags = flags or DISTANCE
                out.writeByte(flags)
                if (flags and LATLON != 0) {
                    out.writeInt(Math.rint(p.lat!! * DEGREE_SCALE).toInt())
                    out.writeInt(Math.rint(p.lon!! * DEGREE_SCALE).toInt())
                }
                if (flags and ELE != 0) out.writeFloat(p.ele!!.toFloat())
                if (flags and HR != 0) out.writeShort(p.hr!!)
                if (flags and CADENCE != 0) out.writeShort(p.cadence!!)
                if (flags and POWER != 0) out.writeShort(p.power!!)
                if (flags and DISTANCE != 0) out.writeDouble(p.distanceM!!)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(data: ByteArray): List<TrackPoint> {
        if (data.isEmpty()) return emptyList()
        DataInputStream(GZIPInputStream(ByteArrayInputStream(data))).use { input ->
            val version = input.readUnsignedByte()
            require(version == VERSION) { "Unknown track encoding version $version" }
            val count = input.readInt()
            val points = ArrayList<TrackPoint>(count)
            var millis = 0L
            repeat(count) { index ->
                millis = if (index == 0) input.readLong() else millis + input.readInt()
                val flags = input.readUnsignedByte()
                val hasPos = flags and LATLON != 0
                val lat = if (hasPos) input.readInt() / DEGREE_SCALE else null
                val lon = if (hasPos) input.readInt() / DEGREE_SCALE else null
                points.add(
                    TrackPoint(
                        time = Instant.ofEpochMilli(millis),
                        lat = lat,
                        lon = lon,
                        ele = if (flags and ELE != 0) input.readFloat().toDouble() else null,
                        hr = if (flags and HR != 0) input.readShort().toInt() else null,
                        cadence = if (flags and CADENCE != 0) input.readShort().toInt() else null,
                        power = if (flags and POWER != 0) input.readShort().toInt() else null,
                        distanceM = if (flags and DISTANCE != 0) input.readDouble() else null,
                    ),
                )
            }
            return points
        }
    }
}
