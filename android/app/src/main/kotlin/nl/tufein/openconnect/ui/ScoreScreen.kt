package nl.tufein.openconnect.ui

import android.content.Intent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.RecordEntry
import nl.tufein.openconnect.core.YearScore
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun ScoreRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** The hi-score card of one year: the numbers you would want to show. Nothing leaves the phone until you press share. */
@Composable
internal fun ScoreCard(score: YearScore) {
    val metrics = OpenConnectColors.metrics
    val context = LocalContext.current
    @Composable fun record(r: RecordEntry) = stringResource(R.string.score_record_value, Fmt.oneDecimal(r.value), Fmt.date(r.date))
    SurfaceCard(accent = metrics.calories) {
        Text(stringResource(R.string.score_title, score.year), style = MaterialTheme.typography.headlineSmall, color = metrics.calories)
        ScoreRow(stringResource(R.string.score_activities), score.activities.toString())
        ScoreRow(stringResource(R.string.score_distance), stringResource(R.string.score_distance_value, Fmt.oneDecimal(score.distanceKm)))
        score.distanceChangePct?.let { change ->
            val sign = if (change >= 0) "+" else ""
            Text(stringResource(R.string.score_vs_last, sign + Fmt.oneDecimal(change)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ScoreRow(stringResource(R.string.score_time), stringResource(R.string.score_hours, Fmt.oneDecimal(score.hours)))
        ScoreRow(stringResource(R.string.score_active_days), score.activeDays.toString())
        ScoreRow(stringResource(R.string.score_streak), stringResource(R.string.score_days, score.longestStreakDays))
        score.longestRun?.let { ScoreRow(stringResource(R.string.score_longest_run), record(it)) }
        score.longestRide?.let { ScoreRow(stringResource(R.string.score_longest_ride), record(it)) }
        score.biggestClimb?.let { ScoreRow(stringResource(R.string.score_climb), stringResource(R.string.score_climb_record_value, it.value.roundToInt(), Fmt.date(it.date))) }
        score.busiestMonth?.let { ScoreRow(stringResource(R.string.score_busiest), it.getDisplayName(TextStyle.FULL, currentLocale()).replaceFirstChar { c -> c.uppercase() }) }
        if (score.steps > 0) ScoreRow(stringResource(R.string.score_steps), Fmt.thousands(score.steps.toInt()))
        score.bestStepsDay?.let { (date, steps) -> ScoreRow(stringResource(R.string.score_best_steps), stringResource(R.string.score_best_steps_value, Fmt.thousands(steps), Fmt.date(date))) }
        val shareText = stringResource(
            R.string.score_share_text, score.year, Fmt.oneDecimal(score.distanceKm), score.activities, Fmt.oneDecimal(score.hours), score.activeDays, score.longestStreakDays,
        )
        PrimaryButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text(stringResource(R.string.score_share)) }
    }
}

/** The Hi-score tab: pick a year and see its card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreContent(state: ScoreState, onYear: (Int) -> Unit, contentPadding: PaddingValues) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        if (state.years.size > 1) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (year in state.years.take(8)) {
                        PixelChip(selected = year == state.selected, onClick = { onYear(year) }, label = { Text(year.toString()) })
                    }
                }
            }
        }
        val score = state.score
        if (score == null) {
            item {
                SurfaceCard { Text(stringResource(R.string.score_none), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
        } else {
            item { ScoreCard(score) }
        }
        item { Text(stringResource(R.string.score_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}
