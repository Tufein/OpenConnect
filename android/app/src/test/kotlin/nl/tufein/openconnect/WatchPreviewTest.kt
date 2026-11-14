package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.ui.WatchColor
import nl.tufein.openconnect.ui.WatchFaceConfig
import nl.tufein.openconnect.ui.WatchField
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** v0.20.0: the round watch face preview in Settings, its choices and the build command they produce. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class WatchPreviewTest : AppTestBase() {
    private fun launchApp() = launchFresh { app ->
        app.containerOverride = AppContainer(
            SqliteRepository(app),
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            FakeUsb { error("no watch in this test") },
            FakeBle(),
        )
    }

    private fun settings() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings

    @Test
    fun theFacePreviewFollowsTheChoicesAndTheyAreKept() {
        launchApp()
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        tab("Settings").performClick()
        scrollToText("Watch face preview")
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription("Watch face preview, Clean Pro", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("59-watch-preview-clean")

        clickText("Dashboard face")
        clickText("Amber")
        rule.waitUntil(5_000) { settings().watchDesign == "dashboard" && settings().watchColor == "amber" }
        shot("60-watch-preview-dashboard")

        clickText("Retro face")
        clickText("Pink")
        clickText("fenix 8 Pro 47 mm")
        rule.waitUntil(5_000) { settings().watchDevice == "fenix8pro47mm" }
        scrollToText("The file is named openconnect-retro-FF5FA2-fenix8pro47mm.prg", substring = true)
        clickText("fenix 8 47 mm")
        // A new field replaces the last one; tapping a chosen field then makes it the big one.
        clickText("Floors")
        rule.waitUntil(5_000) { settings().watchFields == "battery,heart,stress,floors" }
        clickText("4 Floors")
        rule.waitUntil(5_000) { settings().watchDesign == "retro" && settings().watchColor == "pink" && settings().watchFields == "floors,battery,heart,stress" }
        scrollToText("watch/build.sh --device fenix847mm --design retro --color pink --fields floors,battery,heart,stress")
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription("Watch face preview, Retro Pro", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("61-watch-preview-retro")
    }

    @Test
    fun theBuildCommandNamesTheChoices() {
        val config = WatchFaceConfig("retro", "pink", listOf("steps", "battery", "heart", "stress"))
        assertEquals("watch/build.sh --device fenix847mm --design retro --color pink --fields steps,battery,heart,stress", config.buildCommand())
        assertEquals("openconnect-retro-FF5FA2-fenix847mm.prg", config.fileName())
        assertEquals("openconnect-clean-7AA7FF-fenix8solar51mm.prg", WatchFaceConfig(device = "fenix8solar51mm").fileName())
        assertEquals("fenix847mm", WatchFaceConfig(device = "nonsense").deviceValue.id)
        assertEquals(WatchColor.BLUE, WatchColor.of("nonsense"))
        // Anything that is not four different known fields falls back to the defaults.
        assertEquals(listOf(WatchField.BATTERY, WatchField.HEART, WatchField.STRESS, WatchField.STEPS), WatchField.parse("sleep,steps,heart"))
    }
}
