package nl.tufein.openconnect.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import nl.tufein.openconnect.ai.AiSettings
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.garmin.GarminSettings
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

    /** The record of the last syncs, as the text [nl.tufein.openconnect.core.SyncLog] writes. */
    var syncLogText: String
        get() = prefs.getString(KEY_SYNC_LOG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SYNC_LOG, value).apply()

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
        private const val KEY_SYNC_LOG = "sync_log"
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
