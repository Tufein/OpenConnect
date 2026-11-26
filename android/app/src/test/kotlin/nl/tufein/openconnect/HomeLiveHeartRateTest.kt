package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import nl.tufein.openconnect.core.watch.SensorContact
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import nl.tufein.openconnect.watch.LivePhase
import nl.tufein.openconnect.watch.bluetoothPermissions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live heart-rate card on the front page, with a fake Bluetooth radio: starting the scan, picking the watch and
 * reading its beats all go through the real controller. The card is the one from the Watch screen, so the same live
 * state is shown in both places and leaving the front page ends the connection instead of leaving the radio on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class HomeLiveHeartRateTest : AppTestBase() {
    private val ble = FakeBle()
    private val sensor = BleSensor("AA:01", "Forerunner 265 1234")

    /**
     * Starts the app with a fake radio, one day of data (so the live card sits high on the front page, as it does for
     * someone who already uses the app) and, when asked, a front-page setting already chosen.
     */
    private fun launchWithBle(configure: (SettingsStore) -> Unit = {}) = launchFresh { app ->
        val settings = SettingsStore(app)
        configure(settings)
        val repository = SqliteRepository(app)
        repository.upsertDaily(DailySummary(date = LocalDate.now(ZoneOffset.UTC), steps = 4_200, restingHr = 55))
        app.containerOverride = AppContainer(
            repository = repository,
            settings = settings,
            usb = FakeUsb { error("not opened in this test") },
            ble = ble,
        )
    }

    private fun grantBluetooth() {
        shadowOf(ApplicationProvider.getApplicationContext<Context>() as android.app.Application)
            .grantPermissions(*bluetoothPermissions())
    }

    /** Starts the scan, picks the fake watch, connects and sends a few beats; leaves the card streaming. */
    private fun startAndConnect(lastBpm: Int = 81) {
        waitForText("Start live heart rate")
        clickText("Start live heart rate")
        rule.waitUntil(5_000) { ble.onFound != null }
        assertTrue(ble.scanning, "the scan reached the Bluetooth link")
        ble.onFound!!(sensor)
        waitForText(sensor.name)
        clickText(sensor.name)
        rule.waitUntil(5_000) { ble.listener != null }
        assertEquals(1, ble.connectCalls, "one tap on the watch asks for one connection")
        waitForText("Connecting to ${sensor.name}…")
        ble.listener!!.onConnected()
        waitForText("Connected to ${sensor.name}")
        for (value in listOf(72, 75, lastBpm)) {
            ble.listener!!.onMeasurement(HeartRateMeasurement(value, SensorContact.DETECTED, emptyList(), null))
        }
        waitForText(lastBpm.toString())
    }

    @Test
    fun theFrontPageCardStartsTheScanAndTheFoundWatchCanBePickedThere() {
        launchWithBle()
        grantBluetooth()
        waitForText("Start live heart rate")
        shot("19-home-live-idle")

        clickText("Start live heart rate")
        rule.waitUntil(5_000) { ble.onFound != null }
        waitForText("No sensor found yet")
        assertTrue(ble.scanning, "the card starts the scan on the front page")

        ble.onFound!!(sensor)
        waitForText(sensor.name)
        shot("20-home-live-sensor")
        clickText(sensor.name)
        rule.waitUntil(5_000) { ble.connectCalls == 1 }
        waitForText("Connecting to ${sensor.name}…")
    }

    @Test
    fun theStreamingPulseIsOnTheCardAndCanBeStoppedAndSaved() {
        launchWithBle()
        grantBluetooth()
        startAndConnect(81)

        val spoken = rule.onNodeWithTag("home-live-bpm").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertTrue("81" in spoken && "bpm" in spoken, "TalkBack hears the pulse as one number: $spoken")
        shot("21-home-live-streaming")

        clickText("Stop")
        waitForText("Save as activity")
        assertNull(ble.connected, "stopping closes the Bluetooth connection")
        shot("22-home-live-stopped")

        clickText("Save as activity")
        waitForText("Saved as an activity")
        waitForText("Start again")
        val saved = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.repository.listActivities().single()
        assertEquals("Live heart rate", saved.name)
        assertEquals("ble", saved.source)
        assertEquals(76, saved.avgHr)
        assertEquals(81, saved.maxHr)
    }

    @Test
    fun leavingTheFrontPageEndsTheLiveConnection() {
        launchWithBle()
        grantBluetooth()
        startAndConnect(81)
        assertNotNull(ble.connected, "the fake radio is connected while the card streams")

        tab("Health").performClick()
        rule.waitUntil(5_000) { ble.connected == null }
        waitForText("Scores")
        assertEquals(
            LivePhase.STOPPED,
            ApplicationProvider.getApplicationContext<OpenConnectApp>().container.watch.state.value.live.phase,
            "the controller stopped the session when the front page went away",
        )
    }

    @Test
    fun theFrontPageSwitchInSettingsTurnsTheLiveCardOff() {
        launchWithBle()
        waitForText("Start live heart rate")
        tab("Settings").performClick()
        openAppearance()

        // The whole switch row is the control, so its label is enough to turn it off.
        scrollToText("Live heart rate card")
        clickText("Live heart rate card")

        tab("Home").performClick()
        waitForText("55")
        rule.waitForIdle()
        assertTrue(
            rule.onAllNodesWithTag("home-live-card").fetchSemanticsNodes().isEmpty(),
            "the new front-page switch hides the card",
        )
    }

    @Test
    fun theLiveCardReadsInTheLightTheme() {
        launchWithBle { settings -> settings.themeMode = "light" }
        waitForText("Start live heart rate")
        shot("25b-home-live-light")
    }

    @Test
    fun theLiveCardKeepsItsShapeInRetro() {
        launchWithBle { settings -> settings.style = "retro" }
        waitForText("Start live heart rate")
        shot("26b-home-live-retro")
    }

    @Test
    fun theCardFollowsTheFrontPageSwitch() {
        launchWithBle { settings -> settings.homeLive = false }
        // The seeded resting heart rate in the hero's Heart orb proves the front page has loaded, so the missing card
        // is the switch's doing and not a page that has not arrived yet.
        waitForText("55")
        rule.waitForIdle()
        assertTrue(
            rule.onAllNodesWithTag("home-live-card").fetchSemanticsNodes().isEmpty(),
            "the switch keeps the live card off the front page",
        )
        assertTrue(!hasText("Start live heart rate", substring = false))
    }

    @Test
    fun withoutGarminTheFrontPageHasNoLiveCard() {
        launchWithBle { settings -> settings.useGarmin = false }
        waitForText("55")
        rule.waitForIdle()
        assertTrue(
            rule.onAllNodesWithTag("home-live-card").fetchSemanticsNodes().isEmpty(),
            "someone who said they have no Garmin watch gets no live card",
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp-night-xhdpi", application = OpenConnectApp::class)
    fun theLiveCardFitsOnANarrowPhone() {
        launchWithBle()
        grantBluetooth()
        waitForText("Start live heart rate")
        rule.onNodeWithTag("home-live-watch").performScrollTo()
        shot("23-home-live-320-idle")

        val screenWidth = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        fun fits(tag: String) {
            val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0f && bounds.right <= screenWidth, "$tag stays inside the 320 dp screen: $bounds")
        }
        fits("home-live-card")

        clickText("Start live heart rate")
        rule.waitUntil(5_000) { ble.onFound != null }
        ble.onFound!!(sensor)
        waitForText(sensor.name)
        clickText(sensor.name)
        rule.waitUntil(5_000) { ble.listener != null }
        ble.listener!!.onConnected()
        ble.listener!!.onMeasurement(HeartRateMeasurement(134, SensorContact.DETECTED, emptyList(), null))
        waitForText("134")
        fits("home-live-bpm")
        rule.onNodeWithText("Stop").performScrollTo()
        shot("24-home-live-320-streaming")
    }
}
