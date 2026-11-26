package nl.tufein.openconnect

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText as hasTextMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import nl.tufein.openconnect.ai.ActiveAi
import nl.tufein.openconnect.ai.CoachUi
import nl.tufein.openconnect.core.PlanAdaptation
import nl.tufein.openconnect.core.PlanConfig
import nl.tufein.openconnect.core.PlanGoal
import nl.tufein.openconnect.core.TrainingPlans
import nl.tufein.openconnect.lock.Unlocker
import nl.tufein.openconnect.ui.CoachContent
import nl.tufein.openconnect.ui.OpenConnectTheme
import nl.tufein.openconnect.ui.PlanContent
import nl.tufein.openconnect.ui.PlanState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        clickChip("More")
        clickChip("Plan")
        scrollToCreatePlan()
        waitForText("Create plan")
        shot("33-plan-setup")
        clickText("10K")
        clickText("Create plan")
        rule.waitUntil(45_000) { !hasText("Create plan", substring = false) }
        scrollToText("10K plan")
        waitForText("10K plan")
        // Since v1.6.0 the own-workout builder sits above the plan, so the next run can start below the fold.
        scrollToText("Next run")
        waitForText("Next run")
        shot("34-plan")
        scrollToText("All weeks")
        shot("35-plan-weeks")
        clickText("Delete plan")
        scrollToCreatePlan()
        waitForText("Create plan")
    }

    /** The plan card is built after the tab and chip switch; retry the scroll until its text is composed, not just once. */
    private fun scrollToCreatePlan() {
        rule.waitUntil(15_000) {
            try {
                scrollToText("Create plan")
                hasText("Create plan", substring = false)
            } catch (_: Throwable) {
                false
            }
        }
    }

    @Test
    fun demoRunsProduceOneClearPlanAdjustmentWithoutApplyingItAutomatically() {
        openPastPlanWithDemo()
        assertSingleProposal()

        val (source, target) = proposalWeeks()
        assertTrue(source < target, "a missed week is moved forward, not back")
        assertTrue(hasText("Missed week", substring = true))
        assertTrue(hasText("Current pattern in week", substring = true))
        assertTrue(hasText("New pattern:", substring = true))
        assertTrue(hasText("Dates, distances, weekly volume and race week stay unchanged.", substring = false))
        assertEquals(1, rule.onAllNodesWithText("Apply").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("Not now").fetchSemanticsNodes().size)
        shot("110-plan-adjustment")
    }

    @Test
    fun applyingAProposalChangesOnlyTheTargetPattern() {
        openPastPlanWithDemo()
        assertSingleProposal()
        val (source, target) = proposalWeeks()
        val oldPattern = pattern("Current pattern in week")
        val newPattern = pattern("New pattern:")
        val before = weekDescription(target)
        assertTrue(oldPattern in before, "the target week starts with its current pattern")

        clickText("Apply")
        waitForText("No adjustment is available right now", substring = true)
        val after = weekDescription(target)
        assertTrue(newPattern in after, "the suggested ordered pattern is now in the target week: $after")
        assertTrue(after != before, "applying changes the target week")
        assertEquals(0, rule.onAllNodesWithText("Apply").fetchSemanticsNodes().size)
        assertTrue(!hasText("Week $source → week $target", substring = false), "the applied proposal is gone")
    }

    @Test
    fun notNowLeavesThePlanSessionsAloneAndDoesNotOfferThatSourceAgain() {
        openPastPlanWithDemo()
        assertSingleProposal()
        val (source, target) = proposalWeeks()
        val before = weekDescription(target)

        clickText("Not now")
        waitForText("No adjustment is available right now", substring = true)
        assertEquals(0, rule.onAllNodesWithText("Apply").fetchSemanticsNodes().size)
        assertEquals(before, weekDescription(target), "dismissing must not rewrite any plan session")

        tab("Home").performClick()
        tab("Insights").performClick()
        scrollToText("Plan adjustment")
        waitForText("Plan adjustment")
        assertEquals(0, rule.onAllNodesWithText("Apply").fetchSemanticsNodes().size)
        assertTrue(!hasText("Week $source → week $target", substring = false), "the dismissed source is not proposed again")
    }

    @Test
    fun aPlanWithoutMissedWeeksExplainsThatNoAdjustmentIsNeeded() {
        launchFresh()
        tab("Insights").performClick()
        clickChip("More")
        clickChip("Plan")
        // The plan loads off the main thread; wait for its first card before looking for the button below the fold.
        waitForText("Race goal")
        scrollToText("Create plan")
        waitForText("Create plan")
        clickText("Create plan")
        // The plan is built off the main thread; the setup disappears when its title has replaced it.
        rule.waitUntil(45_000) { !hasText("Create plan", substring = false) }
        scrollToText("Plan adjustment")
        waitForText("Plan adjustment")
        scrollToText("No missed weeks.", substring = true)
        assertEquals(0, rule.onAllNodesWithText("Apply").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Not now").fetchSemanticsNodes().size)
    }

    private fun openPastPlanWithDemo() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val start = TrainingPlans.mondayOf(today.minusWeeks(1))
        // One completed week at a much higher volume than the demo runs makes the missed week deterministic;
        // the proposal itself still comes from the app, never from the test.
        val config = PlanConfig(PlanGoal.TEN_K, runsPerWeek = 3, weeks = 8, start = start.toString(), baselineKm = 30.0)
        launchFresh { app -> app.container.settings.trainingPlan = config.coerced().encode() }
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        clickChip("More")
        clickChip("Plan")
        scrollToText("Plan adjustment")
        waitForText("Plan adjustment")
    }

    private fun assertSingleProposal() {
        scrollToText("Apply")
        waitForText("Apply")
        assertEquals(1, rule.onAllNodesWithText("Apply").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("Not now").fetchSemanticsNodes().size)
        val figures = rule.onAllNodes(hasTextMatcher("km planned", substring = true)).fetchSemanticsNodes()
        assertTrue(figures.isNotEmpty(), "planned distance is visible")
        assertTrue(
            figures.any { node -> Regex("""\d+[.,]\d km planned · \d+[.,]\d km actual""").containsMatchIn(node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }) },
            "planned and actual figures are shown together",
        )
        for (label in listOf("Apply", "Not now")) {
            val node = rule.onNodeWithText(label).fetchSemanticsNode()
            val density = node.layoutInfo.density.density
            val width = maxOf(node.size.width.toFloat(), node.touchBoundsInRoot.width) / density
            val height = maxOf(node.size.height.toFloat(), node.touchBoundsInRoot.height) / density
            assertTrue(width >= 48f && height >= 48f, "$label has a ${width.toInt()} × ${height.toInt()} dp touch target")
        }
    }

    private fun proposalWeeks(): Pair<Int, Int> {
        val node = rule.onNode(hasTextMatcher("Week ", substring = true) and hasTextMatcher("→", substring = true)).fetchSemanticsNode()
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        val match = Regex("Week (\\d+) → week (\\d+)").find(text) ?: error("No source/target weeks in: $text")
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    private fun pattern(label: String): String {
        val node = rule.onNodeWithText(label, substring = true).fetchSemanticsNode()
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        return text.substringAfter(':').trim()
    }

    private fun weekDescription(number: Int): String {
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag("plan-week-$number"))
        val node = rule.onNodeWithTag("plan-week-$number").fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    }

    @Test
    fun aRaceGoalIsSetMovedAndRemoved() {
        launchFresh()
        waitForText("Load demo data")
        rule.onNodeWithText("Load demo data").performClick()
        rule.waitUntil(45_000) { !hasText("Load demo data", substring = false) }
        rule.waitUntil(45_000) { !hasText("Demo data loaded", substring = true) }
        tab("Insights").performClick()
        waitForText("Week of", substring = true)
        clickChip("More")
        clickChip("Plan")
        waitForText("Race goal")
        shot("48-race-setup")
        clickText("Set race goal")
        // Twelve weeks out, in UTC like the plan itself.
        waitForText("in 84 days (12 weeks)")
        clickText("+1 week")
        waitForText("in 91 days (13 weeks)")
        val settings = androidx.test.core.app.ApplicationProvider.getApplicationContext<OpenConnectApp>().container.settings
        val goal = nl.tufein.openconnect.core.RaceGoal.decode(settings.raceGoal)!!
        assertEquals(java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(91), goal.date)
        val target = goal.targetSeconds
        clickText("+1 min")
        rule.waitUntil(5_000) { nl.tufein.openconnect.core.RaceGoal.decode(settings.raceGoal)?.targetSeconds == target + 60 }
        shot("49-race-goal")
        clickText("Remove race goal")
        waitForText("Set race goal")
        assertEquals("", settings.raceGoal)
    }

    @Test
    fun theLockHidesTheAppUntilTheCheckPassesAndOpensWithoutAScreenLock() {
        val fake = FakeUnlocker()
        launchFresh { app ->
            app.unlockerOverride = { fake }
            app.container.setAppLock(true)
            app.container.lockGate.lock()
        }
        waitForText("Cercis is locked")
        assertTrue(fake.asked >= 1, "the check starts by itself")
        assertEquals(0, rule.onAllNodesWithTextCount("Load demo data"), "no data behind the lock")
        shot("36-locked")
        fake.answer(false) // cancelled: still locked
        rule.waitForIdle()
        waitForText("Cercis is locked")
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
        val lockSwitch = androidx.compose.ui.test.hasTestTag("app-lock-switch")
        rule.onNode(lockSwitch).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) // may sit behind the tab bar after scrolling
        rule.waitUntil(5_000) { fake.asked == 1 }
        rule.onNode(lockSwitch).assertIsOff() // not on yet: the check has not passed
        fake.answer(true)
        rule.waitUntil(5_000) { runCatching { rule.onNode(lockSwitch).assertIsOn() }.isSuccess }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}

