package nl.tufein.openconnect.core

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** One square of the map and how many different routes went through it. */
data class HeatCell(val x: Int, val y: Int, val count: Int)

/**
 * The squares your routes went through, [cols] by [rows] with row 0 at the top (north). [maxCount] is the busiest square and
 * [routes] the number of routes with a position.
 */
data class HeatGrid(val cols: Int, val rows: Int, val cells: List<HeatCell>, val maxCount: Int, val routes: Int) {
    /** 0 for an empty square, then 1 to 4 on a log scale up to the busiest square. */
    fun level(count: Int): Int {
        if (count <= 0) return 0
        if (maxCount <= 1) return 1
        return min(4, 1 + floor(3.0 * ln(count.toDouble()) / ln(maxCount.toDouble())).toInt())
    }
}

/**
 * A personal heatmap in the spirit of Strava's: all your tracks drawn on one grid, a square counts once per route however long you
 * stay in it, so a road you run often is hotter than a place you stood still. There is no background map: the picture is only your
 * own routes, which also means no map provider ever sees where you go.
 */
object Heatmap {
    /** The share of positions cut off at each end of both axes when there are enough, so one stray point does not shrink the rest. */
    private const val TRIM = 0.01
    private const val MIN_POINTS_TO_TRIM = 200

    fun build(tracks: List<List<TrackPoint>>, size: Int = 48): HeatGrid? {
        val routes = tracks.map { track -> track.filter { it.hasPosition } }.filter { it.isNotEmpty() }
        if (routes.isEmpty()) return null
        val all = routes.flatten()
        // Longitude shrinks with latitude: work in a flat x/y where a degree of either is about the same distance.
        val scale = cos(Math.toRadians(all.map { it.lat!! }.average()))
        fun x(p: TrackPoint) = p.lon!! * scale
        fun y(p: TrackPoint) = p.lat!!

        fun bounds(values: List<Double>): Pair<Double, Double> {
            val sorted = values.sorted()
            val cut = if (sorted.size >= MIN_POINTS_TO_TRIM) (sorted.size * TRIM).toInt() else 0
            return sorted[cut] to sorted[sorted.size - 1 - cut]
        }
        val (minX, maxX) = bounds(all.map(::x))
        val (minY, maxY) = bounds(all.map(::y))
        val span = max(max(maxX - minX, maxY - minY), 1e-9)
        val cell = span / size
        val cols = min(size, floor((maxX - minX) / cell).toInt() + 1)
        val rows = min(size, floor((maxY - minY) / cell).toInt() + 1)

        fun inside(px: Double, py: Double) = px in minX..maxX && py in minY..maxY
        fun column(px: Double) = min(cols - 1, floor((px - minX) / cell).toInt())
        fun row(py: Double) = min(rows - 1, floor((maxY - py) / cell).toInt())

        val counts = HashMap<Pair<Int, Int>, Int>()
        for (route in routes) {
            val seen = HashSet<Pair<Int, Int>>()
            var previous: TrackPoint? = null
            for (point in route) {
                val px = x(point)
                val py = y(point)
                if (!inside(px, py)) {
                    previous = null
                    continue
                }
                val from = previous
                if (from == null) {
                    seen += column(px) to row(py)
                } else {
                    // Join far apart samples so a sparse track has no holes: a step is at most half a square.
                    val dx = px - x(from)
                    val dy = py - y(from)
                    val steps = max(1, Math.ceil(hypot(dx, dy) / cell * 2).toInt())
                    for (s in 1..steps) {
                        val qx = x(from) + dx * s / steps
                        val qy = y(from) + dy * s / steps
                        seen += column(qx) to row(qy)
                    }
                }
                previous = point
            }
            for (square in seen) counts.merge(square, 1, Int::plus)
        }
        if (counts.isEmpty()) return null
        val cells = counts.map { (square, n) -> HeatCell(square.first, square.second, n) }
        return HeatGrid(cols, rows, cells, cells.maxOf { it.count }, routes.size)
    }
}
