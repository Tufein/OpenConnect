package nl.tufein.openconnect.ui

import androidx.annotation.StringRes
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tufein.openconnect.R

/*
 * The OpenConnect look (v1.9.0).
 *
 * From 1.9 the app opens in the editorial style: a calm, paper-like page with Instrument Serif display type, IBM Plex
 * Sans running text, large rounded cards, hairline borders and the Olive colour theme. Olive follows the quiet, natural
 * palette of the Oura app: warm cream paper and deep olive in light, a deep warm charcoal-green and soft sage in dark.
 * Five more themes are offered next to it (Sand, Graphite, Forest, High contrast and the phone's wallpaper); every older
 * key from before this change opens as Olive instead of crashing or showing a theme that no longer exists.
 *  - Instrument Serif carries display and section titles; IBM Plex Sans the sentences, numbers and labels; Plex Mono is
 *    still in the app for people who choose Retro. All fonts are bundled (res/font).
 *  - Editorial surfaces use generous corners and a thin low-contrast line; Retro keeps its pixel-cut corners and hard
 *    shadow, Sleek its compact panels.
 *  - Dark mode is a deep charcoal-green with warm cream text; light mode is warm paper with deep olive text.
 *  - The tab bar and the quiet editorial controls use smooth line icons (LineIcons.kt); the rest of the app keeps the
 *    small grid icons (PixelIcons.kt) that belong to the retro look.
 */

/** Jersey 10: pixel headlines and numbers. It has one weight, so styles using it never ask for bold. */
internal val Jersey = FontFamily(Font(R.font.jersey_10))

/** Instrument Serif: the editorial display face, in roman and italic (SIL OFL, see licenses/). */
internal val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** IBM Plex Sans: running text. */
internal val PlexSans = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
)

/** IBM Plex Mono: labels, units and data. */
internal val PlexMono = FontFamily(Font(R.font.plex_mono_medium, FontWeight.Medium))

/** How the themes are grouped in Settings. */
enum class PaletteGroup(@StringRes val label: Int) {
    CALM(R.string.palette_group_calm), NEUTRAL(R.string.palette_group_neutral), PHONE(R.string.palette_group_phone),
}

/**
 * The colour themes to choose from. "olive" is the Oura-inspired editorial default; Sand is the same warmth with clay
 * and straw, Forest goes a shade deeper green, Graphite is the dark neutral, High contrast is there for readability and
 * "dynamic" takes the phone's wallpaper colours on Android 12 and newer. The many older screens and bright colour
 * themes are gone, so the picker stays calm. A key from an older version that is no theme any more opens as Olive.
 */
enum class Palette(val key: String, @StringRes val label: Int, val group: PaletteGroup) {
    OLIVE("olive", R.string.palette_olive, PaletteGroup.CALM),
    SAND("sand", R.string.palette_sand, PaletteGroup.CALM),
    FOREST("forest", R.string.palette_forest, PaletteGroup.CALM),
    GRAPHITE("graphite", R.string.palette_graphite, PaletteGroup.NEUTRAL),
    CONTRAST("contrast", R.string.palette_contrast, PaletteGroup.NEUTRAL),
    DYNAMIC("dynamic", R.string.palette_dynamic, PaletteGroup.PHONE);

    companion object {
        // No key at all means a fresh install: Olive. A key of a theme that no longer exists is not an error: it opens
        // as Olive, the calm default, so an old setting never crashes and never shows a theme that is not offered.
        fun of(key: String?): Palette = if (key == null) OLIVE else entries.firstOrNull { it.key == key } ?: OLIVE
    }
}

/** The letters of the app: "pixel" (Jersey for titles and numbers, the Retro look) or "clear" (plain Plex Sans, easier to read). */
enum class Letters(val key: String) {
    PIXEL("pixel"), CLEAR("clear");

    companion object {
        fun of(key: String?): Letters = entries.firstOrNull { it.key == key } ?: CLEAR
    }
}

val LocalLetters = staticCompositionLocalOf { Letters.CLEAR }

