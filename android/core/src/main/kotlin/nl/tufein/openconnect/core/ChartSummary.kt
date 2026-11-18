package nl.tufein.openconnect.core

/**
 * What can be said about a chart in words: how many values it has, the lowest, the highest and the latest. A screen reader
 * cannot read a drawing, so each chart carries this as its description (v0.57.0). Days without a value are left out.
 */
data class ChartSummary(val count: Int, val lowest: Double, val highest: Double, val latest: Double, val latestIndex: Int) {
    companion object {
        fun of(values: List<Double?>): ChartSummary? {
            val present = values.filterNotNull()
            if (present.isEmpty()) return null
            return ChartSummary(present.size, present.min(), present.max(), present.last(), values.indexOfLast { it != null })
        }
    }
}
