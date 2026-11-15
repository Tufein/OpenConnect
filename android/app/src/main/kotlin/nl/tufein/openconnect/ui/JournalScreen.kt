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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.BodyWarning
import nl.tufein.openconnect.core.JournalBook
import nl.tufein.openconnect.core.JournalMetric
import nl.tufein.openconnect.core.JournalTag
import nl.tufein.openconnect.core.TagEffect
import nl.tufein.openconnect.core.WarningLevel
import java.time.LocalDate
import kotlin.math.roundToInt

internal fun tagLabel(tag: JournalTag): Int = when (tag) {
    JournalTag.ALCOHOL -> R.string.journal_tag_alcohol
    JournalTag.LATE_MEAL -> R.string.journal_tag_late_meal
    JournalTag.CAFFEINE -> R.string.journal_tag_caffeine
    JournalTag.STRESS -> R.string.journal_tag_stress
    JournalTag.TRAVEL -> R.string.journal_tag_travel
    JournalTag.ILL -> R.string.journal_tag_ill
}

/** Mark yesterday or today with a few tags: what you drank, ate late, how stressful it was. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JournalCard(book: JournalBook, today: LocalDate, onToggle: (LocalDate, JournalTag) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var yesterday by rememberSaveable { mutableStateOf(false) }
    val day = if (yesterday) today.minusDays(1) else today
    SurfaceCard {
        Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelChip(selected = yesterday, onClick = { yesterday = true }, label = { Text(stringResource(R.string.journal_yesterday)) })
            PixelChip(selected = !yesterday, onClick = { yesterday = false }, label = { Text(stringResource(R.string.journal_today)) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (tag in JournalTag.entries) {
                PixelChip(selected = tag in book.tags(day), onClick = { onToggle(day, tag) }, label = { Text(stringResource(tagLabel(tag))) })
            }
        }
        Text(stringResource(R.string.journal_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** A heads-up when resting heart rate is up or HRV is down for days in a row. Never a diagnosis. */
@Composable
internal fun WarningCard(w: BodyWarning) {
    val colors = MaterialTheme.colorScheme
    val tint = if (w.level == WarningLevel.STRONG) colors.error else OpenConnectColors.metrics.stress
    SurfaceCard(accent = tint) {
        Text(stringResource(R.string.warning_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (w.level == WarningLevel.STRONG) R.string.warning_strong else R.string.warning_mild, w.days),
            style = MaterialTheme.typography.titleLarge,
            color = tint,
        )
        w.restingHrUp?.let { Text(stringResource(R.string.warning_resting, it.roundToInt()), style = MaterialTheme.typography.bodyMedium) }
        w.hrvDownPct?.let { Text(stringResource(R.string.warning_hrv, it.roundToInt()), style = MaterialTheme.typography.bodyMedium) }
        Text(stringResource(R.string.warning_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** The Journal tab: what the nights after tagged days look like next to the other nights. */
@Composable
fun JournalContent(state: JournalState, contentPadding: PaddingValues) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        item {
            SurfaceCard {
                Text(stringResource(R.string.journal_effects_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.journal_logged_days, state.loggedDays), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(if (state.effects.isEmpty()) R.string.journal_effects_none else R.string.journal_effects_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        for ((tag, effects) in state.effects.groupBy { it.tag }) {
            item(key = tag.key) {
                SurfaceCard {
                    Text(stringResource(tagLabel(tag)), style = MaterialTheme.typography.titleMedium)
                    for (e in effects) EffectRow(e)
                }
            }
        }
        item {
            Text(stringResource(R.string.journal_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EffectRow(e: TagEffect) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(
        when (e.metric) {
            JournalMetric.HRV -> R.string.journal_metric_hrv
            JournalMetric.RESTING_HR -> R.string.journal_metric_resting
            JournalMetric.SLEEP_HOURS -> R.string.journal_metric_sleep
        },
    )
    fun format(v: Double) = when (e.metric) {
        JournalMetric.HRV -> "${v.roundToInt()} ms"
        JournalMetric.RESTING_HR -> "${v.roundToInt()} bpm"
        JournalMetric.SLEEP_HOURS -> Fmt.oneDecimal(v) + " h"
    }
    val tint = when {
        !e.notable -> colors.onSurfaceVariant
        e.worse -> colors.error
        else -> OpenConnectColors.metrics.readiness
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.journal_effect_row, format(e.withTag), format(e.without)),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
    Text(stringResource(R.string.journal_effect_nights, e.taggedNights, e.otherNights), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
}
