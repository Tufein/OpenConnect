package nl.tufein.openconnect.ui

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.ai.FakeAiSettings
import nl.tufein.openconnect.ai.FakeAiTransport
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Backup
import nl.tufein.openconnect.core.InMemoryRepository
import nl.tufein.openconnect.core.JournalBook
import nl.tufein.openconnect.core.JournalTag
import nl.tufein.openconnect.core.OwnWorkout
import nl.tufein.openconnect.core.PlanAdaptation
import nl.tufein.openconnect.core.PlanConfig
import nl.tufein.openconnect.core.PlanDecision
import nl.tufein.openconnect.core.PlanGoal
import nl.tufein.openconnect.core.PlanProposal
import nl.tufein.openconnect.core.SessionType
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.TrackPoint
import nl.tufein.openconnect.core.TrainingPlans
import nl.tufein.openconnect.core.UserData
import nl.tufein.openconnect.core.WorkoutBook
import nl.tufein.openconnect.core.Workouts
import nl.tufein.openconnect.core.watch.InMemoryWatchSource
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import nl.tufein.openconnect.watch.OpenedWatch
import nl.tufein.openconnect.watch.WatchInfo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Non-UI app tests for serialized plan decisions, current-config watch export and the read-only coach digest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OpenConnectApp::class)
class AppViewModelPlanTest {
    private val app get() = ApplicationProvider.getApplicationContext<OpenConnectApp>()

    private fun config(today: LocalDate = LocalDate.now(ZoneOffset.UTC)) = PlanConfig(
        goal = PlanGoal.FIVE_K,
        runsPerWeek = 3,
        weeks = 12,
        start = TrainingPlans.mondayOf(today).minusWeeks(1).toString(),
        baselineKm = 15.0,
    )

