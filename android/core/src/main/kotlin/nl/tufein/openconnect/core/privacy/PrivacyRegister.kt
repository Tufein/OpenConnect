package nl.tufein.openconnect.core.privacy

import nl.tufein.openconnect.core.DataSourceKind

/**
 * The canonical privacy register (v1.12.0): every subject that can carry data across a boundary of this app, with the
 * operation that does it, the fields it carries, where it goes, what is kept and what it needs.
 *
 * This is the only source of truth. The compatibility view in [nl.tufein.openconnect.core.PrivacyStatement], the
 * privacy page in the app and the technical detail fold are all written from it. The test in this package checks the
 * coverage of [DataSourceKind], the presence of every part of a subject and the wire path of every field that crosses
 * a network boundary. It says nothing about the wording of a screen.
 */

/** Whether a subject's data leaves the phone, leaves for the watch, stays on this phone, goes to a file the person chooses, or is legacy code. */
enum class PrivacyGroup(val key: String) {
    LEAVES_PHONE("leaves-phone"),
    LEAVES_FOR_WATCH("leaves-for-watch"),
    STAYS_ON_PHONE("stays-on-phone"),
    FILES_YOU_CHOOSE("files-you-choose"),
    REMOVED("removed"),
}

/** The stable key of a subject. It is also the key of the matching `privacy_subject_<key>` text. */
enum class PrivacySubject(val key: String) {
    GARMIN("garmin"),
    OURA("oura"),
    CLAUDE("claude"),
    GEMINI("gemini"),
    GITHUB("github"),
    NOTIFICATIONS("notifications"),
    WORKOUTS("workouts"),
    WATCH("watch"),
    HEALTH_CONNECT("health-connect"),
    FILES("files"),
    KEYS("keys"),
    REMOVED("removed"),
}

/**
 * Where an operation sends its request or its file. [hosts] is the list of network names a place really uses; it is
 * empty for a place that is not a network service (the watch, Health Connect, this phone, a file the person chooses).
 */
enum class PrivacyPlace(val key: String, val hosts: List<String> = emptyList()) {
    GARMIN("garmin", listOf("personal.connectapi.garmin.com")),
    OURA("oura", listOf("cloud.ouraring.com", "api.ouraring.com")),
    ANTHROPIC("anthropic", listOf("api.anthropic.com")),
    GOOGLE("google", listOf("generativelanguage.googleapis.com")),
    GITHUB("github", listOf("api.github.com", "github.com")),
    YOUR_WATCH("your-watch"),
    HEALTH_CONNECT("health-connect"),
    THIS_PHONE("this-phone"),
    FILE_YOU_CHOOSE("file-you-choose"),
    APP_YOU_CHOOSE("app-you-choose"),
}

/** What starts an operation. Static: the app does not show live traffic here. */
enum class PrivacyTrigger(val key: String) {
    MANUAL_SYNC("manual-sync"),
    APP_OPEN("app-open"),
    BACKGROUND("background"),
    CONNECT("connect"),
    REPORT("report"),
    COACH("coach"),
    KEY_CHECK("key-check"),
    UPDATE_CHECK("update-check"),
    DOWNLOAD("download"),
    NOTIFICATION("notification"),
    WORKOUT_SEND("workout-send"),
    FILE_EXPORT("file-export"),
    DIAGNOSTICS("diagnostics"),
    USER_ACTION("user-action"),
    NONE("none"),
}

/** Where data is kept on this phone, or on the device the person chose. */
enum class PrivacyStore(val key: String) {
    SQLITE("sqlite"),
    PLAIN_PREFS("plain-prefs"),
    ENCRYPTED_PREFS("encrypted-prefs"),
    MEMORY("memory"),
    APP_CACHE("app-cache"),
    USER_FILE("user-file"),
    HEALTH_CONNECT("health-connect"),
    WATCH("watch"),
}

/** Things a subject never sends, whatever else its operation does. */
enum class PrivacyNever(val key: String) {
    GPS_POINTS("gps-points"),
    JOURNAL("journal"),
    NOTES("notes"),
    OWN_WORKOUT_TEXT("own-workout-text"),
    OTHER_KEYS("other-keys"),
    LOCATION("location"),
    CONTACTS("contacts"),
    CALENDAR("calendar"),
    NOTIFICATION_TEXT_TO_SERVER("notification-text-to-server"),
}

/** What the person has to have given or done before the operation can run. */
enum class PrivacyRequirement(val key: String) {
    GARMIN_KEY("garmin-key"),
    OURA_LOGIN("oura-login"),
    AI_KEY("ai-key"),
    NOTIFICATION_ACCESS("notification-access"),
    WATCH_CONNECTION("watch-connection"),
    HEALTH_CONNECT_PERMISSION("health-connect-permission"),
    USER_ACTION("user-action"),
}

