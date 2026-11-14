package nl.tufein.openconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.DayTotals
import nl.tufein.openconnect.core.MonthView
import nl.tufein.openconnect.core.StreakSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "November 2026", in the language of the phone. */
fun monthTitle(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month)

/** The tag of one day's cell, for tests: `day-2026-11-14`. */
fun dayTag(day: LocalDate): String = "day-$day"

@Composable
fun CalendarContent(
    state: CalendarState,
    onShift: (Int) -> Unit,
    onOpenActivity: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val view = state.view
        if (!state.loaded || view == null) return@LazyColumn
        item { StreakTiles(state.streaks) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                PixelIconButton(onClick = { onShift(-1) }, enabled = state.canGoPrevious) {
                    PixelIcon(PixelIcons.ChevronLeft, contentDescription = stringResource(R.string.calendar_prev))
                }
                Text(monthTitle(view.month), style = MaterialTheme.typography.titleMedium)
                PixelIconButton(onClick = { onShift(1) }, enabled = state.canGoNext) {
                    PixelIcon(PixelIcons.ChevronRight, contentDescription = stringResource(R.string.calendar_next))
                }
            }
        }
        item { MonthCard(view, onOpenActivity) }
        item {
            Text(
                stringResource(R.string.calendar_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StreakTiles(s: StreakSummary) {
    val colors = OpenConnectColors.metrics
    TileRow(
        { StatTile(stringResource(R.string.calendar_current_days), s.currentDays.length.toString(), it, unit = stringResource(R.string.calendar_days), color = colors.steps) },
        { StatTile(stringResource(R.string.calendar_longest_days), s.longestDays.length.toString(), it, unit = stringResource(R.string.calendar_days), color = colors.distance) },
    )
    Spacer(Modifier.height(12.dp))
    TileRow(
        { StatTile(stringResource(R.string.calendar_current_weeks), s.currentWeeks.length.toString(), it, unit = stringResource(R.string.calendar_weeks), color = colors.steps) },
        { StatTile(stringResource(R.string.calendar_longest_weeks), s.longestWeeks.length.toString(), it, unit = stringResource(R.string.calendar_weeks), color = colors.distance) },
    )
    if (s.todayOpen) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.calendar_open_today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthCard(view: MonthView, onOpenActivity: (String) -> Unit) {
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = selectedDay?.let { LocalDate.ofEpochDay(it) }?.takeIf { YearMonth.from(it) == view.month }
    SurfaceCard {
        Text(
            stringResource(R.string.calendar_month_summary, view.totalCount, view.activeDays, Fmt.oneDecimal(view.totalDistanceM / 1000.0)),
            style = MaterialTheme.typography.bodyMedium,
        )
        MonthGrid(view, selected) { selectedDay = it.toEpochDay() }
        if (selected == null) {
            Text(stringResource(R.string.calendar_tap), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            DayDetail(selected, view.days[selected], onOpenActivity)
        }
    }
}

@Composable
private fun MonthGrid(view: MonthView, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        for (d in DayOfWeek.values()) {
            Text(
                d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val cells: List<LocalDate?> = List(view.leadingBlanks) { null } + (1..view.month.lengthOfMonth()).map { view.month.atDay(it) }
    val longest = view.days.values.maxOfOrNull { it.distanceM } ?: 0.0
    for (week in cells.chunked(7)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val day = week.getOrNull(i)
                DayCell(day, day?.let { view.days[it] }, longest, selected = day != null && day == selected, onClick = { day?.let(onSelect) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate?, totals: DayTotals?, longestM: Double, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    if (day == null) {
        Spacer(modifier.height(CELL_HEIGHT))
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    val share = if (totals == null || longestM <= 0.0) 0f else (totals.distanceM / longestM).coerceIn(0.0, 1.0).toFloat()
    val background = if (totals == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else primary.copy(alpha = 0.25f + 0.55f * share)
    val shape = MaterialTheme.shapes.extraSmall
    Column(
        modifier
            .height(CELL_HEIGHT)
            .padding(2.dp)
            .clip(shape)
            .background(background)
            .then(if (selected) Modifier.border(2.dp, primary, shape) else Modifier)
            .testTag(dayTag(day))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        if (totals != null) {
            val label = if (totals.distanceM > 0) Fmt.oneDecimal(totals.distanceM / 1000.0) else "${totals.count}×"
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private val CELL_HEIGHT = 48.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDetail(day: LocalDate, totals: DayTotals?, onOpenActivity: (String) -> Unit) {
    Text(Fmt.date(day), style = MaterialTheme.typography.titleSmall)
    if (totals == null) {
        Text(stringResource(R.string.calendar_rest_day), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val resources = LocalContext.current.resources
    Text(
        resources.getQuantityString(R.plurals.calendar_day_summary, totals.count, totals.count, Fmt.oneDecimal(totals.distanceM / 1000.0)),
        style = MaterialTheme.typography.bodyMedium,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (item in totals.items) {
            SecondaryButton(onClick = { onOpenActivity(item.id) }) { Text(item.name) }
        }
    }
}
