package nl.tufein.openconnect

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

/**
 * The store channel ships without the in-app updater: the build flag is off, Settings has no update card and the
 * privacy page leaves the GitHub subject out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class StoreChannelTest : AppTestBase() {
    @Test
    fun theStoreBuildHasNoUpdaterCard() {
        assertFalse(BuildConfig.UPDATER_ENABLED, "the store flavor must not build the updater")

        launchFresh()
        waitForText("Load demo data")
        tab("Settings").performClick()

        // The updater card sits before the About card. Reach the end of Settings first, so an absence is not just
        // an item that was never composed.
        scrollToText("About and licences")
        assertFalse(hasText("Updates", substring = false), "the store build has no updater section")
        assertFalse(hasText("Check for updates", substring = false), "the store build must not offer an in-app update")
        assertFalse(hasText("Current version:", substring = true), "the update card's version line is absent too")
        shot("store-settings")
    }

    @Test
    fun theStorePrivacyPageLeavesTheGithubSubjectOut() {
        launchFresh()
        waitForText("Load demo data")
        tab("Settings").performClick()
        clickText("About and licences")
        waitForText("About OpenConnect")
        scrollToText("What leaves your phone")
        clickText("What leaves your phone")
        waitForText("What leaves your phone")

        // Scroll the whole index looking for the updater subject: unlike a plain on-screen check, this fails if
        // the row is anywhere in the list, not only in the first viewport.
        val lists = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        val index = lists.indices.maxByOrNull { lists[it].boundsInRoot.height } ?: 0
        val found = runCatching {
            rule.onAllNodes(hasScrollAction())[index].performScrollToNode(hasText("In-app updater"))
        }.isSuccess
        assertFalse(found, "the store build must not list the GitHub updater subject")
        shot("store-privacy-index")
    }
}
