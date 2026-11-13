package nl.tufein.openconnect.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import nl.tufein.openconnect.ai.AiSettings
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.garmin.GarminSettings
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
        private const val KEY_CLAUDE = "anthropic_api_key"
        private const val KEY_CLAUDE_MODEL = "model"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_GARMIN = "garmin_personal_api_key"
        private const val KEY_GARMIN_DAYS = "garmin_days"
        private const val KEY_GARMIN_AUTO = "garmin_auto_sync"
        private const val KEY_GARMIN_LAST = "garmin_last_sync"

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
