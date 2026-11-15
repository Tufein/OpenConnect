package nl.tufein.openconnect.watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.OpenConnectApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The recording notification's text and shape, on the JVM; what cannot be checked here is how Android shows it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OpenConnectApp::class)
class LiveRecordingServiceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the notification line shows the heart rate and the elapsed time`() {
        val live = LiveUi(phase = LivePhase.STREAMING, bpm = 132, seconds = 754)
        assertEquals("132 bpm · 12:34", LiveRecordingService.text(context, live))
    }

    @Test
    fun `without a reading yet the heart rate is a dash`() {
        val live = LiveUi(phase = LivePhase.CONNECTING)
        assertEquals("– bpm · 0:00", LiveRecordingService.text(context, live))
    }

    @Test
    fun `the notification has one stop action and stays until dismissed`() {
        val live = LiveUi(phase = LivePhase.STREAMING, bpm = 132, seconds = 10)
        val notification = LiveRecordingService.notification(context, live)
        assertEquals(1, notification.actions.size)
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }
}
