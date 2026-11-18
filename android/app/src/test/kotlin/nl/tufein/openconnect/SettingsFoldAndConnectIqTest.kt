package nl.tufein.openconnect

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.watch.ConnectIqStore
import nl.tufein.openconnect.core.watch.StoreStep
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.ui.ConnectIqLauncher
import nl.tufein.openconnect.ui.openConnectIq
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v0.43.0 on the JVM: the look settings fold away, the Connect IQ button starts Garmin's app instead of a web page, and
 * the watch face is gone from the app. Whether Garmin's real Connect IQ app opens a store address handed to it cannot be
 * tried here: that needs a phone with that app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class SettingsFoldAndConnectIqTest : AppTestBase() {
    private fun launchApp() = launchFresh { app ->
        app.containerOverride = AppContainer(
            SqliteRepository(app),
            SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) }),
            FakeUsb { error("no watch in this test") },
            FakeBle(),
        )
    }

    private fun settings() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings

    private fun loadDemo() {
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
    }

    // ------------------------------------------------------------------ the look settings fold away

    @Test
    fun theLookSettingsAreFoldedAwayUntilTheirHeaderIsTapped() {
        launchApp()
        waitForText("Load demo data")
        tab("Settings").performClick()
        waitForText("Appearance")
        // Closed: the header says what is chosen, and none of the choices are on screen.
        assertTrue(hasText("Handheld · Retro · Pixel letters", substring = false))
        for (hidden in listOf("Colours", "Style", "Follow phone", "Game Boy", "App icon", "Clear letters")) {
            assertFalse(hasText(hidden, substring = false), "\"$hidden\" is folded away")
        }
        // What used to be three screens down is now right under it.
        assertTrue(hasText("Weekly goals", substring = false))
        shot("76-settings-appearance-folded")

        clickText("Appearance")
        waitForText("Colours")
        for (shown in listOf("Style", "Follow phone", "Game Boy", "App icon", "Clear letters")) {
            assertTrue(hasText(shown, substring = false), "\"$shown\" shows once the section is open")
        }
        shot("77-settings-appearance-open")

        clickText("Appearance")
        rule.waitUntil(5_000) { !hasText("Colours", substring = false) }
    }

    @Test
    fun aColourThemeIsPickedInTheOpenedSectionAndTheHeaderNamesIt() {
        launchApp()
        loadDemo()
        tab("Settings").performClick()
        openAppearance()
        clickText("Synthwave")
        rule.waitUntil(5_000) { settings().palette == "synthwave" }
        waitForText("Synthwave · Retro · Pixel letters")
        shot("44-settings-themes")
        tab("Home").performClick()
        waitForText("Readiness")
        shot("45-home-synthwave")
        // The section is still open after a look at another tab.
        tab("Settings").performClick()
        openAppearance()
        clickText("Game Boy")
        rule.waitUntil(5_000) { settings().palette == "gameboy" }
        tab("Health").performClick()
        waitForText("Scores")
        shot("46-health-gameboy")
    }

    // ------------------------------------------------------------------ the watch face is gone

    @Test
    fun theWatchFaceIsGoneFromTheApp() {
        // No text of it is left in the app: not the preview, not the builder, not the .prg installer.
        val names = R.string::class.java.fields.map { it.name }
        assertTrue(names.size > 500, "the string table was read")
        val left = names.filter { it.startsWith("wf_") || it.startsWith("watchface_") || it == "settings_watchface" }
        assertEquals(emptyList(), left)

        launchApp()
        waitForText("Load demo data")
        tab("Settings").performClick()
        waitForText("Appearance")
        // The preview and the installer sat between Appearance and the weekly goals.
        scrollToText("Your devices")
        assertFalse(hasText("Watch face", substring = true))
        assertFalse(hasText(".prg", substring = true))
    }

    // ------------------------------------------------------------------ Connect IQ opens Garmin's app

    @Test
    fun theConnectIqButtonLeadsToGarminsAppAndNotToAWebPage() {
        launchApp()
        waitForText("Load demo data")
        tab("Settings").performClick()
        scrollToText("Open Garmin Connect IQ")
        clickText("Open Garmin Connect IQ")
        // This test phone has no Connect IQ app, so the place to get it opens: the Play Store, on that app's own page.
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>())
        var opened: Intent? = null
        rule.waitUntil(5_000) {
            while (opened == null) {
                val next = started.nextStartedActivity ?: break
                if (next.dataString.orEmpty().contains(ConnectIqStore.PACKAGE)) opened = next
            }
            opened != null
        }
        assertEquals(Intent.ACTION_VIEW, opened!!.action)
        assertEquals("market://details?id=com.garmin.connectiq", opened!!.dataString)
        assertFalse(hasText("could not be opened", substring = true))
    }

    /** Records what was started; [refuse] decides which intents nothing on the phone can take. */
    private class FakePhone(val installed: Boolean, val refuse: (Intent) -> Boolean = { false }) {
        val started = ArrayList<Intent>()
        val launcher = ConnectIqLauncher(
            launchIntent = { name -> if (installed) Intent(Intent.ACTION_MAIN).setClassName(name, "$name.Main") else null },
            start = { intent ->
                if (refuse(intent)) throw ActivityNotFoundException()
                started.add(intent)
            },
        )
    }

    @Test
    fun withGarminsAppInstalledThatAppIsStartedItself() {
        val phone = FakePhone(installed = true)
        assertEquals(StoreStep.LaunchApp("com.garmin.connectiq"), phone.launcher.open(listing = ""))
        val intent = phone.started.single()
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals("com.garmin.connectiq", intent.component?.packageName)
        assertNull(intent.data, "no web address is involved")
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun aListingOfOurOwnIsHandedToGarminsAppAndFallsBackToTheBrowser() {
        val id = "4debed0e-4266-495e-8215-a3a8755ee87a"
        val address = "https://apps.garmin.com/apps/$id"
        val phone = FakePhone(installed = true)
        assertEquals(StoreStep.OpenAddress(address, "com.garmin.connectiq"), phone.launcher.open(listing = id))
        assertEquals(address, phone.started.single().dataString)
        assertEquals("com.garmin.connectiq", phone.started.single().`package`)

        // Garmin's app does not take the address: the same page opens in whatever the phone uses for links.
        val picky = FakePhone(installed = true, refuse = { it.`package` != null })
        assertEquals(StoreStep.OpenAddress(address), picky.launcher.open(listing = id))
        assertEquals(address, picky.started.single().dataString)
        assertNull(picky.started.single().`package`)
    }

    @Test
    fun whenNothingCanOpenAnythingThatIsReportedAndNothingCrashes() {
        val phone = FakePhone(installed = false, refuse = { true })
        assertNull(phone.launcher.open(listing = ""))
        assertTrue(phone.started.isEmpty())
        // Without the Play Store app its web page is next.
        val noPlay = FakePhone(installed = false, refuse = { it.scheme == "market" })
        assertEquals(StoreStep.OpenAddress("https://play.google.com/store/apps/details?id=com.garmin.connectiq"), noPlay.launcher.open(listing = ""))
    }

    @Test
    fun onARealContextTheSameStepsRun() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        // Robolectric's phone has no Connect IQ app.
        assertEquals(StoreStep.OpenAddress("market://details?id=com.garmin.connectiq"), openConnectIq(context))
        assertEquals("market://details?id=com.garmin.connectiq", shadowOf(context).nextStartedActivity.dataString)
    }
}
