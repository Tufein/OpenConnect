package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.RaceDistance
import nl.tufein.openconnect.core.RaceShape
import nl.tufein.openconnect.core.ShapeVerdict
import nl.tufein.openconnect.core.Vo2Max
import nl.tufein.openconnect.core.Vo2Summary
import kotlin.math.roundToInt

internal fun distanceLabel(d: RaceDistance): Int = when (d) {
    RaceDistance.K5 -> R.string.race_d_5k
    RaceDistance.K10 -> R.string.race_d_10k
    RaceDistance.HALF -> R.string.race_d_half
    RaceDistance.MARATHON -> R.string.race_d_marathon
}

private fun verdictLabel(v: ShapeVerdict): Int = when (v) {
    ShapeVerdict.READY -> R.string.shape_ready
    ShapeVerdict.ALMOST -> R.string.shape_almost
    ShapeVerdict.BUILDING -> R.string.shape_building
    ShapeVerdict.FAR -> R.string.shape_far
}

/** How well the last six weeks of running fit each race distance (Runalyze's Marathon Shape idea). */
@Composable
internal fun ShapeCard(shapes: List<RaceShape>, goal: RaceDistance?) {
    val colors = MaterialTheme.colorScheme
    val metric = OpenConnectColors.metrics
    SectionTitle(stringResource(R.string.shape_title))
    SurfaceCard {
        Text(stringResource(R.string.shape_intro), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        for (s in shapes) {
            val name = stringResource(distanceLabel(s.distance))
            val percent = (s.shape * 100).roundToInt()
            val verdict = stringResource(verdictLabel(s.verdict))
            val tint = when (s.verdict) {
                ShapeVerdict.READY -> metric.readiness
                ShapeVerdict.ALMOST -> colors.primary
                ShapeVerdict.BUILDING -> metric.stress
                ShapeVerdict.FAR -> colors.error
            }
            val description = stringResource(R.string.shape_row_description, name, percent, verdict)
            Row(Modifier.fillMaxWidth().semantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium)
                SegmentBar(s.shape.toFloat(), tint, Modifier.weight(1f).height(12.dp), segments = 20)
                Text("$percent%", style = MaterialTheme.typography.bodyMedium)
            }
            Text(verdict, style = MaterialTheme.typography.bodySmall, color = tint)
        }
        val detail = shapes.firstOrNull { it.distance == goal } ?: shapes.lastOrNull()
        if (detail != null) {
            Text(
                stringResource(
                    R.string.shape_detail,
                    stringResource(distanceLabel(detail.distance)),
                    Fmt.oneDecimal(detail.weeklyKm),
                    Fmt.oneDecimal(detail.requiredWeeklyKm),
                    Fmt.oneDecimal(detail.longestRunKm),
                    Fmt.oneDecimal(detail.requiredLongRunKm),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.shape_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** VO2max estimated from pace and heart rate of your runs, and the race times it stands for. */
@Composable
internal fun Vo2Card(vo2: Vo2Summary) {
    val colors = MaterialTheme.colorScheme
    val metric = OpenConnectColors.metrics
    SectionTitle(stringResource(R.string.vo2_title))
    SurfaceCard(accent = metric.heart) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Fmt.oneDecimal(vo2.current), style = MaterialTheme.typography.displayMedium, color = metric.heart)
            Text(stringResource(R.string.vo2_unit), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Text(stringResource(R.string.vo2_based_on, vo2.estimates.size), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (vo2.estimates.size >= 2) {
            LineChart(
                vo2.estimates.map { it.vo2max },
                stringResource(R.string.vo2_title),
                color = metric.heart,
                labels = vo2.estimates.map { Fmt.shortDate(it.date) },
                format = { Fmt.oneDecimal(it) },
            )
        }
        Text(stringResource(R.string.vo2_predict_title), style = MaterialTheme.typography.titleSmall)
        for (d in RaceDistance.entries) {
            val seconds = Vo2Max.predictSeconds(vo2.current, d.meters)
            Text(
                stringResource(R.string.vo2_row, stringResource(distanceLabel(d)), Fmt.duration(seconds), Fmt.pace(seconds / d.km)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(stringResource(R.string.vo2_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
