package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.OwnWorkout
import nl.tufein.openconnect.core.PlanGoal
import nl.tufein.openconnect.core.PlanProposal
import nl.tufein.openconnect.core.PlannedSession
import nl.tufein.openconnect.core.RaceDistance
import nl.tufein.openconnect.core.RacePrediction
import nl.tufein.openconnect.core.RaceState
import nl.tufein.openconnect.core.RaceVerdict
import nl.tufein.openconnect.core.Reliability
import nl.tufein.openconnect.core.SessionType
import nl.tufein.openconnect.core.WeekProgress
import nl.tufein.openconnect.core.WeekState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Plan tab: choose a 5K or 10K plan, or follow the one you made. */
@Composable
fun PlanContent(
    state: PlanState,
    onCreate: (PlanGoal, Int, Int) -> Unit,
    onDelete: () -> Unit,
    onRace: (RaceAction) -> Unit,
    onSendWorkouts: () -> Unit,
    onSaveWorkout: (OwnWorkout) -> Unit,
    onDeleteWorkout: (String) -> Unit,
    contentPadding: PaddingValues,
    onApplyPlanChange: (String) -> Unit = {},
    onDismissPlanChange: (String) -> Unit = {},
) {
    // Creating or deleting the plan changes the list a lot: start from the top again instead of landing somewhere in the middle.
    val listState = remember(state.plan == null) { LazyListState() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        item { RaceCard(state.race, onRace) }
        item { OwnWorkoutsCard(state.ownWorkouts, onSaveWorkout, onDeleteWorkout) }
        val plan = state.plan
        val progress = state.progress
        if (plan == null || progress == null) {
            item { PlanSetup(state, onCreate) }
            analysisItems(state)
            return@LazyColumn
        }
        item {
            SurfaceCard(accent = OpenConnectColors.metrics.distance) {
                val title = stringResource(if (plan.config.goal == PlanGoal.FIVE_K) R.string.plan_title_5k else R.string.plan_title_10k)
                Text(title, style = MaterialTheme.typography.titleMedium)
                val status = when {
                    progress.finished -> stringResource(R.string.plan_finished, Fmt.date(plan.raceDate))
                    progress.notStarted -> stringResource(R.string.plan_not_started, Fmt.date(plan.start))
                    else -> progress.current?.let { stringResource(R.string.plan_week_x_of_y, it.week.number, plan.weeks.size) }.orEmpty()
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.plan_race_on, Fmt.date(plan.raceDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { PlanAdjustmentCard(state, onApplyPlanChange, onDismissPlanChange) }
        item {
            FoldableCard("plan-next", stringResource(R.string.plan_next)) {
                val next = progress.next
                if (next == null) Text(stringResource(R.string.plan_next_none), style = MaterialTheme.typography.bodyMedium) else SessionRow(next, detailed = true)
            }
        }
        progress.current?.let { current ->
            item {
                FoldableCard("plan-week", stringResource(R.string.plan_this_week)) {
                    for (s in current.week.sessions) SessionRow(s, detailed = false)
                    Text(
                        stringResource(R.string.plan_km_of, Fmt.oneDecimal(current.doneKm), Fmt.oneDecimal(current.week.volumeKm)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SectionTitle(stringResource(R.string.plan_all_weeks)) }
        items(progress.weeks.size) { i -> WeekRow(progress.weeks[i]) }
        item {
            FoldableCard("plan-workouts", stringResource(R.string.workouts_title)) {
                Text(stringResource(R.string.workouts_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton(onClick = onSendWorkouts, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.workouts_send)) }
            }
        }
        item {
            Text(stringResource(R.string.plan_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SecondaryButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.plan_delete)) }
        }
        analysisItems(state)
    }
}

private data class PendingPlanAction(
    val actionId: String,
    val proposalId: String,
    val decisionIds: List<String>,
)

/**
 * The honest adherence check and, when the app has one, a single suggested pattern change. Nothing is applied here:
 * the action token goes back to the app, which stores the choice and reloads this card with the new assessment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanAdjustmentCard(
    state: PlanState,
    onApply: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val proposal = state.proposal
    val decisionIds = state.plan?.config?.decisions.orEmpty().map { it.proposalId }
    var pending by remember { mutableStateOf<PendingPlanAction?>(null) }
    var observedBusy by remember { mutableStateOf(false) }
    // A rejected/stale action reports actionFailed; a successful decision changes the proposal or decision list.
    // Either outcome releases the local guard. A rotated action token also means the old request is no longer valid.
    LaunchedEffect(state.actionBusy, state.actionFailed, state.proposalActionId, proposal?.proposalId, decisionIds) {
        if (state.actionBusy) {
            observedBusy = true
        } else if (pending != null && (
            observedBusy || state.actionFailed || state.proposalActionId != pending?.actionId ||
            decisionIds != pending?.decisionIds || proposal?.proposalId != pending?.proposalId
        )) {
            pending = null
            observedBusy = false
        }
    }
    val actionId = state.proposalActionId
    val busyState = stringResource(R.string.plan_adjustment_busy_state)
    FoldableCard(
        "plan-adjustment",
        stringResource(R.string.plan_adjustment_title),
        modifier = Modifier.testTag("plan-adjustment-card"),
        accent = MaterialTheme.colorScheme.primary,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.plan_adjustment_intro), style = MaterialTheme.typography.bodyMedium)
            state.progress?.current?.let { current ->
                PlanAdherenceLine(stringResource(R.string.plan_adjustment_current_week, current.week.number), current)
            }
            for (missed in state.assessment.behind) {
                PlanAdherenceLine(stringResource(R.string.plan_adjustment_missed_week, missed.week.number), missed)
            }
            if (state.actionBusy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelSpinner(Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.plan_adjustment_busy),
                        modifier = Modifier.semantics {
                            stateDescription = busyState
                            liveRegion = LiveRegionMode.Polite
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            when {
                proposal != null -> PlanProposalCard(
                    proposal = proposal,
                    actionId = actionId,
                    busy = state.actionBusy,
                    pending = pending != null,
                    busyState = busyState,
                    onApply = { id ->
                        if (!state.actionBusy && pending == null) {
                            pending = PendingPlanAction(id, proposal.proposalId, decisionIds)
                            onApply(id)
                        }
                    },
                    onDismiss = { id ->
                        if (!state.actionBusy && pending == null) {
                            pending = PendingPlanAction(id, proposal.proposalId, decisionIds)
                            onDismiss(id)
                        }
                    },
                )
                state.assessment.behind.isEmpty() -> Text(
                    stringResource(R.string.plan_adjustment_on_track),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    stringResource(R.string.plan_adjustment_no_proposal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.actionFailed && !state.actionBusy) {
                Text(
                    stringResource(R.string.plan_adjustment_failed),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PlanAdherenceLine(label: String, progress: WeekProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.plan_adjustment_adherence, Fmt.oneDecimal(progress.week.volumeKm), Fmt.oneDecimal(progress.doneKm)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanProposalCard(
    proposal: PlanProposal,
    actionId: String?,
    busy: Boolean,
    pending: Boolean,
    busyState: String,
    onApply: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.plan_adjustment_weeks, proposal.source.week.number, proposal.target.number),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.plan_adjustment_change), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(
                R.string.plan_adjustment_old_pattern,
                proposal.target.number,
                sessionPattern(proposal.target.sessions.map { it.type }),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.plan_adjustment_new_pattern, sessionPattern(proposal.sessionTypes)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.plan_adjustment_unchanged),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                onClick = { actionId?.let(onApply) },
                enabled = !busy && !pending && actionId != null,
                modifier = Modifier.semantics { if (busy) stateDescription = busyState },
            ) {
                Text(stringResource(R.string.plan_adjustment_apply))
            }
            SecondaryButton(
                onClick = { actionId?.let(onDismiss) },
                enabled = !busy && !pending && actionId != null,
                modifier = Modifier.semantics { if (busy) stateDescription = busyState },
            ) {
                Text(stringResource(R.string.plan_adjustment_not_now))
            }
        }
    }
}

/** Work-outs made in the app: a warm-up, a number of fast repeats with rest, and a cool-down. They go to the watch with the plan. */
@Composable
private fun OwnWorkoutsCard(workouts: List<OwnWorkout>, onSave: (OwnWorkout) -> Unit, onDelete: (String) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var repeatsText by rememberSaveable { mutableStateOf("4") }
    var fastText by rememberSaveable { mutableStateOf("4") }
    var recoveryText by rememberSaveable { mutableStateOf("90") }
    var warmupText by rememberSaveable { mutableStateOf("10") }
    var cooldownText by rememberSaveable { mutableStateOf("5") }
    var distanceText by rememberSaveable { mutableStateOf("") }
    var paceText by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }

    fun open(workout: OwnWorkout) {
        editingId = workout.id
        name = workout.name
        repeatsText = workout.repeats.toString()
        fastText = minutesText(workout.fastS)
        recoveryText = workout.recoveryS.toString()
        warmupText = minutesText(workout.warmupS)
        cooldownText = minutesText(workout.cooldownS)
        distanceText = workout.fastMeters?.toString().orEmpty()
        paceText = if (workout.paceFastS != null && workout.paceSlowS != null) {
            if (workout.paceFastS == workout.paceSlowS) "${workout.paceFastS}" else "${workout.paceFastS}-${workout.paceSlowS}"
        } else {
            ""
        }
        adding = true
        tried = false
    }

    fun clear() {
        editingId = null
        name = ""
        repeatsText = "4"
        fastText = "4"
        recoveryText = "90"
        warmupText = "10"
        cooldownText = "5"
        distanceText = ""
        paceText = ""
        adding = false
        tried = false
    }

    val repeats = repeatsText.trim().toIntOrNull()
    val fastMin = fastText.trim().replace(',', '.').toDoubleOrNull()
    val recoveryS = recoveryText.trim().toIntOrNull()
    val warmupMin = warmupText.trim().replace(',', '.').toDoubleOrNull()
    val cooldownMin = cooldownText.trim().replace(',', '.').toDoubleOrNull()
    val meters = distanceText.trim().toIntOrNull()
    val pace = parsePace(paceText)
    val paceBad = paceText.isNotBlank() && pace == null
    val draft = if (repeats != null && recoveryS != null && warmupMin != null && cooldownMin != null &&
        (meters != null || fastMin != null) && !paceBad
    ) {
        OwnWorkout(
            id = editingId.orEmpty(),
            name = name,
            warmupS = (warmupMin * 60).toInt(),
            fastS = ((fastMin ?: 4.0) * 60).toInt(),
            recoveryS = recoveryS,
            repeats = repeats,
            cooldownS = (cooldownMin * 60).toInt(),
            fastMeters = meters,
            paceFastS = pace?.first,
            paceSlowS = pace?.second,
        )
    } else {
        null
    }
    val problem: Int? = when {
        draft == null -> R.string.own_error_range
        draft.problem == OwnWorkout.Problem.NAME -> R.string.own_error_name
        draft.problem == OwnWorkout.Problem.PACE -> R.string.own_error_pace
        draft.problem == OwnWorkout.Problem.FAST_METERS -> R.string.own_error_distance
        draft.problem != null -> R.string.own_error_range
        else -> null
    }

    FoldableCard("plan-own", stringResource(R.string.own_title)) {
        Text(stringResource(R.string.own_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        workouts.forEach { workout ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(workout.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        workout.fastMeters?.let {
                            stringResource(R.string.own_summary_distance, workout.repeats, it, workout.totalS / 60)
                        } ?: stringResource(R.string.own_summary, workout.repeats, workout.fastS / 60, workout.totalS / 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(onClick = { open(workout) }) { Text(stringResource(R.string.own_edit)) }
                    SecondaryButton(onClick = { onDelete(workout.id) }) { Text(stringResource(R.string.own_delete)) }
                }
            }
        }
        if (!adding) {
            SecondaryButton(onClick = { clear(); adding = true }) { Text(stringResource(R.string.own_add)) }
        } else {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.own_name)) },
            )
            OutlinedTextField(
                value = repeatsText,
                onValueChange = { repeatsText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.own_repeats)) },
            )
            OutlinedTextField(
                value = fastText,
                onValueChange = { fastText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.own_fast)) },
            )
            OutlinedTextField(
                value = distanceText,
                onValueChange = { distanceText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.own_distance)) },
            )
            OutlinedTextField(
                value = paceText,
                onValueChange = { paceText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.own_pace)) },
            )
            OutlinedTextField(
                value = recoveryText,
                onValueChange = { recoveryText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.own_recovery)) },
            )
            OutlinedTextField(
                value = warmupText,
                onValueChange = { warmupText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.own_warmup)) },
            )
            OutlinedTextField(
                value = cooldownText,
                onValueChange = { cooldownText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.own_cooldown)) },
            )
            if (tried && problem != null) {
                Text(stringResource(problem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    onClick = {
                        if (draft != null && problem == null) {
                            onSave(draft.copy(id = editingId ?: OwnWorkout.id(java.time.Instant.now())))
                            clear()
                        } else {
                            tried = true
                        }
                    },
                ) {
                    Text(stringResource(R.string.own_save))
                }
                SecondaryButton(onClick = { clear() }) { Text(stringResource(R.string.own_cancel)) }
            }
        }
    }
}