    private fun launch(
        usb: FakeUsb = FakeUsb { error("no watch in this test") },
        aiSettings: FakeAiSettings = FakeAiSettings(),
        aiTransport: FakeAiTransport = FakeAiTransport(),
        initialConfig: PlanConfig = config(),
    ): AppViewModel {
        app.deleteDatabase("openconnect.db")
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("secure-test", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = SettingsStore(app, secure = { app.getSharedPreferences("secure-test", Context.MODE_PRIVATE) })
        settings.trainingPlan = initialConfig.encode()
        val container = nl.tufein.openconnect.AppContainer(
            repository = SqliteRepository(app),
            settings = settings,
            usb = usb,
            ble = FakeBle(),
            aiSettings = aiSettings,
            aiTransport = aiTransport,
        )
        app.containerOverride = container
        return AppViewModel(app)
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() >= deadline) fail("Timed out waiting for $message")
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun loaded(vm: AppViewModel) {
        await("the plan to load") { vm.plan.value.loaded }
        assertNotNull(vm.plan.value.proposal)
        assertNotNull(vm.plan.value.proposalActionId)
        assertFalse(vm.plan.value.actionBusy)
    }

    private fun proposalFor(config: PlanConfig): PlanProposal {
        val today = LocalDate.now(ZoneOffset.UTC)
        val plan = TrainingPlans.build(config)
        val progress = TrainingPlans.progress(plan, emptyList(), today)
        return checkNotNull(PlanAdaptation.propose(plan, PlanAdaptation.assess(plan, progress.weeks), emptyList(), today))
    }

    private fun decisionFor(proposal: PlanProposal, accepted: Boolean = true) = PlanDecision(
        policyVersion = PlanAdaptation.POLICY_VERSION,
        proposalId = proposal.proposalId,
        accepted = accepted,
        sourceWeek = proposal.source.week.number,
        targetWeek = proposal.target.number,
        sessionTypes = proposal.sessionTypes,
    )

    private fun decisionId(sourceWeek: Int, targetWeek: Int, types: List<SessionType>): String =
        "v${PlanAdaptation.POLICY_VERSION}:$sourceWeek:$targetWeek:${types.joinToString(",") { it.name }}"

    @After
    fun close() {
        app.containerOverride = null
    }

    @Test
    fun doubleAndStaleApplyAreHarmlessAndPersistOneImmutableDecision() {
        val vm = launch()
        loaded(vm)
        val proposal = vm.plan.value.proposal!!
        val actionId = vm.plan.value.proposalActionId!!
        val originalTargetTypes = proposal.target.sessions.map { it.type }

        vm.applyPlanChange(actionId)
        vm.applyPlanChange(actionId)
        await("one accepted decision and its reload") {
            val decisions = PlanConfig.decode(app.container.settings.trainingPlan)?.decisions.orEmpty()
            decisions.size == 1 && vm.plan.value.proposal == null
        }

        val config = PlanConfig.decode(app.container.settings.trainingPlan)!!
        val decision = config.decisions.single()
        assertTrue(decision.accepted)
        assertEquals(nl.tufein.openconnect.core.PlanAdaptation.POLICY_VERSION, decision.policyVersion)
        assertEquals(proposal.proposalId, decision.proposalId)
        assertEquals(proposal.source.week.number, decision.sourceWeek)
        assertEquals(proposal.target.number, decision.targetWeek)
        assertEquals(proposal.sessionTypes, decision.sessionTypes)
        val target = vm.plan.value.plan!!.weeks.single { it.number == proposal.target.number }
        assertEquals(proposal.sessionTypes, target.sessions.map { it.type })
        assertFalse(originalTargetTypes == target.sessions.map { it.type }, "the accepted pattern really changed the target")

        assertFalse(vm.plan.value.actionBusy)
        assertFalse(vm.plan.value.actionFailed, "a duplicate while busy is an idempotent no-op")
        val once = app.container.settings.trainingPlan
        vm.applyPlanChange(actionId)
        vm.applyPlanChange("stale-action-id")
        await("the stale action to be rejected") { !vm.plan.value.actionBusy && vm.plan.value.actionFailed }
        assertEquals(once, app.container.settings.trainingPlan, "a stale tap cannot append or compound")
    }

    @Test
    fun proposalChangeRotatesTokenSoStaleCallbackCannotAuthorizeTheNextProposal() {
        val base = config().copy(start = TrainingPlans.mondayOf(LocalDate.now(ZoneOffset.UTC)).minusWeeks(2).toString())
        val vm = launch(initialConfig = base)
        loaded(vm)
        val first = vm.plan.value.proposal!!
        val firstToken = vm.plan.value.proposalActionId!!

        vm.applyPlanChange(firstToken)
        await("the second proposal and its rotated token") {
            val state = vm.plan.value
            val decisions = PlanConfig.decode(app.container.settings.trainingPlan)?.decisions.orEmpty()
            decisions.size == 1 && state.proposal != null && state.proposal.proposalId != first.proposalId &&
                state.proposalActionId != null && state.proposalActionId != firstToken
        }
        val second = vm.plan.value.proposal!!
        val secondToken = vm.plan.value.proposalActionId!!
        val afterFirst = app.container.settings.trainingPlan

        vm.applyPlanChange(firstToken)
        await("the stale first token to be rejected") { !vm.plan.value.actionBusy && vm.plan.value.actionFailed }
        assertEquals(afterFirst, app.container.settings.trainingPlan)
        assertEquals(1, PlanConfig.decode(afterFirst)!!.decisions.size)
        assertEquals(secondToken, vm.plan.value.proposalActionId)

        vm.applyPlanChange(secondToken)
        await("the second proposal to be applied") {
            val state = vm.plan.value
            PlanConfig.decode(app.container.settings.trainingPlan)?.decisions?.size == 2 && state.proposal == null && !state.actionBusy
        }
        val decisions = PlanConfig.decode(app.container.settings.trainingPlan)!!.decisions
        assertEquals(listOf(first.proposalId, second.proposalId), decisions.map { it.proposalId })
        assertTrue(decisions.all { it.accepted })
        assertFalse(vm.plan.value.actionFailed)
        assertNotNull(vm.plan.value.proposalActionId)
        assertFalse(vm.plan.value.proposalActionId == secondToken, "proposal to null also rotates the token")
    }

    @Test
    fun doubleAndStaleDismissAreHarmlessAndPersistOneRejectedDecision() {
        val vm = launch()
        loaded(vm)
        val proposal = vm.plan.value.proposal!!
        val actionId = vm.plan.value.proposalActionId!!
        val before = vm.plan.value.plan!!

        vm.dismissPlanChange(actionId)
        vm.dismissPlanChange(actionId)
        await("one dismissed decision and its reload") {
            val decisions = PlanConfig.decode(app.container.settings.trainingPlan)?.decisions.orEmpty()
            decisions.size == 1 && vm.plan.value.proposal == null
        }

        val config = PlanConfig.decode(app.container.settings.trainingPlan)!!
        val decision = config.decisions.single()
        assertFalse(decision.accepted)
        assertEquals(proposal.proposalId, decision.proposalId)
        assertEquals(before.weeks, TrainingPlans.build(config).weeks, "dismissal never changes the schedule")

        assertFalse(vm.plan.value.actionBusy)
        assertFalse(vm.plan.value.actionFailed)
        val once = app.container.settings.trainingPlan
        vm.dismissPlanChange(actionId)
        await("the stale dismissal to be rejected") { !vm.plan.value.actionBusy && vm.plan.value.actionFailed }
        assertEquals(once, app.container.settings.trainingPlan)
    }

    @Test
    fun acceptedDecisionSurvivesAFreshViewModelReload() {
        val vm = launch()
        loaded(vm)
        val proposal = vm.plan.value.proposal!!
        vm.applyPlanChange(vm.plan.value.proposalActionId!!)
        await("the accepted decision") { PlanConfig.decode(app.container.settings.trainingPlan)?.decisions?.size == 1 }
        val persisted = PlanConfig.decode(app.container.settings.trainingPlan)!!

        val reloaded = AppViewModel(app)
        await("the fresh view model to reload persisted state") { reloaded.plan.value.loaded && reloaded.plan.value.proposal == null }
        assertEquals(persisted, PlanConfig.decode(app.container.settings.trainingPlan))
        assertEquals(TrainingPlans.build(persisted), reloaded.plan.value.plan)
        assertEquals(proposal.sessionTypes, reloaded.plan.value.plan!!.weeks.single { it.number == proposal.target.number }.sessions.map { it.type })
    }

    @Test
    fun dismissedDecisionSurvivesAFreshViewModelReloadWithoutChangingWeeks() {
        val vm = launch()
        loaded(vm)
        val proposal = vm.plan.value.proposal!!
        val before = vm.plan.value.plan!!.weeks
        vm.dismissPlanChange(vm.plan.value.proposalActionId!!)
        await("the dismissed decision") { PlanConfig.decode(app.container.settings.trainingPlan)?.decisions?.size == 1 }
        val persisted = PlanConfig.decode(app.container.settings.trainingPlan)!!

        val reloaded = AppViewModel(app)
        await("the fresh view model to reload persisted state") { reloaded.plan.value.loaded && reloaded.plan.value.proposal == null }
        assertEquals(persisted, PlanConfig.decode(app.container.settings.trainingPlan))
        assertFalse(persisted.decisions.single().accepted)
        assertEquals(before, reloaded.plan.value.plan!!.weeks)
    }

    @Test
    fun duplicateEffectiveActionIsAnIdempotentNoOpAndClearsFailure() {
        val base = config()
        val vm = launch(initialConfig = base)
        loaded(vm)
        val proposal = vm.plan.value.proposal!!
        val actionId = vm.plan.value.proposalActionId!!
        vm.applyPlanChange("invalid-action")
        await("the invalid action to fail") { !vm.plan.value.actionBusy && vm.plan.value.actionFailed }

        // Leave the loaded UI token in place but make its core proposal effectively decided underneath it.
        val persisted = base.copy(decisions = listOf(decisionFor(proposal))).encode()
        app.container.settings.trainingPlan = persisted
        vm.applyPlanChange(actionId)
        await("the effective duplicate to finish") { !vm.plan.value.actionBusy }
        assertFalse(vm.plan.value.actionFailed)
        assertEquals(persisted, app.container.settings.trainingPlan)
    }

    @Test
    fun correctIdInvalidPolicyRowDoesNotBlockApplyAndIsRemoved() {
        val base = config()
        val proposal = proposalFor(base)
        val invalid = decisionFor(proposal).copy(policyVersion = PlanAdaptation.POLICY_VERSION + 1)
        val vm = launch(initialConfig = base.copy(decisions = listOf(invalid)))
        loaded(vm)
        assertEquals(proposal.proposalId, vm.plan.value.proposal!!.proposalId)

        vm.applyPlanChange(vm.plan.value.proposalActionId!!)
        await("the valid decision to replace the invalid row") {
            PlanConfig.decode(app.container.settings.trainingPlan)?.decisions?.singleOrNull()?.policyVersion == PlanAdaptation.POLICY_VERSION
        }
        await("the plan action to finish") { !vm.plan.value.actionBusy }
        val persisted = PlanConfig.decode(app.container.settings.trainingPlan)!!
        assertEquals(listOf(decisionFor(proposal)), persisted.decisions)
        assertFalse(vm.plan.value.actionBusy)
        assertFalse(vm.plan.value.actionFailed)
    }

    @Test
    fun semanticallyStaleAcceptedRowWithWrongSourcePatternCannotCancelValidApply() {
        val base = config()
        val proposal = proposalFor(base)
        val sourceTypes = proposal.sessionTypes
        val wrongTypes = sourceTypes.map { if (it == SessionType.EASY) SessionType.TEMPO else SessionType.EASY }
        assertFalse(sourceTypes == wrongTypes)
        val stale = PlanDecision(
            policyVersion = PlanAdaptation.POLICY_VERSION,
            proposalId = decisionId(proposal.source.week.number, proposal.target.number, wrongTypes),
            accepted = true,
            sourceWeek = proposal.source.week.number,
            targetWeek = proposal.target.number,
            sessionTypes = wrongTypes,
        )
        val baseline = TrainingPlans.build(base)
        assertEquals(emptyList(), PlanAdaptation.effectiveDecisions(baseline, listOf(stale)), "replay must reject a row whose source pattern changed")

        val vm = launch(initialConfig = base.copy(decisions = listOf(stale)))
        loaded(vm)
        assertEquals(proposal.proposalId, vm.plan.value.proposal!!.proposalId)
        vm.applyPlanChange(vm.plan.value.proposalActionId!!)
        await("the valid decision to replace the semantically stale row") {
            PlanConfig.decode(app.container.settings.trainingPlan)?.decisions == listOf(decisionFor(proposal))
        }
        val persisted = PlanConfig.decode(app.container.settings.trainingPlan)!!
        assertEquals(listOf(decisionFor(proposal)), persisted.decisions)
        assertFalse(vm.plan.value.actionFailed)
    }

    @Test
    fun staleTargetConflictDoesNotPoisonANewValidDecision() {
        val base = config().copy(start = TrainingPlans.mondayOf(LocalDate.now(ZoneOffset.UTC)).minusWeeks(2).toString())
        val proposal = proposalFor(base)
        val plan = TrainingPlans.build(base)
        val firstTypes = plan.weeks[0].sessions.map { it.type }
        val secondTypes = plan.weeks[1].sessions.map { it.type }
        val staleFirst = PlanDecision(
            policyVersion = PlanAdaptation.POLICY_VERSION,
            proposalId = decisionId(1, proposal.target.number, firstTypes),
            accepted = true,
            sourceWeek = 1,
            targetWeek = proposal.target.number,
            sessionTypes = firstTypes,
        )
        val staleSecond = PlanDecision(
            policyVersion = PlanAdaptation.POLICY_VERSION,
            proposalId = decisionId(2, proposal.target.number, secondTypes),
            accepted = true,
            sourceWeek = 2,
            targetWeek = proposal.target.number,
            sessionTypes = secondTypes,
        )
        val vm = launch(initialConfig = base.copy(decisions = listOf(staleFirst, staleSecond)))
        loaded(vm)
        assertEquals(proposal.proposalId, vm.plan.value.proposal!!.proposalId)

        vm.applyPlanChange(vm.plan.value.proposalActionId!!)
        await("the conflicting rows to be replaced by one effective decision") {
            PlanConfig.decode(app.container.settings.trainingPlan)?.decisions?.size == 1
        }
        val persisted = PlanConfig.decode(app.container.settings.trainingPlan)!!
        assertEquals(listOf(decisionFor(proposal)), persisted.decisions)
        assertEquals(listOf(decisionFor(proposal)), PlanAdaptation.effectiveDecisions(TrainingPlans.build(persisted), persisted.decisions))
    }

    @Test
    fun rejectedActionClearsBusySetsFailureAndOrdinaryReloadPreservesTheToken() {
        val vm = launch()
        loaded(vm)
        val actionId = vm.plan.value.proposalActionId!!
        val before = app.container.settings.trainingPlan
        app.container.settings.workouts = WorkoutBook(listOf(OwnWorkout(id = "reload-check", name = "Reload check"))).encode()
        vm.loadPlan()
        await("an ordinary reload") { vm.plan.value.ownWorkouts.any { it.id == "reload-check" } }
        assertEquals(actionId, vm.plan.value.proposalActionId)

        vm.applyPlanChange("not-the-current-action")
        await("the rejected action to clear busy") { !vm.plan.value.actionBusy }
        assertTrue(vm.plan.value.actionFailed)
        assertEquals(before, app.container.settings.trainingPlan)
        assertEquals(actionId, vm.plan.value.proposalActionId)

        vm.applyPlanChange(actionId)
        await("the subsequent valid action") {
            !vm.plan.value.actionBusy && PlanConfig.decode(app.container.settings.trainingPlan)?.decisions?.size == 1
        }
        assertFalse(vm.plan.value.actionBusy)
        assertFalse(vm.plan.value.actionFailed, "a successful action clears an earlier rejection")
    }

    @Test
    fun staleActionFromDeletedPlanCannotMutateRestoredPlan() {
        val vm = launch()
        loaded(vm)
        val oldToken = vm.plan.value.proposalActionId!!
        vm.deletePlan()
        await("the plan to be deleted") { vm.plan.value.loaded && vm.plan.value.plan == null }

        val restoredConfig = config().copy(goal = PlanGoal.TEN_K, baselineKm = 20.0)
        val backup = Backup.write(InMemoryRepository(), userData = UserData(trainingPlan = restoredConfig.encode()))
        val file = File(app.cacheDir, "plan-restore-test.ocbak").also { it.writeBytes(backup) }
        try {
            vm.restore(Uri.fromFile(file))
            await("the restored plan and its rotated token") {
                val state = vm.plan.value
                state.loaded && state.plan?.config?.goal == PlanGoal.TEN_K && state.proposalActionId != null && state.proposalActionId != oldToken
            }
            val newToken = vm.plan.value.proposalActionId!!
            val before = app.container.settings.trainingPlan
            vm.applyPlanChange(oldToken)
            await("the old plan callback to be rejected") { !vm.plan.value.actionBusy && vm.plan.value.actionFailed }
            assertEquals(before, app.container.settings.trainingPlan)
            assertEquals(newToken, vm.plan.value.proposalActionId)
        } finally {
            file.delete()
        }
    }

    @Test
    fun watchExportUsesTheLatestPersistedConfigInsteadOfLoadedPlanState() {
        val watch = InMemoryWatchSource("fenix 8", emptyMap())
        val usb = FakeUsb(listOf(WatchInfo("usb-1", "fenix 8"))) { OpenedWatch(watch) { } }
        val vm = launch(usb)
        loaded(vm)
        val stale = vm.plan.value.plan!!
        val latest = config().copy(runsPerWeek = 2)
        app.container.settings.trainingPlan = latest.encode() // deliberately leaves PlanState stale
        assertFalse(stale.config.runsPerWeek == latest.runsPerWeek)

        vm.sendPlanToWatch()
        val expected = Workouts.upcoming(TrainingPlans.build(latest), LocalDate.now(ZoneOffset.UTC)).map { Workouts.fileName(it) }.toSet()
        await("the latest persisted plan to reach the watch") { watch.workouts.keys == expected }
        assertEquals(expected, watch.workouts.keys)
        assertTrue(watch.workouts.isNotEmpty())
    }

    @Test
    fun coachRequestHasOnlyBoundedPlanFactsAndNoRawTracksJournalOrOwnWorkoutText() {
        val settings = FakeAiSettings().also { it.setKey(nl.tufein.openconnect.core.reports.AiVendor.GEMINI, "AIzaSyA-0123456789abcdefghijklmnopqrstu") }
        val transport = FakeAiTransport { FakeAiTransport.gemini("The proposal copies the missed pattern.") }
        val vm = launch(aiSettings = settings, aiTransport = transport)
        val rawName = "RAW_ACTIVITY_NAME_SECRET"
        val rawGps = "52.123456"
        val ownName = "OWN_WORKOUT_TEXT_SECRET"
        app.container.repository.upsertActivity(
            Activity(
                id = "raw-track",
                name = rawName,
                sport = Sport.RUNNING,
                startTime = Instant.parse("2026-11-11T17:00:00Z"),
                distanceM = 0.0,
                durationS = 0.0,
                track = listOf(TrackPoint(Instant.parse("2026-11-11T17:00:00Z"), lat = 52.123456, lon = 4.654321)),
            ),
        )
        app.container.settings.journal = JournalBook(mapOf(LocalDate.parse("2026-11-12") to setOf(JournalTag.ALCOHOL))).encode()
        app.container.settings.workouts = WorkoutBook(listOf(OwnWorkout(id = "own-secret", name = ownName))).encode()
        val beforeReload = vm.plan.value
        vm.loadPlan()
        await("the plan and coach facts to reload with private data present") {
            vm.plan.value.loaded && vm.plan.value !== beforeReload && vm.plan.value.proposal != null
        }

        vm.askCoach("What does the current proposal change?")
        await("the coach request") { transport.calls.isNotEmpty() }
        val body = Json.parseToJsonElement(transport.calls.single().body).jsonObject
        val system = body.getValue("system_instruction").jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content
        val prompt = body.getValue("contents").jsonArray.last().jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content
        assertTrue("cannot apply, dismiss or edit" in system)
        assertTrue("Do not invent alternative schedule changes" in system)
        val data = Json.parseToJsonElement(prompt.substringAfter("```json\n").substringBefore("\n```")).jsonObject
        assertEquals(setOf("weeks", "proposal"), data.keys)
        assertTrue(data.getValue("weeks").jsonArray.size <= 12)
        assertEquals(setOf("source_week", "target_week", "session_types", "text"), data.getValue("proposal").jsonObject.keys)
        val sent = transport.calls.single().body
        for (forbidden in listOf(rawName, rawGps, ownName, "alcohol", "decisions", "accepted", "dismissed")) {
            assertFalse(forbidden in sent, "$forbidden must not enter the coach request")
        }
        assertTrue(vm.plan.value.proposal!!.proposalId.isNotEmpty())
    }
}
