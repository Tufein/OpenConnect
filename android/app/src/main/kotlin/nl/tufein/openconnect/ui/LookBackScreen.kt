package nl.tufein.openconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.LookBackMetric
import nl.tufein.openconnect.core.LookBackReport
import nl.tufein.openconnect.core.LookBackTotals
import nl.tufein.openconnect.core.MonthTable
import nl.tufein.openconnect.core.RecordEvent
import nl.tufein.openconnect.core.RecordKind
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.YearComparison
import nl.tufein.openconnect.core.YearSummary
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** How many beaten records the list shows; the rest is counted in a line below it. */
private const val RECORDS_SHOWN = 25

private val MonthColumnWidth = 44.dp
private val YearColumnWidth = 60.dp

@Composable
private fun LookRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** Distance, time or count of [totals] the way it is written in a row. */
@Composable
private fun figure(metric: LookBackMetric, totals: LookBackTotals, locale: Locale): String = when (metric) {
    LookBackMetric.DISTANCE -> stringResource(R.string.lookback_km, Fmt.oneDecimal(totals.distanceM / 1000.0, locale))
    LookBackMetric.TIME -> stringResource(R.string.year_hours, Fmt.oneDecimal(totals.durationS / 3600.0, locale))
    LookBackMetric.COUNT -> Fmt.thousands(totals.count, locale)
}

@Composable
private fun metricLabel(metric: LookBackMetric): String = stringResource(
    when (metric) {
        LookBackMetric.DISTANCE -> R.string.lookback_distance
        LookBackMetric.TIME -> R.string.lookback_time
        LookBackMetric.COUNT -> R.string.lookback_count
    },
)

