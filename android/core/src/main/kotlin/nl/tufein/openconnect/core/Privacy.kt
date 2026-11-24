package nl.tufein.openconnect.core

/**
 * Every place the app can send a request to, and nothing else (v0.59.0). The Privacy card in About, the privacy statement
 * (docs/PRIVACY.md) and a test are all written from this list: the test reads the source and fails when the code mentions an
 * address that is in none of the three lists below, so a new network address cannot slip in without the statement saying so.
 */
object PrivacyStatement {
    /** What the app sends requests to when the person uses the feature, by feature. The key names the text in the app. */
    val called: Map<String, List<String>> = linkedMapOf(
        "garmin" to listOf("personal.connectapi.garmin.com"),
        "oura" to listOf("cloud.ouraring.com", "api.ouraring.com"),
        "claude" to listOf("api.anthropic.com"),
        "gemini" to listOf("generativelanguage.googleapis.com"),
        // The updater: the release list, and the download links in it, which point at github.com.
        "github" to listOf("api.github.com", "github.com"),
    )

    /** Addresses that only appear as a link the person opens in the browser, a credit or a licence line: the app never calls them. */
    val linksOnly: Set<String> = setOf(
        "connect.garmin.com", "apps.garmin.com", "play.google.com", "console.anthropic.com", "aistudio.google.com",
        "developer-dashboard.whoop.com", "api.prod.whoop.com", "codeberg.org", "gadgetbridge.org", "developer.android.com", "kotlinlang.org",
        "unsplash.com",
    )

    /** XML namespace names used when reading GPX and TCX files: they look like addresses and are never opened. */
    val namespaces: Set<String> = setOf("www.garmin.com", "www.topografix.com", "xml.org", "schemas.android.com", "apache.org")

    const val STATEMENT_URL = "https://github.com/Tufein/OpenConnect/blob/main/docs/PRIVACY.md"

    val allKnown: Set<String> get() = called.values.flatten().toSet() + linksOnly + namespaces
}
