package nl.tufein.openconnect.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import nl.tufein.openconnect.ai.AiSettings
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.garmin.GarminSettings
import nl.tufein.openconnect.health.HealthSettings
import nl.tufein.openconnect.notify.NudgeSettings
import nl.tufein.openconnect.watch.GarminBleOptions
import nl.tufein.openconnect.watch.GarminBleWatch
import nl.tufein.openconnect.watch.GarminWatchMemory
import java.util.Locale

/**
 * App settings. Every key (Claude, Gemini, Garmin) lives in Android Keystore-backed encrypted preferences; if that is
 * unavailable on a device the key is simply not stored, never written in plain text.
 *
 * [secure] makes the encrypted preferences, or null when the phone cannot; tests pass plain preferences instead.
 */
class SettingsStore(
    private val context: Context,
    secure: () -> SharedPreferences? = { encryptedPreferences(context) },
) : AiSettings {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences? by lazy(secure)

    /** Language of generated reports and coach answers: "en" or "nl". Defaults to the phone's language. */
    override var language: String
        get() = prefs.getString(KEY_LANGUAGE, null) ?: if (Locale.getDefault().language == "nl") "nl" else "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** "auto" (Claude, else Gemini, else the offline template), "template", "anthropic" or "gemini". */
    override var choice: String
        get() = prefs.getString(KEY_PROVIDER, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_PROVIDER, value).apply()

    override val canStoreKeys: Boolean get() = securePrefs != null

    override fun key(vendor: AiVendor): String? = try {
        securePrefs?.getString(keyName(vendor), null)
    } catch (_: Exception) {
        null
    }

    override fun setKey(vendor: AiVendor, key: String?) {
        val store = securePrefs ?: return
        store.edit().apply { if (key.isNullOrBlank()) remove(keyName(vendor)) else putString(keyName(vendor), key.trim()) }.apply()
    }

    override fun model(vendor: AiVendor): String? = prefs.getString(modelName(vendor), null)?.takeIf { it.isNotBlank() }

    override fun setModel(vendor: AiVendor, model: String?) {
        prefs.edit().apply { if (model.isNullOrBlank()) remove(modelName(vendor)) else putString(modelName(vendor), model.trim()) }.apply()
    }

    /** "system" (follow the phone), "dark" or "light". */
    var themeMode: String
        get() = prefs.getString(KEY_THEME, "system")?.takeIf { it in THEMES } ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value.takeIf { it in THEMES } ?: "system").apply()

    /** The colour theme, a key of [nl.tufein.openconnect.ui.Palette]: "arcade" unless another was picked. */
    var palette: String
        get() = prefs.getString(KEY_PALETTE, "arcade") ?: "arcade"
        set(value) = prefs.edit().putString(KEY_PALETTE, value).apply()

    /** The drawing style, a key of [nl.tufein.openconnect.ui.LookStyle]: "sport", "round" or "pixel". */
    var lookStyle: String
        get() = prefs.getString(KEY_STYLE, "sport") ?: "sport"
        set(value) = prefs.edit().putString(KEY_STYLE, value).apply()

    /** The design (v0.19.0): "clean", "dashboard" or "retro". Retro is the look of earlier versions, so an update changes nothing. */
    /** The watch face preview and build choices (v0.20.0): design, accent colour and four fields, comma separated. */
    var watchDesign: String
        get() = prefs.getString(KEY_WF_DESIGN, "clean") ?: "clean"
        set(value) = prefs.edit().putString(KEY_WF_DESIGN, value).apply()

    var watchColor: String
        get() = prefs.getString(KEY_WF_COLOR, "blue") ?: "blue"
        set(value) = prefs.edit().putString(KEY_WF_COLOR, value).apply()

    var watchFields: String
        get() = prefs.getString(KEY_WF_FIELDS, "battery,heart,stress,sleep") ?: "battery,heart,stress,sleep"
        set(value) = prefs.edit().putString(KEY_WF_FIELDS, value).apply()

    var watchDevice: String
        get() = prefs.getString(KEY_WF_DEVICE, "fenix847mm") ?: "fenix847mm"
        set(value) = prefs.edit().putString(KEY_WF_DEVICE, value).apply()

    val hasDesign: Boolean get() = prefs.contains(KEY_DESIGN)

    var design: String
        get() = prefs.getString(KEY_DESIGN, "retro") ?: "retro"
        set(value) = prefs.edit().putString(KEY_DESIGN, value).apply()

    /** The record of the last syncs, as the text [nl.tufein.openconnect.core.SyncLog] writes. */
    var syncLogText: String
        get() = prefs.getString(KEY_SYNC_LOG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SYNC_LOG, value).apply()

    /** The training plan the user chose, as [nl.tufein.openconnect.core.PlanConfig] writes it; empty for none. */
    var trainingPlan: String
        get() = prefs.getString(KEY_PLAN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PLAN, value).apply()

    /** The race the user is aiming at, as [nl.tufein.openconnect.core.RaceGoal] writes it; empty for none. */
    var raceGoal: String
        get() = prefs.getString(KEY_RACE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RACE, value).apply()

    /** The maximum heart rate the zones are worked out from; 0 means "use the highest measured". */
    var maxHr: Int
        get() = prefs.getInt(KEY_MAX_HR, 0)
        set(value) = prefs.edit().putInt(KEY_MAX_HR, value).apply()

    /** The shoes and the activities put on a pair by hand, as [nl.tufein.openconnect.core.GearBook] writes them. */
    var gear: String
        get() = prefs.getString(KEY_GEAR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEAR, value).apply()

    /** Whether the first-start questions (which devices do you use) were answered. */
    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /** Whether the Garmin parts (account, watch, sync status) are shown. On unless the person said no. */
    var useGarmin: Boolean
        get() = prefs.getBoolean(KEY_USE_GARMIN, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_GARMIN, value).apply()

    /** Whether the Oura tab is shown and the ring is synced. Off unless the person chose it. */
    var useOura: Boolean
        get() = prefs.getBoolean(KEY_USE_OURA, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_OURA, value).apply()

    /** Whether the app asks for the fingerprint, face or screen lock when it opens. */
    var appLock: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK, value).apply()

    /** The weekly goals shown on Home; a goal of 0 is switched off. */
    var weeklyGoals: WeeklyGoals
        get() = WeeklyGoals(
            intensityMinutes = prefs.getInt(KEY_GOAL_INTENSITY, WeeklyGoals().intensityMinutes),
            distanceKm = prefs.getInt(KEY_GOAL_DISTANCE, WeeklyGoals().distanceKm),
            activities = prefs.getInt(KEY_GOAL_ACTIVITIES, WeeklyGoals().activities),
        ).coerced()
        set(value) {
            val g = value.coerced()
            prefs.edit().putInt(KEY_GOAL_INTENSITY, g.intensityMinutes).putInt(KEY_GOAL_DISTANCE, g.distanceKm).putInt(KEY_GOAL_ACTIVITIES, g.activities).apply()
        }

    /**
     * The Garmin Personal API key (encrypted like the AI keys, and never written in plain text) and what the Garmin
     * screen remembers about syncing.
     */
    val garmin: GarminSettings = object : GarminSettings {
        override val canStoreKey: Boolean get() = securePrefs != null

        override var key: String?
            get() = try {
                securePrefs?.getString(KEY_GARMIN, null)
            } catch (_: Exception) {
                null
            }
            set(value) {
                val store = securePrefs ?: return
                store.edit().apply { if (value.isNullOrBlank()) remove(KEY_GARMIN) else putString(KEY_GARMIN, value.trim()) }.apply()
            }

        override var days: Int
            get() = prefs.getInt(KEY_GARMIN_DAYS, 30)
            set(value) = prefs.edit().putInt(KEY_GARMIN_DAYS, value).apply()

        /** On unless the person turned it off: Garmin Connect syncs by itself too, and nothing happens without a key. */
        override var autoSync: Boolean
            get() = prefs.getBoolean(KEY_GARMIN_AUTO, true)
            set(value) = prefs.edit().putBoolean(KEY_GARMIN_AUTO, value).apply()

        override var lastSyncMs: Long
            get() = prefs.getLong(KEY_GARMIN_LAST, 0L)
            set(value) = prefs.edit().putLong(KEY_GARMIN_LAST, value).apply()
    }

    /**
     * The Oura link: the owner's own Oura application and the login. The secret and the tokens are encrypted like the
     * other keys and never written in plain text; the client id and the redirect address are not secret.
     */
    val oura: nl.tufein.openconnect.oura.OuraSettings = object : nl.tufein.openconnect.oura.OuraSettings {
        private fun secret(name: String): String? = try {
            securePrefs?.getString(name, null)
        } catch (_: Exception) {
            null
        }

        private fun putSecret(name: String, value: String?) {
            val store = securePrefs ?: return
            store.edit().apply { if (value.isNullOrBlank()) remove(name) else putString(name, value) }.apply()
        }

        override val canStoreSecrets: Boolean get() = securePrefs != null

        override var clientId: String?
            get() = prefs.getString(KEY_OURA_CLIENT, null)
            set(value) = prefs.edit().apply { if (value.isNullOrBlank()) remove(KEY_OURA_CLIENT) else putString(KEY_OURA_CLIENT, value) }.apply()

        override var clientSecret: String?
            get() = secret(KEY_OURA_SECRET)
            set(value) = putSecret(KEY_OURA_SECRET, value)

        override var redirectUri: String
            get() = prefs.getString(KEY_OURA_REDIRECT, null)?.takeIf { it.isNotBlank() } ?: nl.tufein.openconnect.core.oura.OuraAuth.DEFAULT_REDIRECT
            set(value) = prefs.edit().putString(KEY_OURA_REDIRECT, value).apply()

        override var tokens: nl.tufein.openconnect.core.oura.OuraTokens?
            get() {
                val access = secret(KEY_OURA_ACCESS) ?: return null
                return nl.tufein.openconnect.core.oura.OuraTokens(access, secret(KEY_OURA_REFRESH), java.time.Instant.ofEpochMilli(prefs.getLong(KEY_OURA_EXPIRES, 0L)))
            }
            set(value) {
                putSecret(KEY_OURA_ACCESS, value?.access)
                putSecret(KEY_OURA_REFRESH, value?.refresh)
                prefs.edit().putLong(KEY_OURA_EXPIRES, value?.expiresAt?.toEpochMilli() ?: 0L).apply()
            }

        override var pendingLogin: String?
            get() = secret(KEY_OURA_PENDING)
            set(value) = putSecret(KEY_OURA_PENDING, value)

        override var lastSyncMs: Long
            get() = prefs.getLong(KEY_OURA_LAST, 0L)
            set(value) = prefs.edit().putLong(KEY_OURA_LAST, value).apply()

    }

    /** The watch that last synced over Bluetooth: its address and name, nothing secret. */
    val garminWatch: GarminWatchMemory = object : GarminWatchMemory {
        override fun load(): GarminBleWatch? {
            val address = prefs.getString(KEY_BLE_ADDRESS, null) ?: return null
            return GarminBleWatch(address, prefs.getString(KEY_BLE_NAME, null) ?: address, paired = true)
        }

        override fun save(watch: GarminBleWatch?) {
            prefs.edit().apply {
                if (watch == null) {
                    remove(KEY_BLE_ADDRESS)
                    remove(KEY_BLE_NAME)
                } else {
                    putString(KEY_BLE_ADDRESS, watch.address)
                    putString(KEY_BLE_NAME, watch.name)
                }
            }.apply()
        }
    }

    /** The two Bluetooth switches (archive on the watch, background sync), both off by default. */
    val garminBle: GarminBleOptions = object : GarminBleOptions {
        override var archiveAfterRead: Boolean
            get() = prefs.getBoolean(KEY_BLE_ARCHIVE, false)
            set(value) = prefs.edit().putBoolean(KEY_BLE_ARCHIVE, value).apply()
        override var background: Boolean
            get() = prefs.getBoolean(KEY_BLE_BACKGROUND, false)
            set(value) = prefs.edit().putBoolean(KEY_BLE_BACKGROUND, value).apply()
    }

    /** The notification switch (off by default) and the record of nudges already sent. */
    val nudges: NudgeSettings = object : NudgeSettings {
        override var enabled: Boolean
            get() = prefs.getBoolean(KEY_NUDGES, false)
            set(value) = prefs.edit().putBoolean(KEY_NUDGES, value).apply()
        override var sent: List<String>
            get() = (prefs.getString(KEY_NUDGES_SENT, "") ?: "").lines().filter { it.isNotBlank() }
            set(value) = prefs.edit().putString(KEY_NUDGES_SENT, value.joinToString("\n")).apply()
        override val goals: WeeklyGoals get() = weeklyGoals
        override val gear: String get() = this@SettingsStore.gear
    }

    /** The Health Connect switch: send activities after every sync. Off by default. */
    val health: HealthSettings = object : HealthSettings {
        override var auto: Boolean
            get() = prefs.getBoolean(KEY_HEALTH_AUTO, false)
            set(value) = prefs.edit().putBoolean(KEY_HEALTH_AUTO, value).apply()
    }

    /** What was already sent to Health Connect (client id and a fingerprint per activity), so the automatic export sends only what is new. */
    val healthLedger: nl.tufein.openconnect.core.health.ExportLedger = object : nl.tufein.openconnect.core.health.ExportLedger {
        override fun fingerprints(): Map<String, String> =
            nl.tufein.openconnect.core.health.ExportLedger.decode(prefs.getString(KEY_HEALTH_LEDGER, null))

        override fun remember(sent: Map<String, String>) {
            val all = fingerprints() + sent
            prefs.edit().putString(KEY_HEALTH_LEDGER, nl.tufein.openconnect.core.health.ExportLedger.encode(all)).apply()
        }
    }

    private fun keyName(vendor: AiVendor) = when (vendor) {
        AiVendor.CLAUDE -> KEY_CLAUDE // the name v0.1 to v0.4 used, so an earlier key is still found
        AiVendor.GEMINI -> KEY_GEMINI
    }

    private fun modelName(vendor: AiVendor) = when (vendor) {
        AiVendor.CLAUDE -> KEY_CLAUDE_MODEL
        AiVendor.GEMINI -> KEY_GEMINI_MODEL
    }

    companion object {
        val THEMES = listOf("system", "dark", "light")

        private const val KEY_LANGUAGE = "report_language"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PALETTE = "theme_palette"
        private const val KEY_STYLE = "theme_style"
        private const val KEY_DESIGN = "theme_design"
        private const val KEY_WF_DESIGN = "wf_design"
        private const val KEY_WF_COLOR = "wf_color"
        private const val KEY_WF_FIELDS = "wf_fields"
        private const val KEY_WF_DEVICE = "wf_device"
        private const val KEY_OURA_CLIENT = "oura_client_id"
        private const val KEY_OURA_SECRET = "oura_client_secret"
        private const val KEY_OURA_REDIRECT = "oura_redirect"
        private const val KEY_OURA_ACCESS = "oura_access"
        private const val KEY_OURA_REFRESH = "oura_refresh"
        private const val KEY_OURA_EXPIRES = "oura_expires"
        private const val KEY_OURA_PENDING = "oura_pending"
        private const val KEY_OURA_LAST = "oura_last_sync"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_USE_GARMIN = "use_garmin"
        private const val KEY_USE_OURA = "use_oura"
        private const val KEY_PLAN = "training_plan"
        private const val KEY_RACE = "race_goal"
        private const val KEY_MAX_HR = "max_hr"
        private const val KEY_GEAR = "gear"
        private const val KEY_APP_LOCK = "app_lock"
        private const val KEY_SYNC_LOG = "sync_log"
        private const val KEY_HEALTH_AUTO = "health_connect_auto"
        private const val KEY_HEALTH_LEDGER = "health_connect_ledger"
        private const val KEY_NUDGES = "nudges_enabled"
        private const val KEY_NUDGES_SENT = "nudges_sent"
        private const val KEY_BLE_ARCHIVE = "ble_archive_after_read"
        private const val KEY_BLE_BACKGROUND = "ble_background"
        private const val KEY_GOAL_INTENSITY = "goal_intensity_minutes"
        private const val KEY_GOAL_DISTANCE = "goal_distance_km"
        private const val KEY_GOAL_ACTIVITIES = "goal_activities"
        private const val KEY_CLAUDE = "anthropic_api_key"
        private const val KEY_CLAUDE_MODEL = "model"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_GARMIN = "garmin_personal_api_key"
        private const val KEY_GARMIN_DAYS = "garmin_days"
        private const val KEY_GARMIN_AUTO = "garmin_auto_sync"
        private const val KEY_GARMIN_LAST = "garmin_last_sync"
        private const val KEY_BLE_ADDRESS = "garmin_ble_address"
        private const val KEY_BLE_NAME = "garmin_ble_name"

        private fun encryptedPreferences(context: Context): SharedPreferences? = try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context,
                "secure_settings",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            null
        }
    }
}
