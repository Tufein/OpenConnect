package nl.tufein.openconnect.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import nl.tufein.openconnect.core.reports.AnthropicReportProvider
import nl.tufein.openconnect.core.reports.ReportProvider
import nl.tufein.openconnect.core.reports.TemplateReportProvider
import java.util.Locale

/**
 * App settings. The Anthropic API key lives in Android Keystore-backed encrypted preferences; if
 * that is unavailable on a device the key is simply not stored, never written in plain text.
 */
class SettingsStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences? by lazy {
        try {
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

    /** Language of generated reports: "en" or "nl". Defaults to the phone's language. */
    var reportLanguage: String
        get() = prefs.getString(KEY_LANGUAGE, null) ?: if (Locale.getDefault().language == "nl") "nl" else "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** "auto" (Claude when a key is saved, else the offline template), "template" or "anthropic". */
    var providerChoice: String
        get() = prefs.getString(KEY_PROVIDER, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_PROVIDER, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, null) ?: AnthropicReportProvider.DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim().ifEmpty { AnthropicReportProvider.DEFAULT_MODEL }).apply()

    val hasApiKey: Boolean get() = !apiKey.isNullOrBlank()

    val canStoreApiKey: Boolean get() = secure != null

    var apiKey: String?
        get() = try {
            secure?.getString(KEY_API, null)
        } catch (_: Exception) {
            null
        }
        set(value) {
            val store = secure ?: return
            store.edit().apply { if (value.isNullOrBlank()) remove(KEY_API) else putString(KEY_API, value.trim()) }.apply()
        }

    fun reportProvider(): ReportProvider {
        val key = apiKey
        val useClaude = when (providerChoice) {
            "template" -> false
            "anthropic" -> !key.isNullOrBlank()
            else -> !key.isNullOrBlank()
        }
        return if (useClaude && key != null) AnthropicReportProvider(key, model = model) else TemplateReportProvider()
    }

    private companion object {
        const val KEY_LANGUAGE = "report_language"
        const val KEY_PROVIDER = "provider"
        const val KEY_MODEL = "model"
        const val KEY_API = "anthropic_api_key"
    }
}
