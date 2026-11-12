package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R

@Composable
fun HealthScreen(
    state: HealthState,
    onDemo: () -> Unit,
    onImport: () -> Unit,
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
            Text(stringResource(R.string.tab_health), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (!state.loaded) return@LazyColumn
        if (state.dailies.isEmpty() && state.sleeps.isEmpty()) {
            item { EmptyState(onDemo, onImport) }
            return@LazyColumn
        }
        val trend = state.trend
        item {
            val steps = trend.map { it.steps?.toDouble() }
            val average = steps.filterNotNull().average().takeIf { !it.isNaN() }
            MetricCard(
                title = stringResource(R.string.steps),
                subtitle = average?.let { stringResource(R.string.avg_per_day, Fmt.thousands(Math.rint(it).toInt())) },
            ) { BarChart(steps, goal = 10_000.0, description = stringResource(R.string.steps)) }
        }
        item {
            val sleep = trend.map { it.sleepHours }
            val average = sleep.filterNotNull().average().takeIf { !it.isNaN() }
            MetricCard(
                title = stringResource(R.string.sleep),
                subtitle = average?.let { stringResource(R.string.avg_hours_per_night, Fmt.oneDecimal(it)) },
            ) { BarChart(sleep, goal = 8.0, color = MaterialTheme.colorScheme.tertiary, description = stringResource(R.string.sleep)) }
        }
        item {
            val resting = trend.map { it.restingHr?.toDouble() }
            val latest = resting.filterNotNull().lastOrNull()
            MetricCard(
                title = stringResource(R.string.resting_hr),
                subtitle = latest?.let { stringResource(R.string.latest_bpm, Math.rint(it).toInt()) },
            ) { LineChart(resting, description = stringResource(R.string.resting_hr), color = MaterialTheme.colorScheme.error) }
        }
        item {
            val battery = trend.map { it.bodyBatteryHigh?.toDouble() }
            MetricCard(title = stringResource(R.string.body_battery), subtitle = null) {
                LineChart(battery, description = stringResource(R.string.body_battery), color = MaterialTheme.colorScheme.secondary)
            }
        }
        // Values only the watch provides: shown once there is at least one reading in the last 30 days.
        val byDay = state.dailies.associateBy { it.date }
        val watchCards = listOf(
            R.string.hrv to trend.map { byDay[it.date]?.hrvMs?.toDouble() },
            R.string.spo2 to trend.map { byDay[it.date]?.spo2Avg?.toDouble() },
            R.string.respiration to trend.map { byDay[it.date]?.respirationAvg },
        )
        for ((title, values) in watchCards) {
            if (values.all { it == null }) continue
            item {
                val latest = values.filterNotNull().lastOrNull()
                MetricCard(title = stringResource(title), subtitle = latest?.let { Fmt.oneDecimal(it) }) {
                    LineChart(values, description = stringResource(title), color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        item {
            val stress = trend.map { it.avgStress?.toDouble() }
            MetricCard(title = stringResource(R.string.stress), subtitle = null) {
                LineChart(stress, description = stringResource(R.string.stress), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, subtitle: String?, chart: @Composable () -> Unit) {
    SurfaceCard(Modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        chart()
    }
}
