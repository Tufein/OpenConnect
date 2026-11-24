package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.watch.BleSync
import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import nl.tufein.openconnect.core.watch.InMemoryWatchSource
import nl.tufein.openconnect.core.watch.SensorContact
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeGarminBle
import nl.tufein.openconnect.watch.GarminBleWatch
import nl.tufein.openconnect.watch.GarminStep
import nl.tufein.openconnect.watch.FakeUsb
import nl.tufein.openconnect.watch.OpenedWatch
import nl.tufein.openconnect.watch.WatchInfo
import nl.tufein.openconnect.watch.bluetoothPermissions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Watch screen on the JVM, with a fake USB port and a fake Bluetooth radio: the watch fixtures written by
 * Garmin's FIT SDK come in through the same sync code as a real watch's files. What this cannot show is how a
 * real phone, cable and watch behave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class WatchScreenTest : AppTestBase() {
    private fun fixture(path: String): ByteArray =
        checkNotNull(WatchScreenTest::class.java.getResourceAsStream(path)) { "missing $path" }.readBytes()

    private val usb = FakeUsb { _ ->
        OpenedWatch(
            InMemoryWatchSource(
                "Forerunner 265",
                mapOf(
                    "Monitor/46DB3F05.FIT" to fixture("/watch/monitoring.fit"),
                    "Sleep/46DC0001.FIT" to fixture("/watch/sleep.fit"),
                    "HSA/46DB0002.FIT" to fixture("/watch/hsa.fit"),
                    "Activity/run.fit" to fixture("/fixtures/python-run.fit"),
                ),
            ),
        ) { }
    }
    private val ble = FakeBle()

    /** [seed] fills the database before the first screen starts, so no screen can load before the data is there. */
    private fun launchWithFakes(seed: (SqliteRepository) -> Unit = {}) = launchFresh { app ->
        val container = AppContainer(SqliteRepository(app), SettingsStore(app), usb, ble)
        seed(container.repository)
        app.containerOverride = container
    }

    private fun repo() = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.repository

    @Test
    fun readingAWatchOverUsbBringsInItsData() {
        launchWithFakes()
        waitForText("Connect watch")
        shot("08-home-with-watch-card")
        clickText("Connect watch")

        // the USB card sits below the Bluetooth and notification cards, and a list only composes what is on screen
        waitForText("Sync a Garmin watch over Bluetooth")
        scrollToText("Read the watch with a USB cable")
        waitForText("Read the watch with a USB cable")
        scrollToText("Search again")
        waitForText("No Garmin watch found", substring = true)
        shot("09-watch-none")

        usb.watches = listOf(WatchInfo("usb-1", "Forerunner 265"))
        clickText("Search again")
        waitForText("Read Forerunner 265")
        shot("10-watch-found")

        clickText("Read Forerunner 265")
        waitForText("Forerunner 265 has been read")
        shot("11-watch-read")

        val repo = repo()
        val day = repo.listDaily(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10")).single()
        assertEquals(10_200, day.steps)
        assertEquals(90, day.bodyBatteryHigh)
        assertEquals(1, repo.listSleep(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-11")).size)
        assertEquals(1, repo.countActivities())
    }

    @Test
    fun theWatchScreenOffersToReadAFolderAndExplainsWhatBluetoothCanDo() {
        launchWithFakes()
        waitForText("Connect watch")
        clickText("Connect watch")
        waitForText("Sync a Garmin watch over Bluetooth")
        scrollToText("Read a folder with watch files")
        scrollToText("Pick a folder")
        assertTrue(hasText("Pick a folder", substring = false))
        shot("12b-watch-folder")
        scrollToText("The watch can sync over Bluetooth with OpenConnect itself", substring = true)
    }

    @Test
    fun aGarminWatchSyncsOverBluetoothAndIsRememberedForNextTime() {
        val fenix = GarminBleWatch("AA:BB:CC:DD:EE:FF", "fenix 8 - 47mm", paired = false)
        val garmin = FakeGarminBle(nearby = listOf(fenix)) { _, onStep ->
            onStep(GarminStep.PAIRING)
            onStep(GarminStep.HANDSHAKE)
            OpenedWatch(
                InMemoryWatchSource(
                    "fenix 8",
                    mapOf(
                        "Monitor/MONITOR_1.fit" to fixture("/watch/monitoring.fit"),
                        "Sleep/SLEEP_2.fit" to fixture("/watch/sleep.fit"),
                        "Activity/ACTIVITY_3.fit" to fixture("/fixtures/python-run.fit"),
                    ),
                ),
            ) { }
        }
        launchFresh { app ->
            app.containerOverride = AppContainer(SqliteRepository(app), SettingsStore(app), usb, ble, garminBle = garmin)
        }
        shadowOf(ApplicationProvider.getApplicationContext<Context>() as android.app.Application)
            .grantPermissions(*bluetoothPermissions())
        waitForText("Connect watch")
        clickText("Connect watch")
        waitForText("Sync a Garmin watch over Bluetooth")
        assertTrue(hasText("no longer sync with the Garmin Connect app", substring = true))
        shot("16-watch-ble-card")
        scrollToText("Mark files as synced on the watch")
        assertTrue(hasText("Look for the watch in the background (experimental)", substring = false))
        assertTrue(hasText("Not tried on a real watch yet", substring = true))
        shot("16b-watch-ble-switches")

        clickText("Find watch")
        waitForText("fenix 8 - 47mm")
        shot("17-watch-ble-found")
        clickText("fenix 8 - 47mm")
        waitForText("fenix 8 has been read")
        shot("18-watch-ble-read")
        assertEquals(1, repo().countActivities())
        assertEquals(1, repo().listSleep(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-11")).size)

        // the watch is remembered: one tap next time, also after the app restarts
        waitForText("Sync fenix 8 - 47mm")
        assertEquals("fenix 8 - 47mm", SettingsStore(ApplicationProvider.getApplicationContext()).garminWatch.load()?.name)
        clickText("Forget this watch")
        rule.waitUntil(5_000) { !hasText("Sync fenix 8 - 47mm", substring = false) }
    }

    @Test
    fun theFindListSaysHonestlyWhichWatchesAreNotOnTheListTheSyncWasMadeFor() {
        val garmin = FakeGarminBle(
            nearby = listOf(
                GarminBleWatch("AA:01", "Forerunner 965", paired = false, bleSync = BleSync.LISTED),
                GarminBleWatch("AA:02", "Edge 1040", paired = false, bleSync = BleSync.UNKNOWN),
                GarminBleWatch("AA:03", "Venu X1", paired = false, bleSync = BleSync.NEEDS_NEWER_PROTOCOL),
            ),
        ) { _, _ -> error("not opened") }
        launchFresh { app ->
            app.containerOverride = AppContainer(SqliteRepository(app), SettingsStore(app), usb, ble, garminBle = garmin)
        }
        shadowOf(ApplicationProvider.getApplicationContext<Context>() as android.app.Application)
            .grantPermissions(*bluetoothPermissions())
        waitForText("Connect watch")
        clickText("Connect watch")
        waitForText("Sync a Garmin watch over Bluetooth")
        assertTrue(!hasText("every kind of Garmin watch", substring = true), "the note about what is searched is shown once there is a search")

        clickText("Find watch")
        waitForText("every kind of Garmin watch", substring = true)
        waitForText("Forerunner 965")
        waitForText("Edge 1040")
        waitForText("Venu X1")
        // one note for each of the two that carry a doubt, none for the one on the list
        assertEquals(1, rule.onAllNodesWithText("nothing was tried on this model", substring = true).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("needs Garmin's newer sync protocol", substring = true).fetchSemanticsNodes().size)
        shot("17b-watch-ble-found-notes")
    }

    @Test
    fun liveHeartRateFromASensorCanBeSavedAsAnActivity() {
        launchWithFakes()
        shadowOf(ApplicationProvider.getApplicationContext<Context>() as android.app.Application)
            .grantPermissions(*bluetoothPermissions())
        waitForText("Connect watch")
        clickText("Connect watch")

        clickText("Search for sensors")
        rule.waitUntil(5_000) { ble.onFound != null }
        ble.onFound!!(BleSensor("AA:01", "Forerunner 265 1234"))
        waitForText("Forerunner 265 1234")
        shot("12-watch-sensor-found")

        clickText("Forerunner 265 1234")
        rule.waitUntil(5_000) { ble.listener != null }
        waitForText("Connecting to Forerunner 265 1234…")
        ble.listener!!.onConnected()
        waitForText("Connected to Forerunner 265 1234")

        for (bpm in listOf(72, 75, 81)) {
            ble.listener!!.onMeasurement(HeartRateMeasurement(bpm, SensorContact.DETECTED, emptyList(), null))
        }
        waitForText("81")
        shot("13-watch-live")

        clickText("Stop")
        waitForText("Save as activity")
        shot("14-watch-live-stopped")
        clickText("Save as activity")
        waitForText("Saved as an activity")

        val saved = repo().listActivities().single()
        assertEquals("ble", saved.source)
        assertEquals(76, saved.avgHr)
        assertEquals(81, saved.maxHr)
    }

    @Test
    fun healthShowsWatchOnlyValuesOnceTheyExist() {
        val today = LocalDate.now(ZoneOffset.UTC)
        launchWithFakes { repo ->
            repo.upsertDaily(DailySummary(date = today, steps = 4000, hrvMs = 52, spo2Avg = 96, respirationAvg = 14.5))
            repo.upsertDaily(DailySummary(date = today.minusDays(1), steps = 5000, hrvMs = 48))
        }
        waitForText("Health")
        tab("Health").performClick()
        clickChip("Steps chart")
        waitForText("Steps counted by your watch each day", substring = true)
        clickChip("HRV (last night) chart")
        scrollToText("Heart rate variability overnight", substring = true)
        assertTrue(hasText("Heart rate variability overnight", substring = true))
        shot("15-health-watch-values")
        clickChip("Blood oxygen (SpO2) chart")
        assertTrue(hasText("Blood oxygen (SpO2)", substring = false))
        clickChip("Respiration chart")
        assertTrue(hasText("Respiration", substring = false))
    }

    @Test
    fun theWatchTabShowsTheChargeOfTheWatchAndTheRing() {
        launchWithFakes()
        val app = ApplicationProvider.getApplicationContext<OpenConnectApp>()
        app.container.settings.watchBattery = 87
        app.container.settings.watchBatteryAt = System.currentTimeMillis() - 5 * 60_000
        app.container.settings.ringBattery = 64
        app.container.settings.ringBatteryAt = System.currentTimeMillis() - 120 * 60_000
        waitForText("Connect watch")
        clickText("Connect watch")

        waitForText("Battery")
        waitForText("87%")
        waitForText("64%")
        // the ring last reported two hours ago; the watch five minutes ago
        assertTrue(hasText("updated", substring = true))
        shot("16-watch-batteries")
    }
}
