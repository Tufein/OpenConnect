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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
 * footer. Each [HeroTone] keeps its own part of the photograph, its own crop and its own sky, so the four hours read as
 * four places rather than four colour filters. The photograph is decorative, so no description is given for it; the
 * words on top are real text.
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
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .testTag("editorial-hero-${light.key}"),
    ) {
        Image(
            painter = painterResource(R.drawable.hero_editorial),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = light.focus,
            modifier = Modifier.fillMaxSize().scale(light.zoom),
        )
        // The hour changes the photograph itself: another crop, another atmosphere and, at night, a moon over it.
        Box(Modifier.fillMaxSize().background(light.wash.copy(alpha = heroWashAlpha(light.sky))))
        HeroAtmosphere(light)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(*heroScrimStops(light.sky, light.scrim).toTypedArray())),
        )
        HeroGlow(light.sky)
        HeroVignette(light.scrim)
        HeroBodies(light.sky)
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
                    color = photo.ink.copy(alpha = 0.90f),
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
                color = photo.ink.copy(alpha = 0.86f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            footer()
        }
    }
}

/** The atmosphere of each hour: a warm dawn, a clear day, an ember evening or a deep blue night. */
@Composable
private fun HeroAtmosphere(light: HeroTone) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(*heroAtmosphereStops(light.sky, light.scrim).toTypedArray())),
    )
}

/** A soft light around the sun or moon; it sits above the scrim so the hour still shows on a dark header. */
@Composable
private fun HeroGlow(sky: HeroSky) {
    val light = heroLight(sky)
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width * light.x, size.height * light.y)
        val radius = size.height * light.glowRadius
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(light.glow, light.glow.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

/** The sun or moon itself and, at night, a handful of quiet stars. */
@Composable
private fun HeroBodies(sky: HeroSky) {
    val light = heroLight(sky)
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width * light.x, size.height * light.y)
        val radius = light.discRadius.toPx()
        if (light.moon) {
            drawCircle(light.disc.copy(alpha = 0.96f), radius, center)
            drawCircle(Color(0xFFBCC5D2).copy(alpha = 0.22f), radius * 0.26f, center + Offset(radius * 0.24f, -radius * 0.16f))
            drawCircle(Color(0xFFBCC5D2).copy(alpha = 0.16f), radius * 0.17f, center + Offset(-radius * 0.28f, radius * 0.30f))
        } else {
            drawCircle(light.disc.copy(alpha = 0.94f), radius, center)
            drawCircle(Color.White.copy(alpha = 0.20f), radius * 0.52f, center - Offset(radius * 0.28f, radius * 0.28f))
        }
        if (light.stars) {
            for ((index, point) in HeroStars.withIndex()) {
                drawCircle(
                    color = Color(0xFFE9EFFA).copy(alpha = if (index % 3 == 0) 0.62f else 0.34f),
                    radius = (if (index % 4 == 0) 1.25f else 0.8f).dp.toPx(),
                    center = Offset(size.width * point.first, size.height * point.second),
                )
            }
        }
    }
}

/** Darkens the edges a little, so the eye stays on the title and the orbs. */
@Composable
private fun HeroVignette(scrim: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0.52f to scrim.copy(alpha = 0f),
                    1f to scrim.copy(alpha = 0.32f),
                ),
            ),
    )
}

/** The same photograph, four different views: each tone sets its own crop and its own light. */
private class HeroLight(
    val x: Float,
    val y: Float,
    val glowRadius: Float,
    val glow: Color,
    val disc: Color,
    val discRadius: Dp,
    val moon: Boolean = false,
    val stars: Boolean = false,
)

private fun heroLight(sky: HeroSky): HeroLight = when (sky) {
    HeroSky.DAWN -> HeroLight(0.77f, 0.32f, 0.46f, Color(0xFFFFBF87).copy(alpha = 0.36f), Color(0xFFFFE8BE), 13.dp)
    HeroSky.DAY -> HeroLight(0.81f, 0.22f, 0.34f, Color(0xFFFFE9B8).copy(alpha = 0.20f), Color(0xFFFFF2D3), 9.dp)
    HeroSky.DUSK -> HeroLight(0.80f, 0.59f, 0.44f, Color(0xFFFFA165).copy(alpha = 0.34f), Color(0xFFFFC88E), 16.dp)
    HeroSky.NIGHT -> HeroLight(0.78f, 0.26f, 0.40f, Color(0xFF93AAD0).copy(alpha = 0.30f), Color(0xFFEDF0DD), 12.dp, moon = true, stars = true)
}

