package nl.tufein.openconnect.core

/**
 * The three shapes the home-screen widget can take. The launcher owns the size; the widget picks the layout that fits
 * and shows more when there is room. Added in v1.7.0.
 */
enum class WidgetFormat {
    /** Two cells wide or one high: readiness and today's steps, nothing else. */
    SMALL,

    /** The usual card (three by two cells): readiness, steps and this week's activity goal. */
    MEDIUM,

    /** Four by three cells or more: the medium card plus last night's sleep and the resting heart rate. */
    LARGE,
}

/** Picks a [WidgetFormat] from the space the launcher reports, in dp. */
object WidgetFormats {
    /** Three cells wide (70 × 3 − 30), the size the widget asks for by default. */
    const val MEDIUM_WIDTH_DP = 180

    /** Four cells wide. */
    const val LARGE_WIDTH_DP = 250

    /** Two cells high. */
    const val MEDIUM_HEIGHT_DP = 110

    /** Three cells high. */
    const val LARGE_HEIGHT_DP = 180

    /**
     * A size the launcher does not report yet (both numbers zero) means "no options": the default card, exactly what
     * the widget looked like before v1.7.0.
     */
    fun forSize(widthDp: Int, heightDp: Int): WidgetFormat = when {
        widthDp <= 0 && heightDp <= 0 -> WidgetFormat.MEDIUM
        widthDp >= LARGE_WIDTH_DP && heightDp >= LARGE_HEIGHT_DP -> WidgetFormat.LARGE
        widthDp >= MEDIUM_WIDTH_DP && heightDp >= MEDIUM_HEIGHT_DP -> WidgetFormat.MEDIUM
        else -> WidgetFormat.SMALL
    }
}
