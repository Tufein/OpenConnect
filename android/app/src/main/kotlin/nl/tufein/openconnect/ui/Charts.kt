package nl.tufein.openconnect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import nl.tufein.openconnect.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Evenly thin a series to at most [max] points so long tracks stay cheap to draw. */
fun <T> downsample(values: List<T>, max: Int = 300): List<T> {
    if (values.size <= max) return values
    val step = values.size.toDouble() / max
    return List(max) { values[(it * step).toInt()] }
}

private fun DrawScope.grid(color: Color, lines: Int = 3) {
    for (i in 0..lines) {
        val y = size.height * i / lines
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

/**
 * With [labels] (one per value) a chart can be read by touch: tap or slide a finger along it and the line above shows
 * that day and its value, formatted by [format].
 */
@Composable
private fun Inspectable(
    values: List<Double?>,
    labels: List<String>?,
    format: (Double) -> String,
    modifier: Modifier,
    indexAt: (x: Float, width: Float) -> Int,
    chart: @Composable (Modifier, selected: Int?) -> Unit,
) {
    if (labels == null || labels.size != values.size || values.isEmpty()) {
        chart(modifier, null)
        return
    }
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    var width by remember { mutableStateOf(1f) }
    fun pick(x: Float) {
        selected = indexAt(x, width).coerceIn(0, values.lastIndex)
    }
    Column(modifier.fillMaxWidth()) {
        val i = selected
        Text(
            if (i == null) stringResource(R.string.chart_hint) else labels[i] + " · " + (values[i]?.let(format) ?: "–"),
            style = MaterialTheme.typography.labelMedium,
            color = if (i == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        chart(
            Modifier
                .onSizeChanged { width = it.width.toFloat() }
                .pointerInput(values) { detectTapGestures { pick(it.x) } }
                .pointerInput(values) {
                    detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { change, _ -> pick(change.position.x) }
                },
            i,
        )
    }
}

/** A line chart; missing values (null) break the line. */
@Composable
fun LineChart(
    values: List<Double?>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 120.dp,
    includeZero: Boolean = false,
    labels: List<String>? = null,
    format: (Double) -> String = { String.format(java.util.Locale.getDefault(), "%.1f", it) },
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val cursor = MaterialTheme.colorScheme.onSurface
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inset = with(density) { 4.dp.toPx() }
    Inspectable(values, labels, format, modifier, indexAt = { x, w ->
        if (values.size < 2) 0 else Math.round((x - inset) / (w - 2 * inset) * (values.size - 1))
    }) { extra, selected ->
    Canvas(Modifier.fillMaxWidth().height(height).then(extra).semantics { contentDescription = description }) {
        grid(gridColor)
        val present = values.filterNotNull()
        if (values.size < 2 || present.isEmpty()) return@Canvas
        var lo = present.min()
        var hi = present.max()
        if (includeZero) lo = min(lo, 0.0)
        if (hi - lo < 1e-9) hi = lo + 1
        val inset = 4.dp.toPx()
        fun x(i: Int) = inset + (size.width - 2 * inset) * i / (values.size - 1)
        fun y(v: Double) = size.height - inset - (size.height - 2 * inset) * ((v - lo) / (hi - lo)).toFloat()
        val path = Path()
        var open = false
        values.forEachIndexed { i, v ->
            if (v == null) {
                open = false
            } else if (!open) {
                path.moveTo(x(i), y(v))
                open = true
            } else {
                path.lineTo(x(i), y(v))
            }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (selected != null) {
            drawLine(cursor.copy(alpha = 0.5f), Offset(x(selected), 0f), Offset(x(selected), size.height), strokeWidth = 1.dp.toPx())
            values.getOrNull(selected)?.let { drawCircle(color, 5.dp.toPx(), Offset(x(selected), y(it))) }
        }
    }
    }
}

/** A bar chart with an optional dashed goal line. */
@Composable
fun BarChart(
    values: List<Double?>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    goal: Double? = null,
    height: Dp = 120.dp,
    labels: List<String>? = null,
    format: (Double) -> String = { String.format(java.util.Locale.getDefault(), "%.1f", it) },
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.secondary
    val cursor = MaterialTheme.colorScheme.onSurface
    Inspectable(values, labels, format, modifier, indexAt = { x, w -> (x / (w / values.size)).toInt() }) { extra, selected ->
    Canvas(Modifier.fillMaxWidth().height(height).then(extra).semantics { contentDescription = description }) {
        grid(gridColor)
        val present = values.filterNotNull()
        if (values.isEmpty() || present.isEmpty()) return@Canvas
        val top = max(present.max(), goal ?: 0.0).coerceAtLeast(1.0)
        val slot = size.width / values.size
        val barWidth = slot * 0.7f
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val h = (size.height * (v / top)).toFloat()
            // With one bar picked the others step back, so the eye finds it.
            val tint = if (selected == null || selected == i) color else color.copy(alpha = 0.35f)
            drawRect(tint, Offset(slot * i + (slot - barWidth) / 2, size.height - h), Size(barWidth, h), style = Fill)
        }
        if (selected != null) {
            val cx = slot * selected + slot / 2
            drawLine(cursor.copy(alpha = 0.4f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
        }
        if (goal != null) {
            val y = size.height - (size.height * (goal / top)).toFloat()
            drawLine(
                goalColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )
        }
    }
    }
}

/** The route as a polyline, scaled to fit with longitude shrunk by cos(latitude) so it is not squashed. */
@Composable
fun RouteMap(
    points: List<Pair<Double, Double>>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
) {
    val line = MaterialTheme.colorScheme.primary
    val start = MaterialTheme.colorScheme.secondary
    val end = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        grid(gridColor, lines = 4)
        if (points.size < 2) return@Canvas
        val meanLat = points.map { it.first }.average()
        val scale = cos(Math.toRadians(meanLat))
        val xs = points.map { it.second * scale }
        val ys = points.map { it.first }
        val minX = xs.min()
        val minY = ys.min()
        val spanX = (xs.max() - minX).coerceAtLeast(1e-9)
        val spanY = (ys.max() - minY).coerceAtLeast(1e-9)
        val inset = 16.dp.toPx()
        val availW = size.width - 2 * inset
        val availH = size.height - 2 * inset
        val k = min(availW / spanX, availH / spanY).toFloat()
        val offX = inset + (availW - (spanX * k).toFloat()) / 2
        val offY = inset + (availH - (spanY * k).toFloat()) / 2
        fun px(i: Int) = Offset(offX + ((xs[i] - minX) * k).toFloat(), size.height - offY - ((ys[i] - minY) * k).toFloat())
        val path = Path().apply {
            moveTo(px(0).x, px(0).y)
            for (i in 1 until points.size) lineTo(px(i).x, px(i).y)
        }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(start, 6.dp.toPx(), px(0))
        drawCircle(end, 6.dp.toPx(), px(points.lastIndex))
    }
}
