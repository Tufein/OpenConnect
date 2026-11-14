package nl.tufein.openconnect.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.reports.AiVendor
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
    fun `weekly goals start at the recommendations, are kept, and stay within limits`() {
        val s = store()
        assertEquals(WeeklyGoals(150, 20, 3), s.weeklyGoals)
        s.weeklyGoals = WeeklyGoals(intensityMinutes = 200, distanceKm = 0, activities = 5)
        assertEquals(WeeklyGoals(200, 0, 5), store().weeklyGoals, "kept for the next start")
        s.weeklyGoals = WeeklyGoals(intensityMinutes = 99_999, distanceKm = -4, activities = 99)
        assertEquals(WeeklyGoals(1000, 0, 21), s.weeklyGoals)
    }
}
