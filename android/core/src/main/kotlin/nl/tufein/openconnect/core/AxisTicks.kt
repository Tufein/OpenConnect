package nl.tufein.openconnect.core

import kotlin.math.roundToInt

/** Which points of a chart get a date under them, and where under the chart that date sits (v0.44.0). */
object AxisTicks {
    const val DEFAULT_MAX = 5

    /**
     * The indices of the points to label: the first and the last, and the others spread evenly between them, at most [max] in all.
     * A chart of three days has three labels; a chart of thirty has five.
     */
    fun indices(count: Int, max: Int = DEFAULT_MAX): List<Int> {
        if (count <= 0 || max <= 0) return emptyList()
        if (count == 1 || max == 1) return listOf(0)
        val labels = minOf(count, max)
        return (0 until labels).map { (it * (count - 1).toDouble() / (labels - 1)).roundToInt() }.distinct()
    }

    /**
     * How far along the chart (0 is the left edge, 1 the right) point [index] of [count] sits. A line chart puts its first and
     * last point on the edges; a bar chart gives each bar an equal slot and the bar sits in the middle of it.
     */
    fun fraction(index: Int, count: Int, bars: Boolean): Double = when {
        count <= 0 -> 0.5
        bars -> (index + 0.5) / count
        count == 1 -> 0.5
        else -> index.toDouble() / (count - 1)
    }
}
