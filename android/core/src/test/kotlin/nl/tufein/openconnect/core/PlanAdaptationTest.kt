package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlanAdaptationTest {
    private val today = LocalDate.parse("2026-11-25")

    private data class Fixture(
        val plan: TrainingPlan,
        val progress: List<WeekProgress>,
    )

    /** A twelve-week plan seen on Wednesday of week four, with deliberately varied progress states. */
    private fun fixture(): Fixture {
        val config = PlanConfig(PlanGoal.FIVE_K, 3, 12, "2026-11-02", 15.0)
        val plan = TrainingPlans.build(config)
        val states = mapOf(
            1 to WeekState.BEHIND,
            2 to WeekState.BEHIND,
            3 to WeekState.DONE,
            4 to WeekState.BEHIND, // current, so not a source
            5 to WeekState.BEHIND, // future, so not a source
            6 to WeekState.BEHIND, // future, so not a source
            7 to WeekState.UPCOMING,
            8 to WeekState.BEHIND, // future, so not a source
            9 to WeekState.UPCOMING,
            10 to WeekState.UPCOMING,
            11 to WeekState.UPCOMING,
            12 to WeekState.BEHIND, // race, so never a source
        )
        return Fixture(plan, progress(plan, states))
    }

    private fun progress(plan: TrainingPlan, states: Map<Int, WeekState>): List<WeekProgress> =
        plan.weeks.map { week ->
            val behind = states[week.number] == WeekState.BEHIND
            WeekProgress(
                week = week,
                doneKm = if (behind) week.volumeKm * 0.5 else week.volumeKm,
                doneRuns = if (behind) 1 else week.sessions.size,
                state = states[week.number] ?: WeekState.UPCOMING,
            )
        }

    private fun progress(plan: TrainingPlan, vararg behind: Int): List<WeekProgress> =
        progress(plan, behind.associateWith { WeekState.BEHIND })

    private fun types(week: PlanWeek): List<SessionType> = week.sessions.map { it.type }

    private fun withTypes(week: PlanWeek, sessionTypes: List<SessionType>): PlanWeek =
        week.copy(sessions = week.sessions.mapIndexed { index, session -> session.copy(type = sessionTypes[index]) })

    private fun decision(proposal: PlanProposal, accepted: Boolean = true) = PlanDecision(
        policyVersion = PlanAdaptation.POLICY_VERSION,
        proposalId = proposal.proposalId,
        accepted = accepted,
        sourceWeek = proposal.source.week.number,
        targetWeek = proposal.target.number,
        sessionTypes = proposal.sessionTypes,
    )

    @Test
    fun `assessment contains every BEHIND week but proposal excludes current future and race sources`() {
        assertEquals(1, PlanAdaptation.POLICY_VERSION)
        val f = fixture()
        val assessment = PlanAdaptation.assess(f.plan, f.progress)
        assertEquals(listOf(1, 2, 4, 5, 6, 8, 12), assessment.behind.map { it.week.number })

        val proposal = assertNotNull(PlanAdaptation.propose(f.plan, assessment, emptyList(), today))
        assertEquals(2, proposal.source.week.number, "newest closed BEHIND week")
        assertEquals(5, proposal.target.number, "earliest future non-race/non-recovery week")
        assertEquals("v1:2:5:EASY,INTERVALS,LONG", proposal.proposalId)
        assertEquals(types(f.plan.weeks[1]), proposal.sessionTypes)
        assertEquals(f.plan.weeks[4], proposal.target, "a proposal leaves the target baseline intact")
    }

    @Test
    fun `accepted and dismissed decisions resolve both weeks and the next source is the newest remaining one`() {
        val f = fixture()
        val assessment = PlanAdaptation.assess(f.plan, f.progress)
        val first = assertNotNull(PlanAdaptation.propose(f.plan, assessment, emptyList(), today))

        for (accepted in listOf(false, true)) {
            val resolved = decision(first, accepted)
            val next = assertNotNull(PlanAdaptation.propose(f.plan, assessment, listOf(resolved), today))
            assertEquals(1, next.source.week.number)
            assertEquals(6, next.target.number)
            assertEquals("v1:1:6:EASY,TEMPO,LONG", next.proposalId)

            val second = PlanDecision(1, next.proposalId, accepted, 1, 6, next.sessionTypes)
            assertNull(PlanAdaptation.propose(f.plan, assessment, listOf(resolved, second), today))
        }
    }

    @Test
    fun `target search skips race recovery used and matching-shape weeks`() {
        val f = fixture()
        val sourceTypes = types(f.plan.weeks[1])
        val plan = f.plan.copy(weeks = f.plan.weeks.map { week ->
            if (!week.race && !week.recovery && week.number >= 5 && week.number != 9) withTypes(week, sourceTypes) else week
        })
        val proposal = assertNotNull(PlanAdaptation.propose(plan, PlanAdaptation.assess(plan, progress(plan, 1, 2)), emptyList(), today))
        assertEquals(9, proposal.target.number, "weeks 5-7 match, week 8 recovers, and week 9 is the first different shape")
    }

    @Test
    fun `no target or no differently shaped target means no proposal`() {
        val f = fixture()
        val noTarget = f.plan.copy(weeks = f.plan.weeks.map { week ->
            if (week.number >= 5) week.copy(recovery = !week.race) else week
        })
        assertNull(PlanAdaptation.propose(noTarget, PlanAdaptation.assess(noTarget, progress(noTarget, 1, 2)), emptyList(), today))

        val sourceTypes = types(f.plan.weeks[1])
        val sameShape = f.plan.copy(weeks = f.plan.weeks.map { week ->
            if (!week.race && !week.recovery && week.number >= 5) withTypes(week, sourceTypes) else week
        })
        assertNull(PlanAdaptation.propose(sameShape, PlanAdaptation.assess(sameShape, progress(sameShape, 1, 2)), emptyList(), today))
    }

    @Test
    fun `different source and target session counts never cycle session types`() {
        val baseline = TrainingPlans.build(PlanConfig(PlanGoal.FIVE_K, 3, 6, "2026-11-02", 15.0))
        val plan = baseline.copy(weeks = baseline.weeks.map { week ->
            if (week.number == 5) week.copy(sessions = week.sessions.dropLast(1)) else week
        })
        assertNull(PlanAdaptation.propose(plan, PlanAdaptation.assess(plan, progress(plan, 1, 2)), emptyList(), today))
    }

    @Test
    fun `fixture shows only the accepted target types changing while every other baseline field stays equal`() {
        val f = fixture()
        val assessment = PlanAdaptation.assess(f.plan, f.progress)
        val proposal = assertNotNull(PlanAdaptation.propose(f.plan, assessment, emptyList(), today))
        val accepted = decision(proposal)
        val expectedTarget = withTypes(proposal.target, proposal.sessionTypes)
        val expected = f.plan.copy(weeks = f.plan.weeks.map { if (it.number == expectedTarget.number) expectedTarget else it })

        val applied = PlanAdaptation.apply(f.plan, listOf(accepted))
        assertEquals(expected, applied)
        assertEquals(expectedTarget, applied.weeks.single { it.number == proposal.target.number })
        assertEquals(f.plan.weeks.last(), applied.weeks.last(), "race week is untouched")
        for (week in f.plan.weeks.filter { it.number != proposal.target.number }) {
            assertEquals(week, applied.weeks.single { it.number == week.number })
        }
        assertEquals(f.plan, PlanAdaptation.apply(f.plan, listOf(accepted.copy(accepted = false))))
        assertEquals(applied, PlanAdaptation.apply(applied, listOf(accepted)), "applying twice is idempotent")
        val built = TrainingPlans.build(f.plan.config.copy(decisions = listOf(accepted)))
        assertEquals(applied.weeks, built.weeks)
        assertEquals(listOf(accepted), built.config.decisions)
    }

    @Test
    fun `later decisions can use an adapted target as their source without changing the source again`() {
        val f = fixture()
        val firstTypes = types(f.plan.weeks[1])
        val first = PlanDecision(1, "v1:2:5:EASY,INTERVALS,LONG", true, 2, 5, firstTypes)
        val second = PlanDecision(1, "v1:5:9:EASY,INTERVALS,LONG", true, 5, 9, firstTypes)
        val expected = f.plan.copy(weeks = f.plan.weeks.map { week ->
            when (week.number) {
                5 -> withTypes(week, firstTypes)
                9 -> withTypes(week, firstTypes)
                else -> week
            }
        })

        val applied = PlanAdaptation.apply(f.plan, listOf(second, first))
        assertEquals(listOf(first, second), PlanAdaptation.effectiveDecisions(f.plan, listOf(second, first)))
        assertEquals(expected, applied, "decision list order does not matter")
        assertEquals(applied, PlanAdaptation.apply(applied, listOf(second, first)))
        val built = TrainingPlans.build(f.plan.config.copy(decisions = listOf(second, first)))
        assertEquals(expected.weeks, built.weeks)
    }

    @Test
    fun `effective decisions reject a stale source pattern before it can cancel a valid action`() {
        val f = fixture()
        val proposal = assertNotNull(PlanAdaptation.propose(f.plan, PlanAdaptation.assess(f.plan, f.progress), emptyList(), today))
        val valid = decision(proposal)
        val stale = valid.copy(
            sessionTypes = types(f.plan.weeks[3]),
            proposalId = "v1:2:5:EASY,EASY,LONG",
        )

        assertEquals(emptyList(), PlanAdaptation.effectiveDecisions(f.plan, listOf(stale)))
        assertEquals(listOf(valid), PlanAdaptation.effectiveDecisions(f.plan, listOf(stale, valid)))
        val expected = f.plan.copy(weeks = f.plan.weeks.map { if (it.number == 5) withTypes(it, valid.sessionTypes) else it })
        assertEquals(expected, PlanAdaptation.apply(f.plan, listOf(stale, valid)))
    }

    @Test
    fun `a dismissed predecessor does not adapt the source of a later decision`() {
        val f = fixture()
        val firstTypes = types(f.plan.weeks[1])
        val dismissed = PlanDecision(1, "v1:2:5:EASY,INTERVALS,LONG", false, 2, 5, firstTypes)
        val dependent = PlanDecision(1, "v1:5:9:EASY,INTERVALS,LONG", true, 5, 9, firstTypes)

        assertEquals(listOf(dismissed), PlanAdaptation.effectiveDecisions(f.plan, listOf(dependent, dismissed)))
        assertEquals(f.plan, PlanAdaptation.apply(f.plan, listOf(dependent, dismissed)))
    }

    @Test
    fun `old config JSON has no decisions and valid decisions survive config and backup round trips`() {
        val old = """{"goal":"FIVE_K","runsPerWeek":3,"weeks":12,"start":"2026-11-02","baselineKm":15.0}"""
        assertEquals(emptyList(), assertNotNull(PlanConfig.decode(old)).decisions)

        val f = fixture()
        val proposal = assertNotNull(PlanAdaptation.propose(f.plan, PlanAdaptation.assess(f.plan, f.progress), emptyList(), today))
        val config = f.plan.config.copy(decisions = listOf(decision(proposal)))
        assertEquals(config, PlanConfig.decode(config.encode()))

        val now = Instant.parse("2026-11-25T12:00:00Z")
        val bytes = Backup.write(InMemoryRepository(), now, UserData(trainingPlan = config.encode()))
        val restored = Backup.restore(bytes, InMemoryRepository()).userData
        val decoded = assertNotNull(PlanConfig.decode(restored.trainingPlan))
        assertEquals(config, decoded)
        assertEquals(listOf(decision(proposal)), decoded.decisions)
    }

    @Test
    fun `a malformed persisted decision is ignored without losing the config`() {
        val text = """{"goal":"FIVE_K","runsPerWeek":3,"weeks":12,"start":"2026-11-02","baselineKm":15.0,"decisions":[{"policyVersion":1},42,null]}"""
        assertEquals(fixture().plan.config, PlanConfig.decode(text))
    }

    @Test
    fun `malformed unknown out of range and oversized decisions are harmless`() {
        val f = fixture()
        val assessment = PlanAdaptation.assess(f.plan, f.progress)
        val proposal = assertNotNull(PlanAdaptation.propose(f.plan, assessment, emptyList(), today))
        val valid = decision(proposal)
        val invalid = listOf(
            valid.copy(proposalId = "not-a-proposal-id"),
            valid.copy(proposalId = "x".repeat(10_000)),
            valid.copy(policyVersion = 2),
            valid.copy(sourceWeek = 0),
            valid.copy(sourceWeek = 99),
            valid.copy(targetWeek = 0),
            valid.copy(targetWeek = 99),
            valid.copy(sourceWeek = 5, proposalId = "v1:5:5:EASY,INTERVALS,LONG"),
            valid.copy(targetWeek = 1, proposalId = "v1:2:1:EASY,INTERVALS,LONG"),
            valid.copy(targetWeek = 8, proposalId = "v1:2:8:EASY,INTERVALS,LONG"),
            valid.copy(targetWeek = 12, proposalId = "v1:2:12:EASY,INTERVALS,LONG"),
            valid.copy(sessionTypes = types(f.plan.weeks[0]), proposalId = "v1:2:5:EASY,TEMPO,LONG"),
            valid.copy(sessionTypes = emptyList()),
            valid.copy(sessionTypes = List(2) { SessionType.EASY }, proposalId = "v1:2:5:EASY,EASY"),
            valid.copy(sessionTypes = List(100) { SessionType.EASY }, proposalId = "v1:2:5:${List(100) { "EASY" }.joinToString(",")}"),
        )
        for (bad in invalid) {
            assertEquals(f.plan, PlanAdaptation.apply(f.plan, listOf(bad)), bad.toString())
            assertEquals(proposal, PlanAdaptation.propose(f.plan, assessment, listOf(bad), today), bad.toString())
        }
    }

    @Test
    fun `effective decisions ignore wrong policy rows without blocking later valid rows and preserve usable order`() {
        val f = fixture()
        val proposal = assertNotNull(PlanAdaptation.propose(f.plan, PlanAdaptation.assess(f.plan, f.progress), emptyList(), today))
        val valid = decision(proposal)
        val wrongPolicy = valid.copy(policyVersion = PlanAdaptation.POLICY_VERSION + 1)
        val second = PlanDecision(1, "v1:1:6:EASY,TEMPO,LONG", true, 1, 6, types(f.plan.weeks[0]))

        assertEquals(emptyList(), PlanAdaptation.effectiveDecisions(f.plan, listOf(wrongPolicy)))
        assertEquals(listOf(valid), PlanAdaptation.effectiveDecisions(f.plan, listOf(wrongPolicy, valid)))

        val effective = PlanAdaptation.effectiveDecisions(f.plan, listOf(second, wrongPolicy, valid, second))
        assertEquals(listOf(second, valid), effective, "chronological order, with exact duplicates collapsed")
        assertEquals(valid, effective.single { it.proposalId == proposal.proposalId })
        assertEquals(
            PlanAdaptation.apply(f.plan, listOf(valid, second)),
            PlanAdaptation.apply(f.plan, effective),
            "the returned effective list can be passed back to apply",
        )
    }

    @Test
    fun `exact duplicates deduplicate while conflicting duplicates and week conflicts have no effect`() {
        val f = fixture()
        val plan = f.plan.copy(weeks = f.plan.weeks.map { week ->
            if (week.number == 1) withTypes(week, types(f.plan.weeks[3])) else week
        })
        val assessment = PlanAdaptation.assess(plan, progress(plan, 1, 2, 4, 5, 6, 8, 12))
        val proposal = assertNotNull(PlanAdaptation.propose(plan, assessment, emptyList(), today))
        val accepted = decision(proposal)
        val applied = PlanAdaptation.apply(plan, listOf(accepted))
        assertEquals(listOf(accepted), PlanAdaptation.effectiveDecisions(plan, listOf(accepted, accepted)))
        assertEquals(applied, PlanAdaptation.apply(plan, listOf(accepted, accepted)))

        val conflictingId = listOf(accepted, accepted.copy(accepted = false))
        assertEquals(emptyList(), PlanAdaptation.effectiveDecisions(plan, conflictingId))
        assertEquals(plan, PlanAdaptation.apply(plan, conflictingId))
        assertEquals(proposal, PlanAdaptation.propose(plan, assessment, conflictingId, today))

        val conflictingTarget = PlanDecision(1, "v1:1:5:EASY,EASY,LONG", true, 1, 5, types(plan.weeks[0]))
        val conflictingSource = PlanDecision(1, "v1:2:9:EASY,INTERVALS,LONG", true, 2, 9, types(plan.weeks[1]))
        for (conflict in listOf(listOf(accepted, conflictingTarget), listOf(accepted, conflictingSource))) {
            assertEquals(emptyList(), PlanAdaptation.effectiveDecisions(plan, conflict))
            assertEquals(plan, PlanAdaptation.apply(plan, conflict))
            assertEquals(proposal, PlanAdaptation.propose(plan, assessment, conflict, today))
        }
    }
}
