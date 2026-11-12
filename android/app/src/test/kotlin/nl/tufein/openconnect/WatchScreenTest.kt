package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import nl.tufein.openconnect.core.watch.InMemoryWatchSource
import nl.tufein.openconnect.core.watch.SensorContact
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.watch.FakeBle
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
        rule.onNodeWithText("Connect watch").performClick()

        waitForText("No Garmin watch found", substring = true)
        shot("09-watch-none")

        usb.watches = listOf(WatchInfo("usb-1", "Forerunner 265"))
        rule.onNodeWithText("Search again").performClick()
        waitForText("Read Forerunner 265")
        shot("10-watch-found")

        rule.onNodeWithText("Read Forerunner 265").performClick()
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
    fun liveHeartRateFromASensorCanBeSavedAsAnActivity() {
        launchWithFakes()
        shadowOf(ApplicationProvider.getApplicationContext<Context>() as android.app.Application)
            .grantPermissions(*bluetoothPermissions())
        waitForText("Connect watch")
        rule.onNodeWithText("Connect watch").performClick()

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
        waitForText("HRV (last night)")
        shot("15-health-watch-values")
        scrollToText("Blood oxygen (SpO2)")
        assertTrue(hasText("Blood oxygen (SpO2)", substring = false))
        scrollToText("Respiration")
        assertTrue(hasText("Respiration", substring = false))
    }
}
