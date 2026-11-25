package nl.tufein.openconnect

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * The GitHub channel keeps the updater; its privacy subject and detail page are only in this build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class GithubChannelTest : AppTestBase() {
    @Test
    fun theGithubBuildOffersUpdates() {
        assertTrue(BuildConfig.UPDATER_ENABLED, "the GitHub flavor must build the updater")

        launchFresh()
        waitForText("Load demo data")
        tab("Settings").performClick()
        scrollToText("Updates")
        assertTrue(hasText("Updates", substring = false), "the updater section is in Settings")
        assertTrue(hasText("Check for updates", substring = false), "the updater can be started")
        shot("github-settings")
    }

    @Test
    fun theGithubPrivacySubjectIsOnlyInTheUpdaterBuild() {
        launchFresh()
        waitForText("Load demo data")
        tab("Settings").performClick()
        clickText("About and licences")
        waitForText("About Cercis")
        scrollToText("What leaves your phone")
        clickText("What leaves your phone")
        waitForText("What leaves your phone")

        scrollToText("In-app updater")
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasTestTag("privacy-subject-github")).fetchSemanticsNodes().isNotEmpty()
        }
        clickTag("privacy-subject-github")

        waitForText("Leaves your phone")
        assertTrue(hasText("To: GitHub", substring = false), "the update check goes to GitHub")
        assertTrue(hasText("Release request", substring = true), "the exact request is named")
        assertTrue(hasText("Update check", substring = true), "the trigger is named")
        shot("github-privacy-detail")
    }
}
