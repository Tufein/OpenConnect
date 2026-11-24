package nl.tufein.openconnect.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.Nudge
import nl.tufein.openconnect.core.NudgeKind
import nl.tufein.openconnect.data.SettingsStore
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

/** The nudge's wording follows the saved app language, not only the phone's. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidNudgeNotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun resetLanguage() {
        SettingsStore(context) { null }.appLanguage = "system"
        Locale.setDefault(Locale.ENGLISH)
    }

    @Test
    fun `English is the unchanged default`() {
        val nudge = Nudge("goal", NudgeKind.GOAL_CLOSE, goal = "intensity", numbers = listOf(15))
        assertEquals(
            "Almost there: 15 more intensity minutes reach your weekly goal.",
            AndroidNudgeNotifier.text(context, nudge),
        )
    }

    @Test
    fun `the line follows the saved app language while the phone stays English`() {
        SettingsStore(context) { null }.appLanguage = "de"
        Locale.setDefault(Locale.US)
        val nudge = Nudge("goal", NudgeKind.GOAL_CLOSE, goal = "intensity", numbers = listOf(15))
        assertEquals(
            "Fast geschafft: Mit 15 weiteren Intensitätsminuten erreichst du dein Wochenziel.",
            AndroidNudgeNotifier.text(context, nudge),
        )
    }

    @Test
    fun `an activities nudge uses the translated plural`() {
        SettingsStore(context) { null }.appLanguage = "de"
        Locale.setDefault(Locale.US)
        val nudge = Nudge("goal", NudgeKind.GOAL_CLOSE, goal = "activities", numbers = listOf(1))
        assertEquals(
            "1 weitere Aktivität erreicht dein Wochenziel.",
            AndroidNudgeNotifier.text(context, nudge),
        )
    }
}