/**
 * One field that can cross a boundary. [key] is stable and readable; [wire] is the exact place it travels, in the form
 * of the request or answer: a header (`Authorization: ApiKey`), a query parameter (`query.startDate`), a JSON path
 * (`json.model`), a form field (`form.client_secret`) or a Bluetooth field (`ble.title`). A null [wire] means the field
 * is local or received in a shape without a request path of its own.
 */
data class PrivacyField(val key: String, val wire: String? = null) {
    override fun toString(): String = key
}

/**
 * One thing the app does with one subject: [id] is stable within the subject, [trigger] says when it runs,
 * [destination] says where it goes, [sends] and [receives] are the fields it carries, and [never] lists what that
 * operation never carries.
 */
data class PrivacyOperation(
    val id: String,
    val trigger: PrivacyTrigger,
    val destination: PrivacyPlace,
    val sends: List<PrivacyField>,
    val receives: List<PrivacyField> = emptyList(),
    val never: List<PrivacyNever> = emptyList(),
)

/** One subject of the privacy page, with all [DataSourceKind]s it covers and everything it keeps or needs. */
data class PrivacySubjectSpec(
    val subject: PrivacySubject,
    val group: PrivacyGroup,
    val dataSources: Set<DataSourceKind>,
    val operations: List<PrivacyOperation>,
    val stores: List<PrivacyStore>,
    val requirements: List<PrivacyRequirement>,
    /**
     * Legacy: the feature was removed (WHOOP and Hume). Only rows from an older version can still be on the phone;
     * no operation sends anything and the subject has no active host.
     */
    val legacy: Boolean = false,
)

