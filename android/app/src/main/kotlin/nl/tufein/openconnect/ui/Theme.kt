package nl.tufein.openconnect.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Arcade night": deep indigo screens, mint as the main colour and one colour per body signal, with monospace numbers
 * as a nod to old LCD scoreboards. The light scheme keeps the same colours on a pale indigo-white background.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF37F5A5),
    onPrimary = Color(0xFF00251A),
    primaryContainer = Color(0xFF0F3B33),
    onPrimaryContainer = Color(0xFFB8FFE0),
    secondary = Color(0xFFFFB547),
    onSecondary = Color(0xFF3A2400),
    secondaryContainer = Color(0xFF3F2F10),
    onSecondaryContainer = Color(0xFFFFDFA8),
    tertiary = Color(0xFF4FD3FF),
    onTertiary = Color(0xFF00303F),
    tertiaryContainer = Color(0xFF0F3447),
    onTertiaryContainer = Color(0xFFBFEBFF),
    background = Color(0xFF0B0E1A),
    onBackground = Color(0xFFE8ECFF),
    surface = Color(0xFF121729),
    onSurface = Color(0xFFE8ECFF),
    surfaceVariant = Color(0xFF1D2540),
    onSurfaceVariant = Color(0xFF9AA6D0),
    surfaceContainerLowest = Color(0xFF0B0E1A),
    surfaceContainerLow = Color(0xFF111628),
    surfaceContainer = Color(0xFF161C32),
    surfaceContainerHigh = Color(0xFF1D2540),
    surfaceContainerHighest = Color(0xFF252E4F),
    outline = Color(0xFF3A4570),
    outlineVariant = Color(0xFF262F52),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF3B0008),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00855A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F7E3),
    onPrimaryContainer = Color(0xFF00281B),
    secondary = Color(0xFF9A5B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2B8),
    onSecondaryContainer = Color(0xFF2E1A00),
    tertiary = Color(0xFF006C8E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6EBFF),
    onTertiaryContainer = Color(0xFF001F2C),
    background = Color(0xFFF4F6FD),
    onBackground = Color(0xFF141A33),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141A33),
    surfaceVariant = Color(0xFFE3E8F8),
    onSurfaceVariant = Color(0xFF4A5578),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FE),
    surfaceContainer = Color(0xFFECEFFA),
    surfaceContainerHigh = Color(0xFFE3E8F8),
    surfaceContainerHighest = Color(0xFFDBE1F5),
    outline = Color(0xFF7480A8),
    outlineVariant = Color(0xFFD5DAEE),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/**
 * The colour themes to choose from (v0.18.0). "arcade" is the original look above; the others are built from a few key
 * colours by [scheme]. "dynamic" takes the phone's own wallpaper colours on Android 12 and newer.
 */
enum class Palette(val key: String) {
    ARCADE("arcade"), CLEAN("clean"), MIDNIGHT("midnight"), GAMEBOY("gameboy"), AMBER("amber"), SYNTHWAVE("synthwave"), OCEAN("ocean"), SUNSET("sunset"), DYNAMIC("dynamic");

    companion object {
        fun of(key: String?): Palette = entries.firstOrNull { it.key == key } ?: ARCADE
    }
}

/** How the app is drawn: "sport" (cut corners, leaning headlines), "round" (soft and calm) or "pixel" (square, all monospace). */
enum class LookStyle(val key: String) {
    SPORT("sport"), ROUND("round"), PIXEL("pixel");

    companion object {
        fun of(key: String?): LookStyle = entries.firstOrNull { it.key == key } ?: SPORT
    }
}

/**
 * The three designs (v0.19.0). A design decides how Home is laid out, how cards and charts are drawn and how the tab bar
 * marks the open tab; colours and fonts stay free to choose. Picking a design in Settings also picks its own colours
 * and style ([preset]), which can be changed afterwards.
 * - CLEAN ("Clean Pro"): calm cards without borders, a big readiness ring on top, a "My day" list, soft area charts.
 * - DASHBOARD ("Dashboard Dark"): tiles in two columns first, bold rounded bars with today lit.
 * - RETRO ("Retro Pro"): the arcade look of v0.8.0 to v0.18.0, with borders, glows and glowing lines.
 */
enum class Design(val key: String, val palette: Palette, val style: LookStyle) {
    CLEAN("clean", Palette.CLEAN, LookStyle.ROUND),
    DASHBOARD("dashboard", Palette.MIDNIGHT, LookStyle.ROUND),
    RETRO("retro", Palette.ARCADE, LookStyle.SPORT);

    companion object {
        fun of(key: String?): Design = entries.firstOrNull { it.key == key } ?: RETRO
    }
}

/** The design in use; Retro unless the theme says otherwise, so previews and old screens look as before. */
val LocalDesign = staticCompositionLocalOf { Design.RETRO }