/** The adaptive-plan card and coach prompts without an Activity: lets the UI drive busy, stale and success states directly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class PlanActionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun planActionsUseTheUiTokenAndReportBusyStaleAndSuccessfulStates() {
        val state = mutableStateOf(proposalState())
        var applied: String? = null
        var dismissed: String? = null
        rule.setContent {
            OpenConnectTheme {
                PlanContent(
                    state = state.value,
                    onCreate = { _, _, _ -> },
                    onDelete = {},
                    onRace = {},
                    onSendWorkouts = {},
                    onSaveWorkout = {},
                    onDeleteWorkout = {},
                    contentPadding = PaddingValues(),
                    onApplyPlanChange = {
                        applied = it
                        state.value = state.value.copy(actionBusy = true, actionFailed = false)
                    },
                    onDismissPlanChange = {
                        dismissed = it
                        state.value = state.value.copy(actionBusy = true, actionFailed = false)
                    },
                )
            }
        }
        scrollToText("Apply")
        clickText("Apply")
        assertEquals("ui-action-token", applied, "the UI token, not the core proposal id, is sent")
        waitForText("Saving your choice…")
        rule.onNodeWithText("Apply").assertIsNotEnabled()
        rule.onNodeWithText("Not now").assertIsNotEnabled()
        val busy = rule.onNodeWithText("Saving your choice…").fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, busy.config.getOrNull(SemanticsProperties.LiveRegion))

        rule.runOnIdle { state.value = state.value.copy(actionBusy = false, actionFailed = true) }
        waitForText("That plan choice is no longer current. Check the newest proposal.")
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("Apply").assertIsEnabled() }.isSuccess }
        val failure = rule.onNodeWithText("That plan choice is no longer current. Check the newest proposal.").fetchSemanticsNode()
        assertEquals(LiveRegionMode.Assertive, failure.config.getOrNull(SemanticsProperties.LiveRegion))

        clickText("Not now")
        assertEquals("ui-action-token", dismissed)
        waitForText("Saving your choice…")
        rule.runOnIdle {
            state.value = state.value.copy(actionBusy = false, actionFailed = false, proposal = null, proposalActionId = null)
        }
        waitForText("No adjustment is available right now", substring = true)
        assertFalse(hasText("That plan choice is no longer current. Check the newest proposal.", substring = false))
        assertEquals(0, rule.onAllNodesWithText("Apply").fetchSemanticsNodes().size)
    }

    @Test
    fun aProposalWithoutAUiActionTokenCannotBeApplied() {
        rule.setContent {
            OpenConnectTheme {
                PlanContent(
                    state = proposalState(actionId = null),
                    onCreate = { _, _, _ -> },
                    onDelete = {},
                    onRace = {},
                    onSendWorkouts = {},
                    onSaveWorkout = {},
                    onDeleteWorkout = {},
                    contentPadding = PaddingValues(),
                )
            }
        }
        scrollToText("Apply")
        rule.onNodeWithText("Apply").assertIsNotEnabled()
        rule.onNodeWithText("Not now").assertIsNotEnabled()
    }

    @Test
    fun coachSuggestionsAskOnlyAboutPlanAdherenceAndTheCurrentProposal() {
        var asked: String? = null
        rule.setContent {
            OpenConnectTheme {
                CoachContent(
                    state = CoachUi(canAsk = true),
                    active = ActiveAi.GEMINI,
                    onAsk = { asked = it },
                    onClear = {},
                    onOpenAiSettings = {},
                )
            }
        }
        waitForText("Ask about your training plan", substring = true)
        for (suggestion in listOf(
            "How does my planned distance compare with what I actually ran?",
            "Why was my week marked as behind?",
            "What would the current plan proposal change?",
            "What does my current plan week show?",
        )) {
            scrollToText(suggestion)
            waitForText(suggestion)
        }
        assertFalse(hasText("How did I sleep this week?", substring = false))
        assertFalse(hasText("Am I recovering well?", substring = false))
        clickText("What would the current plan proposal change?")
        assertEquals("What would the current plan proposal change?", asked)
    }

    private fun proposalState(actionId: String? = "ui-action-token"): PlanState {
        val today = LocalDate.now(ZoneOffset.UTC)
        val config = PlanConfig(
            PlanGoal.TEN_K,
            runsPerWeek = 3,
            weeks = 8,
            start = TrainingPlans.mondayOf(today.minusWeeks(1)).toString(),
            baselineKm = 30.0,
        )
        val plan = TrainingPlans.build(config)
        val progress = TrainingPlans.progress(plan, emptyList(), today)
        val assessment = PlanAdaptation.assess(plan, progress.weeks)
        val proposal = requireNotNull(PlanAdaptation.propose(plan, assessment, emptyList(), today))
        return PlanState(
            loaded = true,
            plan = plan,
            progress = progress,
            assessment = assessment,
            proposal = proposal,
            proposalActionId = actionId,
            today = today,
        )
    }

    private fun hasText(text: String, substring: Boolean = false): Boolean =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String, substring: Boolean = false) {
        rule.waitUntil(10_000) { hasText(text, substring) }
    }

    private fun scrollToText(text: String, substring: Boolean = false) {
        if (!hasText(text, substring)) {
            rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTextMatcher(text, substring))
        }
    }

    private fun clickText(text: String) {
        scrollToText(text)
        rule.onNodeWithText(text).performScrollTo().performClick()
    }
}
