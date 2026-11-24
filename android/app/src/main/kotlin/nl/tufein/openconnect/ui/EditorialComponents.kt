package nl.tufein.openconnect.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tufein.openconnect.R
import kotlin.math.min

/*
 * The building blocks of the editorial look: the photo header, metric orbs, the calorie arc, large quiet cards, the
 * status card and pill actions. They read their colours from the theme (OpenConnectColors and MaterialTheme), so they
 * also work in the other styles, but their shape and type are made for Style.EDITORIAL.
 */

/** A value, its place on a 0..1 ring and the label under it, for [MetricOrb]. */
data class OrbValue(
    val label: String,
    val value: String,
    val progress: Float,
    val color: Color,
    val description: String? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * The photo header: a full-width landscape under a deep olive scrim, a thin serif wordmark, and room for orbs or another
 * footer. The photograph is decorative, so no description is given for it; the words on top are real text.
 */
@Composable
fun EditorialHero(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    height: Dp = 360.dp,
    tone: HeroTone? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val light = tone ?: heroTone(LocalHeroChoice.current, java.time.LocalTime.now().hour)
    val photo = OpenConnectColors.photo
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.hero_editorial),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        )
        // The wash gives the same photograph another light with the hour; the scrims below keep the words readable.
        Box(Modifier.fillMaxSize().background(light.wash.copy(alpha = 0.16f)))
        Box(Modifier.fillMaxSize().background(light.scrim.copy(alpha = 0.18f)))
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to light.scrim.copy(alpha = 0.55f),
                        0.32f to light.scrim.copy(alpha = 0.12f),
                        0.66f to light.scrim.copy(alpha = 0.48f),
                        1f to light.scrim.copy(alpha = 0.92f),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = photo.dimmedInk,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trailing()
            }
            Spacer(Modifier.weight(1f))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 2.sp),
                color = photo.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = photo.dimmedInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            footer()
        }
    }
}

/**
 * A metric on its own thin ring: the value in the middle and a small letter-spaced label under it. Missing values show
 * a dash and an empty ring rather than disappearing, so the row keeps its rhythm. The whole orb is one touch target.
 */
@Composable
fun MetricOrb(
    orb: OrbValue,
    modifier: Modifier = Modifier,
    onPhoto: Boolean = false,
    size: Dp = 54.dp,
) {
    val photo = OpenConnectColors.photo
    val ring = LocalRingWeight.current
    val valueColor = if (onPhoto) photo.ink else MaterialTheme.colorScheme.onSurface
    val labelColor = if (onPhoto) photo.dimmedInk.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant
    val shown by animateFloatAsState(
        targetValue = orb.progress.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "orb",
    )
    val description = orb.description ?: "${orb.label} ${orb.value}"
    Column(
        modifier
            .width(size.coerceAtLeast(48.dp))
            .then(if (orb.onClick != null) Modifier.clickable(role = Role.Button, onClick = orb.onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = ring.stroke.toPx()
                val inset = stroke / 2f
                val arc = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(inset, inset)
                val track = orb.color.copy(alpha = if (onPhoto) 0.28f else 0.16f)
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                if (shown > 0f) {
                    drawArc(
                        orb.color,
                        -90f,
                        360f * shown,
                        false,
                        topLeft,
                        arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                orb.value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp),
                color = valueColor,
                maxLines = 1,
            )
        }
        Text(
            orb.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp, letterSpacing = 0.5.sp),
            color = labelColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A row of orbs; five fit at 320 dp because the ring is 54 dp and the labels are small. [spread] puts them at the two
 * edges, which suits the five on Home; a shorter row is centred instead.
 */
@Composable
fun MetricOrbRow(orbs: List<OrbValue>, modifier: Modifier = Modifier, onPhoto: Boolean = false, spread: Boolean = true) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        for (orb in orbs) MetricOrb(orb, onPhoto = onPhoto)
    }
}

/**
 * The editorial semicircular gauge: a thin arc from left to right, the value in large serif type and a small flame in a
 * circle. [startLabel] and [endLabel] sit at the two ends; [value] never has to fit the arc exactly.
 */
@Composable
fun ArcGauge(
    value: Int,
    max: Int,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    startLabel: String = "0",
    endLabel: String = max.toString(),
    icon: PixelArt = PixelIcons.Flame,
) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val diameter = minOf(this.size.width - stroke * 2f, (this.size.height - stroke) * 2f)
                val centre = Offset(this.size.width / 2f, this.size.height - stroke)
                val topLeft = Offset(centre.x - diameter / 2f, centre.y - diameter / 2f)
                val arc = Size(diameter, diameter)
                drawArc(track, 180f, 180f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val fraction = (value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    drawArc(color, 180f, 180f * fraction, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(34.dp).background(color.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    PixelIcon(icon, size = 16.dp, tint = color)
                }
                Text(value.toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                startLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 4.dp),
            )
            Text(
                endLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 4.dp),
            )
        }
    }
}

/** A large, quiet card with the editorial radius, a hairline border and generous padding. */
@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Panel(modifier.fillMaxWidth().tappable(onClick), tint = accent, padding = 20.dp, spacing = 12.dp, content = content)
}

/** A small uppercase section label, with an optional action or count at the end of the row. */
@Composable
fun EditorialSectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** A pill action in the editorial language; [secondary] gives it the quiet surface treatment instead of the accent. */
@Composable
fun EditorialPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
) {
    if (secondary) {
        SecondaryButton(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        PrimaryButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

/**
 * The quiet editorial confirmation: a green check, a title and a line, a close button and a full-width pill.
 * [actionLabel] null hides the pill for a card that only tells something.
 */
@Composable
fun StatusToast(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    val tint = tone ?: OpenConnectColors.metrics.steps
    Panel(modifier.fillMaxWidth(), tint = tint, padding = 18.dp, spacing = 10.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(34.dp).background(tint.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(PixelIcons.Check, size = 17.dp, tint = tint)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PixelIconButton(onClick = onDismiss) {
                PixelIcon(
                    PixelIcons.Close,
                    size = 16.dp,
                    contentDescription = stringResource(R.string.manual_close),
                )
            }
        }
        if (actionLabel != null) {
            EditorialPillButton(actionLabel, onAction, Modifier.fillMaxWidth())
        }
    }
}

/** A thin segmented bar of the night: deep, light and REM sleep, with an optional awake part. */
@Composable
fun SleepStageBar(
    deepS: Int,
    lightS: Int,
    remS: Int,
    modifier: Modifier = Modifier,
    awakeS: Int = 0,
    description: String? = null,
) {
    val metrics = OpenConnectColors.metrics
    val parts = listOf(
        deepS to metrics.sleep,
        lightS to metrics.sleep.copy(alpha = 0.55f),
        remS to metrics.battery,
        awakeS to MaterialTheme.colorScheme.outlineVariant,
    ).filter { it.first > 0 }
    Row(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        if (parts.isEmpty()) {
            Spacer(Modifier.fillMaxWidth().fillMaxHeight())
        } else {
            for ((seconds, color) in parts) {
                Box(Modifier.weight(seconds.toFloat()).fillMaxHeight().background(color))
            }
        }
    }
}

/** A small label over a serif value, used in pairs under an editorial card. */
@Composable
fun EditorialStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** A quiet hairline between two parts of an editorial card. */
@Composable
fun EditorialRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** A one-line button-like hint in a card that opens more, without the weight of a filled action. */
@Composable
fun EditorialTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        PixelIcon(PixelIcons.ChevronRight, size = 12.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
