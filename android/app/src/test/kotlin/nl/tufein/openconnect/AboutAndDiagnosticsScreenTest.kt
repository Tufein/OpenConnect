package nl.tufein.openconnect

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nl.tufein.openconnect.core.Licences
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v0.55.0 (milestone M2, honest and diagnosable): every source says whether it was ever tried on a real device, an About
 * screen holds the licences and the Garmin disclaimer, and *Share diagnostics* shows the exact text before it is shared,
 * with no key or token in it. Whether the share sheet of a real phone takes the text cannot be tried here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class AboutAndDiagnosticsScreenTest : AppTestBase() {
    @Test
    fun deviceSwitchesSayWhetherASourceWasEverTriedOnARealDevice() {
        launchFresh(oura = true, whoop = true, hume = true)
        waitForText("Load demo data")
        tab("Settings").performClick()
        scrollToText("Status: tested on a real device", substring = true)
        assertTrue(hasText("Status: experimental, not tried on a real device yet", substring = false), "Oura, WHOOP and Hume were never tried")
        shot("about-status-lines")
    }

    @Test
    fun theAboutScreenHoldsLabelsLicencesAndTheGarminDisclaimer() {
        launchFresh()
        waitForText("Load demo data")
        tab("Settings").performClick()
        clickText("About and licences")
        waitForText("About OpenConnect")
        scrollToText("Oura Ring: experimental, not tried on a real device yet")
        assertTrue(hasText("Garmin account (your own key): tested on a real device", substring = true), "the one tried source says so")
        scrollToText(Licences.all.first().licence, substring = true)
        scrollToText("Garmin, Garmin Connect and fenix are trademarks", substring = true)
        assertTrue(hasText("not affiliated with or endorsed by Garmin", substring = true) || hasText("is not affiliated with or endorsed by Garmin", substring = true))
        shot("about-screen")
    }

    @Test
    fun diagnosticsShowTheExactReportAndLeaveSecretsOut() {
        launchFresh(prepare = { app ->
            app.container.syncLog.record(
                SyncLogEntry(Instant.now(), SyncLog.SOURCE_ACCOUNT, false, 0, "Garmin said no for token=abcDEF123456secret and 4f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c"),
            )
        })
        waitForText("Load demo data")
        tab("Settings").performClick()
        clickText("Share diagnostics")
        waitForText("Diagnostics report")
        assertTrue(hasText("This is exactly what will be shared", substring = true))
        assertTrue(hasText("OpenConnect diagnostics", substring = true), "the dialog shows the report itself")
        assertTrue(hasText("garmin-account FAILED", substring = true), "the failed sync is in it")
        assertFalse(hasText("abcDEF123456secret", substring = true), "a token must not appear")
        assertFalse(hasText("4f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c", substring = true), "a long hex string must not appear")
        assertTrue(hasText("Share", substring = false))
        shot("about-diagnostics")
        rule.onNodeWithText("Close").performClick()
        rule.waitUntil(45_000) { !hasText("Diagnostics report", substring = false) }
    }

    @Test
    fun theFirstStartSaysWhatStaysOnThePhoneAndWhichSourcesAreExperimental() {
        launchFresh(onboarded = false)
        waitForText("What stays on your phone")
        assertTrue(hasText("never your route", substring = true))
        assertTrue(hasText("experimental", substring = true))
        shot("about-onboarding")
    }
}
