package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.ui.ChiptunePlayer
import nl.tufein.openconnect.ui.Palette
import nl.tufein.openconnect.ui.PaletteGroup
import nl.tufein.openconnect.ui.colorSchemeFor
import nl.tufein.openconnect.ui.scanlineOffsets
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v1.15.0, the first part of the retro pack: an LCD palette in four shades of green, scanlines for the Retro style and
 * 8-bit tunes when a sync brings something new. Everything is our own design and off until chosen; the contrast of
 * every palette, the LCD one included, is checked by [ThemesLettersAndIconTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class RetroPackTest : AppTestBase() {
    private val played = CopyOnWriteArrayList<String>()

    private fun container(app: OpenConnectApp) = AppContainer(
        SqliteRepository(app),
        SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
        FakeUsb { error("no watch in this test") },
        FakeBle(),
        chiptune = ChiptunePlayer { tune -> played += tune.key },
    )

    private fun app() = ApplicationProvider.getApplicationContext<OpenConnectApp>()

    private fun settings() = app().container.settings

    private fun loadDemo() {
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
    }

    /** Hue in degrees (0 to 360) of a colour. */
    private fun hue(c: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
        return hsv[0]
    }

    @Test
    fun theLcdPaletteIsFourShadesOfGreen() {
        assertEquals(Palette.LCD, Palette.of("lcd"))
        assertEquals(PaletteGroup.RETRO, Palette.LCD.group)
        for (dark in listOf(true, false)) {
            val c = colorSchemeFor(Palette.LCD, dark)
            for ((name, colour) in listOf("background" to c.background, "panel" to c.surface, "text" to c.onBackground, "main" to c.primary)) {
                assertTrue(hue(colour) in 60f..150f, "LCD ${if (dark) "dark" else "light"} $name is green, hue ${hue(colour)}")
            }
        }
    }

    @Test
    fun scanlinesAreEvenlySpacedAndCoverTheScreen() {
        assertEquals(listOf(0f, 3f, 6f, 9f), scanlineOffsets(heightPx = 10f, stepPx = 3f))
        assertEquals(emptyList(), scanlineOffsets(heightPx = 0f, stepPx = 3f))
        assertEquals(emptyList(), scanlineOffsets(heightPx = 100f, stepPx = 0f), "a step of zero never loops forever")
        assertEquals(334, scanlineOffsets(heightPx = 1000f, stepPx = 3f).size)
    }

    @Test
    fun scanlinesAreOnlyOfferedInTheRetroStyleAndAreKept() {
        launchFresh { app -> app.containerOverride = container(app) }
        loadDemo()
        tab("Settings").performClick()
        openAppearance()
        assertTrue(!hasText("Scanlines (CRT look)", substring = false), "scanlines belong to the retro style")
        assertEquals(false, settings().scanlines, "off on a fresh install")
        clickText("Retro")
        rule.waitUntil(5_000) { settings().style == "retro" }
        clickText("Scanlines (CRT look)")
        rule.waitUntil(5_000) { settings().scanlines }
        // Switching the lines on must not rebuild the screen: Settings stays open where it was.
        waitForText("Scanlines (CRT look)")
        scenario.onActivity { assertEquals(true, it.viewModel.look.value.scanlines) }
        clickText("LCD green")
        rule.waitUntil(5_000) { settings().palette == "lcd" }
        shot("90-settings-retro-lcd-scanlines")
        tab("Home").performClick()
        waitForText("Readiness")
        shot("91-home-retro-lcd-scanlines")
    }

    @Test
    fun aTunePlaysOnlyWhenSoundsAreOnAndASyncBroughtSomethingIn() {
        launchFresh { app -> app.containerOverride = container(app) }
        tab("Settings").performClick()
        openAppearance()
        assertEquals(false, settings().retroSounds, "off on a fresh install")
        app().container.syncLog.record(SyncLogEntry(Instant.now(), SyncLog.SOURCE_BLE, ok = true, added = 3))
        rule.waitForIdle()
        assertTrue(played.isEmpty(), "no sound while the switch is off")

        clickText("8-bit sounds")
        rule.waitUntil(5_000) { settings().retroSounds }
        clickText("Play tune")
        rule.waitUntil(5_000) { played.size == 1 }
        assertEquals("sync", played.last())

        app().container.syncLog.record(SyncLogEntry(Instant.now(), SyncLog.SOURCE_ACCOUNT, ok = true, added = 2))
        rule.waitUntil(5_000) { played.size == 2 }
        app().container.syncLog.record(SyncLogEntry(Instant.now(), SyncLog.SOURCE_OURA, ok = true, added = 0))
        app().container.syncLog.record(SyncLogEntry(Instant.now(), SyncLog.SOURCE_BLE, ok = false, added = 0, detail = "x"))
        rule.waitForIdle()
        Thread.sleep(300)
        rule.waitForIdle()
        assertEquals(2, played.size, "a sync with nothing new, or a failed one, stays quiet")
    }
}
