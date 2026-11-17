package nl.tufein.openconnect.core.watch

/** One way to get to Garmin's Connect IQ store. The app tries them in order and stops at the first that works. */
sealed interface StoreStep {
    /** Start the app with this package name, as if its icon was tapped. */
    data class LaunchApp(val packageName: String) : StoreStep

    /** Open [url]. With a [packageName] only that app may take it; without one the phone decides (usually the browser). */
    data class OpenAddress(val url: String, val packageName: String? = null) : StoreStep
}

/**
 * How OpenConnect gets to Garmin's Connect IQ store (v0.43.0: straight into Garmin's own Connect IQ app, no browser).
 *
 * A page of our own exists only after a first upload: Garmin then hands out a **store id** (a UUID, not the id inside
 * the watch app's manifest). Until then [LISTING_ID] is empty and the button opens the Connect IQ app at its start.
 * To make it open OpenConnect's own page, paste that id (or the whole store address) into [LISTING_ID]; nothing else
 * changes. See `docs/CONNECT_IQ_STORE.md`.
 */
object ConnectIqStore {
    /** The store id (or address) of OpenConnect's own listing. Empty: nothing of OpenConnect is in the store. */
    const val LISTING_ID = ""

    /** Garmin's "Connect IQ Store" app for Android. */
    const val PACKAGE = "com.garmin.connectiq"

    /** Garmin's store start page, the way out when the Connect IQ app will not start. */
    const val HOME = "https://apps.garmin.com/"

    /** Without a language part (`/en-US/`), so the store opens in the reader's own language. */
    const val BASE = "https://apps.garmin.com/apps/"

    /** Where to get the Connect IQ app: the Play Store app first, its web page when the phone has no Play Store. */
    private const val PLAY_APP = "market://details?id=$PACKAGE"
    private const val PLAY_WEB = "https://play.google.com/store/apps/details?id=$PACKAGE"

    private val UUID = Regex("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")
    private val ADDRESS = Regex("^https://(?:apps|apps-developer)\\.garmin\\.com/(?:[A-Za-z]{2}-[A-Za-z]{2}/)?(?:developer/)?apps/($UUID)/?(?:[?#].*)?$")

    /** True once [LISTING_ID] holds a store id: the button then names OpenConnect's own page. */
    val hasListing: Boolean get() = url(LISTING_ID) != null

    /** The store id inside [value], which is either the bare id or a store address; null for anything else. */
    fun storeId(value: String): String? {
        val text = value.trim()
        val id = when {
            UUID.matches(text) -> text
            else -> ADDRESS.matchEntire(text)?.groupValues?.get(1)
        }
        return id?.lowercase()
    }

    /** The address that opens the app in the store, or null while [value] is empty or is not a store id. */
    fun url(value: String): String? = storeId(value)?.let { BASE + it }

    /**
     * What to try, in order, when the Connect IQ button is tapped. [installed] says whether Garmin's Connect IQ app is
     * on the phone.
     *
     * - A listing of our own: that page, in Garmin's app when it is there, otherwise in the browser.
     * - No listing: Garmin's app itself; without it, the Play Store page where it can be installed.
     */
    fun steps(listing: String = LISTING_ID, installed: Boolean): List<StoreStep> {
        val page = url(listing)
        return when {
            page != null && installed -> listOf(StoreStep.OpenAddress(page, PACKAGE), StoreStep.OpenAddress(page))
            page != null -> listOf(StoreStep.OpenAddress(page))
            installed -> listOf(StoreStep.LaunchApp(PACKAGE), StoreStep.OpenAddress(HOME))
            else -> listOf(StoreStep.OpenAddress(PLAY_APP), StoreStep.OpenAddress(PLAY_WEB))
        }
    }
}
