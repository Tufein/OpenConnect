package nl.tufein.openconnect.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tufein.openconnect.R

/*
 * The OpenConnect look ("Handheld", v0.30.0).
 *
 * One design for the whole app, made to look like the screen of a handheld console rather than like a stock Material app:
 *  - Jersey 10, a pixel typeface made for sports shirts, for headlines and every big number; IBM Plex Sans for reading
 *    and IBM Plex Mono for small labels and data. Fonts are bundled (res/font), so the app looks the same on every phone.
 *  - Corners are cut in pixel steps ([PixelCornerShape]) instead of rounded.
 *  - Panels are flat: a fine ink outline and a hard offset shadow, no gradients, no glow, no blur.
 *  - Warm cream paper in light mode and warm black in dark mode instead of the usual blue-grey, with orange as the one
 *    signal colour. Icons are pixel art (PixelIcons.kt).
 */

/** Jersey 10: pixel headlines and numbers. It has one weight, so styles using it never ask for bold. */
internal val Jersey = FontFamily(Font(R.font.jersey_10))

/** IBM Plex Sans: running text. */
internal val PlexSans = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
)

/** IBM Plex Mono: labels, units and data. */
internal val PlexMono = FontFamily(Font(R.font.plex_mono_medium, FontWeight.Medium))

/**
 * The colour themes to choose from. "handheld" is the look of the app; the other three are screens from the history of
 * gaming and "dynamic" takes the phone's wallpaper colours on Android 12 and newer. Older keys (arcade, clean, midnight,
 * ocean, sunset) from before v0.30.0 are no longer known and open as "handheld".
 */
enum class Palette(val key: String) {
    HANDHELD("handheld"), GAMEBOY("gameboy"), AMBER("amber"), SYNTHWAVE("synthwave"), DYNAMIC("dynamic");

    companion object {
        fun of(key: String?): Palette = entries.firstOrNull { it.key == key } ?: HANDHELD
    }
}

/**
 * The designs of the watch faces (v0.20.0). The app itself has one look since v0.30.0; this list only decides which of
 * the three Connect IQ faces the preview in Settings shows and `watch/build.sh --design` builds.
 */
enum class Design(val key: String) {
    CLEAN("clean"), DASHBOARD("dashboard"), RETRO("retro");

    companion object {
        fun of(key: String?): Design = entries.firstOrNull { it.key == key } ?: RETRO
    }
}

/** The key colours of one theme, for its dark and its light version. [onAccent] is the colour of text on the three accents. */
private class Keys(
    val primary: Color, val secondary: Color, val tertiary: Color, val background: Color, val surface: Color, val text: Color,
    val onAccent: Color? = null,
)

private val PaletteKeys: Map<Palette, Pair<Keys, Keys>> = mapOf(
    // Handheld: warm black and warm paper, orange as the signal, ink-coloured text on every accent.
    Palette.HANDHELD to (Keys(Color(0xFFFF7A3D), Color(0xFFFFD23F), Color(0xFF5BC8F5), Color(0xFF131210), Color(0xFF1F1D19), Color(0xFFF0E9D6), Color(0xFF15110C)) to
        Keys(Color(0xFFE8501B), Color(0xFFC98F00), Color(0xFF1B6FD0), Color(0xFFECE5D0), Color(0xFFF7F2E2), Color(0xFF17150F), Color(0xFF17150F))),
    // The four greens of the 1989 handheld's screen.
    Palette.GAMEBOY to (Keys(Color(0xFF9BBC0F), Color(0xFF8BAC0F), Color(0xFFC4DE5A), Color(0xFF0F380F), Color(0xFF1E4A1E), Color(0xFFD7EFA0)) to
        Keys(Color(0xFF306230), Color(0xFF4F7A1E), Color(0xFF0F380F), Color(0xFFCADC9F), Color(0xFFE0EDBE), Color(0xFF0F380F))),
    // An amber phosphor terminal.
    Palette.AMBER to (Keys(Color(0xFFFFB000), Color(0xFFFF7A00), Color(0xFFFFD45C), Color(0xFF0D0900), Color(0xFF1A1206), Color(0xFFFFE2A8)) to
        Keys(Color(0xFF8A5A00), Color(0xFFB34700), Color(0xFF6B4A00), Color(0xFFFFF6E5), Color(0xFFFFFFFF), Color(0xFF2B1D00))),
    // Neon pink and cyan over deep purple, like an eighties album cover.
    Palette.SYNTHWAVE to (Keys(Color(0xFFFF4FB8), Color(0xFF00E5FF), Color(0xFFFFD166), Color(0xFF14061F), Color(0xFF221033), Color(0xFFF5E6FF)) to
        Keys(Color(0xFFB0187A), Color(0xFF007A8C), Color(0xFF8C5A00), Color(0xFFFBF2FF), Color(0xFFFFFFFF), Color(0xFF2A0E3A))),
)

