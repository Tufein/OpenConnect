package nl.tufein.openconnect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The smooth line icons of the editorial look. They share one 24 by 24 grid, a 1.7 stroke, round caps and round joins,
 * so a row of them looks drawn by one hand. [PixelIcon] stays the icon of the retro look and of the launcher mark; the
 * tab bar and the quiet editorial controls use these instead, which is what makes the bottom of the screen feel like the
 * Oura app rather than like a handheld console.
 */

/** The drawings of [LineIcon]. */
enum class LineGlyph {
    Home, Activities, Health, Insights, Ring, Sliders,
    Flame, Check, Close, ChevronRight, ChevronDown, ChevronUp, Pencil,
}

/** Draws [glyph] in one colour at any size; the stroke scales with the icon so it stays visually one weight. */
@Composable
fun LineIcon(
    glyph: LineGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(described).size(size)) {
        val unit = this.size.minDimension / 24f
        val offX = (this.size.width - 24f * unit) / 2f
        val offY = (this.size.height - 24f * unit) / 2f
        translate(offX, offY) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawGlyph(glyph, tint)
            }
        }
    }
}

/** A fold arrow that follows the style: a smooth chevron in the editorial look, the small pixel arrow elsewhere. */
@Composable
fun FoldChevron(open: Boolean, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    if (LocalStyle.current == Style.EDITORIAL) {
        LineIcon(
            if (open) LineGlyph.ChevronUp else LineGlyph.ChevronDown,
            modifier = modifier,
            size = size,
            tint = tint,
        )
    } else {
        PixelIcon(
            if (open) PixelIcons.ChevronUp else PixelIcons.ChevronDown,
            modifier = modifier,
            size = size,
            tint = tint,
        )
    }
}

/** The pixel equivalent of each line glyph, for the retro tab bar. */
internal fun LineGlyph.pixelArt(): PixelArt = when (this) {
    LineGlyph.Home -> PixelIcons.Home
    LineGlyph.Activities -> PixelIcons.Stopwatch
    LineGlyph.Health -> PixelIcons.Heart
    LineGlyph.Insights -> PixelIcons.Star
    LineGlyph.Ring -> PixelIcons.Ring
    LineGlyph.Sliders -> PixelIcons.Sliders
    LineGlyph.Flame -> PixelIcons.Flame
    LineGlyph.Check -> PixelIcons.Check
    LineGlyph.Close -> PixelIcons.Close
    LineGlyph.ChevronRight -> PixelIcons.ChevronRight
    LineGlyph.ChevronDown -> PixelIcons.ChevronDown
    LineGlyph.ChevronUp -> PixelIcons.ChevronUp
    // The pencil is only used inside editorial cards, never in the retro bar.
    LineGlyph.Pencil -> PixelIcons.ChevronRight
}

