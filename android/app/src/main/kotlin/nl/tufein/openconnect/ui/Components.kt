package nl.tufein.openconnect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import nl.tufein.openconnect.R
import nl.tufein.openconnect.ai.ActiveAi
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.StoredReport
import kotlin.math.cos
import kotlin.math.sin

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

/** The tag of a sport on a square in the sport's colour, with an ink outline: like a sticker on a cartridge. */
@Composable
fun SportBadge(sport: Sport, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier.size(46.dp).background(sportColor(sport), shape).border(2.dp, inkColor(), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(sportTag(sport), style = MaterialTheme.typography.titleMedium, color = Color(0xFF17150F), maxLines = 1)
    }
}

/** The title of a screen in big pixel letters. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        trailing()
    }
}

/** A section heading: a signal-coloured square like a menu cursor, the title in pixel letters and a dotted rule to the edge. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        DottedRule(Modifier.weight(1f))
    }
}

/**
 * A score like a game HUD: a big pixel number, its name, and a bar of 20 blocks that fills with the score.
 * A null [value] shows a dash.
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
    Panel(modifier.tappable(onClick), tint = color, padding = 14.dp, spacing = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (onClick != null) OpenHint()
        }
        Text(
            value?.toString() ?: "–",
            style = MaterialTheme.typography.displayMedium,
            color = if (value == null) colors.onSurfaceVariant else color,
        )
        SegmentBar(fraction = (value ?: 0) / 100f, color = color, Modifier.fillMaxWidth().height(10.dp))
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** [segments] square-cut blocks, the first `fraction` of them lit, like the health bar of a game character. */
@Composable
fun SegmentBar(fraction: Float, color: Color, modifier: Modifier = Modifier, segments: Int = 20, off: Color? = null) {
    val unlit = off ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val lit = (fraction.coerceIn(0f, 1f) * segments).let { kotlin.math.round(it).toInt() }
        for (i in 0 until segments) {
            val x = i * (w + gap)
            drawRect(if (i < lit) color else unlit, Offset(x, 0f), Size(w, size.height))
        }
    }
}

/** The card of the app (see [Panel]). [accent] washes a little of that colour into it. */
@Composable
fun SurfaceCard(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    Panel(modifier.fillMaxWidth(), tint = accent) { content() }
}

/** A small square in a signal's colour, the bullet in front of a label. */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color))
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, color: Color? = null, onClick: (() -> Unit)? = null) {
    val tint = color ?: MaterialTheme.colorScheme.onSurface
    Panel(modifier.tappable(onClick), tint = color, padding = 14.dp, spacing = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (color != null) {
                ColorDot(color)
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
                    modifier = Modifier.padding(bottom = 4.dp),
                )
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
    Panel(modifier.tappable(onClick), tint = color, padding = 14.dp, spacing = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color)
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
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Sparkline(series, color, Modifier.fillMaxWidth().height(26.dp))
    }
}

/** Two tiles side by side; a missing second tile leaves the first at half width. */
@Composable
fun TileRow(first: @Composable (Modifier) -> Unit, second: (@Composable (Modifier) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        first(Modifier.weight(1f))
        if (second != null) second(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

/**
 * A dial of 40 blocks around a centre that light up clockwise from the top, like the meter of an old gadget, with
 * [content] in the middle. [description] is what a screen reader says.
 */
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
    // The dial fills up from empty when it first appears.
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(progress) { shown.animateTo(progress.coerceIn(0f, 1f), androidx.compose.animation.core.tween(800)) }
    val off = color.copy(alpha = 0.18f)
    Box(modifier.size(size).semantics { if (description.isNotEmpty()) contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ticks = 40
            val outer = this.size.minDimension / 2f
            val inner = outer - stroke.toPx()
            val width = (2f * Math.PI.toFloat() * outer / ticks) * 0.55f
            val lit = Math.round(shown.value * ticks)
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            for (i in 0 until ticks) {
                val angle = Math.toRadians(-90.0 + 360.0 * i / ticks)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                drawLine(
                    if (i < lit) color else off,
                    centre + Offset(dx * inner, dy * inner),
                    centre + Offset(dx * outer, dy * outer),
                    strokeWidth = width,
                    cap = StrokeCap.Butt,
                )
            }
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
        val inset = 4.dp.toPx()
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
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter))
        // The latest day is a square, not a dot.
        lastPoint?.let { drawRect(color, Offset(it.x - 3.dp.toPx(), it.y - 3.dp.toPx()), Size(6.dp.toPx(), 6.dp.toPx())) }
    }
}

/**
 * The app's mark: a pixel heart with a pulse through it, drawn from the same grid as the launcher icon
 * (see [PixelIcons.Logo]). The outline follows the text colour, so it works on paper and on black.
 */
@Composable
fun LogoMark(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onBackground
    val fill = colors.primary
    val shine = colors.secondary
    Canvas(modifier.size(size)) {
        drawPixels(PixelIcons.Logo) { c ->
            when (c) {
                '#' -> ink
                'o' -> fill
                'y' -> shine
                'w' -> LogoPulse
                else -> null
            }
        }
    }
}

private val LogoPulse = Color(0xFFF7F2E2)

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
        PrimaryButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_demo)) }
        SecondaryButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_import)) }
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
                PixelSpinner(Modifier.size(20.dp))
                Text(stringResource(R.string.report_writing), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            PrimaryButton(onClick = { onGenerate(report != null) }) {
                Text(stringResource(if (report == null) R.string.report_generate else R.string.report_regenerate))
            }
        }
    }
}

/** The small arrow that says "tap for more". */
@Composable
fun OpenHint() {
    PixelIcon(PixelIcons.ChevronRight, size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
