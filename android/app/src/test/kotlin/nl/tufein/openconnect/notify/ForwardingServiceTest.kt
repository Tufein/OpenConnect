package nl.tufein.openconnect.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.data.SettingsStore
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

/** The persistent notification's wording, on the JVM; a real phone is only needed to see it shown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForwardingServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun resetLanguage() {
        SettingsStore(context) { null }.appLanguage = "system"
        Locale.setDefault(Locale.ENGLISH)
    }

    @Test
    fun `English waiting line stays the same`() {
        assertEquals("Waiting for the watch…", ForwardingService.text(context, ForwardUi(phase = ForwardPhase.WAITING)))
    }

    @Test
    fun `the waiting line follows the saved app language while the phone stays English`() {
        SettingsStore(context) { null }.appLanguage = "de"
        Locale.setDefault(Locale.US)
        assertEquals("Warte auf die Uhr…", ForwardingService.text(context, ForwardUi(phase = ForwardPhase.WAITING)))
    }

    @Test
    fun `the channel, title and turn-off action use the saved app language`() {
        SettingsStore(context) { null }.appLanguage = "de"
        Locale.setDefault(Locale.US)
        val notification = ForwardingService.notification(context, ForwardUi(phase = ForwardPhase.WAITING, watchName = "fenix 8"))
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals("Benachrichtigungen an die Uhr", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Warte auf fenix 8…", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Ausschalten", notification.actions.single().title.toString())
        assertEquals("Benachrichtigungen an die Uhr", manager.getNotificationChannel(notification.channelId).name.toString())
    }
}
