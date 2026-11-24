package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * v0.31.0: the app has one look of its own instead of three designs to pick from. These tests walk through the main
 * screens in dark and light mode, which also writes the screenshots, and prove that a colour theme from before v0.31.0
 * opens as the calm Olive default instead of crashing or showing a theme that is no longer offered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class LookTest : AppTestBase() {
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
    fun anOldColourThemeOpensAsOliveAndTheMainScreensDrawInIt() {
        launchFresh { app ->
            val container = container(app)
            // Up to v0.29.0 the default was "arcade", next to a design and a style: none of them exist any more.
            container.settings.palette = "arcade"
            app.containerOverride = container
        }
        scenario.onActivity { assertEquals("olive", it.viewModel.look.value.palette) }
        loadDemo()
        waitForText("Readiness")
        shot("53-home-olive-dark")
        tab("Activities").performClick()
        waitForText("Activities")
        shot("54-activities-olive-dark")
        tab("Health").performClick()
        waitForText("Scores")
        shot("55-health-olive-dark")
        tab("Insights").performClick()
        rule.waitForIdle()
        shot("56-insights-olive-dark")
        tab("Settings").performClick()
        openAppearance()
        // The old three designs are gone from Settings; the colours are left, and since v0.33.0 a style (Retro, Sleek, Classic).
        assertEquals(true, hasText("Style", substring = false))
        assertEquals(false, hasText("Dashboard Dark", substring = false))
        shot("57-settings-olive-dark")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun theSameLookInLightMode() {
        launchFresh { app -> app.containerOverride = container(app) }
        loadDemo()
        waitForText("Readiness")
        shot("58-home-handheld-light")
        tab("Health").performClick()
        waitForText("Scores")
        shot("59-health-handheld-light")
        tab("Settings").performClick()
        openAppearance()
        shot("60-settings-handheld-light")
    }

    @Test
    fun theFirstStartAsksForDevicesOnly() {
        launchFresh(onboarded = false) { app -> app.containerOverride = container(app) }
        waitForText("Your devices")
        assertEquals(false, hasText("Pick a design", substring = false))
        shot("61-onboarding-handheld")
    }

    @Test
    fun theFirstStartLetsYouPickALookAndKeepsIt() {
        launchFresh(onboarded = false) { app -> app.containerOverride = container(app) }
        waitForText("Choose your look")
        // Olive and the editorial style are what a fresh install starts on.
        assertEquals("olive", settings().palette)
        assertEquals("editorial", settings().style)
        clickText("Retro")
        rule.waitUntil(5_000) { settings().style == "retro" }
        scenario.onActivity { assertEquals("retro", it.viewModel.look.value.style) }
        shot("61b-onboarding-look")
    }
}