/**
 * The style of the shapes and lines. "editorial" is the default: very large corners, hairline borders and serif display
 * type. "clean" is the modern dashboard, "retro" keeps the original Handheld console look (pixel-cut corners, ink
 * outlines, hard shadow, pixel letters by choice) and "classic" is formal (small corners, thin lines, serif titles). The
 * colour themes work in all four.
 */
enum class Style(val key: String) {
    EDITORIAL("editorial"), RETRO("retro"), CLEAN("clean"), CLASSIC("classic");

    companion object {
        fun of(key: String?): Style = entries.firstOrNull { it.key == key } ?: EDITORIAL
    }
}

val LocalStyle = staticCompositionLocalOf { Style.EDITORIAL }

/** The thickness of the metric rings: "thin" as drawn since v1.9.0 or "bold" for a stronger row of orbs. */
enum class RingWeight(val key: String, val stroke: Dp) {
    THIN("thin", 2.dp), BOLD("bold", 4.5.dp);

    companion object {
        fun of(key: String?): RingWeight = entries.firstOrNull { it.key == key } ?: THIN
    }
}

val LocalRingWeight = staticCompositionLocalOf { RingWeight.THIN }

/**
 * The light on the photo header: [scrim] darkens it behind the words, [wash] tints the photograph itself. "auto" is not a
 * tone but a choice: [heroTone] turns it into dawn, day, dusk or night from the hour, so the front page changes with the
 * day without the person touching a setting.
 */
@Immutable
class HeroTone(val key: String, val scrim: Color, val wash: Color)

private val DawnTone = HeroTone("dawn", Color(0xFF1A120C), Color(0xFFE9B98A))
private val DayTone = HeroTone("day", Color(0xFF0B0E0A), Color(0xFF9AA76B))
private val DuskTone = HeroTone("dusk", Color(0xFF160F11), Color(0xFFC8826A))
private val NightTone = HeroTone("night", Color(0xFF070A12), Color(0xFF5C6E96))

/** The tone for a stored choice and the local hour; an unknown key falls back to the automatic one. */
fun heroTone(choice: String?, hour: Int): HeroTone {
    val key = when (choice) {
        "dawn", "day", "dusk", "night" -> choice
        else -> when (hour) {
            in 5..9 -> "dawn"
            in 10..16 -> "day"
            in 17..20 -> "dusk"
            else -> "night"
        }
    }
    return when (key) {
        "dawn" -> DawnTone
        "dusk" -> DuskTone
        "night" -> NightTone
        else -> DayTone
    }
}

val LocalHeroChoice = staticCompositionLocalOf { "auto" }

/** The key colours of one theme, for its dark and its light version. [onAccent] is the colour of text on the three accents. */
private class Keys(
    val primary: Color, val secondary: Color, val tertiary: Color, val background: Color, val surface: Color, val text: Color,
    val onAccent: Color? = null,
)