/** The field catalogue, shared by the operations so that one field always has one key and one wire path. */
object PrivacyFields {
    val API_KEY = PrivacyField("API_KEY", "Authorization: ApiKey")
    val AI_KEY = PrivacyField("AI_KEY", "x-api-key / x-goog-api-key")
    val ACCESS_TOKEN = PrivacyField("ACCESS_TOKEN", "Authorization: Bearer")
    val DATE_RANGE = PrivacyField("DATE_RANGE", "query.startDate,query.endDate / query.start_date,query.end_date")
    val ACTIVITY_ID = PrivacyField("ACTIVITY_ID", "path.activityId")
    val CLIENT_ID = PrivacyField("CLIENT_ID", "query.client_id / form.client_id")
    val CLIENT_SECRET = PrivacyField("CLIENT_SECRET", "form.client_secret")
    val REDIRECT_URI = PrivacyField("REDIRECT_URI", "query.redirect_uri / form.redirect_uri")
    val SCOPE = PrivacyField("SCOPE", "query.scope")
    val STATE = PrivacyField("STATE", "query.state")
    val OAUTH_CODE = PrivacyField("OAUTH_CODE", "query.code / form.code")
    val PKCE_VERIFIER = PrivacyField("PKCE_VERIFIER", "query.code_challenge / form.code_verifier")
    val REFRESH_TOKEN = PrivacyField("REFRESH_TOKEN", "form.refresh_token")
    val NEXT_TOKEN = PrivacyField("NEXT_TOKEN", "query.next_token / response.next_token")
    val MODEL = PrivacyField("MODEL", "json.model / path.model")
    val SYSTEM_PROMPT = PrivacyField("SYSTEM_PROMPT", "json.system / json.system_instruction.parts[0].text")
    val PING = PrivacyField("PING", "json.messages[0].content / json.contents[0].parts[0].text")
    val AI_FACTS = PrivacyField("AI_FACTS", "json.messages[].content / json.contents[].parts[].text")
    val COACH_QUESTION = PrivacyField("COACH_QUESTION", "json.messages[].content (question) / json.contents[].parts[].text (question)")
    val COACH_HISTORY = PrivacyField("COACH_HISTORY", "json.messages[0..n].content / json.contents[0..n].parts[].text")
    val LANGUAGE = PrivacyField("LANGUAGE", "json.messages[].content (language line) / json.contents[].parts[].text (language line)")
    val RELEASE_REQUEST = PrivacyField("RELEASE_REQUEST", "GET /repos/Tufein/OpenConnect/releases/latest")
    val APK_REQUEST = PrivacyField("APK_REQUEST", "GET release.assets[].browser_download_url (*.apk)")
    val CHECKSUM_REQUEST = PrivacyField("CHECKSUM_REQUEST", "GET release.assets[].browser_download_url (*.sha256)")
    val NOTIFICATION_ID = PrivacyField("NOTIFICATION_ID", "ble.notification.id")
    val NOTIFICATION_APP = PrivacyField("NOTIFICATION_APP", "ble.notification.appId")
    val NOTIFICATION_APP_NAME = PrivacyField("NOTIFICATION_APP_NAME", "ble.notification.appName")
    val NOTIFICATION_TITLE = PrivacyField("NOTIFICATION_TITLE", "ble.notification.title")
    val NOTIFICATION_BODY = PrivacyField("NOTIFICATION_BODY", "ble.notification.body")
    val NOTIFICATION_SUBTITLE = PrivacyField("NOTIFICATION_SUBTITLE", "ble.notification.subtitle")
    val NOTIFICATION_TIME = PrivacyField("NOTIFICATION_TIME", "ble.notification.whenMillis")
    val NOTIFICATION_CATEGORY = PrivacyField("NOTIFICATION_CATEGORY", "ble.notification.category")
    val NOTIFICATION_FLAGS = PrivacyField("NOTIFICATION_FLAGS", "ble.notification.canDismiss")
    val WORKOUT_SPEC = PrivacyField("WORKOUT_SPEC", "watchFile:GARMIN/NewFiles/<workout>.fit")
    val HEALTH_READ_TYPES = PrivacyField("HEALTH_READ_TYPES", "healthConnect.read(StepsRecord,SleepSessionRecord,WeightRecord)")
    val HEALTH_WRITE_TYPES = PrivacyField("HEALTH_WRITE_TYPES", "healthConnect.write(ExerciseSessionRecord,DistanceRecord,TotalCaloriesBurnedRecord)")
    val FILE_CONTENT = PrivacyField("FILE_CONTENT", "file:user-chosen")
    val DIAGNOSTICS = PrivacyField("DIAGNOSTICS", "file:user-chosen (diagnostics text)")
    val DAILY_METRICS = PrivacyField("DAILY_METRICS", "response.dailySummaries[]")
    val ACTIVITY_FILE = PrivacyField("ACTIVITY_FILE", "response:application/zip (FIT download)")
    val SLEEP_DATA = PrivacyField("SLEEP_DATA", "response.data[] (daily_sleep)")
    val READINESS_DATA = PrivacyField("READINESS_DATA", "response.data[] (daily_readiness)")
    val ACTIVITY_DATA = PrivacyField("ACTIVITY_DATA", "response.data[] (daily_activity)")
    val SPO2_DATA = PrivacyField("SPO2_DATA", "response.data[] (daily_spo2)")
    val WORKOUT_DATA = PrivacyField("WORKOUT_DATA", "response.data[] (workout)")
    val RING_BATTERY = PrivacyField("RING_BATTERY", "response.data[0].level")
    val AI_TEXT = PrivacyField("AI_TEXT", "response.content[].text / response.candidates[].content.parts[].text")
    val RELEASE_JSON = PrivacyField("RELEASE_JSON", "response.json (releases/latest)")
    val APK_FILE = PrivacyField("APK_FILE", "response.body (application/vnd.android.package-archive)")
    val CHECKSUM_FILE = PrivacyField("CHECKSUM_FILE", "response.body (text/plain sha256)")
    val WATCH_DATA = PrivacyField("WATCH_DATA", "watchFile:Activity/*.fit,Monitor/*.FIT,Sleep/*.FIT,HSA/*.FIT")
    val HEART_RATE = PrivacyField("HEART_RATE", "ble.heartRate.measurement")
    val TOKEN = PrivacyField("TOKEN", "response.access_token")

    /** Every field once, for tests that check stable keys and wire paths. */
    val all: List<PrivacyField> = listOf(
        API_KEY, AI_KEY, ACCESS_TOKEN, DATE_RANGE, ACTIVITY_ID, CLIENT_ID, CLIENT_SECRET, REDIRECT_URI, SCOPE, STATE,
        OAUTH_CODE, PKCE_VERIFIER, REFRESH_TOKEN, NEXT_TOKEN, MODEL, SYSTEM_PROMPT, PING, AI_FACTS, COACH_QUESTION,
        COACH_HISTORY, LANGUAGE, RELEASE_REQUEST, APK_REQUEST, CHECKSUM_REQUEST, NOTIFICATION_ID, NOTIFICATION_APP,
        NOTIFICATION_APP_NAME, NOTIFICATION_TITLE, NOTIFICATION_BODY, NOTIFICATION_SUBTITLE, NOTIFICATION_TIME,
        NOTIFICATION_CATEGORY, NOTIFICATION_FLAGS, WORKOUT_SPEC, HEALTH_READ_TYPES, HEALTH_WRITE_TYPES, FILE_CONTENT,
        DIAGNOSTICS, DAILY_METRICS, ACTIVITY_FILE,
        SLEEP_DATA, READINESS_DATA, ACTIVITY_DATA, SPO2_DATA, WORKOUT_DATA, RING_BATTERY, AI_TEXT, RELEASE_JSON,
        APK_FILE, CHECKSUM_FILE, WATCH_DATA, HEART_RATE, TOKEN,
    )
}

