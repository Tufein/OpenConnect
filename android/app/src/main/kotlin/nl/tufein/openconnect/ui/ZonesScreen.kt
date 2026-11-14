package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.ActivityZones
import nl.tufein.openconnect.core.HeartZones
import nl.tufein.openconnect.core.MaxHrSource
import nl.tufein.openconnect.core.WeekZones
import nl.tufein.openconnect.core.ZoneOverview
import nl.tufein.openconnect.core.ZoneRange
import nl.tufein.openconnect.core.ZoneTimes
import nl.tufein.openconnect.core.ZoneVerdict
import kotlin.math.roundToInt

/** A change to the maximum heart rate the zones are worked out from. */
sealed interface ZonesAction {
    /** Move the maximum by [bpm] beats (negative lowers it). */
    data class ShiftMax(val bpm: Int) : ZonesAction

    /** Stop using a typed-in maximum and take the highest one measured. */
    data object UseMeasured : ZonesAction
}

/** One colour per zone, from a calm blue to red. */
private val ZONE_COLORS = listOf(Color(0xFF7E9CB5), Color(0xFF4CAF50), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFF44336))

private fun zoneName(zone: Int): Int = when (zone) {
    1 -> R.string.zone_name_1
    2 -> R.string.zone_name_2
    3 -> R.string.zone_name_3
    4 -> R.string.zone_name_4
    else -> R.string.zone_name_5
}

private fun verdictText(v: ZoneVerdict): Int = when (v) {
    ZoneVerdict.NO_DATA -> R.string.zones_verdict_no_data
    ZoneVerdict.BALANCED -> R.string.zones_verdict_balanced
    ZoneVerdict.TOO_HARD -> R.string.zones_verdict_too_hard
    ZoneVerdict.TOO_MIDDLE -> R.string.zones_verdict_too_middle
    ZoneVerdict.ALL_EASY -> R.string.zones_verdict_all_easy
}

@Composable
fun ZonesContent(state: ZonesState, onAction: (ZonesAction) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        val overview = state.overview
        item { MaxHrCard(overview, onAction) }
        if (overview.ranges.isNotEmpty()) {
            item { ZoneTableCard(overview.ranges) }
            item { WeekCard(overview) }
            item { EarlierWeeksCard(overview.weeks.dropLast(1).reversed()) }
        }
        item {
            Text(
                stringResource(R.string.zones_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaxHrCard(overview: ZoneOverview, onAction: (ZonesAction) -> Unit) {
    SectionTitle(stringResource(R.string.zones_max_title))
    SurfaceCard {
        val max = overview.maxHr
        if (max.bpm > 0) Text(stringResource(R.string.zones_max_value, max.bpm), style = MaterialTheme.typography.headlineSmall)
        val note = when (max.source) {
            MaxHrSource.SETTING -> R.string.zones_max_setting
            MaxHrSource.MEASURED -> R.string.zones_max_measured
            MaxHrSource.NONE -> R.string.zones_max_none
        }
        Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(onClick = { onAction(ZonesAction.ShiftMax(-5)) }) { Text(stringResource(R.string.zones_minus)) }
            SecondaryButton(onClick = { onAction(ZonesAction.ShiftMax(5)) }) { Text(stringResource(R.string.zones_plus)) }
            if (max.source == MaxHrSource.SETTING) {
                SecondaryButton(onClick = { onAction(ZonesAction.UseMeasured) }) { Text(stringResource(R.string.zones_use_measured)) }
            }
        }
    }
}

@Composable
private fun ZoneTableCard(ranges: List<ZoneRange>) {
    SectionTitle(stringResource(R.string.zones_table_title))
    SurfaceCard {
        for (r in ranges) {
            val to = r.to
            val text = if (to == null) {
                stringResource(R.string.zones_range_top, r.zone, stringResource(zoneName(r.zone)), r.from)
            } else {
                stringResource(R.string.zones_range, r.zone, stringResource(zoneName(r.zone)), r.from, to)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WeekCard(overview: ZoneOverview) {
    SectionTitle(stringResource(R.string.zones_week_title))
    SurfaceCard {
        val week = overview.thisWeek
        if (week == null || week.times.zoned <= 0.0) {
            Text(stringResource(R.string.zones_week_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            ZoneBars(week.times)
            if (!week.exact) {
                Text(stringResource(R.string.zones_guess), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(verdictText(overview.verdict)), style = MaterialTheme.typography.bodyMedium)
    }
}

/** One bar per zone: how long, and how much of the zoned time that is. */
@Composable
private fun ZoneBars(times: ZoneTimes) {
    val zoned = times.zoned
    for (zone in 1..HeartZones.ZONES) {
        val seconds = times.seconds[zone]
        val share = if (zoned > 0) seconds / zoned else 0.0
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Z$zone", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
            SegmentBar(share.toFloat(), ZONE_COLORS[zone - 1], Modifier.weight(1f).height(10.dp), segments = 20)
            Text(
                stringResource(R.string.zones_bar_value, Fmt.duration(seconds), (share * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(96.dp),
            )
        }
    }
    if (times.seconds[0] > 0) {
        Text(
            stringResource(R.string.zones_below, Fmt.duration(times.seconds[0])),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EarlierWeeksCard(weeks: List<WeekZones>) {
    SectionTitle(stringResource(R.string.zones_earlier_title))
    SurfaceCard {
        for (w in weeks) {
            Text(
                stringResource(R.string.zones_earlier_line, Fmt.shortDate(w.start), Fmt.duration(w.times.zoned), w.activities),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The zones of one activity, under its heart-rate chart. */
@Composable
fun ActivityZonesCard(zones: ActivityZones, maxHr: Int) {
    SectionTitle(stringResource(R.string.zones_activity_title))
    SurfaceCard {
        ZoneBars(zones.times)
        Text(
            stringResource(R.string.zones_activity_max, maxHr),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!zones.exact) {
            Text(stringResource(R.string.zones_activity_guess), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
