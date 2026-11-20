package nl.tufein.openconnect.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
    return when (LocalStyle.current) {
        Style.CLEAN -> c.outline.copy(alpha = 0.5f)
        Style.CLASSIC -> c.outline
        Style.RETRO -> if (c.background.isDarkSurface) c.onBackground.copy(alpha = 0.55f) else c.onBackground
    }
}

/** The width of every outline: 2 dp in the retro style, a hairline in the other two. */
@Composable
fun lineWidth(): Dp = if (LocalStyle.current == Style.RETRO) 2.dp else 1.dp

/** The colour of the hard shadow behind panels: solid ink on light paper, a faint ghost of the text colour on dark. */
@Composable
fun shadowColor(): Color {
    if (LocalStyle.current != Style.RETRO) return Color.Transparent
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

/** The modern counterpart of [Modifier.hardShadow]: a low, soft lift so cards sit on the background instead of framing it. */
private fun Modifier.softShadow(shape: Shape, elevation: Dp = 4.dp): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.08f),
    spotColor = Color.Black.copy(alpha = 0.16f),
)

/**
 * The card of the app. The modern style is a rounded, softly lifted surface with roomy padding and no border; the retro
 * style keeps its pixel-cut corners, ink outline and hard shadow. A [tint] mixes a little of that colour into the surface
 * (a flat wash, never a gradient). Content is laid out in a padded column.
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
    val modern = LocalStyle.current == Style.CLEAN
    val fill = if (tint == null) colors.surface else lerp(colors.surface, tint, 0.14f)
    Column(
        modifier
            .then(if (modern) Modifier.softShadow(shape) else Modifier.hardShadow(shadowColor(), shape))
            .background(fill, shape)
            .then(if (modern) Modifier else Modifier.border(lineWidth(), inkColor(), shape))
            .clip(shape)
            .padding(if (modern) padding + 2.dp else padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Makes a panel react to a tap when [onClick] is given; leaves it as it is otherwise. */
fun Modifier.tappable(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else this.clickable(role = Role.Button, onClick = onClick)

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
    val retro = LocalStyle.current == Style.RETRO
    val modern = LocalStyle.current == Style.CLEAN
    // Modern actions are pills; the other styles use the shape from the theme.
    val shape = if (modern) CircleShape else MaterialTheme.shapes.small
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = inkColor()
    val colors = MaterialTheme.colorScheme
    // A framed button sits on its shadow; pressing it pushes the button down onto the shadow, like a real key.

    val push = if (retro && framed && pressed && enabled) 3.dp else 0.dp
    // The other styles have no shadow to push into; a pressed button gets a little see-through instead.
    val shown = if (!retro && pressed) fill.copy(alpha = 0.8f) else fill
    Box(modifier.padding(end = if (retro) 3.dp else 0.dp, bottom = if (retro) 3.dp else 0.dp), propagateMinConstraints = true) {
        Row(
            Modifier
                .offset(push, push)
                .then(
                    if (framed && enabled && push == 0.dp) {
                        if (modern) Modifier.softShadow(shape, 3.dp) else Modifier.hardShadow(shadowColor(), shape)
                    } else Modifier
                )
                .background(if (!framed) Color.Transparent else if (enabled) shown else colors.surfaceVariant, shape)
                .then(if (framed && !modern) Modifier.border(lineWidth(), if (enabled) ink else ink.copy(alpha = 0.3f), shape) else Modifier)
                .clip(shape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                // 48 dp is the smallest target a thumb reliably hits (the Android accessibility guideline).
                .heightIn(min = 48.dp)
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
    val retro = LocalStyle.current == Style.RETRO
    val shape = if (retro) MaterialTheme.shapes.small else CircleShape
    // The chosen chip is ink in the retro style (like a highlighted menu entry) and the main colour in the others.
    val fill = if (selected) (if (retro) colors.onBackground else colors.primary) else colors.surface
    val text = if (selected) (if (retro) colors.background else colors.onPrimary) else colors.onSurface
    val line = if (selected) fill else if (retro) inkColor().copy(alpha = 0.5f) else colors.outline
    Row(
        modifier
            .heightIn(min = if (retro) 36.dp else 40.dp)
            .background(fill, shape)
            .border(lineWidth(), line, shape)
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

/**
 * A choice among several that drops a list down (v0.44.0): a framed field with the chosen entry and a pixel arrow, and under it
 * the other entries; the chosen one is filled with ink, like the highlighted entry of a menu. [label] sits above the field.
 * [name] gives the text of an entry. Use [PixelChip] instead when there are two or three entries and no room is short.
 */
@Composable
fun <T> PixelSelect(
    label: String,
    selected: T,
    options: List<T>,
    name: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Box {
            SecondaryButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
                Text(name(selected), Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                PixelIcon(PixelIcons.ChevronDown, size = 16.dp, tint = colors.onSurface)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = MaterialTheme.shapes.small,
                containerColor = colors.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(lineWidth(), inkColor()),
            ) {
                for (option in options) {
                    val chosen = option == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (chosen) colors.onBackground else Color.Transparent)
                            .selectable(selected = chosen, role = Role.RadioButton) {
                                open = false
                                onSelect(option)
                            }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name(option), style = MaterialTheme.typography.bodyMedium, color = if (chosen) colors.background else colors.onSurface)
                    }
                }
            }
        }
    }
}

/** An on/off switch: a pixel-cut track with a square knob that sits left (off) or right (on, in the signal colour). */
@Composable
fun PixelSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val retro = LocalStyle.current == Style.RETRO
    val shape = if (retro) MaterialTheme.shapes.small else androidx.compose.foundation.shape.CircleShape
    val line = if (enabled) inkColor() else inkColor().copy(alpha = 0.3f)
    val toggle = if (onCheckedChange != null) Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange) else Modifier
    Box(
        modifier
            .then(toggle)
            .size(width = 52.dp, height = 30.dp)
            .background(if (checked && enabled) colors.primary else colors.surfaceVariant, shape)
            .border(lineWidth(), line, shape)
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(
                    if (!enabled) colors.outline else if (checked && !retro) colors.onPrimary else colors.onBackground,
                    if (retro) androidx.compose.ui.graphics.RectangleShape else CircleShape,
                ),
        )
    }
}

/**
 * A switch with its words as one control: the whole row is the switch (role Switch, the texts inside are its label for a
 * screen reader), at least 48 dp high, so a thumb finds it and TalkBack says what it switches. The switch itself only draws.
 * [switchFirst] puts the switch before the words, as the small settings rows do; the device rows put it after.
 */
@Composable
fun SwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    switchFirst: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (switchFirst) {
            PixelSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), content = content)
        if (!switchFirst) {
            Spacer(Modifier.width(12.dp))
            PixelSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
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
    if (LocalStyle.current != Style.RETRO) {
        Box(modifier.height(1.dp).background(color))
        return
    }
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
