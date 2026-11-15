package nl.tufein.openconnect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.HeatGrid
import nl.tufein.openconnect.core.RouteHistory
import kotlin.math.min

/** Every route you have recorded on one grid: the brighter a square, the more different activities went through it. */
@Composable
internal fun HeatCanvas(grid: HeatGrid, description: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val hot = OpenConnectColors.metrics.distance
    val alphas = floatArrayOf(0f, 0.3f, 0.5f, 0.75f, 1f)
    Canvas(modifier.fillMaxWidth().height(360.dp).semantics { contentDescription = description }) {
        val cell = min(size.width / grid.cols, size.height / grid.rows)
        val left = (size.width - cell * grid.cols) / 2
        val top = (size.height - cell * grid.rows) / 2
        drawRect(colors.surfaceVariant.copy(alpha = 0.35f), Offset(left, top), Size(cell * grid.cols, cell * grid.rows))
        for (c in grid.cells) {
            val level = grid.level(c.count)
            // A hair of overlap between squares keeps the thin lines from showing seams at fractional sizes.
            drawRect(hot.copy(alpha = alphas[level]), Offset(left + c.x * cell, top + c.y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** The Map tab. */
@Composable
fun MapContent(state: MapState, contentPadding: PaddingValues) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        val grid = state.grid
        item {
            SurfaceCard {
                Text(stringResource(R.string.map_title), style = MaterialTheme.typography.titleMedium)
                if (grid == null) {
                    Text(stringResource(R.string.map_none), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.map_routes, grid.routes), style = MaterialTheme.typography.bodyMedium)
                    HeatCanvas(grid, stringResource(R.string.map_title))
                    Text(stringResource(R.string.map_legend), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        item {
            Text(stringResource(R.string.map_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** On an activity: how this run compares with the other runs on the same route. */
@Composable
internal fun RouteHistoryCard(history: RouteHistory, thisActivity: Activity) {
    val colors = MaterialTheme.colorScheme
    val best = history.rank == 1
    SurfaceCard(accent = if (best) OpenConnectColors.metrics.readiness else null) {
        Text(stringResource(R.string.route_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.route_count, history.total), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.route_rank, history.rank, history.total),
            style = MaterialTheme.typography.titleLarge,
            color = if (best) OpenConnectColors.metrics.readiness else colors.onBackground,
        )
        if (best) {
            Text(stringResource(R.string.route_best), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        } else {
            Text(
                stringResource(
                    R.string.route_behind,
                    Fmt.duration(history.secondsSlowerThanBest),
                    Fmt.duration(history.fastest.durationS * thisActivity.distanceM / history.fastest.distanceM),
                    Fmt.date(history.fastest.startTime.atZone(java.time.ZoneId.systemDefault()).toLocalDate()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        val versus = history.secondsVersusAverage
        Text(
            stringResource(if (versus > 0) R.string.route_vs_slower else R.string.route_vs_faster, Fmt.duration(kotlin.math.abs(versus))),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}