/** Draws on a 24 by 24 grid. The caller has already scaled the canvas, so the numbers here are plain grid units. */
private fun DrawScope.drawGlyph(glyph: LineGlyph, color: Color) {
    val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.7f, cap = StrokeCap.Round)
    fun circle(cx: Float, cy: Float, radius: Float) =
        drawCircle(color = color, radius = radius, center = Offset(cx, cy), style = stroke)
    fun dot(cx: Float, cy: Float, radius: Float) =
        drawCircle(color = color, radius = radius, center = Offset(cx, cy))
    fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = stroke)

    when (glyph) {
        LineGlyph.Home -> path {
            moveTo(4.5f, 10f)
            lineTo(12f, 4f)
            lineTo(19.5f, 10f)
            lineTo(19.5f, 19.5f)
            lineTo(14.5f, 19.5f)
            lineTo(14.5f, 14f)
            lineTo(9.5f, 14f)
            lineTo(9.5f, 19.5f)
            lineTo(4.5f, 19.5f)
            close()
        }
        LineGlyph.Activities -> {
            circle(12f, 13.5f, 6.3f)
            line(12f, 13.5f, 12f, 9.6f)
            line(12f, 13.5f, 14.8f, 13.5f)
            line(9.8f, 3.2f, 14.2f, 3.2f)
            line(12f, 3.2f, 12f, 7.2f)
            line(18.4f, 7f, 19.6f, 5.8f)
        }
        LineGlyph.Health -> path {
            moveTo(12f, 20f)
            cubicTo(9.2f, 17.3f, 4.2f, 13.7f, 4.2f, 9.3f)
            cubicTo(4.2f, 6.5f, 6.3f, 4.4f, 8.9f, 4.4f)
            cubicTo(10.5f, 4.4f, 11.6f, 5.2f, 12f, 6.1f)
            cubicTo(12.4f, 5.2f, 13.5f, 4.4f, 15.1f, 4.4f)
            cubicTo(17.7f, 4.4f, 19.8f, 6.5f, 19.8f, 9.3f)
            cubicTo(19.8f, 13.7f, 14.8f, 17.3f, 12f, 20f)
            close()
        }
        LineGlyph.Insights -> path {
            moveTo(12f, 3.5f)
            lineTo(14.4f, 9.1f)
            lineTo(20.5f, 9.5f)
            lineTo(15.8f, 13.5f)
            lineTo(17.3f, 19.5f)
            lineTo(12f, 16.2f)
            lineTo(6.7f, 19.5f)
            lineTo(8.2f, 13.5f)
            lineTo(3.5f, 9.5f)
            lineTo(9.6f, 9.1f)
            close()
        }
        LineGlyph.Ring -> {
            circle(12f, 12f, 7.2f)
            circle(12f, 12f, 4.3f)
        }
        LineGlyph.Sliders -> {
            line(4f, 6.5f, 20f, 6.5f)
            line(4f, 12f, 20f, 12f)
            line(4f, 17.5f, 20f, 17.5f)
            dot(9f, 6.5f, 1.9f)
            dot(15f, 12f, 1.9f)
            dot(7f, 17.5f, 1.9f)
        }
        LineGlyph.Flame -> path {
            moveTo(12f, 20.4f)
            cubicTo(7.4f, 20.4f, 4.6f, 17.3f, 4.6f, 13.6f)
            cubicTo(4.6f, 10.9f, 6.3f, 9.2f, 7.3f, 7.5f)
            cubicTo(8.3f, 5.8f, 8.6f, 4.5f, 8.6f, 3.5f)
            cubicTo(11.1f, 5.1f, 13.2f, 7.8f, 13.2f, 10.5f)
            cubicTo(13.2f, 11.3f, 13f, 12f, 12.7f, 12.6f)
            cubicTo(14.4f, 11.8f, 15.6f, 10.3f, 16.2f, 8.8f)
            cubicTo(18.8f, 11.1f, 19.4f, 13.8f, 19.4f, 15.6f)
            cubicTo(19.4f, 18.5f, 16.3f, 20.4f, 12f, 20.4f)
            close()
        }
        LineGlyph.Check -> path {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 6.5f)
        }
        LineGlyph.Close -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }
        LineGlyph.ChevronRight -> path {
            moveTo(9.5f, 5.5f)
            lineTo(16f, 12f)
            lineTo(9.5f, 18.5f)
        }
        LineGlyph.ChevronDown -> path {
            moveTo(5.5f, 9.5f)
            lineTo(12f, 16f)
            lineTo(18.5f, 9.5f)
        }
        LineGlyph.ChevronUp -> path {
            moveTo(5.5f, 14.5f)
            lineTo(12f, 8f)
            lineTo(18.5f, 14.5f)
        }
        LineGlyph.Pencil -> {
            path {
                moveTo(4.8f, 19.2f)
                lineTo(5.6f, 15.4f)
                lineTo(15.6f, 5.4f)
                lineTo(18.6f, 8.4f)
                lineTo(8.6f, 18.4f)
                close()
            }
            line(13.8f, 7.2f, 16.8f, 10.2f)
        }
    }
}
