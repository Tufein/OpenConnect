package nl.tufein.openconnect.core.formats

import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.core.finalizeActivity
import org.w3c.dom.Element
import java.time.Instant

/** Garmin Training Center XML (TCX). TCX only knows Running, Biking and Other. */
internal object Tcx {
    const val TCX_NS = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"

    private fun sportName(sport: Sport) = when (sport) {
        Sport.RUNNING -> "Running"
        Sport.CYCLING -> "Biking"
        else -> "Other"
    }

    fun export(activity: Activity): ByteArray {
        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='utf-8'?>\n")
        sb.append("<TrainingCenterDatabase xmlns=\"$TCX_NS\"><Activities>")
        sb.append("<Activity Sport=\"${sportName(activity.sport)}\">")
        sb.append("<Id>${isoZ(activity.startTime)}</Id>")
        sb.append("<Lap StartTime=\"${isoZ(activity.startTime)}\">")
        sb.append(fmt("<TotalTimeSeconds>%.1f</TotalTimeSeconds>", activity.durationS))
        sb.append(fmt("<DistanceMeters>%.1f</DistanceMeters>", activity.distanceM))
        sb.append("<Calories>${activity.calories ?: 0}</Calories>")
        activity.avgHr?.takeIf { it != 0 }?.let {
            sb.append("<AverageHeartRateBpm><Value>$it</Value></AverageHeartRateBpm>")
        }
        activity.maxHr?.takeIf { it != 0 }?.let {
            sb.append("<MaximumHeartRateBpm><Value>$it</Value></MaximumHeartRateBpm>")
        }
        sb.append("<Intensity>Active</Intensity><TriggerMethod>Manual</TriggerMethod><Track>")
        for (p in activity.track) {
            sb.append("<Trackpoint><Time>${isoZ(p.time)}</Time>")
            if (p.hasPosition) {
                sb.append(
                    fmt(
                        "<Position><LatitudeDegrees>%.7f</LatitudeDegrees>" +
                            "<LongitudeDegrees>%.7f</LongitudeDegrees></Position>",
                        p.lat,
                        p.lon,
                    ),
                )
            }
            if (p.ele != null) sb.append(fmt("<AltitudeMeters>%.2f</AltitudeMeters>", p.ele))
            if (p.distanceM != null) sb.append(fmt("<DistanceMeters>%.1f</DistanceMeters>", p.distanceM))
            if (p.hr != null) sb.append("<HeartRateBpm><Value>${p.hr}</Value></HeartRateBpm>")
            if (p.cadence != null) sb.append("<Cadence>${p.cadence}</Cadence>")
            sb.append("</Trackpoint>")
        }
        sb.append("</Track></Lap>")
        if (activity.name.isNotEmpty()) sb.append("<Notes>${xmlEscape(activity.name)}</Notes>")
        sb.append("</Activity></Activities></TrainingCenterDatabase>")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun num(el: Element, vararg path: String): Double? {
        val node = el.path(*path) ?: return null
        val text = node.textContent?.trim().orEmpty()
        if (text.isEmpty()) return null
        return text.toDoubleOrNull() ?: throw FormatError("Invalid number in <${path.last()}>: '$text'")
    }

    private fun time(text: String): Instant = parseTime(text)

    fun parse(data: ByteArray): Activity {
        val root = parseXml(data, "TCX")
        val actEl = root.path("Activities", "Activity")
        if (root.local != "TrainingCenterDatabase" || actEl == null) throw FormatError("Not a TCX activity file")

        val track = ArrayList<TrackPoint>()
        var duration = 0.0
        var distance = 0.0
        var calories = 0
        var maxHr = 0
        var hrWeighted = 0.0
        var start: Instant? = null
        for (lap in actEl.elements().filter { it.local == "Lap" }) {
            val lapStart = lap.getAttribute("StartTime")
            if (lapStart.isNotEmpty() && start == null) start = time(lapStart)
            val lapTime = num(lap, "TotalTimeSeconds") ?: 0.0
            duration += lapTime
            distance += num(lap, "DistanceMeters") ?: 0.0
            calories += (num(lap, "Calories") ?: 0.0).toInt()
            maxHr = maxOf(maxHr, (num(lap, "MaximumHeartRateBpm", "Value") ?: 0.0).toInt())
            hrWeighted += (num(lap, "AverageHeartRateBpm", "Value") ?: 0.0) * lapTime
            for (tp in lap.descendantsAndSelf().filter { it.local == "Trackpoint" }) {
                val timeText = tp.child("Time")?.textContent?.takeIf { it.isNotBlank() } ?: continue
                track.add(
                    TrackPoint(
                        time = time(timeText),
                        lat = num(tp, "Position", "LatitudeDegrees"),
                        lon = num(tp, "Position", "LongitudeDegrees"),
                        ele = num(tp, "AltitudeMeters"),
                        hr = num(tp, "HeartRateBpm", "Value")?.toInt(),
                        cadence = num(tp, "Cadence")?.toInt(),
                        distanceM = num(tp, "DistanceMeters"),
                    ),
                )
            }
        }
        if (start == null) {
            val idText = actEl.child("Id")?.textContent
            start = when {
                !idText.isNullOrBlank() -> time(idText)
                track.isNotEmpty() -> track.first().time
                else -> throw FormatError("TCX activity has no start time")
            }
        }

        val notes = actEl.child("Notes")?.textContent?.trim().orEmpty()
        var activity = Activity(
            id = "pending",
            source = "import",
            name = notes,
            sport = when (actEl.getAttribute("Sport")) {
                "Running" -> Sport.RUNNING
                "Biking" -> Sport.CYCLING
                else -> Sport.OTHER
            },
            startTime = start,
            durationS = duration,
            distanceM = distance,
            calories = calories.takeIf { it != 0 },
            avgHr = if (duration > 0 && hrWeighted > 0) Math.rint(hrWeighted / duration).toInt() else null,
            maxHr = maxHr.takeIf { it != 0 },
            track = track,
        )
        activity = finalizeActivity(activity)
        activity = activity.copy(name = activity.name.ifEmpty { defaultName(activity) })
        return activity.copy(id = importId(activity))
    }
}
