package nl.tufein.openconnect.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.i18n.AppLocale
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The real settings store on Robolectric, with plain preferences standing in for the Keystore-backed ones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {
    private lateinit var context: Context

    @Before
    fun clean() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("test_secure", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store(secure: Boolean = true) =
        SettingsStore(context, secure = { if (secure) context.getSharedPreferences("test_secure", Context.MODE_PRIVATE) else null })

    @Test
    fun `a fresh install uses auto, the phone language and syncs by itself once there is a key`() {
        val s = store()
        assertEquals("auto", s.choice)
        assertTrue(s.garmin.autoSync, "Garmin Connect syncs by itself, so does this")
        assertEquals(30, s.garmin.days)
        assertEquals("system", s.themeMode)
        assertEquals("soft", s.signals)
        assertEquals("thin", s.rings)
        assertEquals("auto", s.hero)
    }

    @Test
    fun `customization keeps only known keys and follows the chosen signals, rings and photo tone`() {
        val s = store()
        s.signals = "clear"
        s.rings = "bold"
        s.hero = "dusk"
        assertEquals("clear", store().signals)
        assertEquals("bold", store().rings)
        assertEquals("dusk", store().hero)
        // A value from a newer version, or a typo, falls back to the calm default instead of breaking the look.
        s.signals = "neon"
        s.rings = "huge"
        s.hero = "midnight"
        assertEquals("soft", s.signals)
        assertEquals("thin", s.rings)
        assertEquals("auto", s.hero)
    }

    @Test
    fun `the front page starts on calories with every card on, keeps a known choice and ignores an unknown one`() {
        val s = store()
        assertEquals("calories", s.homeMetric)
        assertTrue(s.homeBattery, "the battery card is on by default")
        assertTrue(s.homeJournal, "the journal is on by default")
        assertTrue(s.homeLast7, "Last 7 days is on by default")

        s.homeMetric = "sleep"
        s.homeBattery = false
        s.homeJournal = false
        s.homeLast7 = false
        val again = store() // the app was restarted
        assertEquals("sleep", again.homeMetric)
        assertFalse(again.homeBattery)
        assertFalse(again.homeJournal)
        assertFalse(again.homeLast7)

        // A key from a newer version, or a typo, falls back to the card a fresh install has; it never breaks the page.
        s.homeMetric = "blood_oxygen"
        assertEquals("calories", s.homeMetric)
        assertEquals("calories", store().homeMetric)
    }

    @Test
    fun `what was sent to Health Connect is remembered between runs and adds up`() {
        store().healthLedger.remember(mapOf("openconnect-a1" to "f1"))
        val again = store().healthLedger // a new store: the app was restarted
        assertEquals(mapOf("openconnect-a1" to "f1"), again.fingerprints())
        again.remember(mapOf("openconnect-a2" to "f2", "openconnect-a1" to "f1b"))
        assertEquals(mapOf("openconnect-a1" to "f1b", "openconnect-a2" to "f2"), store().healthLedger.fingerprints())
    }

    @Test
    fun `keys are kept per provider and never in the plain settings`() {
        val s = store()
        s.setKey(AiVendor.CLAUDE, "sk-ant-claude-key-0123456789")
        s.setKey(AiVendor.GEMINI, "AIza-gemini-key-0123456789")
        assertEquals("sk-ant-claude-key-0123456789", s.key(AiVendor.CLAUDE))
        assertEquals("AIza-gemini-key-0123456789", s.key(AiVendor.GEMINI))

        val plain = context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toString()
        assertFalse("claude-key" in plain || "gemini-key" in plain, "the plain preferences hold no key")

        s.setKey(AiVendor.CLAUDE, null)
        assertNull(s.key(AiVendor.CLAUDE))
        assertEquals("AIza-gemini-key-0123456789", s.key(AiVendor.GEMINI))
    }

    @Test
    fun `without secure storage no key is kept at all`() {
        val s = store(secure = false)
        assertFalse(s.canStoreKeys)
        s.setKey(AiVendor.GEMINI, "AIza-gemini-key-0123456789")
        assertNull(s.key(AiVendor.GEMINI))
        s.garmin.key = "GCDC_0123456789abcdef.fedcba9876543210.AbCdEf"
        assertNull(s.garmin.key)
        val plain = context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toString()
        assertFalse("AIza-gemini" in plain || "GCDC_" in plain, "a key is never written in plain text as a fallback")
    }

    @Test
    fun `models and the choice are remembered and a missing model means the default`() {
        val s = store()
        assertNull(s.model(AiVendor.GEMINI))
        s.setModel(AiVendor.GEMINI, "gemini-3.5-flash-lite")
        assertEquals("gemini-3.5-flash-lite", s.model(AiVendor.GEMINI))
        s.setModel(AiVendor.GEMINI, null)
        assertNull(s.model(AiVendor.GEMINI))
        s.choice = "gemini"
        assertEquals("gemini", store().choice, "a new store on the same preferences sees it")
    }

    @Test
    fun `a key saved by the first versions of the app is still found`() {
        // v0.1 to v0.4 kept the Claude key under this name in the secure preferences.
        context.getSharedPreferences("test_secure", Context.MODE_PRIVATE).edit().putString("anthropic_api_key", "sk-ant-old-key-0123456789").commit()
        assertEquals("sk-ant-old-key-0123456789", store().key(AiVendor.CLAUDE))
        // and the old model setting
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("model", "claude-sonnet-5-5").commit()
        assertEquals("claude-sonnet-5-5", store().model(AiVendor.CLAUDE))
    }

    @Test
    fun `the theme can be system, dark or light and nothing else`() {
        val s = store()
        s.themeMode = "dark"
        assertEquals("dark", s.themeMode)
        s.themeMode = "light"
        assertEquals("light", s.themeMode)
        s.themeMode = "neon"
        assertEquals("system", s.themeMode, "an unknown value is not kept")
    }

    @Test
    fun `the app language starts on system, is kept, and an unknown value falls back to system`() {
        val s = store()
        assertEquals(AppLocale.SYSTEM, s.appLanguage, "a fresh install follows the phone")
        s.appLanguage = "de"
        assertEquals("de", s.appLanguage)
        assertEquals("de", store().appLanguage, "kept for the next start")
        s.appLanguage = "FR"
        assertEquals("fr", s.appLanguage, "a capital is normalised")
        s.appLanguage = "de-AT"
        assertEquals(AppLocale.SYSTEM, s.appLanguage, "a full tag is not a choice the screen offers")
        s.appLanguage = "it"
        assertEquals(AppLocale.SYSTEM, s.appLanguage, "a language we do not have falls back to the phone")
        assertEquals(
            "system",
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("app_language", null),
            "the normalised choice is what is stored",
        )
    }

    @Test
    fun `an install from before the language choice opens on the phone's language`() {
        // Preferences written by v1.10.0: no app_language key at all.
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("theme_mode", "dark")
            .putString("report_language", "nl")
            .commit()
        val s = store()
        assertEquals("dark", s.themeMode, "the older settings still decode")
        assertEquals("nl", s.language)
        assertEquals(AppLocale.SYSTEM, s.appLanguage)
    }

    @Test
    fun `weekly goals start at the recommendations, are kept, and stay within limits`() {
        val s = store()
        assertEquals(WeeklyGoals(150, 20, 3), s.weeklyGoals)
        s.weeklyGoals = WeeklyGoals(intensityMinutes = 200, distanceKm = 0, activities = 5)
        assertEquals(WeeklyGoals(200, 0, 5), store().weeklyGoals, "kept for the next start")
        s.weeklyGoals = WeeklyGoals(intensityMinutes = 99_999, distanceKm = -4, activities = 99)
        assertEquals(WeeklyGoals(1000, 0, 21), s.weeklyGoals)
    }

    @Test
    fun `forwarding notifications to the watch is off and empty until the person chooses, and what they choose is kept`() {
        val fresh = store().forwarding
        assertFalse(fresh.enabled)
        assertEquals(emptySet(), fresh.apps)
        assertFalse(fresh.hideContent)
        assertFalse(fresh.onlyWhenScreenOff)

        val s = store().forwarding
        s.enabled = true
        s.apps = setOf("com.whatsapp", "com.google.android.gm")
        s.hideContent = true
        s.onlyWhenScreenOff = true
        val again = store().forwarding // the app was restarted
        assertTrue(again.enabled && again.hideContent && again.onlyWhenScreenOff)
        assertEquals(setOf("com.whatsapp", "com.google.android.gm"), again.apps)
        again.apps = emptySet()
        assertEquals(emptySet(), store().forwarding.apps, "the last app can be taken off again")
    }

    /** Preferences like the Keystore-backed ones on a phone whose key store went bad: reading works, encrypting throws. */
    private class FailingWrites(private val real: SharedPreferences) : SharedPreferences by real {
        override fun edit(): SharedPreferences.Editor {
            val editor = real.edit()
            return java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
                if (method.name == "putString") throw SecurityException("Could not encrypt data")
                val result = method.invoke(editor, *(args ?: emptyArray()))
                if (result === editor) proxy else result
            } as SharedPreferences.Editor
        }
    }

    @Test
    fun `a key store that fails while writing is not a crash, the secret is just not stored`() {
        val s = SettingsStore(context, secure = { FailingWrites(context.getSharedPreferences("test_secure", Context.MODE_PRIVATE)) })
        s.setKey(AiVendor.CLAUDE, "sk-ant-claude-key-0123456789") // used to throw from the screen that saves it
        s.garmin.key = "GCDC_0123456789abcdef.fedcba9876543210.AbCdEf/12+34=="
        s.oura.clientSecret = "oura-secret-FAKE"
        assertNull(s.key(AiVendor.CLAUDE))
        assertNull(s.garmin.key)
        assertNull(s.oura.clientSecret)
        val plain = context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toString()
        assertFalse("claude-key" in plain || "FAKE" in plain, "nothing falls back to the plain preferences")
    }
}
