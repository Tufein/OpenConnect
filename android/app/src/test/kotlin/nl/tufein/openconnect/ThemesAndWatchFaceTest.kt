package nl.tufein.openconnect

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.watch.InMemoryWatchSource
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import nl.tufein.openconnect.watch.OpenedWatch
import nl.tufein.openconnect.watch.WatchInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v0.18.0 on the JVM: colour themes and styles that are picked in Settings and kept, and a watch face file copied to a
 * watch on the USB cable (a fake one here). The real MTP write and the watch's own face menu are not part of this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class ThemesAndWatchFaceTest : AppTestBase() {
    private val watch = InMemoryWatchSource("fenix 8", emptyMap())
    private val usb = FakeUsb { OpenedWatch(watch) { } }

    private fun launchApp() = launchFresh { app ->
        app.containerOverride = AppContainer(
            SqliteRepository(app),
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            usb,
            FakeBle(),
        )
    }

    private fun settings() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings

    private fun loadDemo() {
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
    }

    @Test
    fun aColourThemeAndAStyleArePickedAndKept() {
        launchApp()
        loadDemo()
        tab("Settings").performClick()
        waitForText("Colours")
        clickText("Synthwave")
        clickText("Pixel")
        rule.waitUntil(5_000) { settings().palette == "synthwave" && settings().lookStyle == "pixel" }
        shot("44-settings-themes")
        tab("Home").performClick()
        waitForText("Readiness")
        shot("45-home-synthwave-pixel")
        tab("Settings").performClick()
        clickText("Game Boy")
        clickText("Round")
        rule.waitUntil(5_000) { settings().palette == "gameboy" && settings().lookStyle == "round" }
        tab("Health").performClick()
        waitForText("Scores")
        shot("46-health-gameboy-round")
    }

    @Test
    fun aWatchFaceFileGoesToTheWatchOnTheCable() {
        launchApp()
        waitForText("Load demo data")
        tab("Settings").performClick()
        scrollToText("Install a watch face file (.prg) over USB")
        val face = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "Retro Face.prg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        // No watch on the cable: the person is told so.
        scenario.onActivity { it.viewModel.installWatchFace(Uri.fromFile(face)) }
        waitForText("No watch on the USB cable", substring = true)
        assertTrue(watch.installed.isEmpty())

        usb.watches = listOf(WatchInfo("usb-1", "fenix 8"))
        scenario.onActivity { it.viewModel.installWatchFace(Uri.fromFile(face)) }
        waitForText("Retro_Face.prg is on the watch", substring = true)
        assertEquals(listOf("Retro_Face.prg"), watch.installed.keys.toList())

        val photo = File(face.parentFile, "photo.jpg").apply { writeBytes(byteArrayOf(9)) }
        scenario.onActivity { it.viewModel.installWatchFace(Uri.fromFile(photo)) }
        waitForText("That is not a watch face file", substring = true)
        shot("47-settings-watchface")
    }
}
