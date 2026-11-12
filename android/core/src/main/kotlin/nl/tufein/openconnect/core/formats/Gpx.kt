package nl.tufein.openconnect.core.formats

import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.core.finalizeActivity

/** GPX 1.1 import and export with the Garmin TrackPointExtension (heart rate, cadence). */
internal object Gpx {
    const val GPX_NS = "http://www.topografix.com/GPX/1/1"
    const val TPX_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"

    fun export(activity: Activity): ByteArray {
        val positioned = activity.track.filter { it.hasPosition }
        if (positioned.isEmpty()) throw FormatError("GPX needs GPS positions; this activity has none")
        val name = xmlEscape(activity.name.ifEmpty { defaultName(activity) })

        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='utf-8'?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"OpenConnect\" xmlns=\"$GPX_NS\" xmlns:gpxtpx=\"$TPX_NS\">")
        sb.append("<metadata><name>$name</name><time>${isoZ(activity.startTime)}</time></metadata>")
        sb.append("<trk><name>$name</name><type>${activity.sport.key}</type><trkseg>")
        for (p in positioned) {
            sb.append(fmt("<trkpt lat=\"%.7f\" lon=\"%.7f\">", p.lat, p.lon))
            if (p.ele != null) sb.append(fmt("<ele>%.2f</ele>", p.ele))
            sb.append("<time>${isoZ(p.time)}</time>")
            if (p.hr != null || p.cadence != null) {
                sb.append("<extensions><gpxtpx:TrackPointExtension>")
                if (p.hr != null) sb.append("<gpxtpx:hr>${p.hr}</gpxtpx:hr>")
                if (p.cadence != null) sb.append("<gpxtpx:cad>${p.cadence}</gpxtpx:cad>")
                sb.append("</gpxtpx:TrackPointExtension></extensions>")
            }
            sb.append("</trkpt>")
        }
        sb.append("</trkseg></trk></gpx>")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun parse(data: ByteArray): Activity {
        val root = parseXml(data, "GPX")
        if (root.local != "gpx") throw FormatError("Not a GPX file (root element is not <gpx>)")

        val track = ArrayList<TrackPoint>()
        var name: String? = null
        var sportText: String? = null
        for (trk in root.descendantsAndSelf().filter { it.local == "trk" }) {
            name = name ?: trk.childText("name")
            sportText = sportText ?: trk.childText("type")
            for (pt in trk.descendantsAndSelf().filter { it.local == "trkpt" }) {
                val timeText = pt.childText("time") ?: continue
                val lat = pt.getAttribute("lat").toDoubleOrNull()
                val lon = pt.getAttribute("lon").toDoubleOrNull()
                if (lat == null || lon == null) throw FormatError("Malformed track point: missing or invalid lat/lon")
                track.add(
                    TrackPoint(
                        time = parseTime(timeText),
                        lat = lat,
                        lon = lon,
                        ele = pt.childText("ele")?.toDoubleOrNull(),
                        hr = pt.descendantText("hr")?.toDoubleOrNull()?.toInt(),
                        cadence = pt.descendantText("cad")?.toDoubleOrNull()?.toInt(),
                    ),
                )
            }
        }
        if (track.isEmpty()) throw FormatError("GPX file contains no timestamped track points")

        val metaName = root.elements().filter { it.local == "metadata" }.lastOrNull()?.childText("name")
        var activity = Activity(
            id = "pending",
            source = "import",
            name = name ?: metaName ?: "",
            sport = sportFromText(sportText),
            startTime = track.first().time,
            track = track,
        )
        activity = finalizeActivity(activity)
        activity = activity.copy(name = activity.name.ifEmpty { defaultName(activity) })
        return activity.copy(id = importId(activity))
    }
}
