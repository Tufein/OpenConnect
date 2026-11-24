package nl.tufein.openconnect.watch

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.data.SettingsStore
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The recording notification's text and shape, on the JVM; what cannot be checked here is how Android shows it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OpenConnectApp::class)
class LiveRecordingServiceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    /** The service keeps its Stop callback in a static; leave none behind for the next test. */
    @After
    fun forgetTheStopCallback() {
        LiveRecordingService.stop(context)
        SettingsStore(context) { null }.appLanguage = "system"
        Locale.setDefault(Locale.ENGLISH)
    }

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
    fun `the channel, title and stop action follow the saved app language`() {
        SettingsStore(context) { null }.appLanguage = "de"
        Locale.setDefault(Locale.US)
        val notification = LiveRecordingService.notification(context, LiveUi(phase = LivePhase.STREAMING, bpm = 132, seconds = 10))
        assertEquals("Live-Puls", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Stopp", notification.actions.single().title.toString())
        assertEquals("Live-Puls", manager.getNotificationChannel(notification.channelId).name.toString())
    }

    @Test
    fun `the notification has one stop action and stays until dismissed`() {
        val live = LiveUi(phase = LivePhase.STREAMING, bpm = 132, seconds = 10)
        val notification = LiveRecordingService.notification(context, live)
        assertEquals(1, notification.actions.size)
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    // Android 8 and newer end the app when startForeground gets a notification whose channel does not exist, and the
    // first notification of a recording is exactly that one: it is built by the service itself, before anything
    // else could have created the channel.

    @Test
    fun `the channel exists as soon as a notification is built`() {
        val notification = LiveRecordingService.notification(context, LiveUi(phase = LivePhase.STREAMING, bpm = 90, seconds = 5))
        val channel = manager.getNotificationChannel(notification.channelId)
        assertNotNull(channel, "the notification points at a channel that was never created")
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance, "a recording notification must not make a sound")
    }

    @Test
    fun `the service goes to the foreground with a notification that has its channel`() {
        val service = Robolectric.buildService(LiveRecordingService::class.java).create().startCommand(0, 1).get()
        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification, "the service must call startForeground when it is started")
        assertNotNull(manager.getNotificationChannel(notification.channelId), "the first foreground notification has no channel")
    }

    @Test
    fun `the stop action ends the recording and the service`() {
        val live = LiveUi(phase = LivePhase.STREAMING, bpm = 120, seconds = 30)
        var stopped = 0
        LiveRecordingService.start(context, live) { stopped++ }
        val stopIntent = shadowOf(LiveRecordingService.notification(context, live).actions[0].actionIntent).savedIntent

        val service = Robolectric.buildService(LiveRecordingService::class.java, stopIntent).create().startCommand(0, 1).get()

        assertEquals(1, stopped, "Stop on the notification must end the recording")
        assertTrue(shadowOf(service).isStoppedBySelf, "and take the service down with it")
    }
}