/** A full colour scheme from a few key colours: containers are the key colour mixed into the background. */
private fun scheme(k: Keys, dark: Boolean): androidx.compose.material3.ColorScheme {
    fun mix(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
    val on = k.onAccent ?: if (dark) Color(0xFF0A0A0A) else Color.White
    val muted = mix(k.text, k.background, 0.30f)
    fun container(c: Color) = mix(k.background, c, if (dark) 0.28f else 0.22f)
    fun onContainer(c: Color) = mix(c, k.text, if (dark) 0.55f else 0.75f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = k.primary, onPrimary = on, primaryContainer = container(k.primary), onPrimaryContainer = onContainer(k.primary),
        secondary = k.secondary, onSecondary = on, secondaryContainer = container(k.secondary), onSecondaryContainer = onContainer(k.secondary),
        tertiary = k.tertiary, onTertiary = on, tertiaryContainer = container(k.tertiary), onTertiaryContainer = onContainer(k.tertiary),
        background = k.background, onBackground = k.text, surface = k.surface, onSurface = k.text,
        surfaceVariant = mix(k.surface, k.text, 0.08f), onSurfaceVariant = muted,
        surfaceContainerLowest = k.background, surfaceContainerLow = mix(k.background, k.surface, 0.5f),
        surfaceContainer = k.surface, surfaceContainerHigh = mix(k.surface, k.text, 0.06f),
        surfaceContainerHighest = mix(k.surface, k.text, 0.12f),
        outline = mix(k.background, k.text, 0.45f), outlineVariant = mix(k.background, k.text, 0.16f),
    )
}

/** One colour per signal, used for its ring, tile, chart and label so a number is recognised by colour alone. */
@Immutable
class MetricColors(
    val steps: Color,
    val heart: Color,
    val stress: Color,
    val battery: Color,
    val sleep: Color,
    val distance: Color,
    val calories: Color,
    val readiness: Color,
)

private val DarkMetrics = MetricColors(
    steps = Color(0xFF5DD97A),
    heart = Color(0xFFFF5470),
    stress = Color(0xFFFFA23A),
    battery = Color(0xFF4DA3FF),
    sleep = Color(0xFFA98BFF),
    distance = Color(0xFF3FD6CF),
    calories = Color(0xFFFFD23F),
    readiness = Color(0xFFB8E64B),
)

private val LightMetrics = MetricColors(
    steps = Color(0xFF1E8B45),
    heart = Color(0xFFD2203F),
    stress = Color(0xFFC46A00),
    battery = Color(0xFF1F65CF),
    sleep = Color(0xFF6A4DDB),
    distance = Color(0xFF08847F),
    calories = Color(0xFFA97C00),
    readiness = Color(0xFF4C8A00),
)

val LocalMetricColors = staticCompositionLocalOf { DarkMetrics }

/** The colours of the signals in the current theme. */
object OpenConnectColors {
    val metrics: MetricColors
        @Composable get() = LocalMetricColors.current
}

private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

/**
 * Jersey for everything that is a title or a number, Plex Sans for sentences, Plex Mono for the small print. Jersey is
 * a narrow face that looks smaller than its size, so its sizes are larger than the usual Material ones.
 */
private val HandheldTypography = Typography(
    displayLarge = style(Jersey, 72, 72),
    displayMedium = style(Jersey, 56, 56),
    displaySmall = style(Jersey, 44, 46),
    headlineLarge = style(Jersey, 40, 42),
    headlineMedium = style(Jersey, 38, 40),
    headlineSmall = style(Jersey, 30, 32),
    titleLarge = style(Jersey, 28, 30),
    titleMedium = style(Jersey, 22, 24, tracking = 0.3),
    titleSmall = style(PlexSans, 14, 20, FontWeight.SemiBold),
    bodyLarge = style(PlexSans, 16, 24),
    bodyMedium = style(PlexSans, 14, 20),
    bodySmall = style(PlexSans, 12, 16),
    labelLarge = style(PlexMono, 14, 20, FontWeight.Medium),
    labelMedium = style(PlexMono, 12, 16, FontWeight.Medium),
    labelSmall = style(PlexMono, 11, 14, FontWeight.Medium),
)

/**
 * A rectangle whose corners are cut in pixel steps, like a rounded corner drawn on graph paper: two steps of half the
 * corner size each. The corner size is the usual Material one, so every Material component that takes its shape from the
 * theme gets these corners too.
 */
class PixelCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(corner: Dp) : this(CornerSize(corner), CornerSize(corner), CornerSize(corner), CornerSize(corner))

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): CornerBasedShape =
        PixelCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val left = layoutDirection == LayoutDirection.Ltr
        // Whole pixels only, so the steps stay crisp.
        fun step(corner: Float) = Math.round(corner / 2f).toFloat()
        val tl = step(if (left) topStart else topEnd)
        val tr = step(if (left) topEnd else topStart)
        val br = step(if (left) bottomEnd else bottomStart)
        val bl = step(if (left) bottomStart else bottomEnd)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(2 * tl, 0f)
            lineTo(w - 2 * tr, 0f)
            lineTo(w - 2 * tr, tr)
            lineTo(w - tr, tr)
            lineTo(w - tr, 2 * tr)
            lineTo(w, 2 * tr)
            lineTo(w, h - 2 * br)
            lineTo(w - br, h - 2 * br)
            lineTo(w - br, h - br)
            lineTo(w - 2 * br, h - br)
            lineTo(w - 2 * br, h)
            lineTo(2 * bl, h)
            lineTo(2 * bl, h - bl)
            lineTo(bl, h - bl)
            lineTo(bl, h - 2 * bl)
            lineTo(0f, h - 2 * bl)
            lineTo(0f, 2 * tl)
            lineTo(tl, 2 * tl)
            lineTo(tl, tl)
            lineTo(2 * tl, tl)
            close()
        }
        return Outline.Generic(path)
    }
}