object PrivacyRegister {
    /** The active subjects first, the removed ones last. */
    val subjects: List<PrivacySubjectSpec> = listOf(
        garmin(),
        oura(),
        ai(PrivacySubject.CLAUDE, PrivacyPlace.ANTHROPIC),
        ai(PrivacySubject.GEMINI, PrivacyPlace.GOOGLE),
        github(),
        notifications(),
        workouts(),
        watch(),
        healthConnect(),
        files(),
        keys(),
        removed(),
    )

    val bySubject: Map<PrivacySubject, PrivacySubjectSpec> = subjects.associateBy { it.subject }

    /** The network names a [place] really uses; empty for a place that is not on the network. */
    fun hostsFor(place: PrivacyPlace): List<String> = place.hosts

    /**
     * The compatibility view for [nl.tufein.openconnect.core.PrivacyStatement]: the five legacy keys, only for active
     * subjects, with the hosts their active operations use. Nothing is hand-maintained here.
     */
    val called: Map<String, List<String>> = linkedMapOf<String, List<String>>().apply {
        for (key in listOf("garmin", "oura", "claude", "gemini", "github")) {
            val spec = subjects.firstOrNull { it.subject.key == key && !it.legacy } ?: continue
            val hosts = spec.operations.flatMap { it.destination.hosts }.distinct()
            if (hosts.isNotEmpty()) put(key, hosts)
        }
    }

    /** Every network name an active subject can reach; no removed subject contributes. */
    val activeHosts: Set<String> = subjects
        .filterNot { it.legacy }
        .flatMapTo(linkedSetOf()) { spec -> spec.operations.flatMap { it.destination.hosts } }

    /**
     * The wire paths of one operation of [subject], by field key, in the order they are sent and then received. Fields
     * without a path of their own (local data, a file the person chooses) are left out; an unknown operation gives an
     * empty map rather than throwing, because this is a view used by screens and tests.
     */
    fun wirePaths(subject: PrivacySubject, operationId: String): Map<String, String> {
        val operation = bySubject[subject]?.operations?.firstOrNull { it.id == operationId } ?: return emptyMap()
        return (operation.sends + operation.receives)
            .mapNotNull { field -> field.wire?.let { field.key to it } }
            .toMap()
    }

    private fun garmin() = PrivacySubjectSpec(
        subject = PrivacySubject.GARMIN,
        group = PrivacyGroup.LEAVES_PHONE,
        dataSources = setOf(DataSourceKind.GARMIN_ACCOUNT, DataSourceKind.GARMIN_ACCOUNT_ACTIVITIES),
        operations = listOf(
            // The key is exchanged for a bearer token; no health data goes with it.
            PrivacyOperation(
                id = "TOKEN",
                trigger = PrivacyTrigger.MANUAL_SYNC,
                destination = PrivacyPlace.GARMIN,
                sends = listOf(PrivacyFields.API_KEY),
                receives = listOf(PrivacyFields.ACCESS_TOKEN),
                never = garminNever(),
            ),
            // Read-only summaries: only the token and the dates travel out; the days come back.
            PrivacyOperation(
                id = "DAILY",
                trigger = PrivacyTrigger.MANUAL_SYNC,
                destination = PrivacyPlace.GARMIN,
                sends = listOf(PrivacyFields.ACCESS_TOKEN, PrivacyFields.DATE_RANGE),
                receives = listOf(PrivacyFields.DAILY_METRICS),
                never = garminNever(),
            ),
            // One activity file, asked for by id. GPS comes back from Garmin; it is never uploaded.
            PrivacyOperation(
                id = "ACTIVITY",
                trigger = PrivacyTrigger.MANUAL_SYNC,
                destination = PrivacyPlace.GARMIN,
                sends = listOf(PrivacyFields.ACCESS_TOKEN, PrivacyFields.ACTIVITY_ID),
                receives = listOf(PrivacyFields.ACTIVITY_FILE),
                never = garminNever(),
            ),
        ),
        stores = listOf(PrivacyStore.SQLITE, PrivacyStore.ENCRYPTED_PREFS),
        requirements = listOf(PrivacyRequirement.GARMIN_KEY),
    )

