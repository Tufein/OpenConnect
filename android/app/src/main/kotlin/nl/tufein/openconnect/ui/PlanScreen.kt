package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.PlanGoal
import nl.tufein.openconnect.core.PlannedSession
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
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        val plan = state.plan
        val progress = state.progress
        if (plan == null || progress == null) {
            item { PlanSetup(state, onCreate) }
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
            Text(stringResource(R.string.plan_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.plan_delete)) }
        }
    }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = goal == PlanGoal.FIVE_K, onClick = { goal = PlanGoal.FIVE_K }, label = { Text("5K") })
            FilterChip(selected = goal == PlanGoal.TEN_K, onClick = { goal = PlanGoal.TEN_K }, label = { Text("10K") })
        }
        SectionTitle(stringResource(R.string.plan_runs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 2..4) FilterChip(selected = runs == n, onClick = { runs = n }, label = { Text("$n×") })
        }
        SectionTitle(stringResource(R.string.plan_length))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in listOf(6, 8, 10, 12)) FilterChip(selected = weeks == n, onClick = { weeks = n }, label = { Text("$n") })
        }
        Text(
            stringResource(R.string.plan_starts, Fmt.date(nl.tufein.openconnect.core.TrainingPlans.nextMonday(state.today))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onCreate(goal, runs, weeks) }) { Text(stringResource(R.string.plan_create)) }
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
        SegmentBar(fraction = if (w.week.volumeKm <= 0) 0f else (w.doneKm / w.week.volumeKm).coerceIn(0.0, 1.0).toFloat(), color = color)
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