private val PaletteKeys: Map<Palette, Pair<Keys, Keys>> = mapOf(
    // Olive: the Oura-inspired default. A deep warm charcoal-green and soft sage in the dark version; warm cream paper,
    // deep olive text and sage as the one accent in the light one. Text and panels stay above WCAG 7:1 (theme tests).
    Palette.OLIVE to (Keys(Color(0xFFB9C7A9), Color(0xFFD6C5A0), Color(0xFF9CB0B5), Color(0xFF10120E), Color(0xFF191C16), Color(0xFFF1ECE0), Color(0xFF10120E)) to
        Keys(Color(0xFF4C5D3F), Color(0xFF6E5F42), Color(0xFF4F6A64), Color(0xFFF2EFE6), Color(0xFFFAF7EF), Color(0xFF23281F))),
    // Sand: the same warmth without the green, like a linen page with clay and straw.
    Palette.SAND to (Keys(Color(0xFFD8B98C), Color(0xFFB3C0A3), Color(0xFFC7A6A0), Color(0xFF16120C), Color(0xFF211B13), Color(0xFFF3E8D7), Color(0xFF17120C)) to
        Keys(Color(0xFF7A5B34), Color(0xFF586A48), Color(0xFF7A4C48), Color(0xFFF5EFE3), Color(0xFFFCF7ED), Color(0xFF2B2116))),
    // Forest: a deeper green version of the calm default.
    Palette.FOREST to (Keys(Color(0xFF94AF88), Color(0xFFCDBD90), Color(0xFF86ADA6), Color(0xFF0D130E), Color(0xFF121A14), Color(0xFFE8F0E4), Color(0xFF0D130E)) to
        Keys(Color(0xFF3C5740), Color(0xFF6B5D3B), Color(0xFF35655E), Color(0xFFEDF2E9), Color(0xFFF7FAF4), Color(0xFF1A281C))),
    // Graphite: the quiet dark neutral, with a soft sage-grey main colour instead of an electric sports blue.
    Palette.GRAPHITE to (Keys(Color(0xFF9FB9B2), Color(0xFFC0B39E), Color(0xFFA6A9C2), Color(0xFF111315), Color(0xFF1A1D1F), Color(0xFFECEFEE), Color(0xFF111315)) to
        Keys(Color(0xFF4E6059), Color(0xFF665D4E), Color(0xFF565A78), Color(0xFFF0F1EF), Color(0xFFFAFAF8), Color(0xFF1D2220))),
    // The most readable one: pure black and white with a yellow (dark) or blue (light) accent.
    Palette.CONTRAST to (Keys(Color(0xFFFFD60A), Color(0xFF5EEBFF), Color(0xFFFF9EC4), Color(0xFF000000), Color(0xFF141414), Color(0xFFFFFFFF)) to
        Keys(Color(0xFF0033CC), Color(0xFF7A3E00), Color(0xFF006B3C), Color(0xFFFFFFFF), Color(0xFFF2F2F2), Color(0xFF000000))),
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
        // The snackbar and anything else "inverse": a block of ink, like the open tab and the chosen chip.
        inverseSurface = k.text, inverseOnSurface = k.background, inversePrimary = k.primary,
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
    steps = Color(0xFF7FCB8B),
    heart = Color(0xFFFF7C8C),
    stress = Color(0xFFF2B76B),
    battery = Color(0xFF82B6E8),
    sleep = Color(0xFFB8A6E8),
    distance = Color(0xFF63C9C1),
    calories = Color(0xFFE8C97A),
    readiness = Color(0xFFB7CF7F),
)

private val LightMetrics = MetricColors(
    steps = Color(0xFF2E7D4F),
    heart = Color(0xFFC23A53),
    stress = Color(0xFFA86112),
    battery = Color(0xFF2A5EA8),
    sleep = Color(0xFF5F46B8),
    distance = Color(0xFF0C6F6A),
    calories = Color(0xFF8A6500),
    readiness = Color(0xFF4F7A22),
)

/**
 * The Oura-like signals: muted sage, lavender, teal, soft coral, amber and sand. Every calm theme shares this family, so
 * a row of orbs looks like one quiet palette instead of a set of traffic lights. They stay distinct enough to tell apart,
 * and are never the only cue: every orb also has its label and value.
 */
private val EditorialDarkMetrics = MetricColors(
    steps = Color(0xFFA8BE91),
    heart = Color(0xFFCF9C92),
    stress = Color(0xFFD2B073),
    battery = Color(0xFF9BAFBF),
    sleep = Color(0xFFB0A3C9),
    distance = Color(0xFF8FB8AC),
    calories = Color(0xFFD8BE93),
    readiness = Color(0xFFC3D3A3),
)

private val EditorialLightMetrics = MetricColors(
    steps = Color(0xFF4E6B3B),
    heart = Color(0xFF9A5047),
    stress = Color(0xFF8A6523),
    battery = Color(0xFF3F6476),
    sleep = Color(0xFF5E5478),
    distance = Color(0xFF3D6E63),
    calories = Color(0xFF7E6128),
    readiness = Color(0xFF4F6B3A),
)

val LocalMetricColors = staticCompositionLocalOf { DarkMetrics }

/**
 * Colours that sit on the hero photograph, where the theme's own surface colours cannot be used: the type is always
 * warm cream and the scrim always a deep olive-black, in dark mode and in light mode alike.
 */
