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
 * v0.30.0: the app has one look of its own (pixel type, pixel-cut corners, flat panels, pixel icons) instead of three
 * designs to pick from. These tests walk through the main screens in dark and light mode, which also writes the
 * screenshots, and prove that a colour theme from before v0.30.0 opens as the new default.
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
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
    }

    @Test
    fun anOldColourThemeOpensAsHandheldAndTheMainScreensDrawInIt() {
        launchFresh { app ->
            val container = container(app)
            // Up to v0.29.0 the default was "arcade", next to a design and a style: none of them exist any more.
            container.settings.palette = "arcade"
            app.containerOverride = container
        }
        scenario.onActivity { assertEquals("handheld", it.viewModel.look.value.palette) }
        loadDemo()
        waitForText("Readiness")
        shot("53-home-handheld-dark")
        tab("Activities").performClick()
        waitForText("Activities")
        shot("54-activities-handheld-dark")
        tab("Health").performClick()
        waitForText("Scores")
        shot("55-health-handheld-dark")
        tab("Insights").performClick()
        rule.waitForIdle()
        shot("56-insights-handheld-dark")
        tab("Settings").performClick()
        waitForText("Colours")
        // The three designs and the three styles are gone from Settings; the colours are left.
        assertEquals(false, hasText("Style", substring = false))
        assertEquals(false, hasText("Dashboard Dark", substring = false))
        shot("57-settings-handheld-dark")
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
        waitForText("Colours")
        shot("60-settings-handheld-light")
    }

    @Test
    fun theFirstStartAsksForDevicesOnly() {
        launchFresh(onboarded = false) { app -> app.containerOverride = container(app) }
        waitForText("Your devices")
        assertEquals(false, hasText("Pick a design", substring = false))
        shot("61-onboarding-handheld")
    }
}