/** A figure in a grid cell: a whole kilometre, a tenth of an hour or a count, and a dash for nothing. */
private fun cellText(metric: LookBackMetric, totals: LookBackTotals, locale: Locale): String {
    if (totals.count == 0) return "–"
    return when (metric) {
        LookBackMetric.DISTANCE -> Fmt.thousands(Math.round(totals.distanceM / 1000.0).toInt(), locale)
        LookBackMetric.TIME -> Fmt.oneDecimal(totals.durationS / 3600.0, locale)
        LookBackMetric.COUNT -> totals.count.toString()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SportChips(report: LookBackReport, onSport: (Sport?) -> Unit) {
    if (report.sports.size < 2) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelChip(
            selected = report.sport == null,
            onClick = { onSport(null) },
            label = { Text(stringResource(R.string.lookback_all_sports)) },
            modifier = Modifier.testTag("lookback-sport-all"),
        )
        for (sport in report.sports) {
            PixelChip(
                selected = report.sport == sport,
                onClick = { onSport(sport) },
                label = { Text(stringResource(sportLabel(sport))) },
                modifier = Modifier.testTag("lookback-sport-${sport.key}"),
            )
        }
    }
}

@Composable
private fun LegendKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** A signed difference such as `+32.0` or `-13.5`, in the phone's number format. */
private fun signed(value: Double, locale: Locale): String = String.format(locale, "%+.1f", value)

/** This year so far against last year to the same day: three figures with the difference, then the two lines. */
@Composable
private fun ComparisonCards(c: YearComparison, locale: Locale) {
    val metrics = OpenConnectColors.metrics
    val colors = MaterialTheme.colorScheme
    SurfaceCard(accent = metrics.distance) {
        Text(stringResource(R.string.lookback_vs_title, c.year, c.previousYear), style = MaterialTheme.typography.headlineSmall, color = metrics.distance)
        Text(stringResource(R.string.lookback_asof, Fmt.shortDate(c.asOf, locale)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        for (metric in LookBackMetric.entries) {
            val difference = when (metric) {
                LookBackMetric.DISTANCE -> stringResource(R.string.lookback_km, signed(c.distanceDiffM / 1000.0, locale))
                LookBackMetric.TIME -> stringResource(R.string.year_hours, signed(c.durationDiffS / 3600.0, locale))
                LookBackMetric.COUNT -> String.format(locale, "%+d", c.countDiff)
            }
            val percent = c.percent(metric)?.let { stringResource(R.string.lookback_percent, it) }
            LookRow(metricLabel(metric), figure(metric, c.thisYear, locale))
            Text(
                stringResource(
                    R.string.lookback_vs_row,
                    c.previousYear,
                    figure(metric, c.previousToDate, locale),
                    listOfNotNull(difference, percent).joinToString(" "),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (c.previousToDate.count == 0) {
            Text(stringResource(R.string.lookback_no_previous, c.previousYear), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    val months = Month.entries.map { it.getDisplayName(TextStyle.SHORT, locale) }
    val title = stringResource(R.string.lookback_chart_distance)
    SurfaceCard {
        Text(title, style = MaterialTheme.typography.titleSmall)
        LineChart(
            values = c.thisYearLine.map { it?.let { totals -> totals.distanceM / 1000.0 } },
            description = title,
            modifier = Modifier.fillMaxWidth(),
            color = metrics.distance,
            includeZero = true,
            labels = months,
            maxLabels = 12,
            compare = c.previousYearLine.map { it.distanceM / 1000.0 },
            format = { Fmt.thousands(Math.round(it).toInt(), locale) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendKey(metrics.distance, c.year.toString())
            LegendKey(colors.onSurfaceVariant, c.previousYear.toString())
        }
    }
    val timeTitle = stringResource(R.string.lookback_chart_time)
    SurfaceCard {
        Text(timeTitle, style = MaterialTheme.typography.titleSmall)
        LineChart(
            values = c.thisYearLine.map { it?.let { totals -> totals.durationS / 3600.0 } },
            description = timeTitle,
            modifier = Modifier.fillMaxWidth(),
            color = metrics.sleep,
            includeZero = true,
            labels = months,
            maxLabels = 12,
            compare = c.previousYearLine.map { it.durationS / 3600.0 },
            format = { Fmt.oneDecimal(it, locale) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendKey(metrics.sleep, c.year.toString())
            LegendKey(colors.onSurfaceVariant, c.previousYear.toString())
        }
    }
}

@Composable
private fun GridCell(text: String, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(
        text,
        modifier = modifier.width(width).padding(horizontal = 2.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

/** Every month of every year as a grid, the heaviest month marked; wider than the screen it scrolls sideways. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthGridCard(table: MonthTable, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    val mark = OpenConnectColors.metrics.distance.copy(alpha = 0.3f)
    var metric by rememberSaveable { mutableStateOf(LookBackMetric.DISTANCE) }
    val heaviest = table.heaviest(metric)
    SurfaceCard {
        Text(stringResource(R.string.lookback_grid_title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (option in LookBackMetric.entries) {
                PixelChip(
                    selected = metric == option,
                    onClick = { metric = option },
                    label = { Text(metricLabel(option)) },
                    modifier = Modifier.testTag("lookback-metric-${option.name.lowercase()}"),
                )
            }
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Spacer(Modifier.width(MonthColumnWidth))
                for (row in table.years) GridCell(row.year.toString(), YearColumnWidth, bold = true)
            }
            for (monthIndex in 0 until 12) {
                val month = Month.of(monthIndex + 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        month.getDisplayName(TextStyle.SHORT, locale),
                        modifier = Modifier.width(MonthColumnWidth),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                    for (row in table.years) {
                        val totals = row.months[monthIndex]
                        val isHeaviest = heaviest != null && heaviest.year == row.year && heaviest.month == month
                        val text = cellText(metric, totals, locale)
                        val described = stringResource(
                            R.string.lookback_cell,
                            month.getDisplayName(TextStyle.FULL, locale),
                            row.year,
                            figure(metric, totals, locale),
                        )
                        GridCell(
                            text,
                            YearColumnWidth,
                            modifier = (if (isHeaviest) Modifier.background(mark) else Modifier)
                                .then(if (totals.count == 0) Modifier.clearAndSetSemantics {} else Modifier.semantics { contentDescription = described }),
                            bold = isHeaviest,
                        )
                    }
                }
            }
            Row {
                Text(
                    stringResource(R.string.lookback_total),
                    modifier = Modifier.width(MonthColumnWidth).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                for (row in table.years) GridCell(cellText(metric, row.total, locale), YearColumnWidth, bold = true)
            }
        }
        if (heaviest != null) {
            Text(
                stringResource(
                    R.string.lookback_heaviest,
                    Fmt.monthYear(YearMonth.of(heaviest.year, heaviest.month), locale).replaceFirstChar { it.uppercase() },
                    figure(metric, heaviest.totals, locale),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** One year: its totals, each sport on one line, the busiest week and how many weeks were quiet. */
@Composable
private fun YearCard(summary: YearSummary, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    SurfaceCard {
        Text(summary.year.toString(), style = MaterialTheme.typography.titleMedium)
        LookRow(stringResource(R.string.lookback_distance), figure(LookBackMetric.DISTANCE, summary.totals, locale))
        LookRow(stringResource(R.string.lookback_time), figure(LookBackMetric.TIME, summary.totals, locale))
        LookRow(stringResource(R.string.lookback_count), figure(LookBackMetric.COUNT, summary.totals, locale))
        Text(stringResource(R.string.lookback_by_sport), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        val sports = summary.sports.map { entry ->
            val name = stringResource(sportLabel(entry.sport))
            // A sport without a distance (strength, yoga) is told in hours instead.
            val amount = if (entry.totals.distanceM >= 1000.0) {
                figure(LookBackMetric.DISTANCE, entry.totals, locale)
            } else {
                figure(LookBackMetric.TIME, entry.totals, locale)
            }
            "$name $amount"
        }
        Text(sports.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        val week = summary.busiestWeek
        LookRow(
            stringResource(R.string.lookback_busiest_week),
            stringResource(R.string.lookback_busiest_value, Fmt.shortDate(week.weekStart, locale), Fmt.oneDecimal(week.totals.durationS / 3600.0, locale)),
        )
        LookRow(stringResource(R.string.lookback_quiet_weeks), summary.quietWeeks.toString())
    }
}

private fun recordValue(event: RecordEvent, value: Double, locale: Locale, fine: Boolean = false): String = when (event.kind) {
    RecordKind.LONGEST_DISTANCE -> if (fine) String.format(locale, "%.3f", value / 1000) + " km" else Fmt.km(value, locale) + " km"
    RecordKind.LONGEST_DURATION -> Fmt.duration(value)
    RecordKind.MOST_ELEVATION -> if (fine) Fmt.oneDecimal(value, locale) + " m" else Math.rint(value).toInt().toString() + " m"
    RecordKind.FASTEST_PACE -> Fmt.pace(value) + " /km"
}

/**
 * The new value and the one it beat, as text. A record can be beaten by less than the display rounds to (50.4 m against
 * 50.2 m reads "50 m, before 50 m"); then both get one more digit so the line shows the step instead of looking like a bug.
 */
internal fun recordValues(event: RecordEvent, locale: Locale): Pair<String, String> {
    val now = recordValue(event, event.value, locale)
    val before = recordValue(event, event.previousValue, locale)
    if (now != before) return now to before
    return recordValue(event, event.value, locale, fine = true) to recordValue(event, event.previousValue, locale, fine = true)
}

@Composable
private fun recordName(kind: RecordKind): String = stringResource(
    when (kind) {
        RecordKind.LONGEST_DISTANCE -> R.string.record_longest_distance
        RecordKind.LONGEST_DURATION -> R.string.record_longest_duration
        RecordKind.MOST_ELEVATION -> R.string.record_most_elevation
        RecordKind.FASTEST_PACE -> R.string.record_fastest_pace
    },
)

/** When each record was beaten, newest first; a tap opens the activity that did it. */
@Composable
private fun RecordsCard(records: List<RecordEvent>, onOpenActivity: (String) -> Unit, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    SurfaceCard(accent = OpenConnectColors.metrics.calories) {
        Text(stringResource(R.string.lookback_records_title), style = MaterialTheme.typography.titleMedium)
        if (records.isEmpty()) {
            Text(stringResource(R.string.lookback_records_none), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            return@SurfaceCard
        }
        for (event in records.take(RECORDS_SHOWN)) {
            val values = recordValues(event, locale)
            Column(Modifier.fillMaxWidth().clickable { onOpenActivity(event.activityId) }.padding(vertical = 6.dp)) {
                Text(
                    stringResource(
                        R.string.lookback_record_line,
                        Fmt.date(event.date, locale),
                        stringResource(sportLabel(event.sport)),
                        recordName(event.kind),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        R.string.lookback_record_value,
                        values.first,
                        values.second,
                        Fmt.date(event.previousDate, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (records.size > RECORDS_SHOWN) {
            Text(stringResource(R.string.lookback_records_more, RECORDS_SHOWN, records.size), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * Terugblik / Look back (M15): this year against last, every month of every year, each year on its own and the records
 * that were beaten along the way. All of it is worked out on the phone from the stored activities.
 */
@Composable
fun LookBackContent(
    state: LookBackState,
    onSport: (Sport?) -> Unit,
    onLoadDemoYears: () -> Unit,
    onOpenActivity: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val report = state.report
        if (!state.loaded || report == null) return@LazyColumn
        if (report.isEmpty) {
            item {
                SurfaceCard {
                    Text(stringResource(R.string.lookback_empty), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    SecondaryButton(onClick = onLoadDemoYears) { Text(stringResource(R.string.lookback_demo_years)) }
                }
            }
            return@LazyColumn
        }
        item { SportChips(report, onSport) }
        report.comparison?.let { comparison -> item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { ComparisonCards(comparison, locale) } } }
        report.table?.let { table -> item { MonthGridCard(table, locale) } }
        item { Text(stringResource(R.string.lookback_years_title), style = MaterialTheme.typography.titleMedium) }
        items(report.years.size) { index -> YearCard(report.years[index], locale) }
        item { RecordsCard(report.records, onOpenActivity, locale) }
        item { Text(stringResource(R.string.lookback_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}