/** Cards keep their fine border only in the Retro design; the other two rely on calm surfaces. */
@Composable
fun cardBorder(): androidx.compose.foundation.BorderStroke? =
    if (LocalDesign.current == Design.RETRO) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null

/** The soft coloured glow at the top of a card belongs to the Retro design. */
@Composable
fun glowsCards(): Boolean = LocalDesign.current == Design.RETRO

/** The key colours of one theme, for its dark and its light version. */
private class Keys(
    val primary: Color, val secondary: Color, val tertiary: Color, val background: Color, val surface: Color, val text: Color,
)

private val PaletteKeys: Map<Palette, Pair<Keys, Keys>> = mapOf(
    // Clean Pro: calm blue on neutral grey and white.
    Palette.CLEAN to (Keys(Color(0xFF7AA7FF), Color(0xFF4FD1C5), Color(0xFFB794FF), Color(0xFF101317), Color(0xFF1A1E24), Color(0xFFECEFF4)) to
        Keys(Color(0xFF1F5FD1), Color(0xFF0E7C70), Color(0xFF6E56CF), Color(0xFFF5F6F8), Color(0xFFFFFFFF), Color(0xFF15181D))),
    // Dashboard Dark: pure black with green, for OLED screens.
    Palette.MIDNIGHT to (Keys(Color(0xFF3DDC97), Color(0xFF4FB3FF), Color(0xFFB794FF), Color(0xFF000000), Color(0xFF14171B), Color(0xFFF2F4F7)) to
        Keys(Color(0xFF0B7A50), Color(0xFF1268B3), Color(0xFF6E56CF), Color(0xFFF0F1F3), Color(0xFFFFFFFF), Color(0xFF101214))),
    // The four greens of the 1989 handheld's screen.
    Palette.GAMEBOY to (Keys(Color(0xFF9BBC0F), Color(0xFF8BAC0F), Color(0xFFC4DE5A), Color(0xFF0F380F), Color(0xFF1E4A1E), Color(0xFFD7EFA0)) to
        Keys(Color(0xFF306230), Color(0xFF4F7A1E), Color(0xFF0F380F), Color(0xFFCADC9F), Color(0xFFE0EDBE), Color(0xFF0F380F))),
    // An amber phosphor terminal.
    Palette.AMBER to (Keys(Color(0xFFFFB000), Color(0xFFFF7A00), Color(0xFFFFD45C), Color(0xFF0D0900), Color(0xFF1A1206), Color(0xFFFFE2A8)) to
        Keys(Color(0xFF8A5A00), Color(0xFFB34700), Color(0xFF6B4A00), Color(0xFFFFF6E5), Color(0xFFFFFFFF), Color(0xFF2B1D00))),
    // Neon pink and cyan over deep purple, like an eighties album cover.
    Palette.SYNTHWAVE to (Keys(Color(0xFFFF4FB8), Color(0xFF00E5FF), Color(0xFFFFD166), Color(0xFF14061F), Color(0xFF221033), Color(0xFFF5E6FF)) to
        Keys(Color(0xFFB0187A), Color(0xFF007A8C), Color(0xFF8C5A00), Color(0xFFFBF2FF), Color(0xFFFFFFFF), Color(0xFF2A0E3A))),
    Palette.OCEAN to (Keys(Color(0xFF4FC3F7), Color(0xFF26E0C2), Color(0xFFB39DFF), Color(0xFF06141F), Color(0xFF0C2030), Color(0xFFE2F3FF)) to
        Keys(Color(0xFF0061A4), Color(0xFF00796B), Color(0xFF5B45C2), Color(0xFFF2F8FD), Color(0xFFFFFFFF), Color(0xFF0B2233))),
    Palette.SUNSET to (Keys(Color(0xFFFF8A5B), Color(0xFFFFC857), Color(0xFFE85D9A), Color(0xFF1A0E0B), Color(0xFF2A1712), Color(0xFFFFEDE4)) to
        Keys(Color(0xFFB34A1E), Color(0xFF946200), Color(0xFFA8326A), Color(0xFFFFF5EF), Color(0xFFFFFFFF), Color(0xFF331A10))),
)