private fun heroWashAlpha(sky: HeroSky): Float = when (sky) {
    HeroSky.DAWN -> 0.20f
    HeroSky.DAY -> 0.12f
    HeroSky.DUSK -> 0.22f
    HeroSky.NIGHT -> 0.26f
}

private fun heroAtmosphereStops(sky: HeroSky, scrim: Color): List<Pair<Float, Color>> = when (sky) {
    HeroSky.DAWN -> listOf(
        0f to Color(0xFFFFC89E).copy(alpha = 0.32f),
        0.25f to Color(0xFFF3A472).copy(alpha = 0.22f),
        0.54f to Color(0xFFFFC89E).copy(alpha = 0f),
        0.80f to Color(0xFF392018).copy(alpha = 0.20f),
        1f to scrim.copy(alpha = 0.90f),
    )
    HeroSky.DAY -> listOf(
        0f to Color(0xFFE8EED7).copy(alpha = 0.18f),
        0.34f to Color(0xFFB6C59A).copy(alpha = 0.11f),
        0.66f to Color(0xFFE8EED7).copy(alpha = 0f),
        0.84f to Color(0xFF1C2418).copy(alpha = 0.16f),
        1f to scrim.copy(alpha = 0.84f),
    )
    HeroSky.DUSK -> listOf(
        0f to Color(0xFF381C2A).copy(alpha = 0.46f),
        0.36f to Color(0xFF7E3B3D).copy(alpha = 0.25f),
        0.58f to Color(0xFFD87A50).copy(alpha = 0.20f),
        0.73f to Color(0xFFF0A26C).copy(alpha = 0.16f),
        1f to scrim.copy(alpha = 0.94f),
    )
    HeroSky.NIGHT -> listOf(
        0f to Color(0xFF07101D).copy(alpha = 0.76f),
        0.40f to Color(0xFF0C1B2E).copy(alpha = 0.48f),
        0.66f to Color(0xFF132942).copy(alpha = 0.30f),
        0.84f to Color(0xFF0A1625).copy(alpha = 0.48f),
        1f to scrim.copy(alpha = 0.97f),
    )
}

/** The readable layer: darkest at the eyebrow and the orbs, still gentle across the title. */
private fun heroScrimStops(sky: HeroSky, scrim: Color): List<Pair<Float, Color>> {
    val top = when (sky) {
        HeroSky.DAWN -> 0.74f
        HeroSky.DAY -> 0.78f
        HeroSky.DUSK -> 0.80f
        HeroSky.NIGHT -> 0.84f
    }
    val quiet = when (sky) {
        HeroSky.DAWN -> 0.34f
        HeroSky.DAY -> 0.34f
        HeroSky.DUSK -> 0.36f
        HeroSky.NIGHT -> 0.38f
    }
    return listOf(
        0f to scrim.copy(alpha = top),
        0.24f to scrim.copy(alpha = quiet),
        0.42f to scrim.copy(alpha = 0.52f),
        0.58f to scrim.copy(alpha = 0.64f),
        0.76f to scrim.copy(alpha = 0.80f),
        1f to scrim.copy(alpha = 0.96f),
    )
}

/** Quiet, fixed points in the upper sky; they never move or twinkle, so the header stays calm. */
private val HeroStars = listOf(
    0.07f to 0.15f, 0.15f to 0.07f, 0.22f to 0.20f, 0.29f to 0.09f,
    0.34f to 0.27f, 0.41f to 0.13f, 0.47f to 0.22f, 0.52f to 0.06f,
    0.58f to 0.18f, 0.63f to 0.30f, 0.69f to 0.11f, 0.84f to 0.10f,
    0.89f to 0.22f, 0.93f to 0.36f, 0.12f to 0.33f, 0.26f to 0.40f,
)

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
                LineIcon(LineGlyph.Check, size = 17.dp, tint = tint)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PixelIconButton(onClick = onDismiss) {
                LineIcon(
                    LineGlyph.Close,
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
        LineIcon(LineGlyph.ChevronRight, size = 13.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
