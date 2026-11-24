package nl.tufein.openconnect.i18n

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app-language rules, on the plain JVM: no Context, no Robolectric. [AppLocale.wrap] and [AppLocale.apply] need
 * Android and are covered by the screen tests that switch the language.
 */
class AppLocaleTest {
    @Test
    fun `the choices are the phone's language followed by the translations`() {
        assertEquals(listOf("en", "nl", "de", "fr"), AppLocale.SUPPORTED)
        assertEquals(listOf(AppLocale.SYSTEM, "en", "nl", "de", "fr"), AppLocale.CHOICES)
    }

    @Test
    fun `a supported choice is kept as it is and everything else means system`() {
        for (language in AppLocale.SUPPORTED) assertEquals(language, AppLocale.normalize(language))
        assertEquals(AppLocale.SYSTEM, AppLocale.normalize(AppLocale.SYSTEM))
        assertEquals(AppLocale.SYSTEM, normalizeEmpty())
        assertEquals(AppLocale.SYSTEM, AppLocale.normalize(null))
        assertEquals(AppLocale.SYSTEM, AppLocale.normalize("it"), "a language without texts")
        assertEquals(AppLocale.SYSTEM, AppLocale.normalize("de-AT"), "a full tag is not one of the choices")
        assertEquals(AppLocale.SYSTEM, AppLocale.normalize("english"))
        assertEquals("nl", AppLocale.normalize("NL"), "a capital from an old file still means Dutch")
        assertEquals("de", AppLocale.normalize("  de  "), "stray spaces do not break the choice")
    }

    @Test
    fun `an explicit choice wins over the phone's language`() {
        assertEquals("nl", AppLocale.resolve("nl", "de-AT"))
        assertEquals("de", AppLocale.resolve("de", "fr-CA"))
        assertEquals("fr", AppLocale.resolve("fr", "ja-JP"))
        assertEquals("en", AppLocale.resolve("en", "nl-NL"))
    }

    @Test
    fun `system follows the first supported part of the phone's tag`() {
        assertEquals("de", AppLocale.resolve(AppLocale.SYSTEM, "de-AT"))
        assertEquals("fr", AppLocale.resolve(AppLocale.SYSTEM, "fr-CA"))
        assertEquals("nl", AppLocale.resolve(AppLocale.SYSTEM, "nl-BE"))
        assertEquals("en", AppLocale.resolve(AppLocale.SYSTEM, "en-GB"))
    }

    @Test
    fun `system falls back to English for a phone language we do not have`() {
        assertEquals("en", AppLocale.resolve(AppLocale.SYSTEM, "ja-JP"))
        assertEquals("en", AppLocale.resolve(AppLocale.SYSTEM, "it"))
        assertEquals("en", AppLocale.resolve(AppLocale.SYSTEM, ""))
        assertEquals("en", AppLocale.resolve(AppLocale.SYSTEM, "   "))
    }

    @Test
    fun `without a tag the phone's own default is used and the answer is always one we have`() {
        // On the JVM Resources.getSystem() is a stub, so the JVM's default locale stands in for the phone's.
        assertTrue(AppLocale.resolve(AppLocale.SYSTEM) in AppLocale.SUPPORTED)
        assertTrue(AppLocale.systemLanguage() in AppLocale.SUPPORTED)
    }

    private fun normalizeEmpty() = AppLocale.normalize("")
}
