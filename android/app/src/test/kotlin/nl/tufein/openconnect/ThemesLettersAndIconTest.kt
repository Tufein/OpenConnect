package nl.tufein.openconnect

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.ui.AppIcon
import nl.tufein.openconnect.ui.AppIcons
import nl.tufein.openconnect.ui.Palette
import nl.tufein.openconnect.ui.colorSchemeFor
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** WCAG contrast ratio of two colours, 1 (the same) to 21 (black on white). */
private fun contrast(a: Color, b: Color): Double {
    fun lum(c: Color): Double {
        fun lin(v: Float): Double = v.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }
    val hi = maxOf(lum(a), lum(b))
    val lo = minOf(lum(a), lum(b))
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * v0.32.0: more than twenty colour themes, a choice between pixel and clear letters, and a choice of launcher icon.
 * The first test is plain arithmetic over every theme; the others walk through Settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class ThemesLettersAndIconTest : AppTestBase() {
    private fun container(app: OpenConnectApp) = AppContainer(
        SqliteRepository(app),
        SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
        FakeUsb { error("no watch in this test") },
        FakeBle(),
    )

    private fun settings() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings

    private fun loadDemo() {
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
    }

    @Test
    fun everyThemeIsReadableInDarkAndInLight() {
        val themes = Palette.entries.filter { it != Palette.DYNAMIC }
        assertTrue(themes.size >= 20, "there are plenty of themes to choose from")
        for (palette in themes) {
            for (dark in listOf(true, false)) {
                val c = colorSchemeFor(palette, dark)
                val name = "${palette.key} ${if (dark) "dark" else "light"}"
                assertTrue(contrast(c.onBackground, c.background) >= 7.0, "$name: text on the background")
                assertTrue(contrast(c.onSurface, c.surface) >= 7.0, "$name: text on a panel")
                assertTrue(contrast(c.onSurfaceVariant, c.surface) >= 4.5, "$name: small grey text on a panel")
                for ((accent, on) in listOf(c.primary to c.onPrimary, c.secondary to c.onSecondary, c.tertiary to c.onTertiary)) {
                    assertTrue(contrast(on, accent) >= 4.5, "$name: text on an accent colour")
                }
                assertTrue(contrast(c.primary, c.surface) >= 3.0, "$name: the main colour against a panel")
            }
        }
    }

    @Test
    fun manyThemesCanBePickedAndTheMainScreensDrawInThem() {
        launchFresh { app -> app.containerOverride = container(app) }
        loadDemo()
        tab("Settings").performClick()
        openAppearance()
        shot("62-settings-many-themes")
        for ((name, key) in listOf("Virtual Boy" to "virtualboy", "Terminal" to "terminal", "High contrast" to "contrast", "Dracula" to "dracula", "Candy" to "candy")) {
            clickText(name)
            rule.waitUntil(5_000) { settings().palette == key }
        }
        clickText("High contrast")
        rule.waitUntil(5_000) { settings().palette == "contrast" }
        tab("Home").performClick()
        waitForText("Readiness")
        shot("63-home-high-contrast")
        tab("Settings").performClick()
        openAppearance()
        clickText("Terminal")
        rule.waitUntil(5_000) { settings().palette == "terminal" }
        tab("Health").performClick()
        waitForText("Scores")
        shot("64-health-terminal")
    }

    @Test
    fun clearLettersAreChosenAndKept() {
        launchFresh { app -> app.containerOverride = container(app) }
        loadDemo()
        assertEquals("pixel", settings().letters)
        tab("Settings").performClick()
        openAppearance()
        // The modern default already uses clear letters. The choice only shows up in the retro style, so open that first.
        clickText("Retro")
        rule.waitUntil(5_000) { settings().style == "retro" }
        waitForText("Clear letters")
        clickText("Clear letters")
        rule.waitUntil(5_000) { settings().letters == "clear" }
        scenario.onActivity { assertEquals("clear", it.viewModel.look.value.letters) }
        shot("65-settings-clear-letters")
        tab("Home").performClick()
        waitForText("Readiness")
        shot("66-home-clear-letters")
        tab("Activities").performClick()
        waitForText("Activities")
        shot("67-activities-clear-letters")
        tab("Settings").performClick()
        openAppearance()
        clickText("Pixel letters")
        rule.waitUntil(5_000) { settings().letters == "pixel" }
    }

    @Test
    fun sleekAndClassicStylesAreChosenAndKept() {
        launchFresh { app -> app.containerOverride = container(app) }
        loadDemo()
        assertEquals("clean", settings().style, "the modern style is the default from v1.0.0")
        tab("Settings").performClick()
        openAppearance()
        clickText("Sleek")
        rule.waitUntil(5_000) { settings().style == "clean" }
        scenario.onActivity { assertEquals("clean", it.viewModel.look.value.style) }
        assertTrue(!hasText("Clear letters", substring = false), "only the retro style has a choice of letters")
        shot("69-settings-sleek")
        tab("Home").performClick()
        waitForText("Readiness")
        shot("70-home-sleek")
        tab("Settings").performClick()
        openAppearance()
        clickText("Classic")
        rule.waitUntil(5_000) { settings().style == "classic" }
        tab("Home").performClick()
        waitForText("Readiness")
        shot("71-home-classic")
        tab("Health").performClick()
        waitForText("Scores")
        shot("72-health-classic")
        tab("Settings").performClick()
        openAppearance()
        clickText("Retro")
        rule.waitUntil(5_000) { settings().style == "retro" }
        waitForText("Clear letters")
    }

    @Test
    fun theLauncherIconIsChosenInSettings() {
        launchFresh { app -> app.containerOverride = container(app) }
        waitForText("Load demo data")
        val context = ApplicationProvider.getApplicationContext<OpenConnectApp>()
        assertEquals(AppIcon.HANDHELD, AppIcons.current(context), "a fresh install has the first icon")
        tab("Settings").performClick()
        // The icons sit at the end of the Appearance section, which is folded away until it is opened.
        openAppearance()
        scrollToText("App icon")
        rule.waitForIdle()
        scrollToText("Night heart")
        shot("68-settings-app-icon")
        clickText("Night heart")
        rule.waitUntil(5_000) { AppIcons.current(context) == AppIcon.NIGHT }
        val pm = context.packageManager
        fun state(icon: AppIcon) = pm.getComponentEnabledSetting(ComponentName(context.packageName, "nl.tufein.openconnect.${icon.alias}"))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state(AppIcon.NIGHT))
        for (other in AppIcon.entries.filter { it != AppIcon.NIGHT }) {
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(other), "${other.key} is off")
        }
        scenario.onActivity { assertEquals(AppIcon.NIGHT, it.viewModel.appIcon.value) }
        clickText("Stopwatch")
        rule.waitUntil(5_000) { AppIcons.current(context) == AppIcon.STOPWATCH }
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(AppIcon.NIGHT))
    }
}
