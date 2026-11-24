package nl.tufein.openconnect.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The language of the app itself, separate from the language of generated reports
 * ([nl.tufein.openconnect.ai.AiSettings.language]): "system" follows the phone, or one of [SUPPORTED] is chosen.
 *
 * The choice is stored in [nl.tufein.openconnect.data.SettingsStore] so it survives updates, and is handed to
 * Android 13's per-app language (through AppCompat) so the system settings, the launcher label and the home-screen
 * widget agree with what the screens show. Pure Kotlin where it matters: [normalize] and [resolve] need no Android
 * context, so they can be tested on the JVM.
 */
object AppLocale {
    /** Follow the phone's own language. */
    const val SYSTEM = "system"

    /** The languages the app has translations for; English is the base `values/` and the fallback. */
    val SUPPORTED = listOf("en", "nl", "de", "fr")

    /** Everything the Settings screen offers: the phone's language first, then the translations. */
    val CHOICES = listOf(SYSTEM) + SUPPORTED

    /** A stored or picked value that this version knows: a supported language, otherwise [SYSTEM]. */
    fun normalize(choice: String?): String {
        val key = choice?.trim()?.lowercase(Locale.ROOT) ?: return SYSTEM
        return if (SUPPORTED.contains(key)) key else SYSTEM
    }

    /**
     * The language to show: an explicitly chosen one as it is, or for [SYSTEM] the first supported language of
     * [systemLanguageTag] (for example "de-AT" gives "de"), and English when the phone speaks nothing we have.
     * [systemLanguageTag] is passed in pure JVM tests; the app leaves it out and the phone's tag is used.
     */
    fun resolve(choice: String, systemLanguageTag: String? = null): String {
        val normalized = normalize(choice)
        if (normalized != SYSTEM) return normalized
        return supportedPrefix(systemLanguageTag ?: phoneLanguageTag()) ?: "en"
    }

    /**
     * [base] with its texts in the language [choice] stands for, for the places that hold a Context of their own (a
     * widget, a notification, the ViewModel's messages) and so do not follow the Activity around.
     */
    fun wrap(base: Context, choice: String): Context {
        val language = resolve(choice)
        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(language))
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    /**
     * Makes [choice] the app's language for Android itself: the per-app locale (Android 13 and newer, through
     * AppCompat on every version) and the JVM-wide defaults that date and number formatting read.
     */
    fun apply(choice: String) {
        val language = resolve(choice)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        Locale.setDefault(Locale.forLanguageTag(language))
        LocaleList.setDefault(LocaleList.forLanguageTags(language))
    }

    /** The language the phone is in when it is one we have, otherwise English. */
    fun systemLanguage(): String = resolve(SYSTEM)

    /** "de-AT" -> "de"; null when the first part of the tag is not one of [SUPPORTED]. */
    internal fun supportedPrefix(tag: String?): String? {
        val language = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT) ?: return null
        return SUPPORTED.firstOrNull { it == language }
    }

    /** The phone's language tag. Reading Android's resources is not available in a plain JVM test, hence the fallback. */
    private fun phoneLanguageTag(): String? = try {
        Resources.getSystem().configuration.locales[0]?.toLanguageTag()
    } catch (_: Throwable) {
        try {
            Locale.getDefault().toLanguageTag()
        } catch (_: Throwable) {
            null
        }
    }
}