    private fun garminNever() = listOf(
        PrivacyNever.GPS_POINTS, PrivacyNever.JOURNAL, PrivacyNever.NOTES, PrivacyNever.OWN_WORKOUT_TEXT,
        PrivacyNever.OTHER_KEYS, PrivacyNever.NOTIFICATION_TEXT_TO_SERVER,
    )

    private fun oura() = PrivacySubjectSpec(
        subject = PrivacySubject.OURA,
        group = PrivacyGroup.LEAVES_PHONE,
        dataSources = setOf(DataSourceKind.OURA),
        operations = listOf(
            // Login: the browser gets the application id, scope, redirect, state and the PKCE challenge; no health data.
            PrivacyOperation(
                id = "LOGIN",
                trigger = PrivacyTrigger.CONNECT,
                destination = PrivacyPlace.OURA,
                sends = listOf(
                    PrivacyFields.CLIENT_ID, PrivacyFields.SCOPE, PrivacyFields.REDIRECT_URI, PrivacyFields.STATE,
                    PrivacyFields.PKCE_VERIFIER,
                ),
                receives = listOf(PrivacyFields.OAUTH_CODE),
                never = ouraNever(),
            ),
            // Code for token, and refresh token for the next pair. Only the application's own credentials travel.
            PrivacyOperation(
                id = "TOKEN",
                trigger = PrivacyTrigger.CONNECT,
                destination = PrivacyPlace.OURA,
                sends = listOf(
                    PrivacyFields.CLIENT_ID, PrivacyFields.CLIENT_SECRET, PrivacyFields.OAUTH_CODE,
                    PrivacyFields.PKCE_VERIFIER, PrivacyFields.REFRESH_TOKEN,
                ),
                receives = listOf(PrivacyFields.TOKEN),
                never = ouraNever(),
            ),
            // Day summaries and the ring's charge: a bearer token, the dates and the page token go out.
            PrivacyOperation(
                id = "SYNC",
                trigger = PrivacyTrigger.BACKGROUND,
                destination = PrivacyPlace.OURA,
                sends = listOf(PrivacyFields.ACCESS_TOKEN, PrivacyFields.DATE_RANGE, PrivacyFields.NEXT_TOKEN),
                receives = listOf(
                    PrivacyFields.SLEEP_DATA, PrivacyFields.READINESS_DATA, PrivacyFields.ACTIVITY_DATA,
                    PrivacyFields.SPO2_DATA, PrivacyFields.WORKOUT_DATA, PrivacyFields.RING_BATTERY,
                ),
                never = ouraNever(),
            ),
        ),
        stores = listOf(PrivacyStore.SQLITE, PrivacyStore.ENCRYPTED_PREFS, PrivacyStore.PLAIN_PREFS),
        requirements = listOf(PrivacyRequirement.OURA_LOGIN),
    )

    private fun ouraNever() = listOf(
        PrivacyNever.GPS_POINTS, PrivacyNever.JOURNAL, PrivacyNever.NOTES, PrivacyNever.OWN_WORKOUT_TEXT,
        PrivacyNever.OTHER_KEYS, PrivacyNever.NOTIFICATION_TEXT_TO_SERVER,
    )

    private fun ai(subject: PrivacySubject, place: PrivacyPlace) = PrivacySubjectSpec(
        subject = subject,
        group = PrivacyGroup.LEAVES_PHONE,
        dataSources = setOf(if (subject == PrivacySubject.CLAUDE) DataSourceKind.AI_CLAUDE else DataSourceKind.AI_GEMINI),
        operations = listOf(
            // A key check: the key, the model and "ping". No facts, no health data.
            PrivacyOperation(
                id = "KEY_CHECK",
                trigger = PrivacyTrigger.KEY_CHECK,
                destination = place,
                sends = listOf(PrivacyFields.AI_KEY, PrivacyFields.MODEL, PrivacyFields.PING),
                never = aiNever(),
            ),
            // A report: key, model, the fixed system prompt, the bounded facts and the language.
            PrivacyOperation(
                id = "REPORT",
                trigger = PrivacyTrigger.REPORT,
                destination = place,
                sends = listOf(
                    PrivacyFields.AI_KEY, PrivacyFields.MODEL, PrivacyFields.SYSTEM_PROMPT, PrivacyFields.AI_FACTS,
                    PrivacyFields.LANGUAGE,
                ),
                receives = listOf(PrivacyFields.AI_TEXT),
                never = aiNever(),
            ),
            // A coach answer: key, model, the earlier turns, the question and the plan facts.
            PrivacyOperation(
                id = "COACH",
                trigger = PrivacyTrigger.COACH,
                destination = place,
                sends = listOf(
                    PrivacyFields.AI_KEY, PrivacyFields.MODEL, PrivacyFields.SYSTEM_PROMPT, PrivacyFields.COACH_HISTORY,
                    PrivacyFields.COACH_QUESTION, PrivacyFields.AI_FACTS, PrivacyFields.LANGUAGE,
                ),
                receives = listOf(PrivacyFields.AI_TEXT),
                never = aiNever(),
            ),
        ),
        stores = listOf(PrivacyStore.SQLITE, PrivacyStore.ENCRYPTED_PREFS, PrivacyStore.PLAIN_PREFS),
        requirements = listOf(PrivacyRequirement.AI_KEY),
    )

