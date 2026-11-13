package nl.tufein.openconnect

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nl.tufein.openconnect.lock.Unlocker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stands in for Android's fingerprint check: it records the request and answers when the test says so. */
class FakeUnlocker(private val available: Boolean = true) : Unlocker {
    var asked = 0
    private var waiting: ((Boolean) -> Unit)? = null
    override fun available() = available
    override fun unlock(onResult: (Boolean) -> Unit) {
        asked++
        waiting = onResult
    }
    fun answer(ok: Boolean) = waiting?.invoke(ok)
}

/** v0.15.0 on the JVM: the training plan under Insights and the app lock. The real fingerprint needs a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class PlanAndLockScreenTest : AppTestBase() {
    @Test
    fun aPlanIsCreatedFromTheDemoRunsAndShowsTheNextRunAndTheWeeks() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(15_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(15_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        rule.onNodeWithText("Plan").performClick()
        waitForText("Create plan")
        shot("33-plan-setup")
        rule.onNodeWithText("10K").performClick()
        rule.onNodeWithText("Create plan").performClick()
        waitForText("10K plan")
        waitForText("Next run")
        shot("34-plan")
        scrollToText("All weeks")
        shot("35-plan-weeks")
        scrollToText("Delete plan")
        rule.onNodeWithText("Delete plan").performClick()
        waitForText("Create plan")
    }

    @Test
    fun theLockHidesTheAppUntilTheCheckPassesAndOpensWithoutAScreenLock() {
        val fake = FakeUnlocker()
        launchFresh { app ->
            app.unlockerOverride = { fake }
            app.container.setAppLock(true)
            app.container.lockGate.lock()
        }
        waitForText("OpenConnect is locked")
        assertTrue(fake.asked >= 1, "the check starts by itself")
        assertEquals(0, rule.onAllNodesWithTextCount("Load demo data"), "no data behind the lock")
        shot("36-locked")
        fake.answer(false) // cancelled: still locked
        rule.waitForIdle()
        waitForText("OpenConnect is locked")
        fake.answer(true)
        waitForText("Load demo data")
    }

    @Test
    fun withoutAScreenLockOnThePhoneTheAppOpensInsteadOfLockingYouOut() {
        launchFresh { app ->
            app.unlockerOverride = { FakeUnlocker(available = false) }
            app.container.setAppLock(true)
            app.container.lockGate.lock()
        }
        waitForText("Load demo data")
    }

    @Test
    fun theLockSwitchNeedsTheCheckFirst() {
        val fake = FakeUnlocker()
        launchFresh { app -> app.unlockerOverride = { fake } }
        waitForText("Load demo data")
        tab("Settings").performClick()
        scrollToText("Lock the app")
        shot("37-settings-lock")
        val lockSwitch = androidx.compose.ui.test.isToggleable() and androidx.compose.ui.test.hasAnySibling(androidx.compose.ui.test.hasText("Lock the app"))
        rule.onNode(lockSwitch).performClick()
        rule.waitUntil(5_000) { fake.asked == 1 }
        rule.onNode(lockSwitch).assertIsOff() // not on yet: the check has not passed
        fake.answer(true)
        rule.waitUntil(5_000) { runCatching { rule.onNode(lockSwitch).assertIsOn() }.isSuccess }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}
