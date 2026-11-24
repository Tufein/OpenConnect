package nl.tufein.openconnect

import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * The app really opens in the chosen language, not only in a resource-parity check: the tab bar and the Settings card
 * are read after a launch with `app_language` set. The translation itself is still a machine draft; this proves the
 * plumbing and the strings are present, not that a native speaker approved the wording.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class LocaleSwitchTest : AppTestBase() {
    @Test
    fun germanStartsInGerman() {
        launchFresh(appLanguage = "de")
        waitForText("Einstellungen")
        tab("Einstellungen").performClick()
        waitForText("Sprache")
        assertTrue(hasText("Telefonsprache", substring = false))
        shot("80-language-de")
    }

    @Test
    fun pickingGermanPersistsAndRecreatesIntoGerman() {
        launchFresh(appLanguage = "en")
        tab("Settings").performClick()
        waitForText("Language")
        clickText("Deutsch")
        // AppCompat asks Android to recreate the Activity when the per-app locale changes. Robolectric does not run
        // that lifecycle callback, so recreate here to verify the stored choice also reaches a fresh Activity.
        scenario.onActivity { it.recreate() }
        waitForText("Sprache")
        assertTrue(hasText("Einstellungen", substring = false))
    }

    @Test
    fun frenchStartsInFrench() {
        launchFresh(appLanguage = "fr")
        waitForText("Paramètres")
        tab("Paramètres").performClick()
        waitForText("Langue")
        assertTrue(hasText("Langue du téléphone", substring = false))
        shot("81-language-fr")
    }
}
