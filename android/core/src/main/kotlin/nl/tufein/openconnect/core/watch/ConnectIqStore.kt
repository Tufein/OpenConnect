package nl.tufein.openconnect.core.watch

/** One store link the Settings screen can show: which app it opens and where. */
data class StoreLink(val app: StoreApp, val url: String)

enum class StoreApp { WATCHFACE, WIDGET }

/**
 * Where OpenConnect's watch apps live in Garmin's Connect IQ store.
 *
 * A direct link to an app exists only after its first upload: Garmin then hands out a **store id** (a UUID, not the id
 * inside the app's manifest). Until then the two constants below are empty and the app shows how to install without the
 * store. To switch the links on, paste the id (or the whole store address) of each published app into the matching
 * constant; nothing else changes. See `docs/CONNECT_IQ_STORE.md`.
 */
object ConnectIqStore {
    /** The store id (or address) of the OpenConnect watch face. Empty until the face is published. */
    const val WATCHFACE_ID = ""

    /** The store id (or address) of the OpenConnect widget. Empty until the widget is published. */
    const val WIDGET_ID = ""

    /** Without a language part (`/en-US/`), so the store opens in the reader's own language. */
    const val BASE = "https://apps.garmin.com/apps/"

    private val UUID = Regex("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")
    private val ADDRESS = Regex("^https://(?:apps|apps-developer)\\.garmin\\.com/(?:[A-Za-z]{2}-[A-Za-z]{2}/)?(?:developer/)?apps/($UUID)/?(?:[?#].*)?$")

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

    /** The links to show for the given ids; an app without a valid id has none. */
    fun links(watchFaceId: String = WATCHFACE_ID, widgetId: String = WIDGET_ID): List<StoreLink> = listOfNotNull(
        url(watchFaceId)?.let { StoreLink(StoreApp.WATCHFACE, it) },
        url(widgetId)?.let { StoreLink(StoreApp.WIDGET, it) },
    )
}
