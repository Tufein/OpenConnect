package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.ChartInsight
import nl.tufein.openconnect.core.ChartMetric
import nl.tufein.openconnect.core.Trend
import nl.tufein.openconnect.core.Verdict
import kotlin.math.abs
import kotlin.math.roundToInt

private fun explainRes(metric: ChartMetric): Int = when (metric) {
    ChartMetric.STEPS -> R.string.insight_explain_steps
    ChartMetric.SLEEP -> R.string.insight_explain_sleep
    ChartMetric.RESTING_HR -> R.string.insight_explain_resting_hr
    ChartMetric.BODY_BATTERY -> R.string.insight_explain_body_battery
    ChartMetric.HRV -> R.string.insight_explain_hrv
    ChartMetric.SPO2 -> R.string.insight_explain_spo2
    ChartMetric.RESPIRATION -> R.string.insight_explain_respiration
    ChartMetric.STRESS -> R.string.insight_explain_stress
}

/** A value in the unit people expect for that chart. */
fun insightValue(metric: ChartMetric, v: Double): String = when (metric) {
    ChartMetric.STEPS -> Fmt.thousands(v.roundToInt())
    ChartMetric.SLEEP -> Fmt.oneDecimal(v) + " h"
    ChartMetric.RESTING_HR -> "${v.roundToInt()} bpm"
    ChartMetric.HRV -> "${v.roundToInt()} ms"
    ChartMetric.SPO2 -> "${v.roundToInt()}%"
    ChartMetric.RESPIRATION -> Fmt.oneDecimal(v) + "/min"
    ChartMetric.BODY_BATTERY, ChartMetric.STRESS -> v.roundToInt().toString()
}

/** Plain-language reading of a chart: where it stands, how it moved, what the number means. */
@Composable
fun InsightCaption(insight: ChartInsight?, metric: ChartMetric, first: LocalDate?, last: LocalDate?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (first != null && last != null) {
            Text(
                stringResource(R.string.insight_axis, Fmt.shortDate(first), Fmt.shortDate(last)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (insight != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        when (insight.verdict) {
                            Verdict.GOOD -> R.string.insight_good
                            Verdict.FAIR -> R.string.insight_fair
                            Verdict.WATCH -> R.string.insight_watch
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (insight.verdict) {
                        Verdict.GOOD -> MaterialTheme.colorScheme.primary
                        Verdict.FAIR -> MaterialTheme.colorScheme.secondary
                        Verdict.WATCH -> MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                stringResource(
                    R.string.insight_range,
                    insightValue(metric, insight.average),
                    insightValue(metric, insight.lowest), Fmt.shortDate(insight.lowestDate),
                    insightValue(metric, insight.highest), Fmt.shortDate(insight.highestDate),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            insight.trend?.let { trend ->
                val pct = Fmt.oneDecimal(abs(insight.changePct ?: 0.0))
                val base = when (trend) {
                    Trend.UP -> stringResource(R.string.insight_trend_up, pct)
                    Trend.DOWN -> stringResource(R.string.insight_trend_down, pct)
                    Trend.STABLE -> stringResource(R.string.insight_trend_stable)
                }
                val mark = when (insight.improving) {
                    true -> " (" + stringResource(R.string.insight_better) + ")"
                    false -> " (" + stringResource(R.string.insight_worse) + ")"
                    null -> ""
                }
                Text(base + mark, style = MaterialTheme.typography.bodySmall)
            }
            insight.goalDays?.let {
                Text(stringResource(R.string.insight_goal_days, it, insight.daysWithData), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            stringResource(explainRes(metric)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.insight_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
