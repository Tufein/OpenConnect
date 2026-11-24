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
 * Sans running text, very large rounded cards, hairline borders and the Olive colour theme (near-black olive and warm
 * cream in dark, warm paper and deep olive in light). The modern "Sleek" style, the pixel "Retro" style and all 26 older
 * colour themes remain, and an explicit choice stored by an older version is still honoured.
 *  - Instrument Serif carries display and section titles; IBM Plex Sans the sentences, numbers and labels; Plex Mono is
 *    still in the app for people who choose Retro. All fonts are bundled (res/font).
 *  - Editorial surfaces use very large corners and a thin low-contrast line; Retro keeps its pixel-cut corners and hard
 *    shadow, Sleek its compact panels.
 *  - Dark mode is a near-black olive with warm cream text; light mode is warm paper with deep olive text.
 *  - The icons are our own small grid icons (PixelIcons.kt), drawn crisply at every size.
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
    EDITORIAL(R.string.palette_group_editorial), SPORT(R.string.palette_group_sport), RETRO(R.string.palette_group_retro),
    EYES(R.string.palette_group_eyes), COLOUR(R.string.palette_group_colour), PHONE(R.string.palette_group_phone),
}

/**
 * The colour themes to choose from. "olive" is the editorial default; the others are cool sport neutrals, screens from
 * the history of gaming and computing, calm and high-contrast ones that are easy on the eyes, and colourful ones.
 * "dynamic" takes the phone's wallpaper colours on Android 12 and newer. Older keys (arcade, clean, midnight) from
 * before v0.31.0 are not themes any more and open as "handheld"; ocean and sunset exist again, with new colours.
 */
enum class Palette(val key: String, @StringRes val label: Int, val group: PaletteGroup) {
    OLIVE("olive", R.string.palette_olive, PaletteGroup.EDITORIAL),
    GRAPHITE("graphite", R.string.palette_graphite, PaletteGroup.SPORT),
    TRACK("track", R.string.palette_track, PaletteGroup.SPORT),
    HANDHELD("handheld", R.string.palette_handheld, PaletteGroup.RETRO),
    GAMEBOY("gameboy", R.string.palette_gameboy, PaletteGroup.RETRO),
    VIRTUALBOY("virtualboy", R.string.palette_virtualboy, PaletteGroup.RETRO),
    FAMICOM("famicom", R.string.palette_famicom, PaletteGroup.RETRO),
    C64("c64", R.string.palette_c64, PaletteGroup.RETRO),
    ATARI("atari", R.string.palette_atari, PaletteGroup.RETRO),
    AMBER("amber", R.string.palette_amber, PaletteGroup.RETRO),
    TERMINAL("terminal", R.string.palette_terminal, PaletteGroup.RETRO),
    SYNTHWAVE("synthwave", R.string.palette_synthwave, PaletteGroup.RETRO),
    CONTRAST("contrast", R.string.palette_contrast, PaletteGroup.EYES),
    MONO("mono", R.string.palette_mono, PaletteGroup.EYES),
    SLATE("slate", R.string.palette_slate, PaletteGroup.EYES),
    SAND("sand", R.string.palette_sand, PaletteGroup.EYES),
    NORD("nord", R.string.palette_nord, PaletteGroup.EYES),
    SOLARIZED("solarized", R.string.palette_solarized, PaletteGroup.EYES),
    GRUVBOX("gruvbox", R.string.palette_gruvbox, PaletteGroup.EYES),
    DRACULA("dracula", R.string.palette_dracula, PaletteGroup.EYES),
    OCEAN("ocean", R.string.palette_ocean, PaletteGroup.COLOUR),
    FOREST("forest", R.string.palette_forest, PaletteGroup.COLOUR),
    SUNSET("sunset", R.string.palette_sunset, PaletteGroup.COLOUR),
    CANDY("candy", R.string.palette_candy, PaletteGroup.COLOUR),
    VIOLET("violet", R.string.palette_violet, PaletteGroup.COLOUR),
    CHERRY("cherry", R.string.palette_cherry, PaletteGroup.COLOUR),
    DYNAMIC("dynamic", R.string.palette_dynamic, PaletteGroup.PHONE);

