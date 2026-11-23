package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import nl.tufein.openconnect.R
import kotlin.math.roundToInt
import nl.tufein.openconnect.ai.ActiveAi
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.Lap
import nl.tufein.openconnect.core.ActivitySort
import nl.tufein.openconnect.core.ManualActivity
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.search

@Composable
fun ActivitiesScreen(
    state: ActivitiesState,
    onSport: (Sport?) -> Unit,
    onOpenActivity: (String) -> Unit,
    onDemo: () -> Unit,
    onImport: () -> Unit,
    onAddActivity: (ManualActivity) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ActivitySort.NEWEST) }
    var typing by rememberSaveable { mutableStateOf(false) }
    val shown = remember(state.items, query, sort) { state.items.search(query, sort) }
    Column(Modifier.fillMaxSize()) {
        if (typing) {
            ManualActivityForm(
                onSave = { manual ->
                    onAddActivity(manual)
                    typing = false
                },
                onCancel = { typing = false },
            )
        }
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.tab_activities), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(onClick = onImport) { Text(stringResource(R.string.action_import_short)) }
                    SecondaryButton(onClick = { typing = !typing }) {
                        Text(stringResource(if (typing) R.string.manual_close else R.string.manual_open))
                    }
                }
            }
        }
        if (!state.loaded) return@LazyColumn
        if (state.sports.isEmpty()) {
            item { EmptyState(onDemo, onImport) }
            return@LazyColumn
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    PixelChip(selected = state.sport == null, onClick = { onSport(null) }, label = { Text(stringResource(R.string.filter_all)) })
                }
                items(state.sports) { sport ->
                    PixelChip(
                        selected = state.sport == sport,
                        onClick = { onSport(sport) },
                        label = { Text(stringResource(sportLabel(sport))) },
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ActivitySort.entries) { entry ->
                    PixelChip(selected = sort == entry, onClick = { sort = entry }, label = { Text(stringResource(sortLabel(entry))) })
                }
            }
        }
        item {
            Text(
                stringResource(R.string.activities_count, shown.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (shown.isEmpty()) item { Text(stringResource(R.string.search_none), style = MaterialTheme.typography.bodyMedium) }
        items(shown, key = { it.id }) { ActivityRow(it, onOpenActivity) }
    }
    }
}

/**
 * v1.3.0: a workout that no device recorded. The numbers are the person's own, so the form only offers to save when
 * they add up; [ManualActivity] holds the rules, not this screen.
 */
@Composable
private fun ManualActivityForm(onSave: (ManualActivity) -> Unit, onCancel: () -> Unit) {
    var sport by rememberSaveable { mutableStateOf(Sport.RUNNING) }
    var name by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf("") }
    var minutesText by rememberSaveable { mutableStateOf("") }
    var kmText by rememberSaveable { mutableStateOf("") }
    var hrText by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val date = if (dateText.isBlank()) today else runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
    val minutes = minutesText.trim().toIntOrNull()
    val km = kmText.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    val hr = hrText.trim().toIntOrNull()
    val minutesOk = minutes != null && minutes in 1..ManualActivity.MAX_MINUTES
    val kmOk = km in 0.0..ManualActivity.MAX_KM
    val hrOk = hr == null || hr in ManualActivity.MIN_HR..ManualActivity.MAX_HR
    val problem: Int? = when {
        date == null -> R.string.manual_error_date
        !minutesOk -> R.string.manual_error_minutes
        !kmOk -> R.string.manual_error_distance
        !hrOk -> R.string.manual_error_hr
        else -> null
    }
    val manual = if (problem == null && date != null && minutes != null) {
        ManualActivity(sport = sport, date = date, minutes = minutes, distanceKm = km, avgHr = hr, name = name)
    } else {
        null
    }

    SurfaceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.manual_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.manual_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Sport.entries) { entry ->
                    PixelChip(selected = sport == entry, onClick = { sport = entry }, label = { Text(stringResource(sportLabel(entry))) })
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("manual-name"),
                singleLine = true,
                label = { Text(stringResource(R.string.manual_name)) },
            )
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                modifier = Modifier.fillMaxWidth().testTag("manual-date"),
                singleLine = true,
                isError = date == null,
                label = { Text(stringResource(R.string.manual_date)) },
                placeholder = { Text(today.toString()) },
            )
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it },
                modifier = Modifier.fillMaxWidth().testTag("manual-minutes"),
                singleLine = true,
                label = { Text(stringResource(R.string.manual_minutes)) },
            )
            OutlinedTextField(
                value = kmText,
                onValueChange = { kmText = it },
                modifier = Modifier.fillMaxWidth().testTag("manual-km"),
                singleLine = true,
                label = { Text(stringResource(R.string.manual_km)) },
            )
            OutlinedTextField(
                value = hrText,
                onValueChange = { hrText = it },
                modifier = Modifier.fillMaxWidth().testTag("manual-hr"),
                singleLine = true,
                label = { Text(stringResource(R.string.manual_hr)) },
            )
            if (tried && problem != null) {
                Text(
                    stringResource(problem),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    onClick = {
                        if (manual != null) onSave(manual) else tried = true
                    },
                ) {
                    Text(stringResource(R.string.manual_save))
                }
                SecondaryButton(onClick = onCancel) { Text(stringResource(R.string.manual_cancel)) }
            }
        }
    }
}

