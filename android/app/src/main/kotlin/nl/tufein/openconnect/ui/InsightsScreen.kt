package nl.tufein.openconnect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.ai.ActiveAi
import nl.tufein.openconnect.ai.CoachUi
import nl.tufein.openconnect.core.RecordEntry
import nl.tufein.openconnect.core.Sport

/** The three things under the Insights tab. */
enum class InsightsTab(val label: Int) {
    Week(R.string.insights_week),
    Trophies(R.string.insights_trophies),
    Coach(R.string.insights_coach),
}

@Composable
fun InsightsScreen(
    tab: InsightsTab,
    onTab: (InsightsTab) -> Unit,
    reports: ReportsState,
    busy: Boolean,
    active: ActiveAi,
    trophies: TrophiesState,
    coach: CoachUi,
    onShiftWeek: (Long) -> Unit,
    onGenerate: (force: Boolean) -> Unit,
    onOpenActivity: (String) -> Unit,
    onAsk: (String) -> Unit,
    onClearCoach: () -> Unit,
    onOpenAiSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tab_insights), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (entry in InsightsTab.entries) {
                    FilterChip(selected = tab == entry, onClick = { onTab(entry) }, label = { Text(stringResource(entry.label)) })
                }
            }
        }
        when (tab) {
            InsightsTab.Week -> WeekContent(reports, busy, active, onShiftWeek, onGenerate, onOpenActivity, contentPadding)
            InsightsTab.Trophies -> TrophiesContent(trophies, contentPadding)
            InsightsTab.Coach -> CoachContent(coach, active, onAsk, onClearCoach, onOpenAiSettings, contentPadding)
        }
    }
}

@Composable
private fun WeekContent(
    state: ReportsState,
    busy: Boolean,
    active: ActiveAi,
    onShiftWeek: (Long) -> Unit,
    onGenerate: (force: Boolean) -> Unit,
    onOpenActivity: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val colors = OpenConnectColors.metrics
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { onShiftWeek(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_week))
                }
                Text(stringResource(R.string.week_of, Fmt.date(state.weekStart)), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onShiftWeek(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_week))
                }
            }
        }
        val week = state.summary
        if (week != null) {
            item {
                TileRow(
                    { StatTile(stringResource(R.string.activities), week.activityCount.toString(), it, color = MaterialTheme.colorScheme.primary) },
                    { StatTile(stringResource(R.string.distance), Fmt.km(week.totalDistanceM), it, unit = "km", color = colors.distance) },
                )
            }
            item {
                TileRow(
                    { StatTile(stringResource(R.string.duration), Fmt.duration(week.totalDurationS), it, color = colors.calories) },
                    { StatTile(stringResource(R.string.avg_steps), week.avgSteps?.let { s -> Fmt.thousands(Math.rint(s).toInt()) } ?: "–", it, color = colors.steps) },
                )
            }
            item {
                TileRow(
                    { StatTile(stringResource(R.string.sleep), week.avgSleepHours?.let { h -> Fmt.oneDecimal(h) } ?: "–", it, unit = "h", color = colors.sleep) },
                    { StatTile(stringResource(R.string.intensity_minutes), week.intensityMinutes.toString(), it, color = colors.stress) },
                )
            }
        }
        item { ReportCard(state.report, busy, onGenerate, active) }
        if (state.records.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.records)) }
            for ((sport, entries) in state.records) {
                item(key = sport.key) { RecordsCard(sport, entries, onOpenActivity) }
            }
        }
    }
}

@Composable
private fun RecordsCard(sport: Sport, entries: Map<String, RecordEntry>, onOpenActivity: (String) -> Unit) {
    SurfaceCard(Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SportBadge(sport)
            Text(stringResource(sportLabel(sport)), style = MaterialTheme.typography.titleMedium)
        }
        for ((key, entry) in entries) {
            val (label, value) = when (key) {
                "longest_distance" -> R.string.record_longest_distance to Fmt.km(entry.value) + " km"
                "longest_duration" -> R.string.record_longest_duration to Fmt.duration(entry.value)
                "most_elevation" -> R.string.record_most_elevation to Math.rint(entry.value).toInt().toString() + " m"
                else -> R.string.record_fastest_pace to Fmt.pace(entry.value) + " /km"
            }
            Row(
                Modifier.fillMaxWidth().clickable { onOpenActivity(entry.activityId) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(label) + " · " + Fmt.date(entry.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
