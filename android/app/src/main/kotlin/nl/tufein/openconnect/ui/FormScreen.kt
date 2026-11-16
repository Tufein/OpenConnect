package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.AdviceLevel
import nl.tufein.openconnect.core.AdviceLimit
import nl.tufein.openconnect.core.FormSeries
import nl.tufein.openconnect.core.FormStatus
import nl.tufein.openconnect.core.MonotonyResult
import nl.tufein.openconnect.core.RampRate
import nl.tufein.openconnect.core.TodayAdvice
import kotlin.math.roundToInt

private fun adviceHeadline(level: AdviceLevel): Int = when (level) {
    AdviceLevel.REST -> R.string.advice_rest
    AdviceLevel.EASY -> R.string.advice_easy
    AdviceLevel.NORMAL -> R.string.advice_normal
    AdviceLevel.PUSH -> R.string.advice_push
}

private fun adviceReason(limit: AdviceLimit): Int = when (limit) {
    AdviceLimit.READINESS -> R.string.advice_reason_readiness
    AdviceLimit.FORM -> R.string.advice_reason_form
    AdviceLimit.BOTH -> R.string.advice_reason_both
    AdviceLimit.NONE -> R.string.advice_reason_none
}

private fun statusLabel(status: FormStatus): Int = when (status) {
    FormStatus.FRESH -> R.string.form_status_fresh
    FormStatus.BALANCED -> R.string.form_status_balanced
    FormStatus.BUILDING -> R.string.form_status_building
    FormStatus.STRAINED -> R.string.form_status_strained
}

private fun statusText(status: FormStatus): Int = when (status) {
    FormStatus.FRESH -> R.string.form_text_fresh
    FormStatus.BALANCED -> R.string.form_text_balanced
    FormStatus.BUILDING -> R.string.form_text_building
    FormStatus.STRAINED -> R.string.form_text_strained
}

/** How hard today should be: the weaker of readiness and form decides. Tapping it opens the Form tab. */
@Composable
internal fun AdviceCard(advice: TodayAdvice) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    val tint = when (advice.level) {
        AdviceLevel.REST -> colors.error
        AdviceLevel.EASY -> metrics.stress
        AdviceLevel.NORMAL -> colors.primary
        AdviceLevel.PUSH -> metrics.readiness
    }
    SurfaceCard(accent = tint) {
        Text(stringResource(R.string.advice_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(adviceHeadline(advice.level)), style = MaterialTheme.typography.titleLarge, color = tint)
        Text(stringResource(adviceReason(advice.limitedBy)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** The Form tab: fitness, fatigue and form over the last 90 days, with today's advice on top. */
@Composable
fun FormContent(form: FormSeries?, advice: TodayAdvice?, monotony: MonotonyResult?, ramp: RampRate?, contentPadding: PaddingValues) {
    val colors = MaterialTheme.colorScheme
    val metrics = OpenConnectColors.metrics
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (advice != null) item { AdviceCard(advice) }
        if (form == null) {
            item {
                SurfaceCard {
                    Text(stringResource(R.string.form_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.form_missing), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            return@LazyColumn
        }
        val latest = form.latest
        val status = form.status
        item {
            SurfaceCard(accent = metrics.readiness) {
                Text(stringResource(R.string.form_title), style = MaterialTheme.typography.titleMedium)
                if (status == null) {
                    Text(stringResource(R.string.form_status_none), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                } else {
                    // The "health bar" of rest: full when you are rested, nearly empty when you carry a lot of fatigue.
                    val ratio = if (latest.fitness <= 0) 0.0 else latest.form / latest.fitness
                    val fraction = ((ratio.coerceIn(-0.4, 0.4) + 0.4) / 0.8).toFloat()
                    val label = stringResource(statusLabel(status))
                    Text(label, style = MaterialTheme.typography.titleLarge, color = metrics.readiness)
                    val description = stringResource(R.string.form_bar_description, label)
                    SegmentBar(fraction, metrics.readiness, Modifier.fillMaxWidth().height(18.dp).semantics { contentDescription = description }, segments = 25)
                    Text(stringResource(statusText(status)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        item {
            TileRow(
                { StatTile(stringResource(R.string.form_fitness), latest.fitness.roundToInt().toString(), it, color = metrics.distance) },
                { StatTile(stringResource(R.string.form_fatigue), latest.fatigue.roundToInt().toString(), it, color = metrics.heart) },
            )
        }
        item {
            TileRow(
                { StatTile(stringResource(R.string.form_form), signed(latest.form), it, color = metrics.readiness) },
                { StatTile(stringResource(R.string.form_load_today), latest.load.roundToInt().toString(), it, color = metrics.calories) },
            )
        }
        val labels = form.points.map { Fmt.shortDate(it.date) }
        item {
            SectionTitle(stringResource(R.string.form_chart_fitness))
            SurfaceCard {
                LineChart(form.points.map { it.fitness }, stringResource(R.string.form_chart_fitness), color = metrics.distance, includeZero = true, labels = labels, format = { it.roundToInt().toString() })
            }
        }
        item {
            SectionTitle(stringResource(R.string.form_chart_fatigue))
            SurfaceCard {
                LineChart(form.points.map { it.fatigue }, stringResource(R.string.form_chart_fatigue), color = metrics.heart, includeZero = true, labels = labels, format = { it.roundToInt().toString() })
            }
        }
        item {
            SectionTitle(stringResource(R.string.form_chart_form))
            SurfaceCard {
                LineChart(form.points.map { it.form }, stringResource(R.string.form_chart_form), color = metrics.readiness, includeZero = true, labels = labels, format = { signed(it) })
            }
        }
        if (ramp != null) item { RampCard(ramp) }
        if (monotony != null) {
            item { MonotonyCard(monotony) }
            item { MonotonyTiles(monotony) }
        }
        item {
            Text(stringResource(R.string.form_explain), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        item {
            Text(stringResource(R.string.form_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun signed(value: Double): String {
    val n = value.roundToInt()
    return if (n > 0) "+$n" else n.toString()
}
