package nl.tufein.openconnect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.ai.ActiveAi
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.StoredReport

fun sportLabel(sport: Sport): Int = when (sport) {
    Sport.RUNNING -> R.string.sport_running
    Sport.CYCLING -> R.string.sport_cycling
    Sport.SWIMMING -> R.string.sport_swimming
    Sport.WALKING -> R.string.sport_walking
    Sport.HIKING -> R.string.sport_hiking
    Sport.STRENGTH -> R.string.sport_strength
    Sport.YOGA -> R.string.sport_yoga
    Sport.OTHER -> R.string.sport_other
}

/** A short arcade-style tag and a colour for each sport. The tags are the same in every language, like a scoreboard. */
fun sportTag(sport: Sport): String = when (sport) {
    Sport.RUNNING -> "RUN"
    Sport.CYCLING -> "BIKE"
    Sport.SWIMMING -> "SWIM"
    Sport.WALKING -> "WALK"
    Sport.HIKING -> "HIKE"
    Sport.STRENGTH -> "GYM"
    Sport.YOGA -> "YOGA"
    Sport.OTHER -> "MISC"
}

fun sportColor(sport: Sport): Color = when (sport) {
    Sport.RUNNING -> Color(0xFFFF8A4D)
    Sport.CYCLING -> Color(0xFF4DA8FF)
    Sport.SWIMMING -> Color(0xFF2FC4F0)
    Sport.WALKING -> Color(0xFF2FD18B)
    Sport.HIKING -> Color(0xFF8BC34A)
    Sport.STRENGTH -> Color(0xFFFF5C7A)
    Sport.YOGA -> Color(0xFFA58BFF)
    Sport.OTHER -> Color(0xFF8E9AC4)
}