    companion object {
        // No key at all means a fresh install: Olive. An unknown key is a theme from an older version, and opens as
        // Handheld, the look those versions had.
        fun of(key: String?): Palette = if (key == null) OLIVE else entries.firstOrNull { it.key == key } ?: HANDHELD
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

/** The key colours of one theme, for its dark and its light version. [onAccent] is the colour of text on the three accents. */
private class Keys(
    val primary: Color, val secondary: Color, val tertiary: Color, val background: Color, val surface: Color, val text: Color,
    val onAccent: Color? = null,
)

private val PaletteKeys: Map<Palette, Pair<Keys, Keys>> = mapOf(
    // Olive: the editorial default. Near-black olive and warm cream in the dark version; warm paper and deep olive in the
    // light one, with muted sage as the one accent. Text and panel colours are kept above WCAG 7:1 by the theme test.
    Palette.OLIVE to (Keys(Color(0xFFB7C4A5), Color(0xFFD8C8A4), Color(0xFF87957A), Color(0xFF0E110D), Color(0xFF171B15), Color(0xFFEDE7D7), Color(0xFF131610)) to
        Keys(Color(0xFF4A573C), Color(0xFF6C5D3F), Color(0xFF526348), Color(0xFFF3EFE4), Color(0xFFE8E3D5), Color(0xFF202719), Color(0xFFF7F3E8))),
    // Graphite: cool neutrals and one electric blue, the sporty theme. Everything that is not data stays quiet.
    Palette.GRAPHITE to (Keys(Color(0xFF4C9AFF), Color(0xFF93A4B8), Color(0xFF6B7F96), Color(0xFF0E1116), Color(0xFF161A21), Color(0xFFE8EDF3)) to
        Keys(Color(0xFF1D4ED8), Color(0xFF475569), Color(0xFF64748B), Color(0xFFF4F6F8), Color(0xFFFFFFFF), Color(0xFF0F172A))),
    // Track: near-black with a single orange accent, for people who want the app to feel like a training log.
    Palette.TRACK to (Keys(Color(0xFFFF7A1A), Color(0xFFC9A227), Color(0xFF7E8B99), Color(0xFF0B0C0E), Color(0xFF15171A), Color(0xFFEDEFF2)) to
        Keys(Color(0xFFC2410C), Color(0xFF8A6A12), Color(0xFF475569), Color(0xFFF5F5F3), Color(0xFFFFFFFF), Color(0xFF141414))),
    // Handheld: warm black and warm paper, orange as the signal, ink-coloured text on every accent.
    Palette.HANDHELD to (Keys(Color(0xFFFF7A3D), Color(0xFFFFD23F), Color(0xFF5BC8F5), Color(0xFF131210), Color(0xFF1F1D19), Color(0xFFF0E9D6), Color(0xFF15110C)) to
        Keys(Color(0xFFDB5614), Color(0xFFC98F00), Color(0xFF2080E8), Color(0xFFECE5D0), Color(0xFFF7F2E2), Color(0xFF17150F), Color(0xFF17150F))),
    // The four greens of the 1989 handheld's screen.
    Palette.GAMEBOY to (Keys(Color(0xFF9BBC0F), Color(0xFF8BAC0F), Color(0xFFC4DE5A), Color(0xFF0F380F), Color(0xFF1B451B), Color(0xFFD7EFA0)) to
        Keys(Color(0xFF306230), Color(0xFF4F7A1E), Color(0xFF0C300C), Color(0xFFCADC9F), Color(0xFFE0EDBE), Color(0xFF0C300C))),
    // Red on black, the one colour of the 1995 headset's screen.
    Palette.VIRTUALBOY to (Keys(Color(0xFFFF3B3B), Color(0xFFFF8080), Color(0xFFE83A3A), Color(0xFF000000), Color(0xFF1C0505), Color(0xFFFFB3B3)) to
        Keys(Color(0xFFB30000), Color(0xFFC23030), Color(0xFF7A0000), Color(0xFFFFEFEF), Color(0xFFFFFFFF), Color(0xFF3A0000))),
    // Famicom red and cream on charcoal and paper.
    Palette.FAMICOM to (Keys(Color(0xFFF0263F), Color(0xFFF2E3C4), Color(0xFFA8A8A8), Color(0xFF1A1414), Color(0xFF2A2020), Color(0xFFF2E8D5)) to
        Keys(Color(0xFFB80F27), Color(0xFF5E5E5E), Color(0xFF7A5A12), Color(0xFFE9E1CF), Color(0xFFF6F0E0), Color(0xFF2B2118))),
    // The blue screen and light-blue border of the 1982 home computer.
    Palette.C64 to (Keys(Color(0xFF8E84E8), Color(0xFFFFFFFF), Color(0xFFC8C0FF), Color(0xFF2B2064), Color(0xFF3A2D82), Color(0xFFE4E0FF)) to
        Keys(Color(0xFF4B3FB0), Color(0xFF6A5ACD), Color(0xFF2B2064), Color(0xFFE8E6FF), Color(0xFFFFFFFF), Color(0xFF1E1750))),
    // Wood-grain brown and orange, like the 2600 console.
    Palette.ATARI to (Keys(Color(0xFFFF8C1A), Color(0xFFE9C46A), Color(0xFFE07A3E), Color(0xFF1B0F05), Color(0xFF2B1A0B), Color(0xFFF5E1C8)) to
        Keys(Color(0xFFA84A00), Color(0xFF7A5A12), Color(0xFF8C3B12), Color(0xFFF3E2C7), Color(0xFFFBF0DC), Color(0xFF2B1708))),
    // An amber phosphor terminal.
    Palette.AMBER to (Keys(Color(0xFFFFB000), Color(0xFFFF7A00), Color(0xFFFFD45C), Color(0xFF0D0900), Color(0xFF1A1206), Color(0xFFFFE2A8)) to
        Keys(Color(0xFF8A5A00), Color(0xFFB34700), Color(0xFF6B4A00), Color(0xFFFFF6E5), Color(0xFFFFFFFF), Color(0xFF2B1D00))),
    // A green phosphor terminal.
    Palette.TERMINAL to (Keys(Color(0xFF33FF66), Color(0xFF9CFFAD), Color(0xFF00D4A8), Color(0xFF000A02), Color(0xFF04180A), Color(0xFFBFFFCC)) to
        Keys(Color(0xFF007A2E), Color(0xFF3B7A00), Color(0xFF00796B), Color(0xFFECFFF1), Color(0xFFFFFFFF), Color(0xFF00300F))),
    // Neon pink and cyan over deep purple, like an eighties album cover.
    Palette.SYNTHWAVE to (Keys(Color(0xFFFF4FB8), Color(0xFF00E5FF), Color(0xFFFFD166), Color(0xFF14061F), Color(0xFF221033), Color(0xFFF5E6FF)) to
        Keys(Color(0xFFB0187A), Color(0xFF007A8C), Color(0xFF8C5A00), Color(0xFFFBF2FF), Color(0xFFFFFFFF), Color(0xFF2A0E3A))),
    // The most readable one: pure black and white with a yellow (dark) or blue (light) accent.
    Palette.CONTRAST to (Keys(Color(0xFFFFD60A), Color(0xFF5EEBFF), Color(0xFFFF9EC4), Color(0xFF000000), Color(0xFF141414), Color(0xFFFFFFFF)) to
        Keys(Color(0xFF0033CC), Color(0xFF7A3E00), Color(0xFF006B3C), Color(0xFFFFFFFF), Color(0xFFF2F2F2), Color(0xFF000000))),
    // Greys only, calm like an e-ink screen.
    Palette.MONO to (Keys(Color(0xFFE0E0E0), Color(0xFFB8B8B8), Color(0xFF9C9C9C), Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFEBEBEB)) to
        Keys(Color(0xFF2E2E2E), Color(0xFF555555), Color(0xFF6B6B6B), Color(0xFFEDEDED), Color(0xFFFAFAFA), Color(0xFF161616))),
    // Cool blue-grey with sky blue.
    Palette.SLATE to (Keys(Color(0xFF38BDF8), Color(0xFFA5B4FC), Color(0xFF5EEAD4), Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFFE2E8F0)) to
        Keys(Color(0xFF0369A1), Color(0xFF4338CA), Color(0xFF0F766E), Color(0xFFF1F5F9), Color(0xFFFFFFFF), Color(0xFF0F172A))),
    // Warm sand tones, soft on tired eyes.
    Palette.SAND to (Keys(Color(0xFFDDA845), Color(0xFFCF8F63), Color(0xFF98C29F), Color(0xFF1E1B16), Color(0xFF2A261F), Color(0xFFEFE6D6)) to
        Keys(Color(0xFF8A5A12), Color(0xFF9A4A22), Color(0xFF3F6B4A), Color(0xFFF4EBDD), Color(0xFFFBF6EC), Color(0xFF2E2418))),
    // The Nord palette: frosty blue-greys.
    Palette.NORD to (Keys(Color(0xFF88C0D0), Color(0xFFA3BE8C), Color(0xFFEBCB8B), Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFFECEFF4)) to
        Keys(Color(0xFF2E6F8E), Color(0xFF4C7A34), Color(0xFF8A6A12), Color(0xFFECEFF4), Color(0xFFFFFFFF), Color(0xFF2E3440))),
    // Solarized, the classic low-glare editor palette.
    Palette.SOLARIZED to (Keys(Color(0xFF3CA0E8), Color(0xFFCDA000), Color(0xFF2AB5A9), Color(0xFF002B36), Color(0xFF073642), Color(0xFFEEE8D5)) to
        Keys(Color(0xFF1F6FA8), Color(0xFF8A6700), Color(0xFF1F7A72), Color(0xFFFDF6E3), Color(0xFFEEE8D5), Color(0xFF073642))),
    // Gruvbox: warm retro-groove browns and yellows.
    Palette.GRUVBOX to (Keys(Color(0xFFFABD2F), Color(0xFF8EC07C), Color(0xFF83A598), Color(0xFF282828), Color(0xFF353230), Color(0xFFEBDBB2)) to
        Keys(Color(0xFFAF3A03), Color(0xFF79740E), Color(0xFF076678), Color(0xFFFBF1C7), Color(0xFFF6EBC3), Color(0xFF2E2A28))),
    // Dracula: purple and pink on indigo.
    Palette.DRACULA to (Keys(Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF50FA7B), Color(0xFF282A36), Color(0xFF343746), Color(0xFFF8F8F2)) to
        Keys(Color(0xFF6B3FC4), Color(0xFFB3236E), Color(0xFF1F7A3A), Color(0xFFF8F8F2), Color(0xFFFFFFFF), Color(0xFF282A36))),
    // Deep sea blue and teal.
    Palette.OCEAN to (Keys(Color(0xFF29B6F6), Color(0xFF26D0CE), Color(0xFFFFD54F), Color(0xFF06141F), Color(0xFF0D2233), Color(0xFFDCEFFB)) to
        Keys(Color(0xFF0277BD), Color(0xFF00796B), Color(0xFF8A6A00), Color(0xFFE6F4FB), Color(0xFFFFFFFF), Color(0xFF06283D))),
    // Forest greens.
    Palette.FOREST to (Keys(Color(0xFF66D17A), Color(0xFFC5E063), Color(0xFFE0B56B), Color(0xFF0B1710), Color(0xFF14261A), Color(0xFFE3F2E1)) to
        Keys(Color(0xFF2E7D32), Color(0xFF6A7F00), Color(0xFF8D5A1C), Color(0xFFE9F3E6), Color(0xFFF9FDF7), Color(0xFF12261A))),
    // Orange and rose over a dusk-red black.
    Palette.SUNSET to (Keys(Color(0xFFFF7043), Color(0xFFFFB74D), Color(0xFFF06292), Color(0xFF1C0F12), Color(0xFF2B1A1F), Color(0xFFFFE8DC)) to
        Keys(Color(0xFFC2410C), Color(0xFF9A5B00), Color(0xFFB0305F), Color(0xFFFFF1E8), Color(0xFFFFFFFF), Color(0xFF3A1410))),
    // Bubblegum pink and mint.
    Palette.CANDY to (Keys(Color(0xFFFF7EB6), Color(0xFF7DF2D0), Color(0xFFFFD66B), Color(0xFF1D1226), Color(0xFF2C1C3A), Color(0xFFFFEAF6)) to
        Keys(Color(0xFFC2185B), Color(0xFF00796B), Color(0xFF8A6A00), Color(0xFFFFEFF8), Color(0xFFFFFFFF), Color(0xFF3A1030))),
    // Violet and sky blue.
    Palette.VIOLET to (Keys(Color(0xFFB388FF), Color(0xFF80D8FF), Color(0xFFFFAB91), Color(0xFF140F26), Color(0xFF211A3D), Color(0xFFEEE8FF)) to
        Keys(Color(0xFF5B2EC9), Color(0xFF00668A), Color(0xFFA33F1B), Color(0xFFF3EEFF), Color(0xFFFFFFFF), Color(0xFF21134A))),
    // Cherry red and blush.
    Palette.CHERRY to (Keys(Color(0xFFFF5A6E), Color(0xFFFFB3BC), Color(0xFFFFD58A), Color(0xFF1A0D10), Color(0xFF2A1519), Color(0xFFFBE6E8)) to
        Keys(Color(0xFFC01F3D), Color(0xFF8A3A48), Color(0xFF8A6A12), Color(0xFFFDEEF0), Color(0xFFFFFFFF), Color(0xFF3A0F17))),
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

