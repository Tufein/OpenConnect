package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.MonotonyResult
import nl.tufein.openconnect.core.MonotonyVerdict
import nl.tufein.openconnect.core.RampLevel
import nl.tufein.openconnect.core.RampRate
import nl.tufein.openconnect.core.RestingHrDirection
import nl.tufein.openconnect.core.RestingHrTrend
import nl.tufein.openconnect.core.SleepConsistency
import nl.tufein.openconnect.core.SleepDebtLevel
import nl.tufein.openconnect.core.SleepRhythm
import nl.tufein.openconnect.core.StepGoalWeek
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

private fun rhythmLabel(r: SleepRhythm): Int = when (r) {
    SleepRhythm.STEADY -> R.string.sleep_rhythm_steady
    SleepRhythm.VARIABLE -> R.string.sleep_rhythm_variable
    SleepRhythm.ERRATIC -> R.string.sleep_rhythm_erratic
}

private fun rhythmText(r: SleepRhythm): Int = when (r) {
    SleepRhythm.STEADY -> R.string.sleep_rhythm_text_steady
    SleepRhythm.VARIABLE -> R.string.sleep_rhythm_text_variable
    SleepRhythm.ERRATIC -> R.string.sleep_rhythm_text_erratic
}

/** How alike the last two weeks of nights were in length (Oura and WHOOP call it sleep consistency). Lives on Home. */
@Composable
internal fun SleepConsistencyCard(c: SleepConsistency) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    val tint = when (c.rhythm) {
        SleepRhythm.STEADY -> metrics.sleep
        SleepRhythm.VARIABLE -> metrics.stress
        SleepRhythm.ERRATIC -> colors.error
    }
    SurfaceCard(accent = tint) {
        Text(stringResource(R.string.sleep_rhythm_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(rhythmLabel(c.rhythm)), style = MaterialTheme.typography.titleLarge, color = tint)
        Text(
            stringResource(
                R.string.sleep_rhythm_numbers,
                Fmt.hours((c.averageHours * 3600).roundToInt()),
                c.spreadMinutes,
                Fmt.hours((c.shortestHours * 3600).roundToInt()),
                Fmt.hours((c.longestHours * 3600).roundToInt()),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(rhythmText(c.rhythm)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(stringResource(R.string.sleep_rhythm_note, c.nights), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

private fun restingLabel(d: RestingHrDirection): Int = when (d) {
    RestingHrDirection.LOWER -> R.string.resting_trend_lower
    RestingHrDirection.STABLE -> R.string.resting_trend_stable
    RestingHrDirection.HIGHER -> R.string.resting_trend_higher
}

private fun restingText(d: RestingHrDirection): Int = when (d) {
    RestingHrDirection.LOWER -> R.string.resting_trend_text_lower
    RestingHrDirection.STABLE -> R.string.resting_trend_text_stable
    RestingHrDirection.HIGHER -> R.string.resting_trend_text_higher
}

/** Resting heart rate of the last week against your own normal of the four weeks before. Lives on Home. */
@Composable
internal fun RestingHrTrendCard(t: RestingHrTrend) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    val tint = when (t.direction) {
        RestingHrDirection.LOWER -> metrics.readiness
        RestingHrDirection.STABLE -> metrics.heart
        RestingHrDirection.HIGHER -> metrics.stress
    }
    SurfaceCard(accent = tint) {
        Text(stringResource(R.string.resting_trend_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(restingLabel(t.direction)), style = MaterialTheme.typography.titleLarge, color = tint)
        Text(
            stringResource(R.string.resting_trend_numbers, t.recent.roundToInt(), t.normal.roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(restingText(t.direction)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(stringResource(R.string.resting_trend_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** How many of the last seven days reached the step goal, as a bar of seven blocks. Lives on Home under the steps. */
@Composable
internal fun StepGoalWeekCard(w: StepGoalWeek) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    val tint = if (w.daysReached * 2 >= w.daysWithData) metrics.readiness else metrics.stress
    SurfaceCard(accent = tint) {
        Text(stringResource(R.string.step_week_title), style = MaterialTheme.typography.titleMedium)
        val headline = stringResource(R.string.step_week_days, w.daysReached, w.daysWithData)
        Text(headline, style = MaterialTheme.typography.titleLarge, color = tint)
        SegmentBar(
            w.daysReached.toFloat() / w.daysWithData,
            tint,
            Modifier.fillMaxWidth().height(18.dp).semantics { contentDescription = headline },
            segments = w.daysWithData,
        )
        Text(
            stringResource(R.string.step_week_numbers, Fmt.thousands(w.averageSteps), Fmt.thousands(w.bestSteps), Fmt.shortDate(w.bestDay)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.step_week_note, Fmt.thousands(w.goal)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
