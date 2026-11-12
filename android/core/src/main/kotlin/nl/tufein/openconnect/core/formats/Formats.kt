package nl.tufein.openconnect.core.formats

import nl.tufein.openconnect.core.Activity
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Activity file formats: FIT, GPX and TCX. */
object Formats {
    val SUPPORTED: List<String> = listOf("fit", "gpx", "tcx")

    class Exported(val bytes: ByteArray, val contentType: String, val filename: String)

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    /** Serialise an activity in the requested format. */
    fun export(activity: Activity, format: String): Exported {
        val fmt = format.lowercase().trimStart('.')
        val (bytes, type) = when (fmt) {
            "fit" -> Fit.export(activity) to "application/vnd.ant.fit"
            "gpx" -> Gpx.export(activity) to "application/gpx+xml"
            "tcx" -> Tcx.export(activity) to "application/vnd.garmin.tcx+xml"
            else -> throw FormatError("Unsupported export format '$format'; use one of $SUPPORTED")
        }
        return Exported(bytes, type, filename(activity, fmt))
    }

    /** `running-20260502-070000.gpx`: the file name an exported activity gets. */
    fun filename(activity: Activity, format: String): String =
        "${activity.sport.key}-${STAMP.format(activity.startTime)}.${format.lowercase().trimStart('.')}"

    /** Detect by content first (reliable), falling back to the file extension. */
    fun detect(data: ByteArray, filename: String = ""): String {
        if (data.size >= 12 && String(data, 8, 4, Charsets.ISO_8859_1) == ".FIT") return "fit"
        val head = String(data, 0, minOf(data.size, 2048), Charsets.ISO_8859_1).trimStart().lowercase()
        if ("<gpx" in head) return "gpx"
        if ("<trainingcenterdatabase" in head) return "tcx"
        val ext = if ('.' in filename) filename.substringAfterLast('.').lowercase() else ""
        if (ext in SUPPORTED) return ext
        throw FormatError("Could not detect the file format (expected FIT, GPX or TCX)")
    }

    fun import(data: ByteArray, filename: String = ""): Activity = when (detect(data, filename)) {
        "fit" -> Fit.parse(data)
        "gpx" -> Gpx.parse(data)
        else -> Tcx.parse(data)
    }
}
