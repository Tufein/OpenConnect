package nl.tufein.openconnect.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The building blocks of the Handheld look (see Theme.kt): panels, buttons, chips, switches and loaders. Screens use these
 * instead of the Material ones so the whole app is drawn the same way.
 */

private val Color.isDarkSurface: Boolean get() = luminance() < 0.5f

/** The line colour of the app: the text colour, a little softer on a dark screen where full cream would glare. */
@Composable
fun inkColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.background.isDarkSurface) c.onBackground.copy(alpha = 0.55f) else c.onBackground
}

/** The colour of the hard shadow behind panels: solid ink on light paper, a faint ghost of the text colour on dark. */
@Composable
fun shadowColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.background.isDarkSurface) c.onBackground.copy(alpha = 0.14f) else c.onBackground
}

/** A copy of [shape] drawn [offset] to the right and down in [color], with no blur: the "sticker" shadow of the app. */
fun Modifier.hardShadow(color: Color, shape: Shape, offset: Dp = 3.dp): Modifier = this.drawBehind {
    val o = offset.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(o, o) { fillOutline(outline, color) }
}

private fun DrawScope.fillOutline(outline: Outline, color: Color) {
    when (outline) {
        is Outline.Generic -> drawPath(outline.path, color)
        is Outline.Rectangle -> drawRect(color, Offset(outline.rect.left, outline.rect.top), Size(outline.rect.width, outline.rect.height))
        is Outline.Rounded -> drawPath(Path().apply { addRoundRect(outline.roundRect) }, color)
    }
}

/**
 * The card of the app: a flat surface with a 2 dp ink outline, pixel-cut corners and a hard shadow. A [tint] mixes a
 * little of that colour into the surface (a flat wash, never a gradient). Content is laid out in a padded column.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    padding: Dp = 16.dp,
    spacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val fill = if (tint == null) colors.surface else lerp(colors.surface, tint, 0.14f)
    Column(
        modifier
            .hardShadow(shadowColor(), shape)
            .background(fill, shape)
            .border(2.dp, inkColor(), shape)
            .clip(shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Makes a panel react to a tap when [onClick] is given; leaves it as it is otherwise. */
fun Modifier.tappable(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else this.clickable(onClick = onClick)

/** The label style of every button: Jersey, so a button reads like a menu entry in a game. */
private val ButtonText: androidx.compose.ui.text.TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium

@Composable
private fun ButtonBase(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    textColor: Color,
    framed: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = inkColor()
    val colors = MaterialTheme.colorScheme
    // A framed button sits on its shadow; pressing it pushes the button down onto the shadow, like a real key.
    val push = if (framed && pressed && enabled) 3.dp else 0.dp
    Box(modifier.padding(end = 3.dp, bottom = 3.dp), propagateMinConstraints = true) {
        Row(
            Modifier
                .offset(push, push)
                .then(if (framed && enabled && push == 0.dp) Modifier.hardShadow(shadowColor(), shape) else Modifier)
                .background(if (!framed) Color.Transparent else if (enabled) fill else colors.surfaceVariant, shape)
                .then(if (framed) Modifier.border(2.dp, if (enabled) ink else ink.copy(alpha = 0.3f), shape) else Modifier)
                .clip(shape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .heightIn(min = 44.dp)
                .padding(horizontal = if (framed) 18.dp else 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides (if (enabled) textColor else colors.onSurfaceVariant.copy(alpha = 0.6f))) {
                ProvideTextStyle(ButtonText) { content() }
            }
        }
    }
}

/** The main action of a screen or card: the signal colour, ink text. */
@Composable
fun PrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    ButtonBase(onClick, modifier, enabled, colors.primary, colors.onPrimary, framed = true, content = content)
}

/** An action next to the main one: the surface colour with the same outline. */
@Composable
fun SecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    ButtonBase(onClick, modifier, enabled, colors.surface, colors.onSurface, framed = true, content = content)
}

/** A quiet action with no frame, for things like "Cancel". */
@Composable
fun QuietButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    ButtonBase(onClick, modifier, enabled, Color.Transparent, colors.onBackground, framed = false, content = content)
}

/** A square button for one icon, such as the back arrow. [content] is usually a [PixelIcon], which takes the content colour. */
@Composable
fun PixelIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides (if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline)) { content() }
    }
}

/** A choice among a few: the chosen one is filled with ink, like the highlighted entry of a menu. */
@Composable
fun PixelChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val fill = if (selected) colors.onBackground else colors.surface
    val text = if (selected) colors.background else colors.onSurface
    val line = if (selected) colors.onBackground else inkColor().copy(alpha = 0.5f)
    Row(
        modifier
            .heightIn(min = 36.dp)
            .background(fill, shape)
            .border(2.dp, line, shape)
            .clip(shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides text) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                if (leadingIcon != null) leadingIcon()
                label()
            }
        }
    }
}

/** An on/off switch: a pixel-cut track with a square knob that sits left (off) or right (on, in the signal colour). */
@Composable
fun PixelSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val line = if (enabled) inkColor() else inkColor().copy(alpha = 0.3f)
    val toggle = if (onCheckedChange != null) Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange) else Modifier
    Box(
        modifier
            .then(toggle)
            .size(width = 52.dp, height = 30.dp)
            .background(if (checked && enabled) colors.primary else colors.surfaceVariant, shape)
            .border(2.dp, line, shape)
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).background(if (enabled) colors.onBackground else colors.outline))
    }
}

/** A loader: four squares and one of them lit, going round. Takes its size from the modifier (20 dp otherwise). */
@Composable
fun PixelSpinner(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier.size(20.dp)) {
        val cell = this.size.minDimension / 2f
        val gap = cell * 0.15f
        val lit = phase.toInt() % 4
        val corners = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))
        corners.forEachIndexed { i, c ->
            drawRect(
                color.copy(alpha = if (i == lit) 1f else 0.25f),
                Offset(c.x * cell + gap, c.y * cell + gap),
                Size(cell - 2 * gap, cell - 2 * gap),
            )
        }
    }
}

/** A progress bar of blocks, filled from the left, like the loading bar of an old game. */
@Composable
fun PixelProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color? = null,
) {
    SegmentBar(progress(), color, modifier.height(12.dp), segments = 24, off = trackColor)
}

/** The same bar when nobody knows how long it will take: a few lit blocks run from left to right, again and again. */
@Composable
fun PixelProgress(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "progress")
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "head",
    )
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    Canvas(modifier.height(12.dp)) {
        val segments = 24
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val lead = (head * (segments + 6)).toInt()
        for (i in 0 until segments) {
            val behind = lead - i
            drawRect(if (behind in 0..5) color else off, Offset(i * (w + gap), 0f), Size(w, size.height))
        }
    }
}

/** A row of small squares as a divider: the dotted rule behind section titles. */
@Composable
fun DottedRule(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Canvas(modifier.height(4.dp)) {
        val dot = 3.dp.toPx()
        val pitch = dot * 2f
        var x = 0f
        while (x + dot <= size.width) {
            drawRect(color, Offset(x, (size.height - dot) / 2f), Size(dot, dot))
            x += pitch
        }
    }
}
