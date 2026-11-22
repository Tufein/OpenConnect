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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.OwnWorkout
import nl.tufein.openconnect.core.PlanGoal
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
        item {
            SectionTitle(stringResource(R.string.plan_next))
            SurfaceCard {
                val next = progress.next
                if (next == null) Text(stringResource(R.string.plan_next_none), style = MaterialTheme.typography.bodyMedium) else SessionRow(next, detailed = true)
            }
        }
        progress.current?.let { current ->
            item {
                SectionTitle(stringResource(R.string.plan_this_week))
                SurfaceCard {
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
            SectionTitle(stringResource(R.string.workouts_title))
            SurfaceCard {
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

/** Work-outs made in the app: a warm-up, a number of fast repeats with rest, and a cool-down. They go to the watch with the plan. */
@Composable
private fun OwnWorkoutsCard(workouts: List<OwnWorkout>, onSave: (OwnWorkout) -> Unit, onDelete: (String) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var repeatsText by rememberSaveable { mutableStateOf("4") }
    var fastText by rememberSaveable { mutableStateOf("4") }
    var recoveryText by rememberSaveable { mutableStateOf("90") }
    var warmupText by rememberSaveable { mutableStateOf("10") }
    var cooldownText by rememberSaveable { mutableStateOf("5") }
    var tried by rememberSaveable { mutableStateOf(false) }

    val repeats = repeatsText.trim().toIntOrNull()
    val fastMin = fastText.trim().replace(',', '.').toDoubleOrNull()
    val recoveryS = recoveryText.trim().toIntOrNull()
    val warmupMin = warmupText.trim().replace(',', '.').toDoubleOrNull()
    val cooldownMin = cooldownText.trim().replace(',', '.').toDoubleOrNull()
    val draft = if (repeats != null && fastMin != null && recoveryS != null && warmupMin != null && cooldownMin != null) {
        OwnWorkout(
            id = "",
            name = name,
            warmupS = (warmupMin * 60).toInt(),
            fastS = (fastMin * 60).toInt(),
            recoveryS = recoveryS,
            repeats = repeats,
            cooldownS = (cooldownMin * 60).toInt(),
        )
    } else {
        null
    }
    val problem: Int? = when {
        draft == null -> R.string.own_error_range
        draft.problem == OwnWorkout.Problem.NAME -> R.string.own_error_name
        draft.problem != null -> R.string.own_error_range
        else -> null
    }

    SectionTitle(stringResource(R.string.own_title))
    SurfaceCard {
        Text(stringResource(R.string.own_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        workouts.forEach { workout ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(workout.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.own_summary, workout.repeats, workout.fastS / 60, workout.totalS / 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SecondaryButton(onClick = { onDelete(workout.id) }) { Text(stringResource(R.string.own_delete)) }
            }
        }
        if (!adding) {
            SecondaryButton(onClick = { adding = true }) { Text(stringResource(R.string.own_add)) }
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
                            onSave(draft.copy(id = OwnWorkout.id(java.time.Instant.now())))
                            adding = false
                            name = ""
                            tried = false
                        } else {
                            tried = true
                        }
                    },
                ) {
                    Text(stringResource(R.string.own_save))
                }
                SecondaryButton(onClick = { adding = false; tried = false }) { Text(stringResource(R.string.own_cancel)) }
            }
        }
    }
}

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
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val tag = when {
                w.week.race -> " · " + stringResource(R.string.plan_race_week)
                w.week.recovery -> " · " + stringResource(R.string.plan_light_week)
                else -> ""
            }
            Text(stringResource(R.string.plan_week_row, w.week.number) + tag, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(state), style = MaterialTheme.typography.labelMedium, color = color)
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
    SectionTitle(stringResource(R.string.race_title))
    SurfaceCard {
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
