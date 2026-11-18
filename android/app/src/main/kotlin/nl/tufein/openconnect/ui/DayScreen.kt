package nl.tufein.openconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.ChartInsight
import nl.tufein.openconnect.core.ChartMetric
import nl.tufein.openconnect.core.DayInsight
import nl.tufein.openconnect.core.DayInsights
import nl.tufein.openconnect.core.DayReading
import nl.tufein.openconnect.core.DayWorkout
import nl.tufein.openconnect.core.Extreme
import nl.tufein.openconnect.core.InsightSource
import nl.tufein.openconnect.core.Verdict
import kotlin.math.abs
import kotlin.math.roundToInt

private fun sourceName(source: InsightSource): Int = when (source) {
    InsightSource.GARMIN -> R.string.day_source_garmin
    InsightSource.OURA -> R.string.day_source_oura
    InsightSource.WHOOP -> R.string.day_source_whoop
}

@Composable
private fun verdictColor(verdict: Verdict?): Color = when (verdict) {
    Verdict.GOOD -> MaterialTheme.colorScheme.primary
    Verdict.FAIR -> MaterialTheme.colorScheme.secondary
    Verdict.WATCH -> MaterialTheme.colorScheme.error
    null -> MaterialTheme.colorScheme.outline
}

private fun verdictText(verdict: Verdict): Int = when (verdict) {
    Verdict.GOOD -> R.string.insight_good
    Verdict.FAIR -> R.string.insight_fair
    Verdict.WATCH -> R.string.insight_watch
}

@Composable
private fun metricColor(metric: ChartMetric): Color {
    val m = OpenConnectColors.metrics
    return when (metric) {
        ChartMetric.STEPS, ChartMetric.ACTIVITY_SCORE -> m.steps
        ChartMetric.SLEEP, ChartMetric.SLEEP_SCORE -> m.sleep
        ChartMetric.RESTING_HR, ChartMetric.LOWEST_HR -> m.heart
        ChartMetric.BODY_BATTERY -> m.battery
        ChartMetric.STRESS, ChartMetric.TEMPERATURE, ChartMetric.STRAIN -> m.stress
        ChartMetric.READINESS -> m.readiness
        ChartMetric.HRV, ChartMetric.SPO2, ChartMetric.RESPIRATION -> m.distance
    }
}

/**
 * The Day tab of Insights (v0.44.0): one day read against your own days before it, from the watch or from the ring. Three
 * dropdowns choose the source (only when both devices are in use), the day (with arrows for the day before and after) and
 * the measure that is drawn as a chart under the readings; tapping a reading chooses its chart too.
 */
