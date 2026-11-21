package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.ChartInsight
import nl.tufein.openconnect.core.ChartMetric
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthScreen(
    state: HealthState,
    onDemo: () -> Unit,
    onImport: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    /** The chart to open on arrival (a tile on Home or Insights was tapped), then [onFocusDone] is called. */
    focus: ChartMetric? = null,
    onFocusDone: () -> Unit = {},
) {
    // One chart at a time: the scores stay on screen and a chip row picks the trend to draw.
    val trend = state.trend
    val byDay = state.dailies.associateBy { it.date }
    val available = buildList {
        if (state.readiness.any { it.second != null }) add(ChartMetric.READINESS)
        if (state.sleepScores.any { it.second != null }) add(ChartMetric.SLEEP_SCORE)
        if (trend.any { it.steps != null }) add(ChartMetric.STEPS)
        if (trend.any { it.sleepHours != null }) add(ChartMetric.SLEEP)
        if (trend.any { it.restingHr != null }) add(ChartMetric.RESTING_HR)
        if (trend.any { it.bodyBatteryHigh != null }) add(ChartMetric.BODY_BATTERY)
        if (trend.any { it.avgStress != null }) add(ChartMetric.STRESS)
        if (trend.any { byDay[it.date]?.hrvMs != null }) add(ChartMetric.HRV)
        if (trend.any { byDay[it.date]?.spo2Avg != null }) add(ChartMetric.SPO2)
        if (trend.any { byDay[it.date]?.respirationAvg != null }) add(ChartMetric.RESPIRATION)
    }
    var selected by rememberSaveable { mutableStateOf(focus ?: ChartMetric.READINESS) }
    if (selected !in available && available.isNotEmpty()) selected = available.first()
    LaunchedEffect(focus, state.loaded) {
        if (focus == null || !state.loaded) return@LaunchedEffect
        if (focus in available) selected = focus
        onFocusDone()
    }
    fun labelFor(metric: ChartMetric): Int = when (metric) {
        ChartMetric.READINESS -> R.string.readiness_title
        ChartMetric.SLEEP_SCORE -> R.string.score_sleep
        ChartMetric.STEPS -> R.string.steps
        ChartMetric.SLEEP -> R.string.sleep
        ChartMetric.RESTING_HR -> R.string.resting_hr
        ChartMetric.BODY_BATTERY -> R.string.body_battery
        ChartMetric.STRESS -> R.string.stress
        ChartMetric.HRV -> R.string.hrv
        ChartMetric.SPO2 -> R.string.spo2
        ChartMetric.RESPIRATION -> R.string.respiration
        else -> R.string.tab_health
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fun section(key: String, content: @Composable LazyItemScope.() -> Unit) {
            // Every chart section has the metric name as its key; only the chosen one is composed.
            if (key != "title" && key != "scores" && key != "empty" && key != "tabs" && key != selected.name) return
            item(key = key, content = content)
        }
        section("title") {
            Text(stringResource(R.string.tab_health), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        if (!state.loaded) return@LazyColumn
        if (state.dailies.isEmpty() && state.sleeps.isEmpty()) {
            section("empty") { EmptyState(onDemo, onImport) }
            return@LazyColumn
        }
        val dates = trend.map { it.date }
        fun insightFor(metric: ChartMetric, values: List<Double?>) = ChartInsight.of(metric, dates.zip(values))
        val labels = dates.map { Fmt.shortDate(it) }
        section("scores") {
            SectionTitle(stringResource(R.string.scores_title))
            val metrics = OpenConnectColors.metrics
            val readiness = state.readiness.mapNotNull { it.second }.lastOrNull()
            val sleepScore = state.sleepScores.mapNotNull { it.second }.lastOrNull()?.toInt()
            val battery = trend.mapNotNull { it.bodyBatteryHigh }.lastOrNull()
            val stress = trend.mapNotNull { it.avgStress }.lastOrNull()
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScoreTile(stringResource(R.string.readiness_title), readiness, metrics.readiness, Modifier.weight(1f), onClick = { selected = ChartMetric.READINESS })
                    ScoreTile(stringResource(R.string.score_sleep), sleepScore, metrics.sleep, Modifier.weight(1f), onClick = { selected = ChartMetric.SLEEP_SCORE })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScoreTile(stringResource(R.string.body_battery), battery, metrics.battery, Modifier.weight(1f), onClick = { selected = ChartMetric.BODY_BATTERY })
                    ScoreTile(stringResource(R.string.stress), stress, metrics.stress, Modifier.weight(1f), onClick = { selected = ChartMetric.STRESS })
                }
                val hrv = nl.tufein.openconnect.core.HrvStatusCalculator.compute(state.dailies, LocalDate.now())
                if (hrv != null) {
                    val (label, tint) = when (hrv.level) {
                        nl.tufein.openconnect.core.HrvStatusLevel.LOW -> R.string.hrv_status_low to MaterialTheme.colorScheme.error
                        nl.tufein.openconnect.core.HrvStatusLevel.HIGH -> R.string.hrv_status_high to MaterialTheme.colorScheme.tertiary
                        nl.tufein.openconnect.core.HrvStatusLevel.BALANCED -> R.string.hrv_status_balanced to MaterialTheme.colorScheme.primary
                    }
                    val change = (if (hrv.changePct >= 0) "+" else "") + Fmt.oneDecimal(hrv.changePct)
                    SurfaceCard(Modifier.tappable { selected = ChartMetric.HRV }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.hrv_status), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, color = tint)
                        }
                        Text(
                            stringResource(R.string.hrv_status_line, Fmt.oneDecimal(hrv.recentMs), Fmt.oneDecimal(hrv.normalMs), change),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(R.string.hrv_status_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.scores_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        section("tabs") {
            SectionTitle(stringResource(R.string.health_charts))
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (metric in available) {
                    PixelChip(
                        selected = selected == metric,
                        onClick = { selected = metric },
                        label = { Text(stringResource(R.string.health_chart, stringResource(labelFor(metric)))) },
                    )
                }
            }
        }
        section("READINESS") {
            val values = state.readiness.map { it.second?.toDouble() }
            if (values.any { it != null }) {
                MetricCard(title = stringResource(R.string.score_readiness_30), subtitle = null) {
                    LineChart(values, description = stringResource(R.string.score_readiness_30), color = OpenConnectColors.metrics.readiness, labels = state.readiness.map { Fmt.shortDate(it.first) }, format = { insightValue(ChartMetric.READINESS, it) })
                    InsightCaption(ChartInsight.of(ChartMetric.READINESS, state.readiness.map { it.first to it.second?.toDouble() }), ChartMetric.READINESS)
                }
            }
        }
        section("SLEEP_SCORE") {
            val values = state.sleepScores.map { it.second }
            if (values.any { it != null }) {
                MetricCard(title = stringResource(R.string.score_sleep_30), subtitle = null) {
                    LineChart(values, description = stringResource(R.string.score_sleep_30), color = OpenConnectColors.metrics.sleep, labels = state.sleepScores.map { Fmt.shortDate(it.first) }, format = { insightValue(ChartMetric.SLEEP_SCORE, it) })
                    InsightCaption(ChartInsight.of(ChartMetric.SLEEP_SCORE, state.sleepScores), ChartMetric.SLEEP_SCORE)
                }
            }
        }
        section("STEPS") {
            val steps = trend.map { it.steps?.toDouble() }
            val average = steps.filterNotNull().average().takeIf { !it.isNaN() }
            MetricCard(
                title = stringResource(R.string.steps),
                subtitle = average?.let { stringResource(R.string.avg_per_day, Fmt.thousands(Math.rint(it).toInt())) },
            ) {
                BarChart(steps, goal = 10_000.0, color = OpenConnectColors.metrics.steps, description = stringResource(R.string.steps), labels = labels, format = { insightValue(ChartMetric.STEPS, it) })
                InsightCaption(insightFor(ChartMetric.STEPS, steps), ChartMetric.STEPS)
            }
        }
        section("SLEEP") {
            val sleep = trend.map { it.sleepHours }
            val average = sleep.filterNotNull().average().takeIf { !it.isNaN() }
            MetricCard(
                title = stringResource(R.string.sleep),
                subtitle = average?.let { stringResource(R.string.avg_hours_per_night, Fmt.oneDecimal(it)) },
            ) {
                BarChart(sleep, goal = 8.0, color = OpenConnectColors.metrics.sleep, description = stringResource(R.string.sleep), labels = labels, format = { insightValue(ChartMetric.SLEEP, it) })
                InsightCaption(insightFor(ChartMetric.SLEEP, sleep), ChartMetric.SLEEP)
            }
        }
        section("RESTING_HR") {
            val resting = trend.map { it.restingHr?.toDouble() }
            val latest = resting.filterNotNull().lastOrNull()
            MetricCard(
                title = stringResource(R.string.resting_hr),
                subtitle = latest?.let { stringResource(R.string.latest_bpm, Math.rint(it).toInt()) },
            ) {
                LineChart(resting, description = stringResource(R.string.resting_hr), color = OpenConnectColors.metrics.heart, labels = labels, format = { insightValue(ChartMetric.RESTING_HR, it) })
                InsightCaption(insightFor(ChartMetric.RESTING_HR, resting), ChartMetric.RESTING_HR)
            }
        }
        section("BODY_BATTERY") {
            val battery = trend.map { it.bodyBatteryHigh?.toDouble() }
            MetricCard(title = stringResource(R.string.body_battery), subtitle = null) {
                LineChart(battery, description = stringResource(R.string.body_battery), color = OpenConnectColors.metrics.battery, labels = labels, format = { insightValue(ChartMetric.BODY_BATTERY, it) })
                InsightCaption(insightFor(ChartMetric.BODY_BATTERY, battery), ChartMetric.BODY_BATTERY)
            }
        }
        // Values only the watch provides: shown once there is at least one reading in the last 30 days.
        val watchCards = listOf(
            Triple(R.string.hrv, ChartMetric.HRV, trend.map { byDay[it.date]?.hrvMs?.toDouble() }),
            Triple(R.string.spo2, ChartMetric.SPO2, trend.map { byDay[it.date]?.spo2Avg?.toDouble() }),
            Triple(R.string.respiration, ChartMetric.RESPIRATION, trend.map { byDay[it.date]?.respirationAvg }),
        )
        for ((title, metric, values) in watchCards) {
            if (values.all { it == null }) continue
            section(metric.name) {
                val latest = values.filterNotNull().lastOrNull()
                MetricCard(title = stringResource(title), subtitle = latest?.let { Fmt.oneDecimal(it) }) {
                    LineChart(values, description = stringResource(title), color = OpenConnectColors.metrics.distance, labels = labels, format = { insightValue(metric, it) })
                    InsightCaption(insightFor(metric, values), metric)
                }
            }
        }
        section("STRESS") {
            val stress = trend.map { it.avgStress?.toDouble() }
            MetricCard(title = stringResource(R.string.stress), subtitle = null) {
                LineChart(stress, description = stringResource(R.string.stress), color = OpenConnectColors.metrics.stress, labels = labels, format = { insightValue(ChartMetric.STRESS, it) })
                InsightCaption(insightFor(ChartMetric.STRESS, stress), ChartMetric.STRESS)
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
