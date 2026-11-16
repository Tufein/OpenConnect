package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.MonotonyResult
import nl.tufein.openconnect.core.MonotonyVerdict
import nl.tufein.openconnect.core.RampLevel
import nl.tufein.openconnect.core.RampRate
import nl.tufein.openconnect.core.SleepDebtLevel
import nl.tufein.openconnect.core.SleepNeed
import kotlin.math.roundToInt

private fun verdictLabel(v: MonotonyVerdict): Int = when (v) {
    MonotonyVerdict.VARIED -> R.string.monotony_verdict_varied
    MonotonyVerdict.UNIFORM -> R.string.monotony_verdict_uniform
    MonotonyVerdict.MONOTONOUS -> R.string.monotony_verdict_monotonous
}

private fun verdictText(v: MonotonyVerdict): Int = when (v) {
    MonotonyVerdict.VARIED -> R.string.monotony_text_varied
    MonotonyVerdict.UNIFORM -> R.string.monotony_text_uniform
    MonotonyVerdict.MONOTONOUS -> R.string.monotony_text_monotonous
}

/** How alike the last seven days were, and the strain that comes with it (Foster). Lives on the Form tab. */
@Composable
internal fun MonotonyCard(m: MonotonyResult) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    val tint = if (m.verdict == MonotonyVerdict.MONOTONOUS) colors.error else if (m.verdict == MonotonyVerdict.UNIFORM) metrics.stress else metrics.readiness
    SurfaceCard(accent = tint) {
        Text(stringResource(R.string.monotony_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(verdictLabel(m.verdict)), style = MaterialTheme.typography.titleLarge, color = tint)
        Text(stringResource(verdictText(m.verdict)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(stringResource(R.string.monotony_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** The tiles under the monotony card: the numbers behind the verdict. */
@Composable
internal fun MonotonyTiles(m: MonotonyResult) {
    val metrics = OpenConnectColors.metrics
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    TileRow(
        { StatTile(stringResource(R.string.monotony_value), Fmt.oneDecimal(m.monotony), it, color = metrics.stress) },
        { StatTile(stringResource(R.string.monotony_strain), m.strain.roundToInt().toString(), it, color = metrics.heart) },
    )
    TileRow(
        { StatTile(stringResource(R.string.monotony_rest_days), m.restDays.toString(), it, color = metrics.readiness) },
        { StatTile(stringResource(R.string.monotony_usual), m.usualStrain?.roundToInt()?.toString() ?: "–", it, color = metrics.calories) },
    )
    }
}

/** How long to sleep tonight, from a base need, today's training and the sleep debt of the last week. Lives on Home. */
@Composable
internal fun SleepNeedCard(need: SleepNeed) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    val tint = when (need.level) {
        SleepDebtLevel.NONE -> metrics.sleep
        SleepDebtLevel.SMALL -> metrics.stress
        SleepDebtLevel.BIG -> colors.error
    }
    SurfaceCard(accent = tint) {
        Text(stringResource(R.string.sleep_need_title), style = MaterialTheme.typography.titleMedium)
        val hours = Fmt.hours((need.needTonightHours * 3600).roundToInt())
        Text(stringResource(R.string.sleep_need_value, hours), style = MaterialTheme.typography.titleLarge, color = tint)
        need.lastNightHours?.let { last ->
            Text(stringResource(R.string.sleep_need_last, Fmt.hours((last * 3600).roundToInt())), style = MaterialTheme.typography.bodyMedium)
        }
        val debt = Fmt.oneDecimal(need.debtHours)
        Text(
            when (need.level) {
                SleepDebtLevel.NONE -> stringResource(R.string.sleep_need_debt_none, need.nights)
                SleepDebtLevel.SMALL -> stringResource(R.string.sleep_need_debt_small, debt, need.nights)
                SleepDebtLevel.BIG -> stringResource(R.string.sleep_need_debt_big, debt, need.nights)
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(stringResource(R.string.sleep_need_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

private fun rampLabel(l: RampLevel): Int = when (l) {
    RampLevel.LOW -> R.string.ramp_low
    RampLevel.STEADY -> R.string.ramp_steady
    RampLevel.HIGH -> R.string.ramp_high
    RampLevel.SPIKE -> R.string.ramp_spike
}

private fun rampText(l: RampLevel): Int = when (l) {
    RampLevel.LOW -> R.string.ramp_text_low
    RampLevel.STEADY -> R.string.ramp_text_steady
    RampLevel.HIGH -> R.string.ramp_text_high
    RampLevel.SPIKE -> R.string.ramp_text_spike
}

/** This week's training against your own last four weeks, so a jump shows before it hurts. Lives on the Form tab. */
@Composable
internal fun RampCard(r: RampRate) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    val tint = when (r.level) {
        RampLevel.SPIKE -> colors.error
        RampLevel.HIGH -> metrics.stress
        RampLevel.STEADY -> metrics.readiness
        RampLevel.LOW -> metrics.distance
    }
    SurfaceCard(accent = tint) {
        Text(stringResource(R.string.ramp_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(rampLabel(r.level)), style = MaterialTheme.typography.titleLarge, color = tint)
        Text(
            stringResource(R.string.ramp_numbers, r.weekLoad.roundToInt(), r.usualWeekLoad.roundToInt(), Fmt.oneDecimal(r.ratio)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(rampText(r.level)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(stringResource(R.string.ramp_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