private fun sortLabel(sort: ActivitySort): Int = when (sort) {    ActivitySort.NEWEST -> R.string.sort_newest
    ActivitySort.OLDEST -> R.string.sort_oldest
    ActivitySort.DISTANCE -> R.string.sort_distance
    ActivitySort.DURATION -> R.string.sort_duration
}

@Composable
fun ActivityDetailScreen(
    state: DetailState,
    busy: Boolean,
    active: ActiveAi,
    onBack: () -> Unit,
    onGenerate: (force: Boolean) -> Unit,
    onExport: (format: String) -> Unit,
    onDelete: () -> Unit,
    onShoe: (String?) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PixelIconButton(onClick = onBack) {
                PixelIcon(PixelIcons.ArrowLeft, contentDescription = stringResource(R.string.back))
            }
        }
        val activity = state.activity
        if (activity == null) {
            if (state.loaded) item { Text(stringResource(R.string.activity_not_found)) }
            return@LazyColumn
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SportBadge(activity.sport)
                Column(Modifier.weight(1f)) {
                    Text(activity.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(sportLabel(activity.sport)) + " · " + Fmt.dateTime(activity.startTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SummaryTiles(activity) }
        // A workout only the Oura Ring or the WHOOP band recorded: say what that means before anything else.
        if (state.fromRing) {
            item { Text(stringResource(R.string.ring_row_note, activity.deviceName ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        val positions = activity.track.filter { it.hasPosition }.map { it.lat!! to it.lon!! }
        if (positions.size >= 2) {
            item {
                FoldableCard("activity-route", stringResource(R.string.route)) { RouteMap(downsample(positions, 400), description = stringResource(R.string.route)) }
            }
            state.route?.let { history -> item { RouteHistoryCard(history, activity) } }
        }
        val hr = activity.track.map { it.hr?.toDouble() }
        if (hr.count { it != null } >= 2) {
            item {
                FoldableCard("activity-heart", stringResource(R.string.heart_rate)) {
                    LineChart(downsample(hr), description = stringResource(R.string.heart_rate), color = OpenConnectColors.metrics.heart)
                    val present = hr.filterNotNull()
                    Text(
                        stringResource(R.string.insight_hr_range, "${present.average().roundToInt()} bpm", "${present.max().roundToInt()} bpm"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        state.zones?.let { zones -> item { ActivityZonesCard(zones, state.zonesMaxHr) } }
        if (state.shoeChoices.isNotEmpty()) item { ActivityShoesCard(state, onShoe) }
        val ele = activity.track.map { it.ele }
        if (ele.count { it != null } >= 2) {
            item {
                FoldableCard("activity-elevation", stringResource(R.string.elevation)) {
                    LineChart(downsample(ele), description = stringResource(R.string.elevation), color = OpenConnectColors.metrics.distance)
                    val present = ele.filterNotNull()
                    Text(
                        "${present.min().roundToInt()} m – ${present.max().roundToInt()} m",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (state.laps.isNotEmpty()) {
            item {
                FoldableCard("activity-splits", stringResource(R.string.splits)) { LapTable(state.laps) }
            }
        }
        if (state.efforts.isNotEmpty()) {
            item {
                FoldableCard("activity-efforts", stringResource(R.string.best_efforts_here)) {
                    for (e in state.efforts) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(effortLabel(e.distanceM), style = MaterialTheme.typography.bodyMedium)
                            Text(Fmt.duration(e.seconds), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        // What the watch and the ring measured around it (v0.43.0). It sits after the cards of the activity itself so the route,
        // zones and splits keep their places near the top; a ring-only workout has none of those, so there it comes first.
        state.around?.let { around -> item { ActivityAroundCard(around) } }
        // A ring-only workout is not a stored activity: there is nothing to write a report from, to export or to delete.
        if (state.fromRing) return@LazyColumn
        item { ReportCard(state.report, busy, onGenerate, active) }
        item {
            CollapsibleSection("activity-export", stringResource(R.string.export)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (format in listOf("fit", "gpx", "tcx")) {
                    SecondaryButton(onClick = { onExport(format) }, enabled = format != "gpx" || positions.isNotEmpty()) {
                        Text(format.uppercase())
                    }
                }
            }
            }
        }
        item {
            QuietButton(onClick = { confirmDelete = true }) {
                Text(stringResource(R.string.delete_activity), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_activity)) },
            text = { Text(stringResource(R.string.delete_activity_body)) },
            confirmButton = {
                PrimaryButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { QuietButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SummaryTiles(activity: Activity) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TileRow(
            { StatTile(stringResource(R.string.distance), Fmt.km(activity.distanceM), it, unit = "km") },
            { StatTile(stringResource(R.string.duration), Fmt.duration(activity.durationS), it) },
        )
        TileRow(
            {
                if (activity.sport.usesPace()) {
                    StatTile(stringResource(R.string.pace), Fmt.pace(activity.paceSPerKm), it, unit = "/km")
                } else {
                    StatTile(stringResource(R.string.speed), Fmt.speedKmh(activity.avgSpeedMps), it, unit = "km/h")
                }
            },
            { StatTile(stringResource(R.string.elevation_gain), Math.rint(activity.elevationGainM).toInt().toString(), it, unit = "m") },
        )
        TileRow(
            { StatTile(stringResource(R.string.avg_hr), activity.avgHr?.toString() ?: "–", it, unit = "bpm") },
            { StatTile(stringResource(R.string.max_hr), activity.maxHr?.toString() ?: "–", it, unit = "bpm") },
        )
        TileRow(
            { StatTile(stringResource(R.string.calories), activity.calories?.toString() ?: "–", it, unit = "kcal") },
            { StatTile(stringResource(R.string.device), activity.deviceName ?: "–", it) },
        )
    }
}

/** One row per kilometre: time, pace, heart rate and climbing. The last row may be a shorter lap. */
@Composable
private fun LapTable(laps: List<Lap>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (h in listOf(R.string.lap_km, R.string.lap_time, R.string.lap_pace, R.string.lap_hr, R.string.lap_climb)) {
            Text(stringResource(h), style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
        }
    }
    for (lap in laps) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val label = if (lap.distanceM < 995) Fmt.km(lap.distanceM) else lap.index.toString()
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(Fmt.duration(lap.seconds), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(Fmt.pace(lap.pace), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(lap.avgHr?.toString() ?: "–", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${lap.climbM} m", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
