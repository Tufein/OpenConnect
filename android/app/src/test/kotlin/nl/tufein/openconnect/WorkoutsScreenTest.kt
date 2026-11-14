package nl.tufein.openconnect

import android.content.Context
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v0.26.0 on the JVM: the planned runs of the next seven days go to the watch as .fit workouts over a (fake) USB cable.
 * Whether a real fenix 8 accepts and shows them is not part of this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class WorkoutsScreenTest : AppTestBase() {
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

    @Test
    fun thePlansNextWeekGoesToTheWatchAsFitWorkouts() {
        launchApp()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Plan").performClick()
        waitForText("Create plan")
        clickText("10K")
        clickText("Create plan")
        waitForText("10K plan")

        // No watch on the cable yet.
        clickText("Send the next 7 days to my watch (USB)")
        waitForText("No watch on the USB cable", substring = true)
        assertTrue(watch.workouts.isEmpty())

        usb.watches = listOf(WatchInfo("usb-1", "fenix 8"))
        clickText("Send the next 7 days to my watch (USB)")
        waitForText("workouts copied to the watch", substring = true)
        shot("50-workouts-sent")
        assertTrue(watch.workouts.isNotEmpty())
        for ((name, bytes) in watch.workouts) {
            assertTrue(Regex("OC_\\d{4}-\\d{2}-\\d{2}_[A-Z]+\\.fit").matches(name), name)
            assertEquals(".FIT", String(bytes, 8, 4, Charsets.US_ASCII), name)
        }

        // Sending again leaves what is there.
        val first = watch.workouts.keys.toList()
        clickText("Send the next 7 days to my watch (USB)")
        waitForText("0 workouts copied to the watch", substring = true)
        assertEquals(first, watch.workouts.keys.toList())
    }
}
