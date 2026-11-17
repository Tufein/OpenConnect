package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.ActivityAround
import nl.tufein.openconnect.core.AroundGap
import nl.tufein.openconnect.core.RingAround
import nl.tufein.openconnect.core.WatchAround
import java.util.Locale

/**
 * The card on an activity that fills it in with what the watch and the Oura Ring measured around it (v0.43.0): Body
 * Battery and stress just before and after, the day's resting heart rate and HRV, the night before, the ring's morning
 * scores and the ring's own record of the same effort. What a person could expect and is not there is said in a line
 * (see [AroundGap]), and the card ends by saying that none of it is live.
 */
@Composable
fun ActivityAroundCard(around: ActivityAround) {
    SectionTitle(stringResource(R.string.around_title))
    SurfaceCard(Modifier.testTag("activity-around")) {
        around.watch?.let { WatchBlock(it) }
        around.ring?.let { RingBlock(it) }
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        for (gap in around.gaps) Text(stringResource(gapText(gap)), style = MaterialTheme.typography.bodySmall, color = muted)
        Text(stringResource(R.string.around_not_live), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun WatchBlock(watch: WatchAround) {
    val metrics = OpenConnectColors.metrics
    AroundHeader(stringResource(R.string.around_watch), metrics.battery)
    val start = watch.bodyBatteryStart
    val end = watch.bodyBatteryEnd
    if (start != null && end != null) {
        AroundRow(stringResource(R.string.around_body_battery), stringResource(R.string.around_from_to, start.toString(), end.toString()))
    } else if (start != null) {
        AroundRow(stringResource(R.string.around_body_battery_start), start.toString())
    } else if (end != null) {
        AroundRow(stringResource(R.string.around_body_battery_end), end.toString())
    }
    watch.stressBefore?.let { AroundRow(stringResource(R.string.around_stress_before), it.toString()) }
    watch.stressAfter?.let { AroundRow(stringResource(R.string.around_stress_after), it.toString()) }
    val avg = watch.heartRateAvg
    val max = watch.heartRateMax
    if (avg != null && max != null) AroundRow(stringResource(R.string.around_hr_day), stringResource(R.string.around_hr_day_value, avg, max))
    watch.restingHr?.let { AroundRow(stringResource(R.string.around_resting), "$it bpm") }
    watch.hrvMs?.let { AroundRow(stringResource(R.string.around_hrv), "$it ms") }
    watch.dayStress?.let { AroundRow(stringResource(R.string.around_day_stress), it.toString()) }
    val low = watch.dayBodyBatteryLow
    val high = watch.dayBodyBatteryHigh
    if (low != null && high != null) AroundRow(stringResource(R.string.around_day_bb), "$low – $high")
    watch.sleep?.let { night ->
        val length = Fmt.hours(night.durationS)
        val score = night.score
        AroundRow(stringResource(R.string.around_sleep), if (score != null) stringResource(R.string.around_sleep_score, length, score) else length)
    }
}

@Composable
private fun RingBlock(ring: RingAround) {
    val metrics = OpenConnectColors.metrics
    AroundHeader(stringResource(R.string.source_oura), metrics.sleep)
    ring.readiness?.let { AroundRow(stringResource(R.string.around_ring_readiness), it.toString()) }
    ring.sleepScore?.let { AroundRow(stringResource(R.string.around_ring_sleep_score), it.toString()) }
    ring.sleepS?.let { AroundRow(stringResource(R.string.around_ring_sleep), Fmt.hours(it)) }
    ring.lowestHr?.let { AroundRow(stringResource(R.string.around_ring_lowest), "$it bpm") }
    ring.averageHrv?.let { AroundRow(stringResource(R.string.around_ring_hrv), "$it ms") }
    ring.temperatureDeviation?.let { AroundRow(stringResource(R.string.around_ring_temp), String.format(Locale.getDefault(), "%+.1f °C", it)) }
    ring.workout?.let { w ->
        val parts = listOfNotNull(
            w.name,
            Fmt.duration(w.durationS.toDouble()),
            w.distanceM?.takeIf { it > 0 }?.let { Fmt.km(it) + " km" },
            w.calories?.let { "$it kcal" },
        )
        Text(stringResource(R.string.around_ring_workout), style = MaterialTheme.typography.bodyMedium)
        Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.titleMedium)
    }
}

/** Which device a block of rows comes from: its signal square and its name in the label face. */
@Composable
private fun AroundHeader(text: String, color: Color) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorDot(color)
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A name on the left and its number on the right, in the number face. */
@Composable
private fun AroundRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun gapText(gap: AroundGap): Int = when (gap) {
    AroundGap.WATCH_NOTHING -> R.string.around_gap_watch_nothing
    AroundGap.WATCH_NO_READINGS_AROUND -> R.string.around_gap_watch_day_only
    AroundGap.RING_NO_DAY -> R.string.around_gap_ring_no_day
    AroundGap.RING_NO_HEART_RATE -> R.string.around_gap_ring_hr
}
