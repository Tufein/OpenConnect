package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.SeasonBlock
import nl.tufein.openconnect.core.SeasonPhase
import nl.tufein.openconnect.core.SeasonPlan
import nl.tufein.openconnect.core.SeasonPlanner

private fun phaseName(phase: SeasonPhase): Int = when (phase) {
    SeasonPhase.BASE -> R.string.season_phase_base
    SeasonPhase.BUILD -> R.string.season_phase_build
    SeasonPhase.PEAK -> R.string.season_phase_peak
    SeasonPhase.TAPER -> R.string.season_phase_taper
}

private fun phaseFocus(phase: SeasonPhase): Int = when (phase) {
    SeasonPhase.BASE -> R.string.season_focus_base
    SeasonPhase.BUILD -> R.string.season_focus_build
    SeasonPhase.PEAK -> R.string.season_focus_peak
    SeasonPhase.TAPER -> R.string.season_focus_taper
}

/** The way to the race in phases (base, build, peak, taper), with the phase you are in. Under the race goal on the Plan tab. */
@Composable
internal fun SeasonCard(plan: SeasonPlan, usualWeeklyKm: Double) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    SurfaceCard(accent = metrics.distance) {
        Text(stringResource(R.string.season_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.season_weeks, plan.weeksToRace), style = MaterialTheme.typography.bodyMedium)
        for (block in plan.blocks) BlockRow(block, block == plan.current, usualWeeklyKm)
        plan.current?.let { Text(stringResource(phaseFocus(it.phase)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        Text(stringResource(R.string.season_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun BlockRow(block: SeasonBlock, now: Boolean, usualWeeklyKm: Double) {
    val colors = MaterialTheme.colorScheme
    val tint = if (now) OpenConnectColors.metrics.readiness else colors.onBackground
    Column {
        Text(
            stringResource(phaseName(block.phase)) + if (now) "  ◀ " + stringResource(R.string.season_now) else "",
            style = MaterialTheme.typography.titleSmall,
            color = tint,
        )
        Text(
            stringResource(R.string.season_block, block.weeks, Fmt.shortDate(block.start), Fmt.shortDate(block.end)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        SeasonPlanner.weeklyKm(block.phase, usualWeeklyKm)?.let { (low, high) ->
            Text(
                stringResource(R.string.season_block_km, Fmt.oneDecimal(low), Fmt.oneDecimal(high)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
