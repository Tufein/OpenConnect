package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.ChartInsight
import nl.tufein.openconnect.core.ChartMetric
import java.time.LocalDate

@Composable
fun HealthScreen(
    state: HealthState,
    onDemo: () -> Unit,
    onImport: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    /** A chart to scroll to on arrival (a tile on Home or Insights was tapped), then [onFocusDone] is called. */
    focus: ChartMetric? = null,
    onFocusDone: () -> Unit = {},
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Where each section sits in the list, filled while the list is built, so a tile can scroll to its chart.
    val positions = remember { HashMap<String, Int>() }
    fun scrollTo(metric: ChartMetric) {
        positions[metric.name]?.let { scope.launch { list.animateScrollToItem(it) } }
    }
    LaunchedEffect(focus, state.loaded) {
        if (focus == null || !state.loaded) return@LaunchedEffect
        withFrameNanos { }
        positions[focus.name]?.let { list.animateScrollToItem(it) }
        onFocusDone()
    }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var index = 0
        fun section(key: String, content: @Composable LazyItemScope.() -> Unit) {
            positions[key] = index++
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
        val trend = state.trend
        val dates = trend.map { it.date }
        fun insightFor(metric: ChartMetric, values: List<Double?>) = ChartInsight.of(metric, dates.zip(values))
        val labels = dates.map { Fmt.shortDate(it) }
        val first = dates.firstOrNull()
        val lastDay = dates.lastOrNull()
        section("scores") {
            SectionTitle(stringResource(R.string.scores_title))
            val metrics = OpenConnectColors.metrics
            val readiness = state.readiness.mapNotNull { it.second }.lastOrNull()
            val sleepScore = state.sleepScores.mapNotNull { it.second }.lastOrNull()?.toInt()
            val battery = trend.mapNotNull { it.bodyBatteryHigh }.lastOrNull()
            val stress = trend.mapNotNull { it.avgStress }.lastOrNull()
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScoreTile(stringResource(R.string.readiness_title), readiness, metrics.readiness, Modifier.weight(1f), onClick = { scrollTo(ChartMetric.READINESS) })
                    ScoreTile(stringResource(R.string.score_sleep), sleepScore, metrics.sleep, Modifier.weight(1f), onClick = { scrollTo(ChartMetric.SLEEP_SCORE) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScoreTile(stringResource(R.string.body_battery), battery, metrics.battery, Modifier.weight(1f), onClick = { scrollTo(ChartMetric.BODY_BATTERY) })
                    ScoreTile(stringResource(R.string.stress), stress, metrics.stress, Modifier.weight(1f), onClick = { scrollTo(ChartMetric.STRESS) })
                }
                Text(stringResource(R.string.scores_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        section("READINESS") {
            val values = state.readiness.map { it.second?.toDouble() }
            if (values.any { it != null }) {
                MetricCard(title = stringResource(R.string.score_readiness_30), subtitle = null) {
                    LineChart(values, description = stringResource(R.string.score_readiness_30), color = OpenConnectColors.metrics.readiness, labels = state.readiness.map { Fmt.shortDate(it.first) }, format = { insightValue(ChartMetric.READINESS, it) })
                    InsightCaption(ChartInsight.of(ChartMetric.READINESS, state.readiness.map { it.first to it.second?.toDouble() }), ChartMetric.READINESS, first, lastDay)
                }
            }
        }
        section("SLEEP_SCORE") {
            val values = state.sleepScores.map { it.second }
            if (values.any { it != null }) {
                MetricCard(title = stringResource(R.string.score_sleep_30), subtitle = null) {
                    LineChart(values, description = stringResource(R.string.score_sleep_30), color = OpenConnectColors.metrics.sleep, labels = state.sleepScores.map { Fmt.shortDate(it.first) }, format = { insightValue(ChartMetric.SLEEP_SCORE, it) })
                    InsightCaption(ChartInsight.of(ChartMetric.SLEEP_SCORE, state.sleepScores), ChartMetric.SLEEP_SCORE, first, lastDay)
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
                InsightCaption(insightFor(ChartMetric.STEPS, steps), ChartMetric.STEPS, first, lastDay)
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
                InsightCaption(insightFor(ChartMetric.SLEEP, sleep), ChartMetric.SLEEP, first, lastDay)
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
                InsightCaption(insightFor(ChartMetric.RESTING_HR, resting), ChartMetric.RESTING_HR, first, lastDay)
            }
        }
        section("BODY_BATTERY") {
            val battery = trend.map { it.bodyBatteryHigh?.toDouble() }
            MetricCard(title = stringResource(R.string.body_battery), subtitle = null) {
                LineChart(battery, description = stringResource(R.string.body_battery), color = OpenConnectColors.metrics.battery, labels = labels, format = { insightValue(ChartMetric.BODY_BATTERY, it) })
                InsightCaption(insightFor(ChartMetric.BODY_BATTERY, battery), ChartMetric.BODY_BATTERY, first, lastDay)
            }
        }
        // Values only the watch provides: shown once there is at least one reading in the last 30 days.
        val byDay = state.dailies.associateBy { it.date }
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
                    InsightCaption(insightFor(metric, values), metric, first, lastDay)
                }
            }
        }
        section("STRESS") {
            val stress = trend.map { it.avgStress?.toDouble() }
            MetricCard(title = stringResource(R.string.stress), subtitle = null) {
                LineChart(stress, description = stringResource(R.string.stress), color = OpenConnectColors.metrics.stress, labels = labels, format = { insightValue(ChartMetric.STRESS, it) })
                InsightCaption(insightFor(ChartMetric.STRESS, stress), ChartMetric.STRESS, first, lastDay)
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
