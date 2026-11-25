package nl.tufein.openconnect

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * v1.12.0: the privacy page is built from the canonical register. Settings opens it, the index groups every subject and a
 * detail shows what leaves the phone, what never does and the fields per operation. The Dutch run proves the new texts
 * are wired through the app's own language switch, not only through the English base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class PrivacyScreenTest : AppTestBase() {
    @Test
    fun settingsOpensThePrivacyIndexAndANotificationsDetail() {
        launchFresh()
        waitForText("Load demo data")
        tab("Settings").performClick()
        clickText("About and licences")
        waitForText("About Cercis")
        scrollToText("What leaves your phone")
        clickText("What leaves your phone")
        waitForText("What leaves your phone")

        // The index is built from PrivacyRegister: active subjects first, WHOOP and Hume last as legacy.
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasTestTag("privacy-subject-garmin")).fetchSemanticsNodes().isNotEmpty()
        }
        // The GitHub subject belongs to the updater: it is in this list only when the build has one. The store
        // channel leaves the row out (see StoreChannelTest); the GitHub channel shows it (see GithubChannelTest).
        if (BuildConfig.UPDATER_ENABLED) {
            scrollToText("In-app updater")
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasTestTag("privacy-subject-github")).fetchSemanticsNodes().isNotEmpty()
            }
        }
        scrollToText("Notifications")
        assertTrue(hasText("Leaves for your watch", substring = false), "the notifications row shows its group")
        scrollToText("WHOOP and Hume (removed)")
        assertTrue(hasText("legacy: old rows only", substring = true), "the removed subject is marked as legacy")
        scrollToText("Notifications")
        clickTag("privacy-subject-notifications")

        // The detail carries the exact sections and a readable field name; the raw keys stay in the technical fold.
        waitForText("Leaves your phone")
        assertTrue(hasText("Leaves your phone", substring = false), "the leaves section is on the detail")
        assertTrue(hasText("Never", substring = false), "the never section is on the detail")
        scrollToText("Notification body", substring = true)
        assertTrue(hasText("Notification body", substring = true), "fields read as words, not raw keys")
        shot("privacy-notifications-detail")

        // The system back from a detail returns to the index, and the arrow from the index leaves the page.
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasTestTag("privacy-subject-notifications")).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(hasText("What leaves your phone", substring = false), "the index is back, not the About screen")
    }

    @Test
    fun theDutchPageUsesTheSameRegisterAndSections() {
        launchFresh(appLanguage = "nl")
        waitForText("Instellingen")
        tab("Instellingen").performClick()
        clickText("Over en licenties")
        waitForText("Over Cercis")
        scrollToText("Wat je telefoon verlaat")
        clickText("Wat je telefoon verlaat")
        waitForText("Wat je telefoon verlaat")

        rule.waitUntil(15_000) {
            rule.onAllNodes(hasTestTag("privacy-subject-garmin")).fetchSemanticsNodes().isNotEmpty()
        }
        scrollToText("Meldingen")
        assertTrue(hasText("Gaat naar je horloge", substring = false), "the Dutch group label is shown")
        clickTag("privacy-subject-notifications")

        waitForText("Verlaat je telefoon")
        assertTrue(hasText("Verlaat je telefoon", substring = false), "the Dutch leaves section is on the detail")
        assertTrue(hasText("Nooit", substring = false), "the Dutch never section is on the detail")
        scrollToText("Notification body", substring = true)
        assertTrue(hasText("Notification body", substring = true), "field names stay the code's technical English")
        shot("privacy-nl-detail")
    }
}
