package nl.tufein.openconnect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import nl.tufein.openconnect.core.PlanGoal
import nl.tufein.openconnect.core.RecordEntry
import nl.tufein.openconnect.core.reports.ReportPeriod
import nl.tufein.openconnect.core.Sport

/** The things under the Insights tab. */
enum class InsightsTab(val label: Int) {
    Week(R.string.insights_week),
    Trophies(R.string.insights_trophies),
    Coach(R.string.insights_coach),
    Plan(R.string.insights_plan),
    Zones(R.string.insights_zones),
    Shoes(R.string.insights_shoes),
    Calendar(R.string.insights_calendar),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(
    tab: InsightsTab,
    onTab: (InsightsTab) -> Unit,
    reports: ReportsState,
    busy: Boolean,
    active: ActiveAi,
    trophies: TrophiesState,
    coach: CoachUi,
    plan: PlanState,
    onCreatePlan: (PlanGoal, Int, Int) -> Unit,
    onDeletePlan: () -> Unit,
    onShiftWeek: (Long) -> Unit,
    onPeriod: (ReportPeriod) -> Unit,
    onGenerate: (force: Boolean) -> Unit,
    onOpenActivity: (String) -> Unit,
    onAsk: (String) -> Unit,
    onClearCoach: () -> Unit,
    onOpenAiSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenHealth: (nl.tufein.openconnect.core.ChartMetric) -> Unit = {},
    onOpenActivities: () -> Unit = {},
    onRace: (RaceAction) -> Unit = {},
    onSendWorkouts: () -> Unit = {},
    zones: ZonesState = ZonesState(),
    onZones: (ZonesAction) -> Unit = {},
    gear: GearState = GearState(),
    onGear: (GearAction) -> Unit = {},
    calendar: CalendarState = CalendarState(),
    onShiftCalendar: (Int) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tab_insights), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            // Five chips do not fit on one line of a narrow phone, so they wrap; a scrolling row would also hide them from lists' scroll lookups.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (entry in InsightsTab.entries) {
                    PixelChip(selected = tab == entry, onClick = { onTab(entry) }, label = { Text(stringResource(entry.label)) })
                }
            }
        }
        when (tab) {
            InsightsTab.Week -> WeekContent(reports, busy, active, onShiftWeek, onPeriod, onGenerate, onOpenActivity, contentPadding, onOpenHealth, onOpenActivities)
            InsightsTab.Trophies -> TrophiesContent(trophies, contentPadding)
            InsightsTab.Coach -> CoachContent(coach, active, onAsk, onClearCoach, onOpenAiSettings, contentPadding)
            InsightsTab.Plan -> PlanContent(plan, onCreatePlan, onDeletePlan, onRace, onSendWorkouts, contentPadding)
            InsightsTab.Zones -> ZonesContent(zones, onZones, contentPadding)
            InsightsTab.Shoes -> GearContent(gear, onGear, contentPadding)
            InsightsTab.Calendar -> CalendarContent(calendar, onShiftCalendar, onOpenActivity, contentPadding)
        }
    }
}

@Composable
private fun WeekContent(
    state: ReportsState,
    busy: Boolean,
    active: ActiveAi,
    onShiftWeek: (Long) -> Unit,
    onPeriod: (ReportPeriod) -> Unit,
    onGenerate: (force: Boolean) -> Unit,
    onOpenActivity: (String) -> Unit,
    contentPadding: PaddingValues,
    onOpenHealth: (nl.tufein.openconnect.core.ChartMetric) -> Unit,
    onOpenActivities: () -> Unit,
) {
    val colors = OpenConnectColors.metrics
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((p, label) in listOf(ReportPeriod.WEEK to R.string.period_week, ReportPeriod.MONTH to R.string.period_month, ReportPeriod.YEAR to R.string.period_year)) {
                    PixelChip(selected = state.period == p, onClick = { onPeriod(p) }, label = { Text(stringResource(label)) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                PixelIconButton(onClick = { onShiftWeek(-1) }) {
                    PixelIcon(PixelIcons.ChevronLeft, contentDescription = stringResource(R.string.previous_week))
                }
                Text(periodTitle(state), style = MaterialTheme.typography.titleMedium)
                PixelIconButton(onClick = { onShiftWeek(1) }) {
                    PixelIcon(PixelIcons.ChevronRight, contentDescription = stringResource(R.string.next_week))
                }
            }
        }
        val week = state.summary
        if (week != null) {
            item {
                TileRow(
                    { StatTile(stringResource(R.string.activities), week.activityCount.toString(), it, color = MaterialTheme.colorScheme.primary, onClick = onOpenActivities) },
                    { StatTile(stringResource(R.string.distance), Fmt.km(week.totalDistanceM), it, unit = "km", color = colors.distance, onClick = onOpenActivities) },
                )
            }
            item {
                TileRow(
                    { StatTile(stringResource(R.string.duration), Fmt.duration(week.totalDurationS), it, color = colors.calories, onClick = onOpenActivities) },
                    { StatTile(stringResource(R.string.avg_steps), week.avgSteps?.let { s -> Fmt.thousands(Math.rint(s).toInt()) } ?: "–", it, color = colors.steps, onClick = { onOpenHealth(nl.tufein.openconnect.core.ChartMetric.STEPS) }) },
                )
            }
            item {
                TileRow(
                    { StatTile(stringResource(R.string.sleep), week.avgSleepHours?.let { h -> Fmt.oneDecimal(h) } ?: "–", it, unit = "h", color = colors.sleep, onClick = { onOpenHealth(nl.tufein.openconnect.core.ChartMetric.SLEEP) }) },
                    { StatTile(stringResource(R.string.intensity_minutes), week.intensityMinutes.toString(), it, color = colors.stress, onClick = onOpenActivities) },
                )
            }
        }
        item { ReportCard(state.report, busy, onGenerate, active) }
        if (state.efforts.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.best_efforts))
                SurfaceCard {
                    for (e in state.efforts) {
                        Row(
                            Modifier.fillMaxWidth().clickable { e.activityId?.let(onOpenActivity) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(effortLabel(e.distanceM) + (e.date?.let { " · " + Fmt.date(it) } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Fmt.duration(e.seconds), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
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

@Composable
private fun periodTitle(state: ReportsState): String = when (state.period) {
    ReportPeriod.WEEK -> stringResource(R.string.week_of, Fmt.date(state.weekStart))
    ReportPeriod.MONTH -> state.weekStart.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", java.util.Locale.getDefault())).replaceFirstChar { it.uppercase() }
    ReportPeriod.YEAR -> state.weekStart.year.toString()
}

@Composable
internal fun effortLabel(distanceM: Double): String = when (distanceM) {
    1000.0 -> "1 km"
    5000.0 -> "5 km"
    10_000.0 -> "10 km"
    21_097.5 -> stringResource(R.string.effort_half)
    42_195.0 -> stringResource(R.string.effort_marathon)
    else -> Fmt.km(distanceM) + " km"
}