/** "330" or "300-330" as a fast and slow pace in seconds per kilometre; null when the text is neither. */
private fun parsePace(text: String): Pair<Int, Int>? {
    val parts = text.trim().split('-', '–').map { it.trim() }.filter { it.isNotEmpty() }
    return when (parts.size) {
        1 -> parts[0].toIntOrNull()?.let { it to it }
        2 -> {
            val fast = parts[0].toIntOrNull()
            val slow = parts[1].toIntOrNull()
            if (fast != null && slow != null) fast to slow else null
        }
        else -> null
    }
}

/** Minutes as the form shows them: "10", or "3.5" when it is not a whole minute. */
private fun minutesText(seconds: Int): String =
    if (seconds % 60 == 0) (seconds / 60).toString() else "%.1f".format(seconds / 60.0)

/** What the last weeks of running say: the shape for each distance and the VO2max. They come after the plan, which is the main thing here. */
private fun LazyListScope.analysisItems(state: PlanState) {
    state.season?.let { season -> item { SeasonCard(season, state.baselineKm) } }
    if (state.shapes.isNotEmpty()) item { ShapeCard(state.shapes, state.race.outlook?.goal?.distance) }
    state.vo2?.let { vo2 -> item { Vo2Card(vo2) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanSetup(state: PlanState, onCreate: (PlanGoal, Int, Int) -> Unit) {
    var goal by rememberSaveable { mutableStateOf(PlanGoal.FIVE_K) }
    var runs by rememberSaveable { mutableIntStateOf(3) }
    var weeks by rememberSaveable { mutableIntStateOf(8) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SurfaceCard {
            Text(stringResource(R.string.plan_intro), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.plan_baseline, Fmt.oneDecimal(state.baselineKm)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionTitle(stringResource(R.string.plan_goal))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelChip(selected = goal == PlanGoal.FIVE_K, onClick = { goal = PlanGoal.FIVE_K }, label = { Text("5K") })
            PixelChip(selected = goal == PlanGoal.TEN_K, onClick = { goal = PlanGoal.TEN_K }, label = { Text("10K") })
        }
        SectionTitle(stringResource(R.string.plan_runs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (n in 2..4) PixelChip(selected = runs == n, onClick = { runs = n }, label = { Text("$n×") })
        }
        SectionTitle(stringResource(R.string.plan_length))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (n in listOf(6, 8, 10, 12)) PixelChip(selected = weeks == n, onClick = { weeks = n }, label = { Text("$n") })
        }
        Text(
            stringResource(R.string.plan_starts, Fmt.date(nl.tufein.openconnect.core.TrainingPlans.nextMonday(state.today))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(onClick = { onCreate(goal, runs, weeks) }) { Text(stringResource(R.string.plan_create)) }
    }
}

@Composable
private fun SessionRow(s: PlannedSession, detailed: Boolean) {
    val (name, hint) = sessionText(s.type)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dayLabel(s.date) + " · " + stringResource(name), style = MaterialTheme.typography.bodyMedium)
            Text(Fmt.oneDecimal(s.distanceKm) + " km", style = MaterialTheme.typography.bodyMedium)
        }
        if (detailed) Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekRow(w: WeekProgress) {
    val colors = OpenConnectColors.metrics
    val color = when (w.state) {
        WeekState.DONE -> colors.steps
        WeekState.CURRENT -> colors.distance
        WeekState.BEHIND -> colors.stress
        WeekState.UPCOMING -> MaterialTheme.colorScheme.outline
    }
    val state = when (w.state) {
        WeekState.DONE -> R.string.plan_state_done
        WeekState.CURRENT -> R.string.plan_state_current
        WeekState.BEHIND -> R.string.plan_state_behind
        WeekState.UPCOMING -> R.string.plan_state_upcoming
    }
    val stateName = stringResource(state)
    val description = stringResource(
        R.string.plan_adjustment_week_description,
        w.week.number,
        stateName,
        Fmt.oneDecimal(w.week.volumeKm),
        Fmt.oneDecimal(w.doneKm),
        sessionPattern(w.week.sessions.map { it.type }),
    )
    SurfaceCard(
        modifier = Modifier
            .testTag("plan-week-${w.week.number}")
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val tag = when {
                w.week.race -> " · " + stringResource(R.string.plan_race_week)
                w.week.recovery -> " · " + stringResource(R.string.plan_light_week)
                else -> ""
            }
            Text(stringResource(R.string.plan_week_row, w.week.number) + tag, style = MaterialTheme.typography.bodyMedium)
            Text(stateName, style = MaterialTheme.typography.labelMedium, color = color)
        }
        SegmentBar(fraction = if (w.week.volumeKm <= 0) 0f else (w.doneKm / w.week.volumeKm).coerceIn(0.0, 1.0).toFloat(), color = color, modifier = Modifier.fillMaxWidth().height(10.dp))
        Text(
            stringResource(R.string.plan_km_of, Fmt.oneDecimal(w.doneKm), Fmt.oneDecimal(w.week.volumeKm)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun sessionText(type: SessionType): Pair<Int, Int> = when (type) {
    SessionType.EASY -> R.string.session_easy to R.string.session_easy_hint
    SessionType.LONG -> R.string.session_long to R.string.session_long_hint
    SessionType.TEMPO -> R.string.session_tempo to R.string.session_tempo_hint
    SessionType.INTERVALS -> R.string.session_intervals to R.string.session_intervals_hint
    SessionType.RACE -> R.string.session_race to R.string.session_race_hint
}

/** The order is part of the suggestion: the first type belongs to the first session, and so on. */
@Composable
private fun sessionPattern(types: List<SessionType>): String {
    val names = types.map { stringResource(sessionText(it).first) }
    return names.joinToString(" → ")
}

private fun dayLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

/** What the person can change about the race goal on the Plan tab. */
sealed interface RaceAction {
    data class Start(val distance: RaceDistance) : RaceAction
    data class ShiftDate(val weeks: Long) : RaceAction
    data class ShiftTarget(val seconds: Int) : RaceAction
    data object Clear : RaceAction
}

private fun raceDistanceName(d: RaceDistance): Int = when (d) {
    RaceDistance.K5 -> R.string.race_d_5k
    RaceDistance.K10 -> R.string.race_d_10k
    RaceDistance.HALF -> R.string.race_d_half
    RaceDistance.MARATHON -> R.string.race_d_marathon
}

private fun reliabilityName(r: Reliability): Int = when (r) {
    Reliability.EXACT -> R.string.race_rel_exact
    Reliability.GOOD -> R.string.race_rel_good
    Reliability.ROUGH -> R.string.race_rel_rough
    Reliability.WEAK -> R.string.race_rel_weak
}

@Composable
private fun PredictionRow(p: RacePrediction) {
    Text(
        stringResource(
            R.string.race_row,
            stringResource(raceDistanceName(p.distance)),
            Fmt.duration(p.seconds),
            Fmt.pace(p.paceSecondsPerKm),
            stringResource(reliabilityName(p.reliability)),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Predicted race times from your best efforts and, once you set one, a race goal with its countdown and verdict. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaceCard(race: RaceState, onRace: (RaceAction) -> Unit) {
    var distance by rememberSaveable { mutableStateOf(RaceDistance.K10.key) }
    FoldableCard("plan-race", stringResource(R.string.race_title)) {
        val outlook = race.outlook
        if (outlook == null) {
            Text(stringResource(R.string.race_intro), style = MaterialTheme.typography.bodyMedium)
            if (race.predictions.isEmpty()) {
                Text(stringResource(R.string.race_no_runs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (p in race.predictions) PredictionRow(p)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (d in RaceDistance.entries) {
                    PixelChip(selected = distance == d.key, onClick = { distance = d.key }, label = { Text(stringResource(raceDistanceName(d))) })
                }
            }
            PrimaryButton(onClick = { onRace(RaceAction.Start(RaceDistance.of(distance) ?: RaceDistance.K10)) }) {
                Text(stringResource(R.string.race_set))
            }
        } else {
            val goal = outlook.goal
            Text(
                stringResource(R.string.race_goal_line, stringResource(raceDistanceName(goal.distance)), Fmt.date(goal.date)),
                style = MaterialTheme.typography.titleMedium,
            )
            val days = outlook.daysLeft
            Text(
                when {
                    days > 0 -> stringResource(R.string.race_countdown, days, days / 7)
                    days == 0L -> stringResource(R.string.race_today)
                    else -> stringResource(R.string.race_past, -days)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.race_target, Fmt.duration(goal.targetSeconds.toDouble()), Fmt.pace(goal.targetPaceSecondsPerKm())),
                style = MaterialTheme.typography.bodyMedium,
            )
            val predicted = outlook.predicted
            val gap = outlook.gapSeconds
            Text(
                when (outlook.verdict) {
                    RaceVerdict.AHEAD -> stringResource(R.string.race_verdict_ahead, Fmt.duration(predicted?.seconds ?: 0.0))
                    RaceVerdict.ON_TRACK -> stringResource(R.string.race_verdict_on_track, Fmt.duration(predicted?.seconds ?: 0.0))
                    RaceVerdict.BEHIND -> stringResource(R.string.race_verdict_behind, Fmt.duration(predicted?.seconds ?: 0.0), Fmt.duration(gap ?: 0.0))
                    RaceVerdict.NO_DATA -> stringResource(R.string.race_verdict_no_data)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(onClick = { onRace(RaceAction.ShiftDate(-1)) }) { Text(stringResource(R.string.race_date_minus)) }
                SecondaryButton(onClick = { onRace(RaceAction.ShiftDate(1)) }) { Text(stringResource(R.string.race_date_plus)) }
                SecondaryButton(onClick = { onRace(RaceAction.ShiftTarget(-60)) }) { Text(stringResource(R.string.race_time_minus)) }
                SecondaryButton(onClick = { onRace(RaceAction.ShiftTarget(60)) }) { Text(stringResource(R.string.race_time_plus)) }
            }
            for (p in race.predictions) PredictionRow(p)
            SecondaryButton(onClick = { onRace(RaceAction.Clear) }) { Text(stringResource(R.string.race_clear)) }
        }
    }
}