@Composable
fun DayContent(state: DayState, contentPadding: PaddingValues, onOpenActivity: (String) -> Unit) {
    var savedSource by rememberSaveable { mutableStateOf<String?>(null) }
    var savedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var savedMetric by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        val available = listOfNotNull(
            state.garmin?.let { InsightSource.GARMIN },
            state.oura?.let { InsightSource.OURA },
            state.whoop?.let { InsightSource.WHOOP },
        )
        val source = available.firstOrNull { it.name == savedSource } ?: available.firstOrNull() ?: return@LazyColumn
        val set = when (source) {
            InsightSource.OURA -> state.oura
            InsightSource.WHOOP -> state.whoop
            InsightSource.GARMIN -> state.garmin
        } ?: return@LazyColumn
        val today = state.today
        val day = DayInsights.pick(set, today, savedDay?.let(LocalDate::ofEpochDay))
        val insight = DayInsights.of(set, day)
        val metric = source.metrics.firstOrNull { it.name == savedMetric } ?: insight.readings.firstOrNull()?.metric ?: source.metrics.first()
        val choices = DayInsights.choices(today)

        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (available.size > 1) {
                        val names = available.associateWith { stringResource(sourceName(it)) }
                        PixelSelect(
                            label = stringResource(R.string.day_source),
                            selected = source,
                            options = available,
                            name = { names.getValue(it) },
                            onSelect = { savedSource = it.name },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val titles = source.metrics.associateWith { metricName(it) }
                    PixelSelect(
                        label = stringResource(R.string.day_measure),
                        selected = metric,
                        options = source.metrics,
                        name = { titles.getValue(it) },
                        onSelect = { savedMetric = it.name },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PixelIconButton(onClick = { savedDay = DayInsights.step(day, -1, today).toEpochDay() }, enabled = day.isAfter(choices.last())) {
                        PixelIcon(PixelIcons.ChevronLeft, contentDescription = stringResource(R.string.day_previous))
                    }
                    PixelSelect(
                        label = stringResource(R.string.day_pick),
                        selected = day,
                        options = choices,
                        name = { Fmt.dayLabel(it) },
                        onSelect = { savedDay = it.toEpochDay() },
                        modifier = Modifier.weight(1f),
                    )
                    PixelIconButton(onClick = { savedDay = DayInsights.step(day, 1, today).toEpochDay() }, enabled = day.isBefore(today)) {
                        PixelIcon(PixelIcons.ChevronRight, contentDescription = stringResource(R.string.day_next))
                    }
                }
            }
        }
        item(key = "day") { DayCard(insight, day) }
        if (insight.readings.isNotEmpty()) {
            item(key = "readings") {
                SurfaceCard {
                    for (reading in insight.readings) {
                        ReadingRow(reading, selected = reading.metric == metric) { savedMetric = reading.metric.name }
                    }
                    if (insight.missing.isNotEmpty()) {
                        val names = insight.missing.map { metricName(it) }
                        Text(
                            stringResource(R.string.day_missing, names.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.day_tap_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!insight.isEmpty || set.hasAnyData) {
            item(key = "chart-" + source.name + metric.name + day.toEpochDay()) {
                val series = set.series(metric, day, DayInsights.CHART_DAYS)
                val values = series.map { it.second }
                val labels = series.map { Fmt.shortDate(it.first) }
                val title = metricName(metric)
                val color = metricColor(metric)
                SurfaceCard {
                    Text(stringResource(R.string.day_chart_title, title), style = MaterialTheme.typography.titleMedium)
                    val description = stringResource(R.string.day_chart_title, title)
                    when (metric) {
                        ChartMetric.STEPS -> BarChart(values, description, color = color, goal = ChartInsight.STEP_GOAL, labels = labels, highlight = values.lastIndex, format = { insightValue(metric, it) })
                        ChartMetric.SLEEP -> BarChart(values, description, color = color, goal = 8.0, labels = labels, highlight = values.lastIndex, format = { insightValue(metric, it) })
                        else -> LineChart(values, description, color = color, includeZero = metric == ChartMetric.TEMPERATURE, labels = labels, highlight = values.lastIndex, format = { insightValue(metric, it) })
                    }
                    InsightCaption(ChartInsight.of(metric, series), metric, source = source)
                }
            }
        }
        if (insight.workouts.isNotEmpty()) {
            item(key = "workouts") {
                SectionTitle(stringResource(R.string.day_workouts))
                SurfaceCard {
                    for (w in insight.workouts) WorkoutRow(w, onOpenActivity)
                }
            }
        }
    }
}

@Composable
private fun DayCard(insight: DayInsight, day: LocalDate) {
    SurfaceCard(accent = if (insight.good > 0 && insight.good * 2 >= insight.judged) MaterialTheme.colorScheme.primary else null) {
        Text(Fmt.longDate(day), style = MaterialTheme.typography.titleLarge)
        when {
            insight.isEmpty -> {
                Text(stringResource(R.string.day_none, stringResource(sourceName(insight.source))), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.day_none_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            insight.judged > 0 -> Text(
                stringResource(R.string.day_summary, insight.good, insight.judged),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ReadingRow(reading: DayReading, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = metricName(reading.metric)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(verdictColor(reading.verdict)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = if (selected) colors.primary else colors.onSurface)
            Text(readingDetail(reading), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Text(insightValue(reading.metric, reading.value), style = MaterialTheme.typography.titleMedium)
    }
}

/** "Fair · usual 7,850 · 12% higher (better) · highest in 4 weeks": the words under a reading. */
@Composable
private fun readingDetail(reading: DayReading): String {
    val parts = ArrayList<String>()
    reading.verdict?.let { parts += stringResource(verdictText(it)) }
    reading.usual?.let { parts += stringResource(R.string.day_usual, insightValue(reading.metric, it)) }
    reading.changePct?.let { pct ->
        val size = if (abs(pct) >= 10) abs(pct).roundToInt().toString() else Fmt.oneDecimal(abs(pct))
        val change = when {
            abs(pct) < 1.0 -> stringResource(R.string.day_same)
            pct > 0 -> stringResource(R.string.day_higher, size)
            else -> stringResource(R.string.day_lower, size)
        }
        val mark = when (reading.better) {
            true -> " (" + stringResource(R.string.insight_better) + ")"
            false -> " (" + stringResource(R.string.insight_worse) + ")"
            null -> ""
        }
        parts += change + mark
    }
    reading.extreme?.let { parts += stringResource(if (it == Extreme.HIGHEST) R.string.day_highest else R.string.day_lowest) }
    return parts.joinToString(" · ")
}

@Composable
private fun WorkoutRow(w: DayWorkout, onOpenActivity: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenActivity(w.id) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SportBadge(w.sport)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(w.name.ifBlank { stringResource(sportLabel(w.sport)) }, style = MaterialTheme.typography.bodyLarge)
            val parts = listOfNotNull(
                Fmt.time(Instant.ofEpochMilli(w.startMs)),
                Fmt.duration(w.durationS),
                w.distanceM?.let { Fmt.km(it) + " km" },
                w.calories?.let { "$it kcal" },
            )
            Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
