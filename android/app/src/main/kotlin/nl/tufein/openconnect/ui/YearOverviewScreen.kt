package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.YearOverview
import java.time.format.TextStyle
import kotlin.math.roundToInt

@Composable
private fun YearRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** The totals of the twelve months above the charts, each only when that figure was measured. */
@Composable
private fun YearSummaryCard(overview: YearOverview) {
    val metrics = OpenConnectColors.metrics
    SurfaceCard(accent = metrics.steps) {
        Text(stringResource(R.string.year_title, overview.year), style = MaterialTheme.typography.headlineSmall, color = metrics.steps)
        overview.stepsTotal?.let { YearRow(stringResource(R.string.year_steps_total), Fmt.thousands(it.toInt())) }
        overview.sleepHoursAvg?.let { YearRow(stringResource(R.string.year_sleep_avg), stringResource(R.string.year_hours, Fmt.oneDecimal(it))) }
        overview.restingHrAvg?.let { YearRow(stringResource(R.string.year_rhr_avg), stringResource(R.string.year_bpm, it)) }
        YearRow(stringResource(R.string.year_trainings_total), overview.trainingsTotal.toString())
    }
}

/** A chart of one figure per month; a year without that figure says so instead of drawing an empty grid. */
@Composable
private fun MonthChart(
    title: String,
    months: List<String>,
    values: List<Double?>,
    color: androidx.compose.ui.graphics.Color,
    bars: Boolean,
    format: (Double) -> String,
) {
    if (values.all { it == null }) {
        SurfaceCard { Text(stringResource(R.string.year_no_data, title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        SurfaceCard {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (bars) {
                BarChart(values, title, Modifier.fillMaxWidth(), color = color, labels = months, maxLabels = 12, format = format)
            } else {
                LineChart(values, title, Modifier.fillMaxWidth(), color = color, labels = months, maxLabels = 12, format = format)
            }
        }
    }
}

/** The year in twelve months: steps, sleep, resting heart rate and trainings next to each other. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YearOverviewContent(state: YearState, onYear: (Int) -> Unit, contentPadding: PaddingValues) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    val locale = currentLocale()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        if (state.years.size > 1) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (year in state.years.take(8)) {
                        PixelChip(selected = year == state.selected, onClick = { onYear(year) }, label = { Text(year.toString()) })
                    }
                }
            }
        }
        val overview = state.overview
        if (overview == null) {
            item {
                SurfaceCard { Text(stringResource(R.string.year_none), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
        } else {
            val months = overview.months
            val labels = months.map { it.month.getDisplayName(TextStyle.SHORT, locale) }
            item { YearSummaryCard(overview) }
            item {
                MonthChart(
                    stringResource(R.string.year_steps), labels, months.map { it.steps?.toDouble() }, metrics.steps, bars = true,
                    format = { Fmt.thousands(it.roundToInt()) },
                )
            }
            item {
                MonthChart(
                    stringResource(R.string.year_sleep), labels, months.map { it.sleepHours }, metrics.sleep, bars = false,
                    format = { Fmt.oneDecimal(it) },
                )
            }
            item {
                MonthChart(
                    stringResource(R.string.year_rhr), labels, months.map { it.restingHr?.toDouble() }, metrics.heart, bars = false,
                    format = { it.roundToInt().toString() },
                )
            }
            item {
                MonthChart(
                    stringResource(R.string.year_trainings), labels, months.map { it.trainings.toDouble() }, metrics.calories, bars = true,
                    format = { it.roundToInt().toString() },
                )
            }
            item { Text(stringResource(R.string.year_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        }
    }
}