    /**
     * Facts are names, device, sport, distance, duration, pace/speed, splits, heart-rate halves, week totals and the
     * plan digest. They are not tracks: no GPS points, no journal, no notes, no own workout text and no other keys.
     */
    private fun aiNever() = listOf(
        PrivacyNever.GPS_POINTS, PrivacyNever.JOURNAL, PrivacyNever.NOTES, PrivacyNever.OWN_WORKOUT_TEXT,
        PrivacyNever.OTHER_KEYS, PrivacyNever.LOCATION, PrivacyNever.CONTACTS, PrivacyNever.CALENDAR,
        PrivacyNever.NOTIFICATION_TEXT_TO_SERVER,
    )

    private fun github() = PrivacySubjectSpec(
        subject = PrivacySubject.GITHUB,
        group = PrivacyGroup.LEAVES_PHONE,
        dataSources = setOf(DataSourceKind.UPDATER),
        operations = listOf(
            // The update check: no account, no token, only the public release list is asked for.
            PrivacyOperation(
                id = "CHECK",
                trigger = PrivacyTrigger.UPDATE_CHECK,
                destination = PrivacyPlace.GITHUB,
                sends = listOf(PrivacyFields.RELEASE_REQUEST),
                receives = listOf(PrivacyFields.RELEASE_JSON),
                never = githubNever(),
            ),
            // The APK and its checksum, from the addresses the release list named.
            PrivacyOperation(
                id = "DOWNLOAD",
                trigger = PrivacyTrigger.DOWNLOAD,
                destination = PrivacyPlace.GITHUB,
                sends = listOf(PrivacyFields.APK_REQUEST, PrivacyFields.CHECKSUM_REQUEST),
                receives = listOf(PrivacyFields.APK_FILE, PrivacyFields.CHECKSUM_FILE),
                never = githubNever(),
            ),
        ),
        stores = listOf(PrivacyStore.APP_CACHE),
        requirements = listOf(PrivacyRequirement.USER_ACTION),
    )

    private fun githubNever() = listOf(
        PrivacyNever.GPS_POINTS, PrivacyNever.JOURNAL, PrivacyNever.NOTES, PrivacyNever.OWN_WORKOUT_TEXT,
        PrivacyNever.OTHER_KEYS, PrivacyNever.LOCATION, PrivacyNever.CONTACTS, PrivacyNever.CALENDAR,
        PrivacyNever.NOTIFICATION_TEXT_TO_SERVER,
    )

    private fun notifications() = PrivacySubjectSpec(
        subject = PrivacySubject.NOTIFICATIONS,
        group = PrivacyGroup.LEAVES_FOR_WATCH,
        dataSources = emptySet(),
        operations = listOf(
            // Android hands the listener every app's notification; the rules drop ongoing, grouped, local-only, calls,
            // low-importance and DND ones, and the chosen apps and the hide-text switch filter the rest. At most 16
            // texts wait at most two minutes in memory for a watch that is reconnecting. Nothing goes to a server.
            PrivacyOperation(
                id = "SEND",
                trigger = PrivacyTrigger.NOTIFICATION,
                destination = PrivacyPlace.YOUR_WATCH,
                sends = listOf(
                    PrivacyFields.NOTIFICATION_ID, PrivacyFields.NOTIFICATION_APP, PrivacyFields.NOTIFICATION_APP_NAME,
                    PrivacyFields.NOTIFICATION_TITLE, PrivacyFields.NOTIFICATION_BODY, PrivacyFields.NOTIFICATION_SUBTITLE,
                    PrivacyFields.NOTIFICATION_TIME, PrivacyFields.NOTIFICATION_CATEGORY, PrivacyFields.NOTIFICATION_FLAGS,
                ),
                never = listOf(
                    PrivacyNever.NOTIFICATION_TEXT_TO_SERVER, PrivacyNever.GPS_POINTS, PrivacyNever.JOURNAL,
                    PrivacyNever.NOTES, PrivacyNever.LOCATION, PrivacyNever.CONTACTS, PrivacyNever.CALENDAR,
                    PrivacyNever.OTHER_KEYS,
                ),
            ),
        ),
        stores = listOf(PrivacyStore.MEMORY, PrivacyStore.PLAIN_PREFS),
        requirements = listOf(PrivacyRequirement.NOTIFICATION_ACCESS, PrivacyRequirement.WATCH_CONNECTION),
    )

