package nl.tufein.openconnect

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.core.BodyEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** v1.0.6: the Body card on Health takes today's weight and shows it with the last month as a line. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class BodyScreenTest : AppTestBase() {
    private fun weightField() = rule.onNode(hasSetTextAction() and hasText("Weight (kg)"))
    private fun fatField() = rule.onNode(hasSetTextAction() and hasText("Fat % (optional)"))

    private fun openHealthWithDemo() {
        launchFresh()
        waitForText("Load demo data")
        clickText("Load demo data")
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        tab("Health").performClick()
        waitForText("Body")
    }

    @Test
    fun aWeightIsEnteredShownAndStoredForItsDay() {
        openHealthWithDemo()
        waitForText("Enter today's weight to start the trend.")
        weightField().performTextInput("82,5")
        clickChip("Save")
        // Storing the weight reloads the screens: the card shows it, the inputs are empty again.
        waitForText("82.5 kg")
        assertTrue(!hasText("82,5", substring = false), "the weight field is cleared")
        val stored = ApplicationProvider.getApplicationContext<OpenConnectApp>().container.repository
        assertEquals(82500, stored.listBody(LocalDate.now(), LocalDate.now()).single().grams)
    }

    @Test
    fun bodyFatIsOptionalAndShownWithTheWeight() {
        openHealthWithDemo()
        weightField().performTextInput("83")
        fatField().performTextInput("17,5")
        clickChip("Save")
        waitForText("83.0 kg · 17.5% fat")
    }
}