@Immutable
class PhotoColors(val ink: Color, val dimmedInk: Color, val scrim: Color)

private val PhotoInk = PhotoColors(
    ink = Color(0xFFF6F1E5),
    dimmedInk = Color(0xFFF6F1E5).copy(alpha = 0.72f),
    scrim = Color(0xFF0E110D),
)

val LocalPhotoColors = staticCompositionLocalOf { PhotoInk }

/** The colours of the signals and of the photo header in the current theme. */
object OpenConnectColors {
    val metrics: MetricColors
        @Composable get() = LocalMetricColors.current
    val photo: PhotoColors
        @Composable get() = LocalPhotoColors.current
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
 * The modern face: Plex Sans throughout, compact semi-bold titles and numbers, small labels with a little tracking, and
 * a body that stays readable. Sizes are deliberately a step smaller than a consumer app's, so a dashboard fits more on
 * one screen without looking cramped.
 */
private val ClearTypography = Typography(
    displayLarge = style(PlexSans, 48, 52, FontWeight.SemiBold, tracking = -1.2),
    displayMedium = style(PlexSans, 38, 42, FontWeight.SemiBold, tracking = -0.8),
    displaySmall = style(PlexSans, 30, 34, FontWeight.SemiBold, tracking = -0.4),
    headlineLarge = style(PlexSans, 28, 32, FontWeight.SemiBold, tracking = -0.4),
    headlineMedium = style(PlexSans, 24, 28, FontWeight.SemiBold, tracking = -0.2),
    headlineSmall = style(PlexSans, 20, 24, FontWeight.SemiBold),
    titleLarge = style(PlexSans, 20, 26, FontWeight.SemiBold),
    titleMedium = style(PlexSans, 16, 22, FontWeight.SemiBold),
    titleSmall = style(PlexSans, 14, 20, FontWeight.SemiBold),
    bodyLarge = style(PlexSans, 15, 22),
    bodyMedium = style(PlexSans, 14, 20),
    bodySmall = style(PlexSans, 12, 16),
    labelLarge = style(PlexSans, 13, 18, FontWeight.SemiBold, tracking = 0.2),
    labelMedium = style(PlexSans, 12, 16, FontWeight.SemiBold, tracking = 0.4),
    labelSmall = style(PlexSans, 11, 14, FontWeight.SemiBold, tracking = 0.6),
)

/**
 * The editorial face: Instrument Serif for display, headlines and large scores, IBM Plex Sans for everything that is
 * read as a sentence. The score sizes are generous because the serif's thin strokes need room; micro-labels are small,
 * semi-bold and letter-spaced, and are set in capitals by the components that use them.
 */
private val EditorialTypography = Typography(
    displayLarge = style(InstrumentSerif, 64, 66),
    displayMedium = style(InstrumentSerif, 52, 54),
    displaySmall = style(InstrumentSerif, 40, 44),
    headlineLarge = style(InstrumentSerif, 34, 38),
    headlineMedium = style(InstrumentSerif, 28, 34),
    headlineSmall = style(InstrumentSerif, 24, 30),
    titleLarge = style(InstrumentSerif, 22, 28),
    titleMedium = style(PlexSans, 16, 22, FontWeight.SemiBold, tracking = 0.1),
    titleSmall = style(PlexSans, 14, 20, FontWeight.SemiBold),
    bodyLarge = style(PlexSans, 16, 24),
    bodyMedium = style(PlexSans, 14, 21),
    bodySmall = style(PlexSans, 12, 17),
    labelLarge = style(PlexSans, 13, 18, FontWeight.SemiBold, tracking = 0.6),
    labelMedium = style(PlexSans, 11, 15, FontWeight.SemiBold, tracking = 1.1),
    labelSmall = style(PlexSans, 10, 14, FontWeight.SemiBold, tracking = 1.4),
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

/** Restrained corners: cards read as panels in a dashboard, not as soft bubbles. */
internal val ModernShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

/** Editorial corners: a page of calm, generous cards rather than dashboard panels (10 to 28 dp). */
internal val EditorialShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** The pill used by editorial buttons and chips. */
internal val PillShape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)

private val ClassicShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
)