private val HandheldShapes = Shapes(
    extraSmall = PixelCornerShape(4.dp),
    small = PixelCornerShape(6.dp),
    medium = PixelCornerShape(8.dp),
    large = PixelCornerShape(12.dp),
    extraLarge = PixelCornerShape(16.dp),
)

/** [mode] is "system" (follow the phone), "dark" or "light"; [palette] is a key of [Palette]. */
@Composable
fun OpenConnectTheme(mode: String = "system", palette: String = "handheld", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val chosen = Palette.of(palette)
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = when {
        chosen == Palette.DYNAMIC && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            if (dark) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
        else -> PaletteKeys[chosen]?.let { (d, l) -> scheme(if (dark) d else l, dark) } ?: scheme(PaletteKeys.getValue(Palette.HANDHELD).let { (d, l) -> if (dark) d else l }, dark)
    }
    CompositionLocalProvider(LocalMetricColors provides metricsFor(chosen, colors, dark)) {
        MaterialTheme(colorScheme = colors, typography = HandheldTypography, shapes = HandheldShapes, content = content)
    }
}

/**
 * The signal colours stay the same in every theme, so a number keeps being recognised by its colour; only steps and
 * readiness follow the theme's main colour. The Game Boy theme is the exception: four shades of green, as on the original.
 */
private fun metricsFor(palette: Palette, colors: androidx.compose.material3.ColorScheme, dark: Boolean): MetricColors {
    val base = if (dark) DarkMetrics else LightMetrics
    if (palette == Palette.HANDHELD) return base
    if (palette == Palette.GAMEBOY) {
        val greens = if (dark) listOf(Color(0xFF9BBC0F), Color(0xFF8BAC0F), Color(0xFFC4DE5A), Color(0xFF6FA02A))
        else listOf(Color(0xFF306230), Color(0xFF0F380F), Color(0xFF4F7A1E), Color(0xFF5E8C31))
        return MetricColors(greens[0], greens[1], greens[2], greens[3], greens[1], greens[2], greens[3], greens[0])
    }
    return MetricColors(
        steps = colors.primary, heart = base.heart, stress = base.stress, battery = base.battery, sleep = base.sleep,
        distance = base.distance, calories = base.calories, readiness = colors.primary,
    )
}
