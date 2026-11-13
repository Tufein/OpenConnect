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
 * v0.19.0: the three designs (Clean Pro, Dashboard Dark, Retro Pro). Each can be picked in Settings, brings its own
 * colours and style, is kept, and lays out Home its own way. A new install starts in Clean Pro and can pick at the first start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class DesignsTest : AppTestBase() {
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

    private fun pick(design: String) {
        tab("Settings").performClick()
        waitForText("Design")
        clickText(design)
    }

    @Test
    fun eachDesignIsPickedKeptAndShapesHome() {
        launchFresh { app -> app.containerOverride = container(app) }
        loadDemo()
        // An update keeps the look of earlier versions.
        assertEquals("retro", settings().design)

        pick("Dashboard Dark")
        rule.waitUntil(5_000) { settings().design == "dashboard" && settings().palette == "midnight" && settings().lookStyle == "round" }
        tab("Home").performClick()
        waitForText("Resting heart rate")
        shot("53-home-dashboard-dark")
        tab("Health").performClick()
        waitForText("Scores")
        shot("54-health-dashboard-dark")

        pick("Clean Pro")
        rule.waitUntil(5_000) { settings().design == "clean" && settings().palette == "clean" }
        tab("Home").performClick()
        waitForText("My day")
        shot("55-home-clean-pro-dark")

        pick("Retro Pro")
        rule.waitUntil(5_000) { settings().design == "retro" && settings().palette == "arcade" && settings().lookStyle == "sport" }
        tab("Home").performClick()
        waitForText("Readiness")
        assertEquals(false, hasText("My day", substring = false))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun cleanProInLightMode() {
        launchFresh { app -> app.containerOverride = container(app) }
        loadDemo()
        pick("Clean Pro")
        rule.waitUntil(5_000) { settings().design == "clean" }
        shot("56-settings-designs-light")
        tab("Home").performClick()
        waitForText("My day")
        shot("57-home-clean-pro-light")
        tab("Health").performClick()
        waitForText("Scores")
        shot("58-health-clean-pro-light")
    }

    @Test
    fun aNewInstallStartsInCleanProAndCanPickAtTheFirstStart() {
        launchFresh(onboarded = false) { app -> app.containerOverride = container(app) }
        waitForText("Pick a design")
        assertEquals("clean", settings().design)
        clickText("Dashboard Dark")
        rule.waitUntil(5_000) { settings().design == "dashboard" }
    }
}
