package nl.tufein.openconnect.core

import java.time.LocalDate

/** All weeks whose progress says they are behind, including weeks a proposal cannot use as a source. */
data class PlanAssessment(val behind: List<WeekProgress>)

/** One immutable suggestion. The target is still the baseline week; [sessionTypes] is the source's ordered pattern. */
data class PlanProposal(
    val proposalId: String,
    val source: WeekProgress,
    val target: PlanWeek,
    val sessionTypes: List<SessionType>,
)

/** Deterministic policy v1 for moving a missed week's ordered session types to a future training week. */
object PlanAdaptation {
    const val POLICY_VERSION: Int = 1

    fun assess(plan: TrainingPlan, progress: List<WeekProgress>): PlanAssessment =
        PlanAssessment(progress.filter { it.state == WeekState.BEHIND })

    fun propose(
        plan: TrainingPlan,
        assessment: PlanAssessment,
        decisions: List<PlanDecision>,
        today: LocalDate,
    ): PlanProposal? {
        val monday = TrainingPlans.mondayOf(today)
        val persisted = effectiveDecisions(plan, decisions).filter { decision ->
            val source = plan.week(decision.sourceWeek)
            source != null && source.start.isBefore(monday)
        }
        val resolvedSources = persisted.mapTo(HashSet()) { it.sourceWeek }
        val resolvedTargets = persisted.mapTo(HashSet()) { it.targetWeek }

        val source = assessment.behind.asSequence()
            .filter { it.state == WeekState.BEHIND }
            .filter { it.week.start.isBefore(monday) && !it.week.race }
            .filter { it.week.number !in resolvedSources }
            .maxWithOrNull(compareBy<WeekProgress> { it.week.start }.thenBy { it.week.number })
            ?: return null
        val plannedSource = plan.week(source.week.number)
        if (plannedSource != source.week) return null
        val sessionTypes = plannedSource.sessionTypes()

        val target = plan.weeks.asSequence()
            .filter { it.start.isAfter(monday) && !it.race && !it.recovery }
            .filter { it.number !in resolvedTargets }
            .filter { it.sessions.size == sessionTypes.size }
            .filter { it.sessionTypes() != sessionTypes }
            .minWithOrNull(compareBy<PlanWeek> { it.start }.thenBy { it.number })
            ?: return null

        return PlanProposal(
            proposalId = proposalId(source.week.number, target.number, sessionTypes),
            source = source,
            target = target,
            sessionTypes = sessionTypes,
        )
    }

    fun apply(plan: TrainingPlan, decisions: List<PlanDecision>): TrainingPlan {
        val accepted = effectiveDecisions(plan, decisions).filter { it.accepted }
        if (accepted.isEmpty()) return plan

        val weeks = plan.weeks.toMutableList()
        for (decision in accepted) {
            val targetIndex = weeks.indexOfFirst { it.number == decision.targetWeek }
            if (targetIndex < 0) continue
            val target = weeks[targetIndex]
            weeks[targetIndex] = target.copy(
                sessions = target.sessions.mapIndexed { index, session ->
                    session.copy(type = decision.sessionTypes[index])
                },
            )
        }
        return if (weeks == plan.weeks) plan else plan.copy(weeks = weeks.toList())
    }

    /**
     * The rows that policy v1 actually recognizes, in the chronological order used by [apply]. Exact duplicates
     * collapse, accepted predecessors adapt the replay plan, and dismissed rows do not. Stale rows are excluded before
     * conflict resolution; every row involved in an ID/source/target conflict is removed and replay repeats until stable.
     */
    fun effectiveDecisions(plan: TrainingPlan, decisions: List<PlanDecision>): List<PlanDecision> {
        val candidates = decisions.filter { decision ->
            val source = plan.week(decision.sourceWeek) ?: return@filter false
            val target = plan.week(decision.targetWeek) ?: return@filter false
            decision.policyVersion == POLICY_VERSION &&
                source.start.isBefore(target.start) &&
                !source.race &&
                !target.race &&
                !target.recovery &&
                source.sessions.size == target.sessions.size &&
                decision.sessionTypes.size == source.sessions.size &&
                decision.proposalId == proposalId(decision.sourceWeek, decision.targetWeek, decision.sessionTypes)
        }
        val uniqueIds = candidates.groupBy { it.proposalId }.values.mapNotNull { rows ->
            rows.distinct().singleOrNull()
        }

        var remaining = uniqueIds
        while (true) {
            val replayed = replay(plan, remaining)
            val sourceUses = replayed.groupingBy { it.sourceWeek }.eachCount()
            val targetUses = replayed.groupingBy { it.targetWeek }.eachCount()
            val conflicting = replayed.filter { sourceUses[it.sourceWeek] != 1 || targetUses[it.targetWeek] != 1 }
            if (conflicting.isEmpty()) return replayed
            // Keep stale rows for the next replay: removing an accepted conflict can change their source pattern.
            val conflictIds = conflicting.mapTo(HashSet()) { it.proposalId }
            remaining = remaining.filter { it.proposalId !in conflictIds }
        }
    }

    private fun replay(plan: TrainingPlan, decisions: List<PlanDecision>): List<PlanDecision> {
        val weeks = plan.weeks.toMutableList()
        val ordered = decisions.sortedWith(
            compareBy<PlanDecision>(
                { plan.week(it.sourceWeek)?.start ?: LocalDate.MAX },
                { it.sourceWeek },
                { plan.week(it.targetWeek)?.start ?: LocalDate.MAX },
                { it.targetWeek },
                { it.proposalId },
            ),
        )
        return ordered.filter { decision ->
            val sourceIndex = weeks.indexOfFirst { it.number == decision.sourceWeek }
            val targetIndex = weeks.indexOfFirst { it.number == decision.targetWeek }
            if (sourceIndex < 0 || targetIndex < 0) return@filter false
            val source = weeks[sourceIndex]
            if (source.sessionTypes() != decision.sessionTypes) return@filter false
            if (decision.accepted) {
                val target = weeks[targetIndex]
                weeks[targetIndex] = target.copy(
                    sessions = target.sessions.mapIndexed { index, session ->
                        session.copy(type = decision.sessionTypes[index])
                    },
                )
            }
            true
        }
    }

    private fun proposalId(sourceWeek: Int, targetWeek: Int, sessionTypes: List<SessionType>): String =
        "v$POLICY_VERSION:$sourceWeek:$targetWeek:${sessionTypes.joinToString(",") { it.name }}"

    private fun PlanWeek.sessionTypes(): List<SessionType> = sessions.map { it.type }

    private fun TrainingPlan.week(number: Int): PlanWeek? = weeks.singleOrNull { it.number == number }
}
