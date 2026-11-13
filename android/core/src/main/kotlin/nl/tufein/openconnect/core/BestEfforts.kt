package nl.tufein.openconnect.core

import nl.tufein.openconnect.core.reports.cumulativeDistance
import java.time.LocalDate
import java.time.ZoneOffset

/** One kilometre of a run (the last one may be shorter): how long it took, the heart rate and the climbing in it. */
data class Lap(val index: Int, val distanceM: Double, val seconds: Double, val avgHr: Int?, val climbM: Int) {
    /** Seconds per kilometre. */
    val pace: Double get() = if (distanceM > 0) seconds / (distanceM / 1000.0) else 0.0
}

/** The best time over [distanceM] in one activity or over all of them. */
data class Effort(val distanceM: Double, val seconds: Double, val activityId: String? = null, val name: String? = null, val date: LocalDate? = null)

/**
 * Fastest stretches inside a track (the quickest 1 km, 5 km, 10 km ... of a run, wherever in the run they happen), and
 * a lap table. Works on the track's distance, or on the positions when there is no distance. A jump in the GPS that would
 * mean running faster than anyone can ([MAX_SPEED_MPS]) is not believed.
 */
object BestEfforts {
    val DISTANCES = listOf(1000.0, 5000.0, 10_000.0, 21_097.5, 42_195.0)
    const val MAX_SPEED_MPS = 9.0
    const val MAX_RUNS = 300
    private const val MIN_LAP_M = 100.0

    private class Series(val t: DoubleArray, val d: DoubleArray, val hr: IntArray, val ele: DoubleArray)

    private fun series(track: List<TrackPoint>): Series? {
        val distances = cumulativeDistance(track) ?: return null
        val points = if (distances.size == track.size) track else track.filter { it.hasPosition }
        if (points.size < 2) return null
        val t = DoubleArray(points.size) { points[it].time.epochSecond + points[it].time.nano / 1e9 }
        val d = DoubleArray(points.size)
        var best = 0.0
        for (i in points.indices) {
            best = maxOf(best, distances[i]) // distance never goes back, a glitch must not either
            d[i] = best
        }
        return Series(t, d, IntArray(points.size) { points[it].hr ?: 0 }, DoubleArray(points.size) { points[it].ele ?: Double.NaN })
    }

    /** The fastest time over [distanceM] inside [track], or null when the track is shorter or has no distance. */
    fun inTrack(track: List<TrackPoint>, distanceM: Double): Double? {
        val s = series(track) ?: return null
        var best: Double? = null
        var i = 0
        for (j in 1 until s.d.size) {
            val target = s.d[j] - distanceM
            if (target < s.d[0]) continue
            while (i + 1 < j && s.d[i + 1] <= target) i++
            val span = s.d[i + 1] - s.d[i]
            val startT = if (span > 0) s.t[i] + (target - s.d[i]) / span * (s.t[i + 1] - s.t[i]) else s.t[i]
            val seconds = s.t[j] - startT
            if (seconds <= 0 || distanceM / seconds > MAX_SPEED_MPS) continue
            if (best == null || seconds < best) best = seconds
        }
        return best
    }

    fun efforts(track: List<TrackPoint>): List<Effort> = DISTANCES.mapNotNull { d -> inTrack(track, d)?.let { Effort(d, it) } }

    /** Kilometre by kilometre, with a last shorter lap when at least 100 m are left. */
    fun laps(track: List<TrackPoint>): List<Lap> {
        val s = series(track) ?: return emptyList()
        val laps = ArrayList<Lap>()
        var boundary = 1000.0
        var lapStartT = s.t[0]
        var lapStartD = s.d[0]
        var from = 0
        fun closeLap(endIndex: Int, endD: Double, endT: Double) {
            val hrs = (from..endIndex).map { s.hr[it] }.filter { it != 0 }
            var climb = 0.0
            var last = Double.NaN
            for (k in from..endIndex) {
                val e = s.ele[k]
                if (e.isNaN()) continue
                if (!last.isNaN() && e > last) climb += e - last
                last = e
            }
            laps += Lap(laps.size + 1, endD - lapStartD, endT - lapStartT, hrs.takeIf { it.isNotEmpty() }?.average()?.let { Math.rint(it).toInt() }, Math.rint(climb).toInt())
        }
        for (i in 1 until s.d.size) {
            while (s.d[i] >= boundary && s.d[i] > s.d[i - 1]) {
                val crossT = s.t[i - 1] + (boundary - s.d[i - 1]) / (s.d[i] - s.d[i - 1]) * (s.t[i] - s.t[i - 1])
                closeLap(i, boundary, crossT)
                lapStartT = crossT
                lapStartD = boundary
                from = i
                boundary += 1000.0
            }
        }
        val rest = s.d.last() - lapStartD
        if (rest >= MIN_LAP_M) closeLap(s.d.lastIndex, s.d.last(), s.t.last())
        return laps
    }

    /** The best time for each distance over the most recent [MAX_RUNS] runs that have a track. Fastest first per distance. */
    fun personalBests(repo: Repository): List<Effort> {
        val best = HashMap<Double, Effort>()
        for (summary in repo.listActivities(sport = Sport.RUNNING, limit = MAX_RUNS)) {
            if (summary.distanceM < DISTANCES.first()) continue
            val run = if (summary.track.isNotEmpty()) summary else repo.getActivity(summary.id) ?: continue
            val date = run.startTime.atZone(ZoneOffset.UTC).toLocalDate()
            for (e in efforts(run.track)) {
                val current = best[e.distanceM]
                if (current == null || e.seconds < current.seconds) best[e.distanceM] = e.copy(activityId = run.id, name = run.name, date = date)
            }
        }
        return DISTANCES.mapNotNull { best[it] }
    }
}