    private fun workouts() = PrivacySubjectSpec(
        subject = PrivacySubject.WORKOUTS,
        group = PrivacyGroup.LEAVES_FOR_WATCH,
        dataSources = setOf(DataSourceKind.WORKOUTS_TO_WATCH),
        operations = listOf(
            // A workout made in the app becomes a FIT file in GARMIN/NewFiles on a watch connected by USB. The text of
            // the workout itself goes to the watch, not to a server.
            PrivacyOperation(
                id = "SEND",
                trigger = PrivacyTrigger.WORKOUT_SEND,
                destination = PrivacyPlace.YOUR_WATCH,
                sends = listOf(PrivacyFields.WORKOUT_SPEC),
                never = listOf(
                    PrivacyNever.NOTIFICATION_TEXT_TO_SERVER, PrivacyNever.OTHER_KEYS, PrivacyNever.GPS_POINTS,
                    PrivacyNever.JOURNAL, PrivacyNever.NOTES,
                ),
            ),
        ),
        stores = listOf(PrivacyStore.PLAIN_PREFS, PrivacyStore.WATCH),
        requirements = listOf(PrivacyRequirement.WATCH_CONNECTION, PrivacyRequirement.USER_ACTION),
    )

    private fun watch() = PrivacySubjectSpec(
        subject = PrivacySubject.WATCH,
        group = PrivacyGroup.STAYS_ON_PHONE,
        dataSources = setOf(
            DataSourceKind.WATCH_USB, DataSourceKind.WATCH_FOLDER, DataSourceKind.WATCH_BLUETOOTH,
            DataSourceKind.LIVE_HEART_RATE,
        ),
        operations = listOf(
            // FIT files over USB, a folder or Bluetooth, and live heart rate: data comes in and stays here.
            PrivacyOperation(
                id = "READ",
                trigger = PrivacyTrigger.CONNECT,
                destination = PrivacyPlace.YOUR_WATCH,
                sends = emptyList(),
                receives = listOf(PrivacyFields.WATCH_DATA, PrivacyFields.HEART_RATE),
                never = listOf(PrivacyNever.NOTIFICATION_TEXT_TO_SERVER, PrivacyNever.OTHER_KEYS, PrivacyNever.JOURNAL, PrivacyNever.NOTES),
            ),
        ),
        stores = listOf(PrivacyStore.SQLITE),
        requirements = listOf(PrivacyRequirement.WATCH_CONNECTION),
    )

    private fun healthConnect() = PrivacySubjectSpec(
        subject = PrivacySubject.HEALTH_CONNECT,
        group = PrivacyGroup.STAYS_ON_PHONE,
        dataSources = setOf(DataSourceKind.HEALTH_CONNECT),
        operations = listOf(
            // Read: steps, sleep and weight, only where OpenConnect has nothing yet. No GPS, journal or notes.
            PrivacyOperation(
                id = "READ",
                trigger = PrivacyTrigger.BACKGROUND,
                destination = PrivacyPlace.HEALTH_CONNECT,
                sends = emptyList(),
                receives = listOf(PrivacyFields.HEALTH_READ_TYPES),
                never = healthNever(),
            ),
            // Write: an exercise session with its distance and calories. No track is written.
            PrivacyOperation(
                id = "WRITE",
                trigger = PrivacyTrigger.MANUAL_SYNC,
                destination = PrivacyPlace.HEALTH_CONNECT,
                sends = listOf(PrivacyFields.HEALTH_WRITE_TYPES),
                never = healthNever(),
            ),
        ),
        stores = listOf(PrivacyStore.SQLITE, PrivacyStore.HEALTH_CONNECT),
        requirements = listOf(PrivacyRequirement.HEALTH_CONNECT_PERMISSION),
    )

    private fun healthNever() = listOf(
        PrivacyNever.GPS_POINTS, PrivacyNever.JOURNAL, PrivacyNever.NOTES, PrivacyNever.OWN_WORKOUT_TEXT,
        PrivacyNever.OTHER_KEYS, PrivacyNever.NOTIFICATION_TEXT_TO_SERVER,
    )

