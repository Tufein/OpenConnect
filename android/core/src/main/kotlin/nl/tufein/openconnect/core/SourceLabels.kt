package nl.tufein.openconnect.core

import java.time.LocalDate

/** Everything that reaches outside the app and could therefore behave differently on a real device than in a test. */
enum class DataSourceKind(val key: String) {
    GARMIN_ACCOUNT("garmin-account"),
    GARMIN_ACCOUNT_ACTIVITIES("garmin-activities"),
    WATCH_USB("watch-usb"),
    WATCH_FOLDER("watch-folder"),
    WATCH_BLUETOOTH("watch-bluetooth"),
    LIVE_HEART_RATE("live-heart-rate"),
    WORKOUTS_TO_WATCH("workouts-to-watch"),
    HEALTH_CONNECT("health-connect"),
    OURA("oura"),
    WHOOP("whoop"),
    HUME("hume"),
    FILE_IMPORT("file-import"),
    AI_CLAUDE("ai-claude"),
    AI_GEMINI("ai-gemini"),
    UPDATER("updater"),
    KEY_VAULT_AND_LOCK("key-vault-lock"),
}

/**
 * *Tested on a real device* when [testedOn] and [version] say when and in which release someone tried it, *Experimental*
 * when nobody has. Green automated tests do not count: the point of the label is what only a device can show.
 */
data class SourceStatus(val kind: DataSourceKind, val testedOn: LocalDate? = null, val version: String? = null) {
    val tested: Boolean get() = testedOn != null
}

/**
 * The one list behind every label in the app, the About screen and the diagnostics report. It follows the register in
 * docs/ROADMAP.md (section 7): a source moves to tested only with a real trial to point at (date, version, a line in
 * docs/PUBLIC_RELEASE.md section 2).
 */
object SourceLabels {
    fun status(kind: DataSourceKind): SourceStatus = when (kind) {
        // Run live against a real Garmin account on 2026-11-12 (token, all data types, one activity download).
        DataSourceKind.GARMIN_ACCOUNT -> SourceStatus(kind, LocalDate.of(2026, 11, 12), "0.4.0")
        // Stefan, 2026-11-28, on his fenix 8 and his own phone with v1.13.12: Bluetooth sync, battery, notifications on
        // the watch and live heart rate from the watch's own service "all work very well" (docs/PUBLIC_RELEASE.md, T7/T17).
        DataSourceKind.WATCH_BLUETOOTH,
        DataSourceKind.LIVE_HEART_RATE,
        -> SourceStatus(kind, LocalDate.of(2026, 11, 28), "1.13.12")
        DataSourceKind.GARMIN_ACCOUNT_ACTIVITIES,
        DataSourceKind.WATCH_USB,
        DataSourceKind.WATCH_FOLDER,
        DataSourceKind.WORKOUTS_TO_WATCH,
        DataSourceKind.HEALTH_CONNECT,
        DataSourceKind.OURA,
        DataSourceKind.WHOOP,
        DataSourceKind.HUME,
        DataSourceKind.FILE_IMPORT,
        DataSourceKind.AI_CLAUDE,
        DataSourceKind.AI_GEMINI,
        DataSourceKind.UPDATER,
        DataSourceKind.KEY_VAULT_AND_LOCK,
        -> SourceStatus(kind)
    }

    val all: List<SourceStatus> = DataSourceKind.values().map(::status)
}
