package nl.tufein.openconnect.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/** This run among the others on the same route: [total] counts it, [rank] 1 is the fastest. [others] are newest first. */
data class RouteHistory(
    val total: Int,
    val rank: Int,
    val fastest: Activity,
    val others: List<Activity>,
    /** How much longer this one took than the best on the route (zero when it is the best). */
    val secondsSlowerThanBest: Double,
    /** Positive when this one was slower than the average of the others, negative when faster. */
    val secondsVersusAverage: Double,
)

/**
 * "Matched activities" the way Garmin and Strava have them, without the cloud: two activities are the same route when they are the
 * same sport, within 10 percent of each other's distance, and start, end and the middle of the track are within 300 metres.
 * Times are compared at the distance of the run that is looked at, so a route that was run a little long is not punished.
 */
object RouteMatcher {
    const val DISTANCE_TOLERANCE = 0.10
    const val POINT_TOLERANCE_M = 300.0

    private fun positions(a: Activity) = a.track.filter { it.hasPosition }

    private fun metres(a: TrackPoint, b: TrackPoint): Double {
        val dLat = (b.lat!! - a.lat!!) * 111_320.0
        val dLon = (b.lon!! - a.lon!!) * 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
        return hypot(dLat, dLon)
    }

    fun isSameRoute(a: Activity, b: Activity): Boolean {
        if (a.sport != b.sport || a.distanceM <= 0 || b.distanceM <= 0) return false
        if (abs(a.distanceM - b.distanceM) / max(a.distanceM, b.distanceM) > DISTANCE_TOLERANCE) return false
        val pa = positions(a)
        val pb = positions(b)
        if (pa.size < 2 || pb.size < 2) return false
        return metres(pa.first(), pb.first()) <= POINT_TOLERANCE_M &&
            metres(pa.last(), pb.last()) <= POINT_TOLERANCE_M &&
            metres(pa[pa.size / 2], pb[pb.size / 2]) <= POINT_TOLERANCE_M
    }

    /** The other activities on the route of [target], oldest first. */
    fun matches(target: Activity, all: List<Activity>): List<Activity> =
        all.filter { it.id != target.id && isSameRoute(target, it) }.sortedBy { it.startTime }

    private fun timeAt(distanceM: Double, a: Activity) = a.durationS * distanceM / a.distanceM

    fun history(target: Activity, all: List<Activity>): RouteHistory? {
        val others = matches(target, all)
        if (others.isEmpty()) return null
        val own = target.durationS
        val times = others.associateWith { timeAt(target.distanceM, it) }
        val best = times.minByOrNull { it.value }!!
        val fastest = if (own <= best.value) target else best.key
        val bestTime = minOf(own, best.value)
        return RouteHistory(
            total = others.size + 1,
            rank = 1 + times.values.count { it < own },
            fastest = fastest,
            others = others.sortedByDescending { it.startTime },
            secondsSlowerThanBest = own - bestTime,
            secondsVersusAverage = own - times.values.average(),
        )
    }
}