/**
 * The editorial signals are drawn from the same olive, sage and clay family as the theme, so a row of orbs looks like one
 * quiet palette instead of a set of traffic lights. They stay distinct enough to tell apart, and are never the only cue:
 * every orb also has its label and value.
 */
private val EditorialDarkMetrics = MetricColors(
    steps = Color(0xFFA9BD93),
    heart = Color(0xFFC58F82),
    stress = Color(0xFFC7A86B),
    battery = Color(0xFF8FA6B2),
    sleep = Color(0xFFA79BB8),
    distance = Color(0xFF8FB8AC),
    calories = Color(0xFFD6B98C),
    readiness = Color(0xFFD8D3AF),
)

private val EditorialLightMetrics = MetricColors(
    steps = Color(0xFF4E6B3B),
    heart = Color(0xFF995046),
    stress = Color(0xFF876424),
    battery = Color(0xFF3F6476),
    sleep = Color(0xFF65597A),
    distance = Color(0xFF3D6E63),
    calories = Color(0xFF856526),
    readiness = Color(0xFF5B6539),
)

val LocalMetricColors = staticCompositionLocalOf { DarkMetrics }

/**
 * Colours that sit on the hero photograph, where the theme's own surface colours cannot be used: the type is always
 * warm cream and the scrim always a deep olive-black, in dark mode and in light mode alike.
 */
