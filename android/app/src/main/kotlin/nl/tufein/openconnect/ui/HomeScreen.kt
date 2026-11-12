package nl.tufein.openconnect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.Activity

@Composable
fun HomeScreen(
    state: HomeState,
    onOpenActivity: (String) -> Unit,
    onDemo: () -> Unit,
    onImport: () -> Unit,
    onWatch: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (!state.loaded) return@LazyColumn
        item { WatchCard(onWatch) }
        val overview = state.overview
        if (!state.hasData || overview == null) {
            item { EmptyState(onDemo, onImport) }
            return@LazyColumn
        }
        val today = overview.today
        item {
            SectionTitle(stringResource(R.string.today))
            TileRow(
                { StatTile(stringResource(R.string.steps), Fmt.thousands(today?.steps ?: 0), it) },
                { StatTile(stringResource(R.string.resting_hr), today?.restingHr?.toString() ?: "–", it, unit = "bpm") },
            )
        }
        item {
            TileRow(
                { StatTile(stringResource(R.string.body_battery), today?.bodyBatteryHigh?.toString() ?: "–", it) },
                { StatTile(stringResource(R.string.stress), today?.avgStress?.toString() ?: "–", it) },
            )
        }
        item {
            val week = overview.last7
            SectionTitle(stringResource(R.string.last_7_days))
            TileRow(
                { StatTile(stringResource(R.string.distance), Fmt.km(week.totalDistanceM), it, unit = "km") },
                { StatTile(stringResource(R.string.activities), week.activityCount.toString(), it) },
            )
        }
        item {
            val change = overview.stepsChangePct
            val week = overview.last7
            TileRow(
                { StatTile(stringResource(R.string.avg_steps), week.avgSteps?.let { Fmt.thousands(Math.rint(it).toInt()) } ?: "–", it) },
                {
                    StatTile(
                        stringResource(R.string.steps_vs_previous),
                        change?.let { (if (it >= 0) "+" else "") + Fmt.oneDecimal(it) + "%" } ?: "–",
                        it,
                    )
                },
            )
        }
        item {
            SectionTitle(stringResource(R.string.steps_30_days))
            SurfaceCard {
                BarChart(
                    values = overview.trend.map { it.steps?.toDouble() },
                    goal = 10_000.0,
                    description = stringResource(R.string.steps_30_days),
                )
            }
        }
        item { SectionTitle(stringResource(R.string.recent_activities)) }
        items(overview.recentActivities, key = { it.id }) { ActivityRow(it, onOpenActivity) }
    }
}

@Composable
private fun WatchCard(onWatch: () -> Unit) {
    SurfaceCard {
        Text(stringResource(R.string.watch_card_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.watch_card_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onWatch) { Text(stringResource(R.string.action_watch)) }
    }
}

@Composable
fun ActivityRow(activity: Activity, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    SurfaceCard(modifier.clickable { onOpen(activity.id) }) {
        Text(activity.name, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(sportLabel(activity.sport)) + " · " + Fmt.dateTime(activity.startTime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val summary = buildList {
            if (activity.distanceM > 0) add(Fmt.km(activity.distanceM) + " km")
            add(Fmt.duration(activity.durationS))
            activity.paceSPerKm?.takeIf { activity.sport.usesPace() }?.let { add(Fmt.pace(it) + " /km") }
            activity.avgHr?.let { add("$it bpm") }
        }.joinToString("  ·  ")
        Text(summary, style = MaterialTheme.typography.bodyMedium)
    }
}

fun nl.tufein.openconnect.core.Sport.usesPace(): Boolean = this in setOf(
    nl.tufein.openconnect.core.Sport.RUNNING,
    nl.tufein.openconnect.core.Sport.WALKING,
    nl.tufein.openconnect.core.Sport.HIKING,
)