/** The tag of a sport on a tinted square. */
@Composable
fun SportBadge(sport: Sport, modifier: Modifier = Modifier) {
    val color = sportColor(sport)
    Box(
        modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(sportTag(sport), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** The app's name and a small mint square before it, like a prompt. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        trailing()
    }
}

/** Three slanted bars in the app's colours, like speed lines behind a sprinter. */
@Composable
fun SpeedStripes(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    Canvas(modifier.size(width = height * 1.5f, height = height)) {
        val barW = size.width / 5f
        val slant = size.height * 0.35f
        colors.forEachIndexed { i, color ->
            val x = i * barW * 1.6f
            val path = Path().apply {
                moveTo(x + slant, 0f)
                lineTo(x + slant + barW, 0f)
                lineTo(x + barW, size.height)
                lineTo(x, size.height)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SpeedStripes(height = 14.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A score like a race HUD: a big forward-leaning number, its name, and a bar of 20 segments that fills with the score.
 * [higherIsBetter] false (stress) turns the colour around so a low number looks good. A null [value] shows a dash.
 */
@Composable
fun ScoreTile(
    label: String,
    value: Int?,
    color: Color,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.tappable(onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        border = cardBorder(),
    ) {
        Box(if (glowsCards()) Modifier.background(Brush.linearGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent))) else Modifier) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (onClick != null) OpenHint()
                }
                Text(
                    value?.toString() ?: "–",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp),
                    color = if (value == null) colors.onSurfaceVariant else color,
                )
                SegmentBar(fraction = (value ?: 0) / 100f, color = color, Modifier.fillMaxWidth().height(8.dp))
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** [segments] slanted blocks, the first `fraction` of them lit. */
@Composable
fun SegmentBar(fraction: Float, color: Color, modifier: Modifier = Modifier, segments: Int = 20) {
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val lit = (fraction.coerceIn(0f, 1f) * segments).let { kotlin.math.round(it).toInt() }
        val slant = size.height * 0.4f
        for (i in 0 until segments) {
            val x = i * (w + gap)
            val path = Path().apply {
                moveTo(x + slant, 0f)
                lineTo(x + w, 0f)
                lineTo(x + w - slant, size.height)
                lineTo(x, size.height)
                close()
            }
            drawPath(path, if (i < lit) color else off)
        }
    }
}

/** A rounded card with a fine border. [accent] adds a soft glow of that colour at the top. */
@Composable
fun SurfaceCard(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        val glow = if (accent == null || !glowsCards()) Modifier else Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent)))
        Column(glow.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, color: Color? = null, onClick: (() -> Unit)? = null) {
    val tint = color ?: MaterialTheme.colorScheme.onSurface
    Card(
        modifier = modifier.tappable(onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (color != null) {
                    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (onClick != null) OpenHint()
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = tint)
                if (unit != null) {
                    Text(
                        " $unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/** A signal's tile: its colour, the latest value, and the last days as a small line. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String? = null,
    series: List<Double?> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.tappable(onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(
            (if (glowsCards()) Modifier.background(Brush.verticalGradient(listOf(color.copy(alpha = 0.12f), Color.Transparent))) else Modifier).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
                if (onClick != null) OpenHint()
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                if (unit != null) {
                    Text(
                        " $unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            Sparkline(series, color, Modifier.fillMaxWidth().height(26.dp))
        }
    }
}

/** Two tiles side by side; a missing second tile leaves the first at half width. */
@Composable
fun TileRow(first: @Composable (Modifier) -> Unit, second: (@Composable (Modifier) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        first(Modifier.weight(1f))
        if (second != null) second(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

/** A ring that fills clockwise from the top, with [content] in the middle. [description] is what a screen reader says. */
@Composable
fun RingGauge(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 124.dp,
    stroke: Dp = 12.dp,
    description: String = "",
    content: @Composable BoxScope.() -> Unit = {},
) {
    // The ring fills up from empty when it first appears.
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(progress) { shown.animateTo(progress.coerceIn(0f, 1f), androidx.compose.animation.core.tween(800)) }
    Box(modifier.size(size).semantics { if (description.isNotEmpty()) contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val topLeft = Offset(width / 2, width / 2)
            val arc = Size(this.size.width - width, this.size.height - width)
            drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arc, style = Stroke(width))
            val sweep = 360f * shown.value
            if (sweep > 0f) drawArc(color, -90f, sweep, false, topLeft, arc, style = Stroke(width, cap = StrokeCap.Round))
        }
        content()
    }
}

/** The last values as one thin line; missing values break it. Draws nothing when there are fewer than two points. */
@Composable
fun Sparkline(values: List<Double?>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val present = values.filterNotNull()
        if (values.size < 2 || present.size < 2) return@Canvas
        val lo = present.min()
        val span = (present.max() - lo).takeIf { it > 1e-9 } ?: 1.0
        val inset = 3.dp.toPx()
        val w = size.width - 2 * inset
        val h = size.height - 2 * inset
        fun x(i: Int) = inset + w * i / (values.size - 1)
        fun y(v: Double) = inset + h * (1 - ((v - lo) / span).toFloat())
        val path = Path()
        var penDown = false
        var lastPoint: Offset? = null
        values.forEachIndexed { i, v ->
            if (v == null) {
                penDown = false
            } else {
                val p = Offset(x(i), y(v))
                if (penDown) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
                penDown = true
                lastPoint = p
            }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        lastPoint?.let { drawCircle(color, 3.dp.toPx(), it) }
    }
}

/**
 * The app's mark: an activity ring that is open where a connection node sits, with a heartbeat through it. The same
 * shapes as the launcher icon (res/drawable/ic_launcher_foreground.xml), drawn in the theme's colours.
 */
@Composable
fun LogoMark(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val ring = MaterialTheme.colorScheme.primary
    val pulse = MaterialTheme.colorScheme.onSurface
    val node = MaterialTheme.colorScheme.secondary
    Canvas(modifier.size(size)) {
        val k = this.size.width / 108f
        val strokeRing = 6f * k
        drawArc(
            ring, -45f, 325f, false,
            topLeft = Offset((54f - 24f) * k, (54f - 24f) * k),
            size = Size(48f * k, 48f * k),
            style = Stroke(strokeRing, cap = StrokeCap.Round),
        )
        val path = Path().apply {
            moveTo(37f * k, 56f * k)
            lineTo(45f * k, 56f * k)
            lineTo(49f * k, 46f * k)
            lineTo(55f * k, 66f * k)
            lineTo(60f * k, 50f * k)
            lineTo(63f * k, 56f * k)
            lineTo(71f * k, 56f * k)
        }
        drawPath(path, pulse, style = Stroke(4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(node, 5f * k, Offset(65.08f * k, 32.71f * k))
    }
}

@Composable
fun EmptyState(onDemo: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LogoMark(size = 72.dp)
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_demo)) }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_import)) }
    }
}

/** An AI (or offline template) report with a button to write or refresh it. */
@Composable
fun ReportCard(
    report: StoredReport?,
    busy: Boolean,
    onGenerate: (force: Boolean) -> Unit,
    active: ActiveAi,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier, accent = MaterialTheme.colorScheme.primary) {
        Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                when (active) {
                    ActiveAi.CLAUDE -> R.string.report_via_claude
                    ActiveAi.GEMINI -> R.string.report_via_gemini
                    ActiveAi.TEMPLATE -> R.string.report_via_template
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (report != null) {
            MarkdownText(report.text)
            Text(
                stringResource(R.string.report_footer, report.provider, report.model ?: "–"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.report_writing), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(onClick = { onGenerate(report != null) }) {
                Text(stringResource(if (report == null) R.string.report_generate else R.string.report_regenerate))
            }
        }
    }
}

/** Makes a card react to a tap (with the ripple) when [onClick] is given; leaves it as it is otherwise. */
fun Modifier.tappable(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else this.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)

/** The small arrow that says "tap for more". */
@Composable
fun OpenHint() {
    androidx.compose.material3.Icon(
        androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
}