/** A full colour scheme from a few key colours: containers are the key colour mixed into the background. */
private fun scheme(k: Keys, dark: Boolean): androidx.compose.material3.ColorScheme {
    fun mix(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
    val on = if (dark) Color(0xFF0A0A0A) else Color.White
    val muted = mix(k.text, k.background, 0.35f)
    fun container(c: Color) = mix(k.background, c, if (dark) 0.28f else 0.22f)
    fun onContainer(c: Color) = mix(c, k.text, if (dark) 0.55f else 0.75f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = k.primary, onPrimary = on, primaryContainer = container(k.primary), onPrimaryContainer = onContainer(k.primary),
        secondary = k.secondary, onSecondary = on, secondaryContainer = container(k.secondary), onSecondaryContainer = onContainer(k.secondary),
        tertiary = k.tertiary, onTertiary = on, tertiaryContainer = container(k.tertiary), onTertiaryContainer = onContainer(k.tertiary),
        background = k.background, onBackground = k.text, surface = k.surface, onSurface = k.text,
        surfaceVariant = mix(k.surface, k.primary, 0.10f), onSurfaceVariant = muted,
        surfaceContainerLowest = k.background, surfaceContainerLow = mix(k.background, k.surface, 0.5f),
        surfaceContainer = mix(k.surface, k.primary, 0.05f), surfaceContainerHigh = mix(k.surface, k.primary, 0.10f),
        surfaceContainerHighest = mix(k.surface, k.primary, 0.15f),
        outline = mix(k.background, k.text, 0.35f), outlineVariant = mix(k.background, k.text, 0.15f),
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
    steps = Color(0xFF37F5A5),
    heart = Color(0xFFFF5C7A),
    stress = Color(0xFFFFA24A),
    battery = Color(0xFF4DA8FF),
    sleep = Color(0xFFA58BFF),
    distance = Color(0xFF4FD3FF),
    calories = Color(0xFFFFC84A),
    readiness = Color(0xFF37F5A5),
)

private val LightMetrics = MetricColors(
    steps = Color(0xFF00855A),
    heart = Color(0xFFD63355),
    stress = Color(0xFFC26A00),
    battery = Color(0xFF1F6FD1),
    sleep = Color(0xFF6C4FE0),
    distance = Color(0xFF007FA6),
    calories = Color(0xFFA87400),
    readiness = Color(0xFF00855A),
)

val LocalMetricColors = staticCompositionLocalOf { DarkMetrics }

/** The colours of the signals in the current theme. */
object OpenConnectColors {
    val metrics: MetricColors
        @Composable get() = LocalMetricColors.current
}

private val Mono = FontFamily.Monospace

/** Headlines and big numbers lean forward in heavy italics, like a race bib; labels keep the monospace scoreboard. */
private val Sport = FontFamily.SansSerif

private val SportTypography = Typography(
    displayMedium = TextStyle(fontFamily = Sport, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 44.sp, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontFamily = Sport, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Sport, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Sport, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sport, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

/** Calm: upright headlines, normal weights. */
private val RoundTypography = Typography(
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

/** Everything in monospace, like an old terminal or a handheld's text box. */
private val PixelTypography = Typography(
    displayMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    headlineMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 15.sp),
)

/**
 * Angular instead of round: two opposite corners are cut at an angle, like the nose of a race bike or a sports shoe.
 * Cards use [medium], which is the signature shape of the app.
 */
private val SportShapes = Shapes(
    extraSmall = CutCornerShape(topEnd = 6.dp, bottomStart = 6.dp),
    small = CutCornerShape(topEnd = 10.dp, bottomStart = 10.dp),
    medium = CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp),
    large = CutCornerShape(topEnd = 24.dp, bottomStart = 24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val RoundShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Square corners, like pixels. */
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** [mode] is "system" (follow the phone), "dark" or "light"; [palette], [style] and [design] are keys of [Palette], [LookStyle] and [Design]. */
@Composable
fun OpenConnectTheme(mode: String = "system", palette: String = "arcade", style: String = "sport", design: String = "retro", content: @Composable () -> Unit) {
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
        else -> PaletteKeys[chosen]?.let { (d, l) -> scheme(if (dark) d else l, dark) } ?: if (dark) DarkColors else LightColors
    }
    val look = LookStyle.of(style)
    CompositionLocalProvider(LocalMetricColors provides metricsFor(chosen, colors, dark), LocalDesign provides Design.of(design)) {
        MaterialTheme(
            colorScheme = colors,
            typography = when (look) {
                LookStyle.SPORT -> SportTypography
                LookStyle.ROUND -> RoundTypography
                LookStyle.PIXEL -> PixelTypography
            },
            shapes = when (look) {
                LookStyle.SPORT -> SportShapes
                LookStyle.ROUND -> RoundShapes
                LookStyle.PIXEL -> PixelShapes
            },
            content = content,
        )
    }
}

/**
 * The signal colours stay the same in every theme, so a number keeps being recognised by its colour; only steps and
 * readiness follow the theme's main colour. The Game Boy theme is the exception: four shades of green, as on the original.
 */
private fun metricsFor(palette: Palette, colors: androidx.compose.material3.ColorScheme, dark: Boolean): MetricColors {
    val base = if (dark) DarkMetrics else LightMetrics
    if (palette == Palette.ARCADE) return base
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