/** The clear letters with the titles and big numbers in the phone's serif face. */
private val ClassicTypography: androidx.compose.material3.Typography = ClearTypography.let { t ->
    fun androidx.compose.ui.text.TextStyle.serif() = copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
    t.copy(
        displayLarge = t.displayLarge.serif(), displayMedium = t.displayMedium.serif(), displaySmall = t.displaySmall.serif(),
        headlineLarge = t.headlineLarge.serif(), headlineMedium = t.headlineMedium.serif(), headlineSmall = t.headlineSmall.serif(),
        titleLarge = t.titleLarge.serif(),
    )
}

/** The colours of a theme in dark or light, as a Material colour scheme. [Palette.DYNAMIC] has no fixed colours and gives Olive. */
internal fun colorSchemeFor(palette: Palette, dark: Boolean): androidx.compose.material3.ColorScheme {
    val (d, l) = PaletteKeys[palette] ?: PaletteKeys.getValue(Palette.OLIVE)
    return scheme(if (dark) d else l, dark)
}

/** The background and the main colour of a theme, for the little swatch next to its name. */
internal fun paletteSwatch(palette: Palette, dark: Boolean): Pair<Color, Color>? {
    val (d, l) = PaletteKeys[palette] ?: return null
    val k = if (dark) d else l
    return k.background to k.primary
}

/** [mode] is "system" (follow the phone), "dark" or "light"; [palette] is a key of [Palette]; [letters] a key of [Letters], [style] a key of [Style]; [signals] is "soft" or "clear", [rings] "thin" or "bold" and [hero] a key of [SettingsStore.HERO_TONES]. */
@Composable
fun OpenConnectTheme(
    mode: String = "system",
    palette: String = "olive",
    letters: String = "clear",
    style: String = "editorial",
    signals: String = "soft",
    rings: String = "thin",
    hero: String = "auto",
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val chosen = Palette.of(palette)
    val chosenStyle = Style.of(style)
    // Only the retro style has pixel letters; editorial, sleek and classic always use the clear ones.
    val chosenLetters = if (chosenStyle == Style.RETRO) Letters.of(letters) else Letters.CLEAR
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = when {
        chosen == Palette.DYNAMIC && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            // A phone that cannot give its wallpaper colours (an odd build, a test runner) falls back to Olive rather
            // than taking the screen down.
            runCatching {
                if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
                else androidx.compose.material3.dynamicLightColorScheme(context)
            }.getOrElse { colorSchemeFor(Palette.OLIVE, dark) }
        else -> colorSchemeFor(chosen, dark)
    }
    CompositionLocalProvider(
        LocalMetricColors provides metricsFor(chosen, dark, signals),
        LocalLetters provides chosenLetters,
        LocalStyle provides chosenStyle,
        LocalPhotoColors provides PhotoInk,
        LocalRingWeight provides RingWeight.of(rings),
        LocalHeroChoice provides hero,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = when {
                chosenStyle == Style.EDITORIAL -> EditorialTypography
                chosenStyle == Style.CLASSIC -> ClassicTypography
                chosenLetters == Letters.CLEAR -> ClearTypography
                else -> HandheldTypography
            },
            shapes = when (chosenStyle) {
                Style.EDITORIAL -> EditorialShapes
                Style.RETRO -> HandheldShapes
                Style.CLEAN -> ModernShapes
                Style.CLASSIC -> ClassicShapes
            },
            content = content,
        )
    }
}

/**
 * The signal colours stay the same in every calm theme, so a number keeps being recognised by its colour. Olive, Sand,
 * Forest, Graphite and the wallpaper theme share the muted Oura-like family of sage, lavender, teal, coral and amber.
 * "Clear" signals and the high-contrast theme get one strong colour each, because there readability wins over quiet.
 */
private fun metricsFor(palette: Palette, dark: Boolean, signals: String): MetricColors {
    val clear = signals == "clear"
    if (clear || palette == Palette.CONTRAST) return if (dark) DarkMetrics else LightMetrics
    return if (dark) EditorialDarkMetrics else EditorialLightMetrics
}