@Immutable
class PhotoColors(val ink: Color, val dimmedInk: Color, val scrim: Color)

private val PhotoInk = PhotoColors(
    ink = Color(0xFFF4EFE0),
    dimmedInk = Color(0xFFF4EFE0).copy(alpha = 0.72f),
    scrim = Color(0xFF0B0E0A),
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

/** Editorial corners: a page of soft, large cards rather than dashboard panels (12 to 36 dp). */
internal val EditorialShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
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

/** The colours of a theme in dark or light, as a Material colour scheme. [Palette.DYNAMIC] has no fixed colours and gives Handheld. */
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

/** [mode] is "system" (follow the phone), "dark" or "light"; [palette] is a key of [Palette]; [letters] a key of [Letters], [style] a key of [Style]. */
@Composable
fun OpenConnectTheme(mode: String = "system", palette: String = "olive", letters: String = "clear", style: String = "editorial", content: @Composable () -> Unit) {
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
            // A phone that cannot give its wallpaper colours (an odd build, a test runner) falls back to Handheld rather
            // than taking the screen down.
            runCatching {
                if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
                else androidx.compose.material3.dynamicLightColorScheme(context)
            }.getOrElse { colorSchemeFor(Palette.HANDHELD, dark) }
        else -> colorSchemeFor(chosen, dark)
    }
    CompositionLocalProvider(
        LocalMetricColors provides metricsFor(chosen, colors, dark),
        LocalLetters provides chosenLetters,
        LocalStyle provides chosenStyle,
        LocalPhotoColors provides PhotoInk,
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
 * The signal colours stay the same in every theme, so a number keeps being recognised by its colour; only steps and
 * readiness follow the theme's main colour. Olive has its own muted set of sage, clay, ochre and dust, so the editorial
 * page reads as one quiet palette. The one-colour screens (Game Boy, Virtual Boy, Terminal) are the exception: shades of
 * their colour, as on the originals.
 */
private fun metricsFor(palette: Palette, colors: androidx.compose.material3.ColorScheme, dark: Boolean): MetricColors {
    val base = if (dark) DarkMetrics else LightMetrics
    if (palette == Palette.OLIVE) return if (dark) EditorialDarkMetrics else EditorialLightMetrics
    if (palette == Palette.HANDHELD) return base
    // The screens that only had one colour keep to its shades, as on the original.
    val shades: List<Color>? = when (palette) {
        Palette.GAMEBOY -> if (dark) listOf(Color(0xFF9BBC0F), Color(0xFF8BAC0F), Color(0xFFC4DE5A), Color(0xFF6FA02A))
        else listOf(Color(0xFF306230), Color(0xFF0F380F), Color(0xFF4F7A1E), Color(0xFF5E8C31))
        Palette.VIRTUALBOY -> if (dark) listOf(Color(0xFFFF3B3B), Color(0xFFFF8080), Color(0xFFFFB3B3), Color(0xFFE83A3A))
        else listOf(Color(0xFFB30000), Color(0xFFC23030), Color(0xFF7A0000), Color(0xFF990000))
        Palette.TERMINAL -> if (dark) listOf(Color(0xFF33FF66), Color(0xFF9CFFAD), Color(0xFF00D4A8), Color(0xFF66E08A))
        else listOf(Color(0xFF007A2E), Color(0xFF3B7A00), Color(0xFF00796B), Color(0xFF2E7D32))
        else -> null
    }
    if (shades != null) return MetricColors(shades[0], shades[1], shades[2], shades[3], shades[1], shades[2], shades[3], shades[0])
    return MetricColors(
        steps = colors.primary, heart = base.heart, stress = base.stress, battery = base.battery, sleep = base.sleep,
        distance = base.distance, calories = base.calories, readiness = colors.primary,
    )
}