    private fun files() = PrivacySubjectSpec(
        subject = PrivacySubject.FILES,
        group = PrivacyGroup.FILES_YOU_CHOOSE,
        dataSources = setOf(DataSourceKind.FILE_IMPORT),
        operations = listOf(
            // Import: a FIT, GPX, TCX, gzip or ZIP the person chose. It is read here and not sent on.
            PrivacyOperation(
                id = "IMPORT",
                trigger = PrivacyTrigger.USER_ACTION,
                destination = PrivacyPlace.FILE_YOU_CHOOSE,
                sends = emptyList(),
                receives = listOf(PrivacyFields.FILE_CONTENT),
                never = fileNever(),
            ),
            // Export everything: one ZIP with the database, the journal, the shoes, the plan and the goals. No keys.
            PrivacyOperation(
                id = "EXPORT",
                trigger = PrivacyTrigger.FILE_EXPORT,
                destination = PrivacyPlace.FILE_YOU_CHOOSE,
                sends = listOf(PrivacyFields.FILE_CONTENT),
                never = fileNever(),
            ),
            // The backup file alone, for restoring on another phone. No keys.
            PrivacyOperation(
                id = "BACKUP",
                trigger = PrivacyTrigger.FILE_EXPORT,
                destination = PrivacyPlace.FILE_YOU_CHOOSE,
                sends = listOf(PrivacyFields.FILE_CONTENT),
                never = fileNever(),
            ),
            // The diagnostics text: versions, switches, what syncs did; no keys, names or health values.
            PrivacyOperation(
                id = "DIAGNOSTICS",
                trigger = PrivacyTrigger.DIAGNOSTICS,
                destination = PrivacyPlace.FILE_YOU_CHOOSE,
                sends = listOf(PrivacyFields.DIAGNOSTICS),
                never = fileNever(),
            ),
        ),
        stores = listOf(PrivacyStore.USER_FILE),
        requirements = listOf(PrivacyRequirement.USER_ACTION),
    )

    private fun fileNever() = listOf(
        PrivacyNever.OTHER_KEYS, PrivacyNever.NOTIFICATION_TEXT_TO_SERVER, PrivacyNever.GPS_POINTS,
        PrivacyNever.LOCATION, PrivacyNever.CONTACTS, PrivacyNever.CALENDAR,
    )

    private fun keys() = PrivacySubjectSpec(
        subject = PrivacySubject.KEYS,
        group = PrivacyGroup.STAYS_ON_PHONE,
        dataSources = setOf(DataSourceKind.KEY_VAULT_AND_LOCK),
        operations = listOf(
            // Keys and tokens live in the Keystore-backed preferences and never move from this phone.
            PrivacyOperation(
                id = "STORE",
                trigger = PrivacyTrigger.USER_ACTION,
                destination = PrivacyPlace.THIS_PHONE,
                sends = emptyList(),
                never = listOf(
                    PrivacyNever.OTHER_KEYS, PrivacyNever.NOTIFICATION_TEXT_TO_SERVER, PrivacyNever.GPS_POINTS,
                    PrivacyNever.JOURNAL, PrivacyNever.NOTES, PrivacyNever.LOCATION, PrivacyNever.CONTACTS,
                    PrivacyNever.CALENDAR,
                ),
            ),
        ),
        stores = listOf(PrivacyStore.ENCRYPTED_PREFS, PrivacyStore.PLAIN_PREFS),
        requirements = listOf(PrivacyRequirement.USER_ACTION),
    )

    private fun removed() = PrivacySubjectSpec(
        subject = PrivacySubject.REMOVED,
        group = PrivacyGroup.REMOVED,
        dataSources = setOf(DataSourceKind.WHOOP, DataSourceKind.HUME),
        operations = listOf(
            // No app code runs for these any more. Only rows from an older version can still be on this phone.
            PrivacyOperation(
                id = "LEGACY",
                trigger = PrivacyTrigger.NONE,
                destination = PrivacyPlace.THIS_PHONE,
                sends = emptyList(),
                receives = emptyList(),
                never = listOf(
                    PrivacyNever.GPS_POINTS, PrivacyNever.JOURNAL, PrivacyNever.NOTES, PrivacyNever.OWN_WORKOUT_TEXT,
                    PrivacyNever.OTHER_KEYS, PrivacyNever.LOCATION, PrivacyNever.CONTACTS, PrivacyNever.CALENDAR,
                    PrivacyNever.NOTIFICATION_TEXT_TO_SERVER,
                ),
            ),
        ),
        stores = listOf(PrivacyStore.SQLITE),
        requirements = listOf(PrivacyRequirement.USER_ACTION),
        legacy = true,
    )
}
